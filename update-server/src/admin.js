import { escapeHtml } from './page.js';
import { FEATURES, LEVELS, readSwitches } from './announcements.js';

/**
 * The admin page (GET /admin). Deliberately plain: no CSS and no scripts, just forms, so it works in
 * any browser and the content security policy can forbid everything.
 */

/** The first app version that asks the server for the minimum version and shows its messages. */
const GATE_SINCE = '1.4.1';

const NOTICES = {
  saved: 'Saved. The app picks it up the next time it starts or comes back to the front.',
  bad_version: 'Enter a version such as 1.2.0, or leave the field empty for no minimum.',
  bad_level: 'Choose a level.',
  bad_text: 'Write the text in German or English. Each text may have up to 500 characters.',
  bad_until: 'The end time is not a date. Use a form such as 2026-11-07T18:00:00Z (with the offset).',
  not_found: 'That message does not exist any more.',
  granted: 'Payment granted for that code. The app shows its thanks when its user taps "Check payment".',
  already_paid: 'That code already has a payment on record.',
  bad_code: 'Enter a code such as DB-K7M2-9QXP, as the app shows it.',
  code_paid: 'That code has a payment on record.',
  code_unpaid: 'No payment is on record for that code yet.',
  assistant_saved: 'Assistant settings saved. They apply to the next request.',
  logs_cleared: 'The assistant log is empty now.',
  too_many_models: 'At most 20 models.',
  bad_model: 'A model id looks wrong. Use OpenRouter ids such as anthropic/claude-sonnet-5.5, one per line, optionally "id | label".',
  bad_number: 'Every limit must be a number of 0 or more.',
};

function cents(value) {
  return `${Number(value ?? 0).toFixed(3)} ¢`;
}

