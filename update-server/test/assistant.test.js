import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ASSISTANT_LIMITS,
  AssistantService,
  SYSTEM_PROMPT,
  TOOLS,
  buildOpenRouterBody,
  parseAssistantRequest,
} from '../src/assistant.js';
import { AssistantStore, DEFAULT_SETTINGS, dayOf, parseSettingsForm, readSettings, userKey } from '../src/assistant-store.js';
import { buildServer } from '../src/server.js';
import { AnnouncementStore } from '../src/announcements.js';
import { testConfig } from './helpers.js';

const INSTALL = 'a1b2c3d4-e5f6-4711-8888-123456789abc';

const validBody = {
  language: 'de',
  today: '2026-11-09',
  context: 'Mon 2026-11-09: 08:00-08:45 Mathe, teacher MÜL, room R101',
  messages: [{ role: 'user', text: 'Was habe ich morgen?' }],
};

/** A fake OpenRouter: answers chat completions with the given SSE chunks, records the requests. */
function fakeOpenRouter({ chunks, status = 200, errorBody, models = ['anthropic/claude-sonnet-5.5'] } = {}) {
  const calls = [];
  const impl = async (url, init = {}) => {
    calls.push({ url: String(url), body: init.body ? JSON.parse(init.body) : undefined, headers: init.headers });
    if (String(url).endsWith('/models')) {
      return new Response(JSON.stringify({ data: models.map((id) => ({ id })) }), { status: 200 });
    }
    if (status !== 200) return new Response(JSON.stringify(errorBody ?? { error: { message: 'bad' } }), { status });
    const text = (chunks ?? [
      { choices: [{ delta: { content: 'Morgen ' } }] },
      { choices: [{ delta: { content: 'hast du Mathe.' } }] },
      { choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 900, completion_tokens: 12, cost: 0.0021 }, provider: 'Anthropic', model: 'anthropic/claude-sonnet-5.5' },
    ])
      .map((chunk) => (typeof chunk === 'string' ? chunk : `data: ${JSON.stringify(chunk)}`))
      .join('\n\n') + '\n\ndata: [DONE]\n\n';
    return new Response(text, { status: 200, headers: { 'content-type': 'text/event-stream' } });
  };
  impl.calls = calls;
  return impl;
}

async function setup({ fetchImpl = fakeOpenRouter(), settings, paidCodes = [], key = 'sk-or-test', now, clientOff = false, random } = {}) {
  const config = await testConfig({ openRouterApiKey: key, openRouterUrl: 'https://openrouter.test/api/v1' });
  const store = new AssistantStore({ config, now });
  if (settings) await store.saveSettings({ ...DEFAULT_SETTINGS, ...settings });
  const support = { isPaid: async (code) => paidCodes.includes(code) };
  const notices = new AnnouncementStore({ config });
  if (clientOff) await notices.setSwitches({ updates: 'on', requiredVersion: 'on', messages: 'on', 'feature:assistant': 'on' });
  const assistant = new AssistantService({ config, store, support, notices, fetchImpl, now, random });
  const { app } = buildServer({
    config: { ...config, adminToken: 'admin-secret' },
    releases: {},
    apks: {},
    announcements: notices,
    assistant,
    assistantStore: store,
  });
  return { app, store, fetchImpl, config };
}

function events(body) {
  return body
    .split('\n\n')
    .filter((chunk) => chunk.startsWith('data: '))
    .map((chunk) => JSON.parse(chunk.slice(6)));
}

const post = (app, payload, headers = { 'x-daybreak-install': INSTALL }) =>
  app.inject({ method: 'POST', url: '/v1/assistant', payload, headers });

