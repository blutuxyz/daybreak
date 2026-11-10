import { userKey } from './assistant-store.js';

/**
 * The AI assistant of the app ("Frag Daybreak" / "Ask Daybreak"), served through OpenRouter.
 *
 * The app sends the chat, a short summary of the student's own timetable and, for the newest question,
 * pictures or files. This server adds the instructions and the tool definitions, picks the model the admin
 * allowed, checks the user's daily budget and streams the answer back. The OpenRouter key stays here.
 *
 * Tools run in the app, not here: the timetable, exams and the WebUntis session live on the phone. When
 * the model calls a tool, the server passes the calls to the app, the app runs them (asking the student
 * first for anything that sends a message) and posts the chat again with the results.
 */

export const ASSISTANT_LIMITS = Object.freeze({
  maxMessages: 48,
  maxTextChars: 4000,
  maxToolChars: 24_000,
  maxContextChars: 40_000,
  maxAttachments: 4,
  /** Base64 characters of all attachments in one request (about 9 MB of files). */
  maxAttachmentChars: 12_000_000,
  maxOutputTokens: 2048,
});

const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
const TEXT_TYPES = new Set(['text/plain', 'text/markdown', 'text/csv']);
const FILE_TYPES = new Set(['application/pdf', ...TEXT_TYPES]);

/** The tools the app implements (AssistantTools.kt). Their names and parameters are the contract. */
export const TOOLS = [
  {
    name: 'get_lessons',
    description:
      "Lessons of the student's own timetable between two dates (at most 31 days), with teacher, room, cancellations, substitutions, the teacher's lesson text and info notes, and for ranges up to 14 days the class register topic (what was covered). Use it for dates outside the summary or when you need the notes and topics.",
    parameters: {
      type: 'object',
      properties: {
        start_date: { type: 'string', description: 'First day, YYYY-MM-DD' },
        end_date: { type: 'string', description: 'Last day, YYYY-MM-DD' },
      },
      required: ['start_date', 'end_date'],
    },
  },
  {
    name: 'get_exams',
    description: 'Exams and tests of the student, with the text the school entered. Defaults to the next 120 days.',
    parameters: {
      type: 'object',
      properties: {
        start_date: { type: 'string', description: 'YYYY-MM-DD, default today' },
        end_date: { type: 'string', description: 'YYYY-MM-DD, default today + 120 days' },
      },
    },
  },
  {
    name: 'get_lesson_notes',
    description:
      "Everything teachers wrote on the lessons of one subject over a period, newest first: the class register topic (what each lesson covered), lesson text, info and substitution notes. Teachers often put the topics or the material for the next exam there, so use it for 'what do I have to study for…' and 'what did we do in…'.",
    parameters: {
      type: 'object',
      properties: {
        subject: { type: 'string', description: 'Subject short or long name, as in the timetable' },
        weeks_back: { type: 'integer', description: 'How many weeks back to look, 1 to 12, default 6' },
      },
      required: ['subject'],
    },
  },
  {
    name: 'get_homework',
    description: 'Homework with due dates. Defaults to the last 7 and the next 21 days.',
    parameters: {
      type: 'object',
      properties: {
        start_date: { type: 'string', description: 'YYYY-MM-DD' },
        end_date: { type: 'string', description: 'YYYY-MM-DD' },
      },
    },
  },
  {
    name: 'get_absences',
    description: "The student's recorded absences of the whole school year with totals, excused or not, and reasons.",
    parameters: { type: 'object', properties: {} },
  },
  {
    name: 'show_timetable',
    description:
      "Shows the student one day of their own timetable as a card in the chat (every lesson with time, subject, teacher, room, cancellations, substitutions, room changes and exams), built from their real data. Use it whenever the student asks what lessons they have on a day (today, tomorrow, Monday, a date). The result lists the same lessons for you.",
    parameters: {
      type: 'object',
      properties: {
        date: { type: 'string', description: 'The day, YYYY-MM-DD' },
      },
      required: ['date'],
    },
  },
  {
    name: 'list_teachers',
    description: 'The teachers the student is allowed to write a WebUntis message to, with the user_id needed by draft_message.',
    parameters: { type: 'object', properties: {} },
  },
  {
    name: 'draft_message',
    description:
      'Prepares a WebUntis message to one teacher. The app shows the draft to the student, who must confirm it before anything is sent; the result says whether it was sent or cancelled. Get the user_id from list_teachers first. Write the message in the language and tone the student asked for.',
    parameters: {
      type: 'object',
      properties: {
        recipient_user_id: { type: 'integer', description: 'user_id from list_teachers' },
        subject: { type: 'string', description: 'Subject line, short' },
        body: { type: 'string', description: 'The message text' },
      },
      required: ['recipient_user_id', 'subject', 'body'],
    },
  },
];