/** The assistant: OpenRouter models, routing, limits, logging, and the request log. */
function assistantSection(assistant) {
  if (!assistant) return '';
  const { settings, sorts, routings, efforts, keyConfigured, clientSwitchOn, unknownModels, usage, logs } = assistant;
  const effortOptions = ['', ...efforts]
    .map((effort) => `<option value="${effort}"${effort === settings.defaultEffort ? ' selected' : ''}>${effort || "model's own"}</option>`)
    .join('');
  const routingOptions = routings
    .map((routing) => `<option value="${routing.id}"${routing.id === settings.hiddenRouting ? ' selected' : ''}>${escapeHtml(routing.label)}</option>`)
    .join('');
  const modelLines = settings.models.map((model) => (model.label && model.label !== model.id ? `${model.id} | ${model.label}` : model.id)).join('\n');
  const defaultOptions = settings.models
    .map((model) => `<option value="${escapeHtml(model.id)}"${model.id === settings.defaultModel ? ' selected' : ''}>${escapeHtml(model.label)} (${escapeHtml(model.id)})</option>`)
    .join('');
  const sortOptions = sorts
    .map((sort) => `<option value="${sort.id}"${sort.id === settings.providerSort ? ' selected' : ''}>${escapeHtml(sort.label)}</option>`)
    .join('');
  const number = (name, label) => `<p><label>${label} <input name="${name}" type="number" min="0" step="any" value="${escapeHtml(String(settings[name]))}" size="8"></label></p>`;
  let warning = '';
  if (!clientSwitchOn) {
    warning = '<p><strong>The client-side switch (feature "AI assistant" under Switches) is off: the app hides the assistant, and the server-side switch is off too.</strong></p>';
  } else if (!keyConfigured) {
    warning = '<p><strong>OPENROUTER_API_KEY is not set: the assistant is off and the app hides it.</strong></p>';
  } else if (unknownModels === null) {
    warning = '<p>Could not read the OpenRouter model list to check the ids.</p>';
  } else if (unknownModels.length > 0) {
    warning = `<p><strong>OpenRouter does not know: ${unknownModels.map(escapeHtml).join(', ')}.</strong> Requests with these models fail, and the app shows "Upstream provider fail".</p>`;
  }
  const rows = logs.length === 0
    ? '<p>No requests logged yet.</p>'
    : `<table border="1" cellpadding="4" cellspacing="0">
<tr><th>Time (UTC)</th><th>Status</th><th>Model</th><th>User</th><th>Cost</th><th>Tokens in/out</th><th>Duration</th></tr>
${logs
  .map(
    (entry) => `<tr><td><a href="/admin/assistant/logs/${escapeHtml(entry.id)}">${escapeHtml(entry.at.replace('T', ' ').slice(0, 19))}</a></td><td>${escapeHtml(entry.status)}</td><td>${escapeHtml(entry.model || '–')}</td><td>${escapeHtml(entry.user)}${entry.paid ? ' (paid)' : ''}</td><td>${cents(entry.costCents)}</td><td>${entry.promptTokens ?? '–'} / ${entry.completionTokens ?? '–'}</td><td>${(entry.durationMs / 1000).toFixed(1)} s</td></tr>`,
  )
  .join('\n')}
</table>`;
  return `<h2>AI assistant (OpenRouter)</h2>
${warning}
<p>Today: ${usage.users} users, ${cents(usage.cents)} spent. Budgets are per user (the app's random install id) and per UTC day; the app never shows them. A user over the budget is told the assistant is not available right now.</p>
<form method="post" action="/admin/assistant">
<p>${checkbox('enabled', settings.enabled, 'Server-side switch: assistant answers (off: every request fails with "Upstream provider fail"; the app still shows the assistant unless the client-side switch under Switches is off)')}</p>
<p>${checkbox('showModelSwitcher', settings.showModelSwitcher, 'Show the model switcher in the app')}</p>
<p><label>While the switcher is hidden, users get <select name="hiddenRouting">${routingOptions}</select></label></p>
<p><label>Models the app may use, one per line: <code>openrouter/model-id | label</code><br><textarea name="models" rows="5" cols="70" spellcheck="false">${escapeHtml(modelLines)}</textarea></label></p>
<p><label>Default model <select name="defaultModel">${defaultOptions}</select></label> (after changing the list, save once to pick from the new list)</p>
<p><label>Provider routing <select name="providerSort">${sortOptions}</select></label></p>
<p>${checkbox('aiTitles', settings.aiTitles, 'Name new chats with a short AI title (one small extra request per chat, counted in the user\'s budget)')}</p>
<p>${checkbox('showEffortSelector', settings.showEffortSelector, 'Show the reasoning-effort picker in the app')}</p>
<p><label>Levels the app offers (any of ${efforts.join(', ')}; not every model takes every level, a wrong one fails as "Upstream provider fail") <input name="efforts" value="${escapeHtml(settings.efforts.join(', '))}" size="40"></label></p>
<p><label>Default effort <select name="defaultEffort">${effortOptions}</select></label></p>
<p>${checkbox('allowFallbacks', settings.allowFallbacks, 'Allow OpenRouter to fall back to another provider when one fails (best availability)')}</p>
<p>${checkbox('denyDataCollection', settings.denyDataCollection, 'Only providers that do not store or train on prompts (data_collection: deny)')}</p>
${number('freeCentsPerDay', 'Budget per user and day, free users (US cents)')}
${number('paidCentsPerDay', 'Budget per user and day, supporters (US cents)')}
${number('assistantPerMinute', 'Assistant requests per IP address and minute')}
${number('updateChecksPerMinute', 'Update checks and status requests per IP address and minute')}
${number('downloadsPerHour', 'APK and installer downloads per IP address and hour')}
<p>${checkbox('logContent', settings.logContent, 'Log the text of questions, answers and tool calls (needed to view requests below)')}</p>
${number('logRetentionDays', 'Keep the log for (days, at most 30: the privacy text in the app promises that)')}
<p><button type="submit">Save assistant settings</button></p>
</form>
<h3>Requests</h3>
<form method="post" action="/admin/assistant/logs/clear"><button type="submit">Clear log</button></form>
${rows}
`;
}

