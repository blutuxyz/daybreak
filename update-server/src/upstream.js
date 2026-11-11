/**
 * The providers the assistant can ask: OpenRouter, any OpenAI-compatible chat completions server, and any
 * Anthropic-compatible messages server. Each one answers in its own format; everything here turns that into
 * the same events the app gets: { type: 'text' }, { type: 'reasoning' } and, at the end, one { type: 'finish' }
 * with the tool calls and the usage.
 */

/** An error from a provider or the model behind it. */
export class UpstreamError extends Error {
  constructor(message, { status, code = 'upstream' } = {}) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** Reads a server-sent event stream line by line from a fetch body. */
export async function* sseLines(body) {
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

/** The provider a model is served by, or null when there is none (it was removed from the list). */
export function providerFor(settings, model) {
  return settings.providers.find((provider) => provider.id === model.provider) ?? null;
}

/**
 * Where a provider is reached and with which key. OpenRouter falls back to the environment (the key and
 * address the server was set up with); the other kinds need both on the admin page.
 */
export function connectionFor(provider, config) {
  if (provider.type === 'openrouter') {
    return {
      baseUrl: (provider.baseUrl || config.openRouterUrl).replace(/\/$/, ''),
      apiKey: provider.apiKey || config.openRouterApiKey || '',
    };
  }
  return { baseUrl: provider.baseUrl.replace(/\/$/, ''), apiKey: provider.apiKey };
}

/** The cost of one answer in US cents: what OpenRouter reports, or the model's prices per million tokens. */
export function costCents(provider, model, usage) {
  if (provider.type === 'openrouter' && usage?.cost !== undefined) {
    const cents = Number(usage.cost) * 100;
    return Number.isFinite(cents) ? cents : 0;
  }
  const prompt = Number(usage?.prompt_tokens ?? 0);
  const completion = Number(usage?.completion_tokens ?? 0);
  const dollars = (prompt * (model.inputPrice ?? 0) + completion * (model.outputPrice ?? 0)) / 1_000_000;
  return Number.isFinite(dollars) ? dollars * 100 : 0;
}

function headersFor(provider, conn, referer) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${conn.apiKey}` };
  if (provider.type === 'openrouter') {
    headers['HTTP-Referer'] = referer;
    headers['X-Title'] = 'Daybreak';
  }
  return headers;
}

async function postOrThrow(fetchImpl, url, init, label) {
  let response;
  try {
    response = await fetchImpl(url, init);
  } catch (error) {
    if (init.signal?.aborted) throw error;
    throw new UpstreamError(`could not reach ${label}: ${error?.message ?? error}`);
  }
  if (!response.ok) {
    let detail = '';
    try {
      const parsed = JSON.parse(await response.text());
      detail = parsed?.error?.message ?? JSON.stringify(parsed).slice(0, 500);
    } catch {
      detail = '';
    }
    throw new UpstreamError(`${label} answered ${response.status}${detail ? `: ${detail}` : ''}`, {
      status: response.status,
      code: response.status === 429 ? 'busy' : 'upstream',
    });
  }
  return response;
}

/** Streams one chat completion (OpenAI's format, which OpenRouter also speaks). */
export async function* streamChatCompletions({ fetchImpl = fetch, provider, conn, body, referer, signal, label = 'the provider' }) {
  const response = await postOrThrow(
    fetchImpl,
    `${conn.baseUrl}/chat/completions`,
    {
      method: 'POST',
      headers: { ...headersFor(provider, conn, referer), Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal,
    },
    label,
  );

  const calls = new Map();
  let usage;
  let provider_;
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
    if (chunk.provider) provider_ = chunk.provider;
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
      if (call.function?.arguments) {
        current.arguments += call.function.arguments;
        // The app shows a website while its code is still being written, so it gets each new piece as it comes.
        yield { type: 'tool_delta', index: key, id: current.id, name: current.name, delta: call.function.arguments };
      }
      calls.set(key, current);
    }
  }
  const toolCalls = [...calls.values()]
    .filter((call) => call.name)
    .map((call, index) => ({ id: call.id || `call_${index}`, name: call.name, arguments: call.arguments || '{}' }));
  yield { type: 'finish', toolCalls, usage, provider: provider_, model: servedModel, finishReason };
}

/** Streams the OpenRouter way; kept as the name the tests and the older code use. */
export async function* streamOpenRouter({ fetchImpl = fetch, apiKey, baseUrl, referer, body, signal }) {
  yield* streamChatCompletions({
    fetchImpl,
    provider: { type: 'openrouter' },
    conn: { apiKey, baseUrl },
    body,
    referer,
    signal,
    label: 'OpenRouter',
  });
}

/** Splits a data: URL into its media type and base64 data. */
function parseDataUrl(url) {
  const match = /^data:([^;]+);base64,([\s\S]*)$/.exec(String(url ?? ''));
  return match ? { mediaType: match[1], data: match[2] } : null;
}

/**
 * Turns the chat in OpenAI's format into Anthropic's messages format: the system prompt on its own, tool
 * results as user blocks, tool calls as tool_use blocks, pictures and PDFs as their base64 blocks.
 */
export function toAnthropicRequest(messages, tools, maxTokens) {
  const system = messages
    .filter((message) => message.role === 'system')
    .flatMap((message) => (typeof message.content === 'string' ? [message.content] : message.content.map((part) => part.text)))
    .filter(Boolean)
    .join('\n\n');
  const out = [];
  const push = (role, blocks) => {
    const last = out.at(-1);
    if (last?.role === role) last.content.push(...blocks);
    else out.push({ role, content: blocks });
  };
  for (const message of messages) {
    if (message.role === 'user') {
      const blocks = typeof message.content === 'string'
        ? [{ type: 'text', text: message.content }]
        : message.content.flatMap((part) => {
          if (part.type === 'text') return [{ type: 'text', text: part.text }];
          const parsed = parseDataUrl(part.type === 'image_url' ? part.image_url?.url : part.file?.file_data);
          if (!parsed) return [];
          if (part.type === 'image_url') return [{ type: 'image', source: { type: 'base64', media_type: parsed.mediaType, data: parsed.data } }];
          return [{ type: 'document', source: { type: 'base64', media_type: parsed.mediaType, data: parsed.data } }];
        });
      push('user', blocks.length > 0 ? blocks : [{ type: 'text', text: ' ' }]);
    } else if (message.role === 'assistant') {
      const blocks = [];
      if (message.content) blocks.push({ type: 'text', text: message.content });
      for (const call of message.tool_calls ?? []) {
        let input = {};
        try {
          input = JSON.parse(call.function.arguments || '{}');
        } catch {
          input = {};
        }
        blocks.push({ type: 'tool_use', id: call.id, name: call.function.name, input });
      }
      push('assistant', blocks.length > 0 ? blocks : [{ type: 'text', text: ' ' }]);
    } else if (message.role === 'tool') {
      push('user', [{ type: 'tool_result', tool_use_id: message.tool_call_id, content: String(message.content ?? '') }]);
    }
  }
  const converted = {
    system,
    messages: out,
    max_tokens: maxTokens,
  };
  if (tools.length > 0) {
    converted.tools = tools.map((tool) => ({
      name: tool.function.name,
      description: tool.function.description,
      input_schema: tool.function.parameters,
    }));
  }
  return converted;
}

function anthropicHeaders(conn) {
  return {
    'Content-Type': 'application/json',
    'x-api-key': conn.apiKey,
    'anthropic-version': '2023-06-01',
  };
}

/** Streams one Anthropic messages request. */
export async function* streamAnthropic({ fetchImpl = fetch, conn, modelId, messages, tools, maxTokens, signal }) {
  const converted = toAnthropicRequest(messages, tools, maxTokens);
  const response = await postOrThrow(
    fetchImpl,
    `${conn.baseUrl}/v1/messages`,
    {
      method: 'POST',
      headers: { ...anthropicHeaders(conn), Accept: 'text/event-stream' },
      body: JSON.stringify({ model: modelId, stream: true, ...converted, system: converted.system || undefined }),
      signal,
    },
    'the Anthropic-compatible provider',
  );

  const blocks = new Map();
  const usage = { prompt_tokens: 0, completion_tokens: 0 };
  let stopReason;
  for await (const line of sseLines(response.body)) {
    if (!line.startsWith('data:')) continue;
    const data = line.slice(5).trim();
    let event;
    try {
      event = JSON.parse(data);
    } catch {
      continue;
    }
    switch (event.type) {
      case 'message_start':
        usage.prompt_tokens = event.message?.usage?.input_tokens ?? usage.prompt_tokens;
        usage.completion_tokens = event.message?.usage?.output_tokens ?? usage.completion_tokens;
        break;
      case 'content_block_start':
        if (event.content_block?.type === 'tool_use') {
          blocks.set(event.index, { id: event.content_block.id, name: event.content_block.name, json: '' });
        }
        break;
      case 'content_block_delta':
        if (event.delta?.type === 'text_delta' && event.delta.text) {
          yield { type: 'text', text: event.delta.text };
        } else if (event.delta?.type === 'input_json_delta') {
          const block = blocks.get(event.index);
          if (block) {
            const piece = event.delta.partial_json ?? '';
            block.json += piece;
            yield { type: 'tool_delta', index: event.index, id: block.id, name: block.name, delta: piece };
          }
        }
        break;
      case 'message_delta':
        if (event.usage?.output_tokens !== undefined) usage.completion_tokens = event.usage.output_tokens;
        if (event.delta?.stop_reason) stopReason = event.delta.stop_reason;
        break;
      case 'error':
        throw new UpstreamError(`provider error: ${event.error?.message ?? 'unknown error'}`);
      default:
        break;
    }
  }
  const toolCalls = [...blocks.values()]
    .filter((block) => block.name)
    .map((block, index) => ({ id: block.id || `call_${index}`, name: block.name, arguments: block.json || '{}' }));
  yield { type: 'finish', toolCalls, usage, provider: undefined, model: modelId, finishReason: stopReason };
}

/**
 * One answer without streaming, for the short chat title. Returns { text, usage }.
 */
export async function completeOnce({ fetchImpl = fetch, provider, conn, modelId, system, user, maxTokens, referer, signal }) {
  if (provider.type === 'anthropic') {
    const converted = toAnthropicRequest([{ role: 'user', content: user }], [], maxTokens);
    const response = await postOrThrow(
      fetchImpl,
      `${conn.baseUrl}/v1/messages`,
      {
        method: 'POST',
        headers: anthropicHeaders(conn),
        body: JSON.stringify({ model: modelId, ...converted, system }),
        signal,
      },
      'the Anthropic-compatible provider',
    );
    const parsed = await response.json();
    const text = (parsed.content ?? []).filter((block) => block.type === 'text').map((block) => block.text).join('');
    return {
      text,
      usage: { prompt_tokens: parsed.usage?.input_tokens ?? 0, completion_tokens: parsed.usage?.output_tokens ?? 0 },
    };
  }
  const response = await postOrThrow(
    fetchImpl,
    `${conn.baseUrl}/chat/completions`,
    {
      method: 'POST',
      headers: headersFor(provider, conn, referer),
      body: JSON.stringify({
        model: modelId,
        max_tokens: maxTokens,
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: user },
        ],
      }),
      signal,
    },
    provider.type === 'openrouter' ? 'OpenRouter' : 'the provider',
  );
  const parsed = await response.json();
  return { text: parsed?.choices?.[0]?.message?.content ?? '', usage: parsed?.usage ?? {} };
}