const TOOL_NAMES = new Set(TOOLS.map((tool) => tool.name));
const LANGUAGES = new Set(['de', 'en']);

export const SYSTEM_PROMPT = `You are the assistant inside Daybreak, an unofficial WebUntis timetable app for school students.
You help one student with their own school week: lessons, cancellations and substitutions, rooms and teachers, exams and what to study for them, homework, holidays and absences. You can also help plan revision, read pictures or files the student attaches (worksheets, notes, a photo of the board), explain how to use the app, and answer short general school questions.

How to work:
- The <timetable> block is a summary the app exported a moment ago. For other dates, lesson notes and topics, homework, absences or teachers, call the tools; they read the student's real data. Never invent lessons, rooms, teachers, exams, topics or times. If something is not in the data, say so.
- "What do I have today / tomorrow / on Monday": call show_timetable for that day. The card already shows every lesson, so do not list them again; add one or two short sentences on what stands out (cancellations, substitutions, room changes, exams, notes), or just a short friendly line if the day is ordinary. If show_timetable is not available, list the lessons as a short list instead.
- Absences: the summary gives the totals of the whole school year; call get_absences for details. Only say there are no absences if the data says "none recorded".
- For "what do I have to study for my next exam": find the exam (get_exams), then read the teachers' notes on that subject (get_lesson_notes) and the exam text, and summarise the topics. Say clearly when no topics were written down.
- Messages to teachers: only when the student asks. Call list_teachers, then draft_message. The student confirms in the app; never say a message was sent unless the tool result says "sent".
- Answer in the language the app uses (<app_language>) unless the student writes in another one.
- Be brief and friendly, like a helpful classmate: usually one to four short sentences or a short list. 24-hour times (08:00). Name days by weekday and date.
- Format with Markdown: short paragraphs, "- " bullet lists, "1. " numbered steps for worked solutions, **bold** for a key time, room or result. A "### " heading only to split a longer explanation, a small table only to compare a few things side by side. Code blocks only for real program code.
- Maths: always LaTeX inside delimiters, $...$ inline and $$...$$ on a line of its own for bigger formulas; the app renders it. Never write LaTeX commands (\\frac, \\sqrt, ^{...}) without delimiters, never put formulas in code blocks or backticks, and use \\begin{aligned}...\\end{aligned} only inside $$...$$. Write a plain number with a unit as text (5 €, 3 cm), not as a formula.
- For homework or quiz questions, explain the steps so the student learns the method, not only the final answer.
- You cannot change anything in WebUntis or see other students' data.

About the app, for "how do I" questions: the Timetable tab shows days and weeks (swipe between days, pinch to zoom, tap a lesson for details, the arrows icon switches to class, teacher or room timetables); the checklist icon opens homework and exams; the Absences tab lists absences and totals; More holds settings (appearance, AMOLED black, lesson colours, widget, notifications, sync, language), the profile, messages, and "Tutorial" to replay the introduction.`;

export function contextBlock({ language, context, today }) {
  return [
    `<app_language>${language === 'de' ? 'German' : 'English'}</app_language>`,
    today ? `<today>${today}</today>` : '',
    `<timetable>\n${context.trim() || '(no timetable data was loaded in the app)'}\n</timetable>`,
  ]
    .filter(Boolean)
    .join('\n');
}

function text(value, max) {
  return typeof value === 'string' && value.length <= max ? value : undefined;
}

/**
 * Checks the body the app posts. Returns { value } or { error }.
 *
 *   { language, today, context, model?, unlockCode?, messages: [
 *       { role: 'user', text, attachments?: [{ type: 'image'|'file', name, mime, data(base64) }] },
 *       { role: 'assistant', text?, toolCalls?: [{ id, name, arguments }] },
 *       { role: 'tool', toolCallId, content } ] }
 */