/** One logged assistant request. */
export function renderLogEntry({ app, entry }) {
  const head = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)} assistant request</title>
</head>
<body>
<p><a href="/admin">Back to the admin page</a></p>`;
  if (!entry) return `${head}<p>That request is not in the log (any more).</p></body></html>`;
  const field = (label, value) => `<tr><th align="left">${label}</th><td>${escapeHtml(value === undefined || value === null || value === '' ? '–' : String(value))}</td></tr>`;
  const content = entry.content;
  const messages = content
    ? content.messages
        .map((message) => {
          const extra = [
            ...(message.attachments ?? []).map((a) => `[attached ${a}]`),
            ...(message.toolCalls ?? []).map((c) => `[tool call ${c}]`),
          ].join('\n');
          return `<h3>${escapeHtml(message.role)}</h3><pre style="white-space: pre-wrap">${escapeHtml([message.text, extra].filter(Boolean).join('\n'))}</pre>`;
        })
        .join('\n')
    : '<p>The text was not logged (logging of content is off).</p>';
  const details = content
    ? `<h2>Answer</h2><pre style="white-space: pre-wrap">${escapeHtml(content.answer || '(no text)')}</pre>
${content.toolCalls?.length ? `<h2>Tool calls</h2><pre style="white-space: pre-wrap">${escapeHtml(content.toolCalls.join('\n'))}</pre>` : ''}
<h2>Chat sent</h2>${messages}
<h2>Timetable summary sent</h2><pre style="white-space: pre-wrap">${escapeHtml(content.context)}</pre>`
    : messages;
  return `${head}
<h1>Assistant request ${escapeHtml(entry.id)}</h1>
<table border="1" cellpadding="4" cellspacing="0">
${field('Time (UTC)', entry.at)}
${field('Status', entry.status)}
${field('Error', entry.error)}
${field('Upstream status', entry.upstreamStatus)}
${field('Model (asked)', entry.model)}
${field('Model (served)', entry.servedModel)}
${field('Reasoning effort', entry.effort)}
${field('Provider', entry.provider)}
${field('Finish reason', entry.finishReason)}
${field('User', `${entry.user}${entry.paid ? ' (paid)' : ''}`)}
${field('Cost', cents(entry.costCents))}
${field('Tokens in / out', `${entry.promptTokens ?? '–'} / ${entry.completionTokens ?? '–'}`)}
${field('Duration', `${(entry.durationMs / 1000).toFixed(2)} s`)}
</table>
${details}
</body>
</html>
`;
}

function checkbox(name, checked, label) {
  return `<label><input type="checkbox" name="${name}"${checked ? ' checked' : ''}> ${label}</label>`;
}

function switchesSection(state) {
  const switches = readSwitches(state.switches);
  const disabled = new Set(switches.disabledFeatures);
  const features = FEATURES.map(
    (feature) => `<p>${checkbox(`feature:${feature.id}`, disabled.has(feature.id), `Turn off: ${escapeHtml(feature.label)} (<code>${escapeHtml(feature.id)}</code>)`)}</p>`,
  ).join('\n');
  return `<h2>Switches</h2>
<p>Read by the app each time it starts or comes back to the front. Untick a switch to turn it off for everyone.</p>
<form method="post" action="/admin/switches">
<p>${checkbox('updates', switches.updates, 'In-app updates on (update checks, notifications, the update screen and the required-version gate)')}</p>
<p>${checkbox('requiredVersion', switches.requiredVersion, 'Minimum app version on (apps older than it stop at the update screen)')}</p>
<p>${checkbox('messages', switches.messages, 'Messages from this page are shown as pop-ups')}</p>
<p>Single features (tick to turn one off):</p>
${features}
<p><button type="submit">Save switches</button></p>
</form>
`;
}

/** The form to check or grant a payment for an unlock code, as the app shows it. */
function supportSection() {
  return `<h2>Support payments</h2>