test('parseAssistantRequest accepts chats with tool calls and attachments', () => {
  const { value, error } = parseAssistantRequest({
    ...validBody,
    messages: [
      { role: 'user', text: 'Was steht hier?', attachments: [{ type: 'image', mime: 'image/jpeg', name: 'a.jpg', data: 'aGVsbG8=' }] },
      { role: 'assistant', text: '', toolCalls: [{ id: 'c1', name: 'get_exams', arguments: '{}' }] },
      { role: 'tool', toolCallId: 'c1', content: '[]' },
    ],
  });
  assert.equal(error, undefined);
  assert.equal(value.messages.length, 3);
});

test('parseAssistantRequest rejects bad chats', () => {
  assert.equal(parseAssistantRequest(null).error, 'bad_request');
  assert.equal(parseAssistantRequest({ messages: [] }).error, 'no_messages');
  assert.equal(parseAssistantRequest({ messages: [{ role: 'assistant', text: 'x' }] }).error, 'bad_order');
  assert.equal(parseAssistantRequest({ messages: [{ role: 'system', text: 'x' }] }).error, 'bad_role');
  assert.equal(parseAssistantRequest({ messages: [{ role: 'user', text: ' ' }] }).error, 'empty_message');
  assert.equal(
    parseAssistantRequest({ messages: [{ role: 'user', text: 'x'.repeat(ASSISTANT_LIMITS.maxTextChars + 1) }] }).error,
    'message_too_long',
  );
  assert.equal(
    parseAssistantRequest({ messages: [{ role: 'user', text: 'x', attachments: [{ type: 'image', mime: 'image/svg+xml', data: 'aGk=' }] }] }).error,
    'bad_attachment',
  );
  // A tool result needs its call, and every call needs its result.
  assert.equal(parseAssistantRequest({ messages: [{ role: 'user', text: 'x' }, { role: 'tool', toolCallId: 'c9', content: '' }] }).error, 'bad_tool_result');
  assert.equal(
    parseAssistantRequest({
      messages: [{ role: 'user', text: 'x' }, { role: 'assistant', toolCalls: [{ id: 'c1', name: 'get_exams', arguments: '{}' }] }],
    }).error,
    'bad_order',
  );
  assert.equal(
    parseAssistantRequest({
      messages: [{ role: 'user', text: 'x' }, { role: 'assistant', toolCalls: [{ id: 'c1', name: 'rm_rf', arguments: '{}' }] }],
    }).error,
    'bad_tool_call',
  );
});

test('buildOpenRouterBody sends tools, provider routing and attachments in the OpenRouter format', () => {
  const { value } = parseAssistantRequest({
    ...validBody,
    messages: [
      {
        role: 'user',
        text: 'Lies das',
        attachments: [
          { type: 'image', mime: 'image/png', name: 'board.png', data: 'aGVsbG8=' },
          { type: 'file', mime: 'application/pdf', name: 'sheet.pdf', data: 'JVBERg==' },
          { type: 'file', mime: 'text/plain', name: 'notes.txt', data: Buffer.from('Vokabeln Unit 3').toString('base64') },
        ],
      },
    ],
  });
  const body = buildOpenRouterBody({ model: 'anthropic/claude-sonnet-5.5', request: value, settings: readSettings({ providerSort: 'price', allowFallbacks: false }) });
  assert.equal(body.stream, true);
  assert.deepEqual(body.provider, { allow_fallbacks: false, sort: 'price', data_collection: 'deny' });
  assert.equal(body.tools.length, TOOLS.length);
  assert.equal(body.messages[0].role, 'system');
  assert.equal(body.messages[0].content[0].text, SYSTEM_PROMPT);
  assert.ok(body.messages[0].content[1].text.includes('Mathe, teacher MÜL'));
  const parts = body.messages[1].content;
  assert.equal(parts[0].type, 'image_url');
  assert.ok(parts[0].image_url.url.startsWith('data:image/png;base64,'));
  assert.equal(parts[1].type, 'file');
  assert.equal(parts[1].file.filename, 'sheet.pdf');
  assert.ok(parts[2].text.includes('Vokabeln Unit 3'));
  assert.deepEqual(parts[3], { type: 'text', text: 'Lies das' });

  const fallbackDefault = buildOpenRouterBody({ model: 'm', request: value, settings: readSettings({}) });
  assert.equal(fallbackDefault.provider.sort, undefined);
});