export function parseAssistantRequest(body) {
  if (!body || typeof body !== 'object') return { error: 'bad_request' };
  const language = LANGUAGES.has(body.language) ? body.language : 'en';
  const context = typeof body.context === 'string' ? body.context : '';
  if (context.length > ASSISTANT_LIMITS.maxContextChars) return { error: 'context_too_long' };
  const today = typeof body.today === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(body.today) ? body.today : undefined;
  const model = typeof body.model === 'string' ? body.model.slice(0, 120) : undefined;
  const unlockCode = typeof body.unlockCode === 'string' ? body.unlockCode.slice(0, 32) : undefined;
  const effort = typeof body.effort === 'string' ? body.effort.slice(0, 16) : undefined;

  if (!Array.isArray(body.messages) || body.messages.length === 0) return { error: 'no_messages' };
  if (body.messages.length > ASSISTANT_LIMITS.maxMessages) return { error: 'too_many_messages' };

  let attachmentChars = 0;
  const pendingCalls = new Set();
  const messages = [];
  for (const raw of body.messages) {
    const role = raw?.role;
    if (role === 'user') {
      if (pendingCalls.size > 0) return { error: 'bad_order' };
      const content = text(raw.text ?? '', ASSISTANT_LIMITS.maxTextChars);
      if (content === undefined) return { error: 'message_too_long' };
      const attachments = [];
      if (raw.attachments !== undefined) {
        if (!Array.isArray(raw.attachments) || raw.attachments.length > ASSISTANT_LIMITS.maxAttachments) {
          return { error: 'bad_attachment' };
        }
        for (const attachment of raw.attachments) {
          const type = attachment?.type;
          const mime = typeof attachment?.mime === 'string' ? attachment.mime.toLowerCase() : '';
          const data = typeof attachment?.data === 'string' ? attachment.data : '';
          const name = typeof attachment?.name === 'string' ? attachment.name.slice(0, 120) : 'file';
          const allowed = type === 'image' ? IMAGE_TYPES.has(mime) : type === 'file' ? FILE_TYPES.has(mime) : false;
          if (!allowed || data.length === 0 || !/^[A-Za-z0-9+/=]+$/.test(data)) return { error: 'bad_attachment' };
          attachmentChars += data.length;
          attachments.push({ type, mime, data, name });
        }
      }
      if (!content.trim() && attachments.length === 0) return { error: 'empty_message' };
      messages.push({ role, text: content.trim(), attachments });
    } else if (role === 'assistant') {
      const content = text(raw.text ?? '', ASSISTANT_LIMITS.maxToolChars);
      if (content === undefined) return { error: 'message_too_long' };
      const toolCalls = [];
      for (const call of Array.isArray(raw.toolCalls) ? raw.toolCalls : []) {
        const id = text(call?.id, 120);
        const name = call?.name;
        const args = text(call?.arguments ?? '{}', 8000);
        if (!id || !TOOL_NAMES.has(name) || args === undefined) return { error: 'bad_tool_call' };
        toolCalls.push({ id, name, arguments: args });
        pendingCalls.add(id);
      }
      if (!content.trim() && toolCalls.length === 0) return { error: 'empty_message' };
      messages.push({ role, text: content, toolCalls });
    } else if (role === 'tool') {
      const toolCallId = text(raw.toolCallId, 120);
      const content = text(raw.content, ASSISTANT_LIMITS.maxToolChars);
      if (!toolCallId || content === undefined || !pendingCalls.has(toolCallId)) return { error: 'bad_tool_result' };
      pendingCalls.delete(toolCallId);
      messages.push({ role, toolCallId, content });
    } else {
      return { error: 'bad_role' };
    }
  }
  if (attachmentChars > ASSISTANT_LIMITS.maxAttachmentChars) return { error: 'attachments_too_large' };
  if (messages[0].role !== 'user') return { error: 'bad_order' };
  const last = messages.at(-1).role;
  if (last === 'assistant' || pendingCalls.size > 0) return { error: 'bad_order' };
  return { value: { language, context, today, model, unlockCode, effort, messages } };
}