<p>A Ko-fi payment is recorded by itself when its message holds the code. If a payment did not arrive, enter the code the app shows on its "Hide this popup" page. Check it first, or grant the payment by hand. A granted payment is marked as made by hand. The app learns of it when its user taps "Check payment".</p>
<form method="post" action="/admin/support/check">
<p><label>Unlock code <input name="code" placeholder="DB-K7M2-9QXP" size="20" autocomplete="off" spellcheck="false"></label></p>
<p><button type="submit">Check code</button> <button type="submit" formaction="/admin/support/grant">Grant payment</button></p>
</form>
`;
}

function formatMinVersion(minVersion) {
  return minVersion ? `${minVersion.name} (versionCode ${minVersion.code})` : 'none';
}

function messageStatus(message, now) {
  if (!message.enabled) return 'hidden';
  if (message.until && Date.parse(message.until) <= now) return 'ended';
  return 'shown';
}

export function renderAdmin({ app, state, notice, now = Date.now(), assistant }) {
  const notes = NOTICES[notice];
  const levelOptions = LEVELS.map((level) => `<option value="${level}">${level}</option>`).join('');
  const messages = state.messages.length === 0
    ? '<p>No messages yet.</p>'
    : `<ul>${state.messages
        .map((message) => {
          const id = escapeHtml(message.id);
          const status = messageStatus(message, now);
          const ends = message.until ? `, ends ${escapeHtml(message.until)}` : '';
          return `<li>
<p><strong>${escapeHtml(message.level)}</strong> · ${status}${ends} · id ${id}</p>
<p>DE: ${escapeHtml(message.de || '–')}<br>EN: ${escapeHtml(message.en || '–')}</p>
<form method="post" action="/admin/messages/${id}/toggle"><button type="submit">${message.enabled ? 'Hide' : 'Show'}</button></form>
<form method="post" action="/admin/messages/${id}/delete"><button type="submit">Delete</button></form>
</li>`;
        })
        .join('\n')}</ul>`;

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)} update server admin</title>
</head>
<body>
<h1>${escapeHtml(app)} update server: admin</h1>
${notes ? `<p><strong>${escapeHtml(notes)}</strong></p>` : ''}

<h2>Minimum app version</h2>
<p>Apps older than this are stopped at the "update required" screen. Current: ${escapeHtml(formatMinVersion(state.minVersion))}.</p>
<p><strong>Warning:</strong> this only applies to apps from version ${GATE_SINCE} on. Older apps never ask the server for the minimum version, so they keep working. They are offered newer releases through their update check, but they cannot be stopped.</p>
<form method="post" action="/admin/min-version">
<p><label>Version <input name="version" value="${escapeHtml(state.minVersion?.name ?? '')}" placeholder="1.2.0"></label>
<button type="submit">Save minimum version</button></p>
</form>

${switchesSection(state)}
${assistantSection(assistant)}
${supportSection()}
<h2>Add a message</h2>
<p>Each message is shown once per phone as a pop-up when the app opens. There is no editing: to change a text, hide or delete the old message and add a new one, which is shown again.</p>
<form method="post" action="/admin/messages">
<p><label>Level <select name="level">${levelOptions}</select></label></p>
<p><label>Text (German)<br><textarea name="de" rows="3" cols="60"></textarea></label></p>
<p><label>Text (English)<br><textarea name="en" rows="3" cols="60"></textarea></label></p>
<p><label>Ends (optional, e.g. 2026-11-07T18:00:00Z)<br><input name="until" size="30"></label></p>
<p><button type="submit">Add message</button></p>
</form>

<h2>All messages</h2>
${messages}
</body>
</html>
`;
}