test('settings form: models per line, numbers, checkboxes', () => {
  const { value } = parseSettingsForm({
    enabled: 'on',
    models: 'anthropic/claude-haiku-5.5 | Schnell\r\ngoogle/gemini-3.8-flash',
    defaultModel: 'google/gemini-3.8-flash',
    providerSort: 'throughput',
    freeCentsPerDay: '10',
    paidCentsPerDay: '20',
    assistantPerMinute: '5',
    updateChecksPerMinute: '30',
    downloadsPerHour: '20',
    logRetentionDays: '7',
  });
  assert.deepEqual(value.models, [
    { id: 'anthropic/claude-haiku-5.5', label: 'Schnell' },
    { id: 'google/gemini-3.8-flash', label: 'google/gemini-3.8-flash' },
  ]);
  assert.equal(value.defaultModel, 'google/gemini-3.8-flash');
  assert.equal(value.providerSort, 'throughput');
  assert.equal(value.allowFallbacks, false);
  assert.equal(value.logContent, false);
  assert.equal(parseSettingsForm({ models: 'bad model id with spaces' }).error, 'bad_model');
  assert.equal(parseSettingsForm({ models: 'a/b', freeCentsPerDay: '-1' }).error, 'bad_number');
});

test('the defaults are 10 cents a day for free users and 20 for supporters', () => {
  assert.equal(DEFAULT_SETTINGS.freeCentsPerDay, 10);
  assert.equal(DEFAULT_SETTINGS.paidCentsPerDay, 20);
});

test('GET /v1/assistant/models lists the models, and is 404 without a key', async () => {
  const { app } = await setup();
  const response = await app.inject({ method: 'GET', url: '/v1/assistant/models' });
  assert.equal(response.statusCode, 200);
  assert.equal(response.json().defaultModel, DEFAULT_SETTINGS.defaultModel);
  const off = await setup({ key: '' });
  assert.equal((await off.app.inject({ method: 'GET', url: '/v1/assistant/models' })).statusCode, 404);
  assert.equal((await post(off.app, validBody)).statusCode, 404);
});

test('POST /v1/assistant streams the answer, records the spend and logs the request', async () => {
  const { app, store, fetchImpl } = await setup();
  const response = await post(app, validBody);
  assert.equal(response.statusCode, 200);
  const list = events(response.body);
  assert.equal(list.filter((e) => e.type === 'text').map((e) => e.text).join(''), 'Morgen hast du Mathe.');
  assert.deepEqual(list.at(-1), { type: 'done' });
  const sent = fetchImpl.calls.find((call) => call.url.endsWith('/chat/completions'));
  assert.equal(sent.body.model, DEFAULT_SETTINGS.defaultModel);
  assert.equal(sent.headers.Authorization, 'Bearer sk-or-test');
  assert.ok(Math.abs((await store.spentToday(userKey(INSTALL))) - 0.21) < 1e-9);
  const [entry] = await store.logs();
  assert.equal(entry.status, 'ok');
  assert.equal(entry.provider, 'Anthropic');
  assert.equal(entry.promptTokens, 900);
  assert.equal(entry.content.answer, 'Morgen hast du Mathe.');
});