/** The chat in the Chat Completions format OpenRouter takes. */
export function toOpenRouterMessages(request) {
  const out = [
    {
      role: 'system',
      content: [
        // Cached by providers that support it (Anthropic, Gemini); the instructions never change.
        { type: 'text', text: SYSTEM_PROMPT, cache_control: { type: 'ephemeral' } },
        { type: 'text', text: contextBlock(request) },
      ],
    },
  ];
  for (const message of request.messages) {
    if (message.role === 'user') {
      if (message.attachments.length === 0) {
        out.push({ role: 'user', content: message.text });
        continue;
      }
      const parts = [];
      for (const attachment of message.attachments) {
        if (attachment.type === 'image') {
          parts.push({ type: 'image_url', image_url: { url: `data:${attachment.mime};base64,${attachment.data}` } });
        } else if (TEXT_TYPES.has(attachment.mime)) {
          // Text files go in as text, which every model can read.
          const content = Buffer.from(attachment.data, 'base64').toString('utf8').slice(0, 60_000);
          parts.push({ type: 'text', text: `<file name="${attachment.name.replace(/"/g, "'")}">\n${content}\n</file>` });
        } else {
          parts.push({
            type: 'file',
            file: { filename: attachment.name, file_data: `data:${attachment.mime};base64,${attachment.data}` },
          });
        }
      }
      if (message.text) parts.push({ type: 'text', text: message.text });
      out.push({ role: 'user', content: parts });
    } else if (message.role === 'assistant') {
      const entry = { role: 'assistant', content: message.text || null };
      if (message.toolCalls.length > 0) {
        entry.tool_calls = message.toolCalls.map((call) => ({
          id: call.id,
          type: 'function',
          function: { name: call.name, arguments: call.arguments },
        }));
      }
      out.push(entry);
    } else {
      out.push({ role: 'tool', tool_call_id: message.toolCallId, content: message.content });
    }
  }
  return out;
}

/** The request body for OpenRouter's chat completions. */
/** The reasoning effort for a request: the user's pick when the picker is shown, else the admin's default. */
export function pickEffort(settings, requested) {
  if (settings.showEffortSelector && settings.efforts.includes(requested)) return requested;
  return settings.defaultEffort || undefined;
}

export function buildOpenRouterBody({ model, request, settings }) {
  const provider = { allow_fallbacks: settings.allowFallbacks };
  if (settings.providerSort !== 'default') provider.sort = settings.providerSort;
  if (settings.denyDataCollection) provider.data_collection = 'deny';
  return {
    model,
    stream: true,
    max_tokens: ASSISTANT_LIMITS.maxOutputTokens,
    messages: toOpenRouterMessages(request),
    tools: TOOLS.map((tool) => ({ type: 'function', function: tool })),
    tool_choice: 'auto',
    provider,
    ...(pickEffort(settings, request.effort) ? { reasoning: { effort: pickEffort(settings, request.effort) } } : {}),
  };
}

/** Reads a server-sent event stream line by line from a fetch body. */
async function* sseLines(body) {
  const decoder = new TextDecoder();
  let buffer = '';
  for await (const chunk of body) {
    buffer += decoder.decode(chunk, { stream: true });
    let index;
    while ((index = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '');
      buffer = buffer.slice(index + 1);
      yield line;
    }
  }
  if (buffer) yield buffer;
}