test('tool calls are collected from the stream and passed to the app', async () => {
  const chunks = [
    { choices: [{ delta: { tool_calls: [{ index: 0, id: 'call_1', type: 'function', function: { name: 'get_exams', arguments: '' } }] } }] },
    { choices: [{ delta: { tool_calls: [{ index: 0, function: { arguments: '{"start_date":' } }] } }] },
    { choices: [{ delta: { tool_calls: [{ index: 0, function: { arguments: '"2026-11-09"}' } }] } }] },
    { choices: [{ delta: {}, finish_reason: 'tool_calls' }], usage: { cost: 0.001 } },
  ];
  const { app, store } = await setup({ fetchImpl: fakeOpenRouter({ chunks }) });
  const list = events((await post(app, validBody)).body);
  assert.deepEqual(list[0], { type: 'tool_calls', calls: [{ id: 'call_1', name: 'get_exams', arguments: '{"start_date":"2026-11-09"}' }] });
  assert.deepEqual(list.at(-1), { type: 'done' });
  assert.equal((await store.logs())[0].status, 'tool_calls');
});

test('a wrongly configured model ends in an upstream error, logged with the reason', async () => {
  const fetchImpl = fakeOpenRouter({ status: 400, errorBody: { error: { message: 'not-a-model is not a valid model ID' } } });
  const { app, store } = await setup({ fetchImpl, settings: { models: [{ id: 'not-a-model', label: 'x' }], defaultModel: 'not-a-model' } });
  const list = events((await post(app, validBody)).body);
  assert.deepEqual(list, [{ type: 'error', code: 'upstream' }]);
  const [entry] = await store.logs();
  assert.equal(entry.status, 'error');
  assert.match(entry.error, /not a valid model ID/);
});

test('an error inside the stream is an upstream error too', async () => {
  const chunks = [{ choices: [{ delta: { content: 'Mor' } }] }, { error: { code: 502, message: 'provider down' } }];
  const { app } = await setup({ fetchImpl: fakeOpenRouter({ chunks }) });
  const list = events((await post(app, validBody)).body);
  assert.deepEqual(list.at(-1), { type: 'error', code: 'upstream' });
});

test('the daily budget stops a user without saying so, and supporters get more', async () => {
  const time = Date.parse('2026-11-09T10:00:00Z');
  const { app, store } = await setup({ now: () => time, paidCodes: ['DB-PAID-CODE'] });
  await store.addSpend(userKey(INSTALL), 10);
  assert.deepEqual(events((await post(app, validBody)).body), [{ type: 'error', code: 'limit' }]);
  assert.equal((await store.logs())[0].status, 'limit');

  // The same spend is under the supporter budget of 20 cents.
  const paid = events((await post(app, { ...validBody, unlockCode: 'DB-PAID-CODE' })).body);
  assert.deepEqual(paid.at(-1), { type: 'done' });
  assert.equal(dayOf(time), '2026-11-09');
});

test('the assistant needs an install id and limits requests per IP', async () => {
  const { app } = await setup({ settings: { assistantPerMinute: 1 } });
  assert.equal((await post(app, validBody, {})).statusCode, 400);
  await post(app, validBody);
  assert.equal((await post(app, validBody)).statusCode, 429);
});

test('update checks are limited per IP, with the limit from the admin page', async () => {
  const { app } = await setup({ settings: { updateChecksPerMinute: 2 } });
  const statuses = [];
  for (let i = 0; i < 3; i += 1) statuses.push((await app.inject({ method: 'GET', url: '/v1/assistant/models' })).statusCode);
  assert.deepEqual(statuses, [200, 200, 429]);
});

test('the admin page shows the assistant settings and the clickable log', async () => {
  const { app, store } = await setup();
  await post(app, validBody);
  const auth = { authorization: `Basic ${Buffer.from('x:admin-secret').toString('base64')}` };
  const page = await app.inject({ method: 'GET', url: '/admin', headers: auth });
  assert.equal(page.statusCode, 200);
  assert.match(page.body, /AI assistant \(OpenRouter\)/);
  const [entry] = await store.logs();
  assert.ok(page.body.includes(`/admin/assistant/logs/${entry.id}`));
  const detail = await app.inject({ method: 'GET', url: `/admin/assistant/logs/${entry.id}`, headers: auth });
  assert.equal(detail.statusCode, 200);
  assert.ok(detail.body.includes('Was habe ich morgen?'));
  assert.ok(detail.body.includes('Morgen hast du Mathe.'));

  const saved = await app.inject({
    method: 'POST',
    url: '/admin/assistant',
    headers: { ...auth, 'content-type': 'application/x-www-form-urlencoded' },
    payload: 'enabled=on&showModelSwitcher=on&models=google%2Fgemini-3.8-flash&providerSort=latency&freeCentsPerDay=5&paidCentsPerDay=15&assistantPerMinute=10&updateChecksPerMinute=30&downloadsPerHour=20&logRetentionDays=3',
  });
  assert.equal(saved.statusCode, 303);
  const settings = await store.settings();
  assert.equal(settings.defaultModel, 'google/gemini-3.8-flash');
  assert.equal(settings.providerSort, 'latency');
  assert.equal(settings.freeCentsPerDay, 5);
});

test('the server-side switch answers with the upstream error but keeps the assistant visible', async () => {
  const { app, store } = await setup({ settings: { enabled: false } });
  assert.equal((await app.inject({ method: 'GET', url: '/v1/assistant/models' })).statusCode, 200);
  assert.deepEqual(events((await post(app, validBody)).body), [{ type: 'error', code: 'upstream' }]);
  assert.match((await store.logs())[0].error, /switched off/);
});

test('the client-side switch hides the assistant and turns the server side off too', async () => {
  const { app } = await setup({ clientOff: true });
  assert.equal((await app.inject({ method: 'GET', url: '/v1/assistant/models' })).statusCode, 404);
  assert.deepEqual(events((await post(app, validBody)).body), [{ type: 'error', code: 'upstream' }]);
});

test('a hidden model switcher ignores the pick and routes to the default or a random model', async () => {
  const models = [{ id: 'a/one', label: 'One' }, { id: 'b/two', label: 'Two' }];
  const fixed = await setup({ settings: { models, defaultModel: 'a/one', showModelSwitcher: false, hiddenRouting: 'fixed' } });
  const listed = (await fixed.app.inject({ method: 'GET', url: '/v1/assistant/models' })).json();
  assert.deepEqual(listed, { showEffort: false, efforts: [], defaultEffort: null, showSwitcher: false, models: [], defaultModel: null });
  await post(fixed.app, { ...validBody, model: 'b/two' });
  assert.equal(fixed.fetchImpl.calls.find((c) => c.url.endsWith('/chat/completions')).body.model, 'a/one');

  const random = await setup({ settings: { models, defaultModel: 'a/one', showModelSwitcher: false, hiddenRouting: 'random' }, random: () => 0.99 });
  await post(random.app, validBody);
  assert.equal(random.fetchImpl.calls.find((c) => c.url.endsWith('/chat/completions')).body.model, 'b/two');

  const shown = await setup({ settings: { models, defaultModel: 'a/one', showModelSwitcher: true } });
  await post(shown.app, { ...validBody, model: 'b/two' });
  assert.equal(shown.fetchImpl.calls.find((c) => c.url.endsWith('/chat/completions')).body.model, 'b/two');
});

test('the effort picker: off by default, the pick only when shown, else the default', async () => {
  const { pickEffort } = await import('../src/assistant.js');
  assert.equal(pickEffort(readSettings({}), 'high'), undefined);
  const shown = readSettings({ showEffortSelector: true, efforts: ['low', 'high', 'bogus'], defaultEffort: 'low' });
  assert.deepEqual(shown.efforts, ['low', 'high']);
  assert.equal(pickEffort(shown, 'high'), 'high');
  assert.equal(pickEffort(shown, 'xhigh'), 'low');
  const { value } = parseAssistantRequest({ ...validBody, effort: 'high' });
  const body = buildOpenRouterBody({ model: 'm', request: value, settings: shown });
  assert.deepEqual(body.reasoning, { effort: 'high' });
  assert.equal(buildOpenRouterBody({ model: 'm', request: value, settings: readSettings({}) }).reasoning, undefined);

  const app = (await setup({ settings: { showEffortSelector: true, efforts: ['medium', 'xhigh'], defaultEffort: 'medium' } })).app;
  const listed = (await app.inject({ method: 'GET', url: '/v1/assistant/models' })).json();
  assert.equal(listed.showEffort, true);
  assert.deepEqual(listed.efforts, ['medium', 'xhigh']);
  assert.equal(listed.defaultEffort, 'medium');
});