/** An error from OpenRouter or the model's provider. */
export class UpstreamError extends Error {
  constructor(message, { status, code = 'upstream' } = {}) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/**
 * Streams one completion from OpenRouter. Yields { type: 'text' }, then at the end
 * { type: 'finish', toolCalls, usage, provider, model }. Throws UpstreamError.
 */
export async function* streamOpenRouter({ fetchImpl = fetch, apiKey, baseUrl, referer, body, signal }) {
  let response;
  try {
    response = await fetchImpl(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'HTTP-Referer': referer,
        'X-Title': 'Daybreak',
      },
      body: JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new UpstreamError(`could not reach OpenRouter: ${error?.message ?? error}`);
  }
  if (!response.ok) {
    let detail = '';
    try {
      const parsed = JSON.parse(await response.text());
      detail = parsed?.error?.message ?? JSON.stringify(parsed).slice(0, 500);
    } catch {
      detail = '';
    }
    throw new UpstreamError(`OpenRouter answered ${response.status}${detail ? `: ${detail}` : ''}`, {
      status: response.status,
      code: response.status === 429 ? 'busy' : 'upstream',
    });
  }

  const calls = new Map();
  let usage;
  let provider;
  let servedModel;
  let finishReason;
  for await (const line of sseLines(response.body)) {
    if (!line.startsWith('data:')) continue;
    const data = line.slice(5).trim();
    if (data === '[DONE]') break;
    let chunk;
    try {
      chunk = JSON.parse(data);
    } catch {
      continue;
    }
    if (chunk.error) {
      throw new UpstreamError(`provider error: ${chunk.error.message ?? JSON.stringify(chunk.error).slice(0, 300)}`, {
        status: chunk.error.code,
      });
    }
    if (chunk.usage) usage = chunk.usage;
    if (chunk.provider) provider = chunk.provider;
    if (chunk.model) servedModel = chunk.model;
    const choice = chunk.choices?.[0];
    if (!choice) continue;
    if (choice.finish_reason) finishReason = choice.finish_reason;
    if (choice.finish_reason === 'error') throw new UpstreamError('provider ended the answer with an error');
    const delta = choice.delta ?? {};
    // Models that reason stream it as delta.reasoning before the answer (OpenRouter's format).
    if (typeof delta.reasoning === 'string' && delta.reasoning) yield { type: 'reasoning', text: delta.reasoning };
    if (typeof delta.content === 'string' && delta.content) yield { type: 'text', text: delta.content };
    for (const call of delta.tool_calls ?? []) {
      const key = call.index ?? 0;
      const current = calls.get(key) ?? { id: '', name: '', arguments: '' };
      if (call.id) current.id = call.id;
      if (call.function?.name) current.name += call.function.name;
      if (call.function?.arguments) current.arguments += call.function.arguments;
      calls.set(key, current);
    }
  }
  const toolCalls = [...calls.values()]
    .filter((call) => call.name)
    .map((call, index) => ({ id: call.id || `call_${index}`, name: call.name, arguments: call.arguments || '{}' }));
  yield { type: 'finish', toolCalls, usage, provider, model: servedModel, finishReason };
}

/** Checks the body of POST /v1/assistant/title: { language, question, answer, model? }. */
export function parseTitleRequest(body) {
  if (!body || typeof body !== 'object') return { error: 'bad_request' };
  const question = typeof body.question === 'string' ? body.question.trim().slice(0, 600) : '';
  const answer = typeof body.answer === 'string' ? body.answer.trim().slice(0, 1500) : '';
  if (!question && !answer) return { error: 'empty_message' };
  return {
    value: {
      language: LANGUAGES.has(body.language) ? body.language : 'en',
      question,
      answer,
      model: typeof body.model === 'string' ? body.model.slice(0, 120) : undefined,
    },
  };
}

/** A title as the model wrote it, made safe for a list: one line, no quotes or trailing dot, 60 chars. */
export function cleanTitle(text) {
  if (typeof text !== 'string') return '';
  let title = text.replace(/\s+/g, ' ').trim();
  title = title.replace(/^(title|titel)\s*:\s*/i, '').replace(/^["'„“”«»*]+|["'„“”«»*.!]+$/g, '').trim();
  return title.length > 60 ? `${title.slice(0, 59).trimEnd()}…` : title;
}

/** A short, safe copy of the chat for the admin log. */
function logContent(request, answer, toolCalls) {
  const clip = (value, max) => (value.length > max ? `${value.slice(0, max)}… (${value.length} chars)` : value);
  return {
    context: clip(request.context, 6000),
    messages: request.messages.map((message) => {
      if (message.role === 'user') {
        return {
          role: 'user',
          text: clip(message.text, 4000),
          attachments: message.attachments.map((a) => `${a.type} ${a.name} (${a.mime}, ${Math.round((a.data.length * 3) / 4 / 1024)} KB)`),
        };
      }
      if (message.role === 'assistant') {
        return { role: 'assistant', text: clip(message.text, 4000), toolCalls: message.toolCalls.map((c) => `${c.name}(${clip(c.arguments, 500)})`) };
      }
      return { role: 'tool', text: clip(message.content, 3000) };
    }),
    answer: clip(answer, 8000),
    toolCalls: toolCalls.map((c) => `${c.name}(${clip(c.arguments, 1000)})`),
  };
}

/**
 * Runs one assistant request: settings, budget, model choice, the stream, the spend and the log.
 * [send] receives the events for the app.
 */
export class AssistantService {
  constructor({ config, store, support, notices, fetchImpl = fetch, now = () => Date.now(), random = Math.random }) {
    this.config = config;
    this.store = store;
    this.support = support;
    this.notices = notices;
    this.random = random;
    this.fetchImpl = fetchImpl;
    this.now = now;
  }

  get configured() {
    return Boolean(this.config.openRouterApiKey);
  }

  /**
   * The configured model ids OpenRouter does not know, for a warning on the admin page; null when the
   * model list could not be read. The list is public and asked at most once an hour.
   */
  async unknownModels(ids) {
    const time = this.now();
    if (!this.modelList || time - this.modelList.at > 3_600_000) {
      try {
        const response = await this.fetchImpl(`${this.config.openRouterUrl}/models`, { headers: { Accept: 'application/json' } });
        if (!response.ok) return null;
        const parsed = await response.json();
        this.modelList = { at: time, ids: new Set((parsed?.data ?? []).map((model) => model.id)) };
      } catch {
        return null;
      }
    }
    return ids.filter((id) => !this.modelList.ids.has(id));
  }

  /** The client-side switch: feature "assistant" under Switches on the admin page. */
  async clientSwitchOn() {
    if (!this.notices) return true;
    try {
      const status = await this.notices.status();
      return !status.disabledFeatures.includes('assistant');
    } catch {
      return true;
    }
  }

  /** The server-side switch, which is also off while the client-side switch is off. */
  async serverSwitchOn(settings) {
    return settings.enabled && (await this.clientSwitchOn());
  }

  /**
   * What the app may pick from, or null when the app should hide the assistant (no key, or the client-side
   * switch is off). The server-side switch does not hide it: requests then fail with the upstream error.
   */
  async models() {
    const settings = await this.store.settings();
    if (!this.configured || !(await this.clientSwitchOn())) return null;
    return {
      showEffort: settings.showEffortSelector && settings.efforts.length > 0,
      efforts: settings.showEffortSelector ? settings.efforts : [],
      defaultEffort: settings.showEffortSelector ? settings.defaultEffort || settings.efforts.at(-1) || null : null,
      showSwitcher: settings.showModelSwitcher,
      models: settings.showModelSwitcher ? settings.models : [],
      defaultModel: settings.showModelSwitcher ? settings.defaultModel : null,
    };
  }

  /** The model for one request: the user's pick when the switcher is shown, else fixed or random. */
  pickModel(settings, requested) {
    const byId = (id) => settings.models.find((model) => model.id === id);
    if (settings.showModelSwitcher) return byId(requested) ?? byId(settings.defaultModel);
    if (settings.hiddenRouting === 'random' && settings.models.length > 0) {
      return settings.models[Math.min(Math.floor(this.random() * settings.models.length), settings.models.length - 1)];
    }
    return byId(settings.defaultModel);
  }

  /**
   * A short title for a new chat, from its first question and answer. Returns the title, or null when
   * it cannot or should not be made (switched off, over budget, any failure): the app then keeps the
   * first question as the name. Counts against the same daily budget.
   */
  async title({ request, installId }) {
    const settings = await this.store.settings();
    if (!this.configured || !settings.aiTitles || !(await this.serverSwitchOn(settings))) return null;
    const user = userKey(installId);
    const limit = settings.freeCentsPerDay > settings.paidCentsPerDay ? settings.freeCentsPerDay : settings.paidCentsPerDay;
    if ((await this.store.spentToday(user)) >= limit) return null;
    const model = this.pickModel(settings, request.model);
    if (!model) return null;
    const started = this.now();
    const provider = { allow_fallbacks: settings.allowFallbacks };
    if (settings.providerSort !== 'default') provider.sort = settings.providerSort;
    if (settings.denyDataCollection) provider.data_collection = 'deny';
    const entry = { user, paid: false, model: model.id, status: 'title', costCents: 0 };
    try {
      const response = await this.fetchImpl(`${this.config.openRouterUrl}/chat/completions`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.config.openRouterApiKey}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': this.config.publicUrl ?? 'https://github.com/blutuxyz/daybreak',
          'X-Title': 'Daybreak',
        },
        body: JSON.stringify({
          model: model.id,
          max_tokens: 24,
          provider,
          messages: [
            {
              role: 'system',
              content: `Write a short title of 2 to 5 words for this chat between a school student and an assistant, in ${request.language === 'de' ? 'German' : 'English'}. Reply with the title only: no quotes, no full stop, no emoji.`,
            },
            { role: 'user', content: `Question: ${request.question}

Answer: ${request.answer}` },
          ],
        }),
      });
      if (!response.ok) throw new Error(`OpenRouter answered ${response.status}`);
      const parsed = await response.json();
      const cents = Number(parsed?.usage?.cost ?? 0) * 100;
      entry.costCents = Number.isFinite(cents) ? cents : 0;
      await this.store.addSpend(user, entry.costCents);
      const title = cleanTitle(parsed?.choices?.[0]?.message?.content);
      await this.store.addLog({ ...entry, durationMs: this.now() - started, ...(settings.logContent ? { content: { context: '', messages: [], answer: title, toolCalls: [] } } : {}) });
      return title || null;
    } catch (error) {
      await this.store.addLog({ ...entry, status: 'error', error: `title: ${String(error?.message ?? error).slice(0, 300)}`, durationMs: this.now() - started });
      return null;
    }
  }