test('AI titles: a short clean title, none when switched off, and the cost is counted', async () => {
  const { cleanTitle } = await import('../src/assistant.js');
  assert.equal(cleanTitle('  "Mitternachtsformel lösen."  '), 'Mitternachtsformel lösen');
  assert.equal(cleanTitle('Titel: Nächste Prüfung'), 'Nächste Prüfung');
  const fetchImpl = async (url, init) => {
    const body = JSON.parse(init.body);
    assert.equal(body.max_tokens, 24);
    return new Response(JSON.stringify({ choices: [{ message: { content: '"Quadratische Gleichung"' } }], usage: { cost: 0.0001 } }), { status: 200 });
  };
  const { app, store } = await setup({ fetchImpl });
  const response = await app.inject({
    method: 'POST',
    url: '/v1/assistant/title',
    headers: { 'x-daybreak-install': INSTALL },
    payload: { language: 'de', question: 'Löse x^2-5x+6=0', answer: 'x = 2 oder x = 3' },
  });
  assert.deepEqual(response.json(), { title: 'Quadratische Gleichung' });
  assert.ok(Math.abs((await store.spentToday(userKey(INSTALL))) - 0.01) < 1e-9);

  const off = await setup({ fetchImpl, settings: { aiTitles: false } });
  const none = await off.app.inject({ method: 'POST', url: '/v1/assistant/title', headers: { 'x-daybreak-install': INSTALL }, payload: { question: 'x' } });
  assert.equal(none.statusCode, 204);
});

test('reasoning is streamed to the app as its own event and logged', async () => {
  const chunks = [
    { choices: [{ delta: { reasoning: 'Erst die ' } }] },
    { choices: [{ delta: { reasoning: 'Prüfungen.' } }] },
    { choices: [{ delta: { content: 'Morgen.' } }] },
    { choices: [{ delta: {}, finish_reason: 'stop' }], usage: { cost: 0.001 } },
  ];
  const { app, store } = await setup({ fetchImpl: fakeOpenRouter({ chunks }) });
  const list = events((await post(app, validBody)).body);
  assert.deepEqual(list.slice(0, 3), [
    { type: 'reasoning', text: 'Erst die ' },
    { type: 'reasoning', text: 'Prüfungen.' },
    { type: 'text', text: 'Morgen.' },
  ]);
  assert.equal((await store.logs())[0].content.reasoning, 'Erst die Prüfungen.');
});

test('the timetable card tool is offered and its calls and results are accepted', () => {
  const tool = TOOLS.find((entry) => entry.name === 'show_timetable');
  assert.ok(tool);
  assert.deepEqual(tool.parameters.required, ['date']);
  assert.match(SYSTEM_PROMPT, /call show_timetable/);
  assert.match(SYSTEM_PROMPT, /never put formulas in code blocks/);
  const parsed = parseAssistantRequest({
    ...validBody,
    messages: [
      { role: 'user', text: 'Was habe ich heute?' },
      { role: 'assistant', text: '', toolCalls: [{ id: 'c1', name: 'show_timetable', arguments: '{"date":"2026-11-09"}' }] },
      { role: 'tool', toolCallId: 'c1', content: 'Shown to the student as a card for Mon 2026-11-09.' },
    ],
  });
  assert.ok(parsed.value, parsed.error);
});