  async run({ request, installId, send, signal }) {
    const started = this.now();
    const settings = await this.store.settings();
    const user = userKey(installId);
    const paid = Boolean(request.unlockCode && this.support && (await this.support.isPaid(request.unlockCode)));
    const entry = { user, paid, model: '', status: 'ok', durationMs: 0, costCents: 0 };
    const finish = async (extra) => {
      Object.assign(entry, extra, { durationMs: this.now() - started });
      if (!settings.logContent) delete entry.content;
      await this.store.addLog(entry);
    };

    if (!this.configured || !(await this.serverSwitchOn(settings))) {
      // Switched off on the server: the app shows the same message as for a provider failure.
      send({ type: 'error', code: 'upstream' });
      return finish({ status: 'error', error: 'assistant switched off on the admin page' });
    }
    // The budget is not shown in the app: a user over it only learns the assistant is not available now.
    const limit = paid ? settings.paidCentsPerDay : settings.freeCentsPerDay;
    const spent = await this.store.spentToday(user);
    if (spent >= limit) {
      send({ type: 'error', code: 'limit' });
      return finish({ status: 'limit', error: `daily budget used: ${spent.toFixed(3)} of ${limit} cents` });
    }
    const allowed = this.pickModel(settings, request.model);
    if (!allowed) {
      send({ type: 'error', code: 'upstream' });
      return finish({ status: 'error', error: 'no model configured on the admin page' });
    }
    entry.model = allowed.id;
    entry.effort = pickEffort(settings, request.effort);

    let answer = '';
    let reasoning = '';
    let toolCalls = [];
    try {
      const body = buildOpenRouterBody({ model: allowed.id, request, settings });
      for await (const event of streamOpenRouter({
        fetchImpl: this.fetchImpl,
        apiKey: this.config.openRouterApiKey,
        baseUrl: this.config.openRouterUrl,
        referer: this.config.publicUrl ?? 'https://github.com/blutuxyz/daybreak',
        body,
        signal,
      })) {
        if (event.type === 'text') {
          answer += event.text;
          send(event);
        } else if (event.type === 'reasoning') {
          reasoning += event.text;
          send(event);
        } else {
          toolCalls = event.toolCalls;
          const cents = Number(event.usage?.cost ?? 0) * 100;
          entry.costCents = Number.isFinite(cents) ? cents : 0;
          entry.promptTokens = event.usage?.prompt_tokens;
          entry.completionTokens = event.usage?.completion_tokens;
          entry.provider = event.provider;
          entry.servedModel = event.model;
          entry.finishReason = event.finishReason;
        }
      }
      await this.store.addSpend(user, entry.costCents);
      if (toolCalls.length > 0) send({ type: 'tool_calls', calls: toolCalls });
      send({ type: 'done' });
      entry.content = { ...logContent(request, answer, toolCalls), reasoning: reasoning.slice(0, 8000) };
      return finish({ status: toolCalls.length > 0 ? 'tool_calls' : 'ok' });
    } catch (error) {
      entry.content = logContent(request, answer, toolCalls);
      if (signal?.aborted) return finish({ status: 'aborted' });
      const code = error instanceof UpstreamError ? error.code : 'upstream';
      send({ type: 'error', code });
      return finish({ status: 'error', error: String(error?.message ?? error).slice(0, 1000), upstreamStatus: error?.status });
    }
  }
}
