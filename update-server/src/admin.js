import { PROVIDER_TYPES, TAG_KINDS } from './assistant-store.js';
import { escapeHtml } from './page.js';
import { FEATURES, LEVELS, readSwitches } from './announcements.js';

/**
 * The admin page (GET /admin). Plain forms with one small style block and no scripts, so the
 * content security policy still forbids everything but inline styles.
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

/** The look of the admin page and of one logged request. Light, dark, no pictures, no script. */
const STYLE = `
:root{color-scheme:light dark;--bg:#fff;--text:#1b1b1b;--muted:#6b6b6b;--line:#dcdcdc;--field:#fff;--button:#1b1b1b;--on-button:#fff;--notice:#fbf3e6;--danger:#b3261e}
@media (prefers-color-scheme:dark){:root{--bg:#141414;--text:#e6e6e6;--muted:#9a9a9a;--line:#303030;--field:#1c1c1c;--button:#e6e6e6;--on-button:#141414;--notice:#241d13;--danger:#f0a08f}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:36px 20px 72px}
h1{font-size:1.25rem;font-weight:600;margin:0}
.lead{color:var(--muted);font-size:.85rem;margin:2px 0 0}
h2{font-size:.95rem;font-weight:600;margin:32px 0 12px;padding-bottom:6px;border-bottom:1px solid var(--line)}
h3{font-size:.9rem;font-weight:600;margin:22px 0 8px}
p{margin:0 0 12px}
.hint{color:var(--muted);font-size:.85rem;margin:-4px 0 12px}
.notice{background:var(--notice);border:1px solid var(--line);border-radius:4px;padding:8px 10px;margin:24px 0 0;font-size:.88rem}
.notice:empty{display:none}
form{margin:0 0 4px}
input:not([type=checkbox]),select,textarea{font:inherit;color:var(--text);background:var(--field);border:1px solid var(--line);border-radius:4px;padding:5px 8px}
input[type=number]{width:6.5rem}
input[type=checkbox]{margin:0 6px 0 0;vertical-align:-2px}
textarea{display:block;width:100%;margin-top:4px;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.85rem;resize:vertical}
button{font:inherit;font-size:.88rem;background:var(--button);color:var(--on-button);border:1px solid transparent;border-radius:4px;padding:5px 12px;cursor:pointer}
button:hover{opacity:.85}
button.plain{background:transparent;color:var(--text);border-color:var(--line)}
button.danger{background:transparent;color:var(--danger);border-color:var(--line)}
.grid{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px 16px;align-items:center;max-width:520px;margin:0 0 16px}
.grid label{margin:0}
.row{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
table{width:100%;border-collapse:collapse;font-size:.85rem;margin:8px 0 0}
th,td{text-align:left;padding:6px 8px;border-bottom:1px solid var(--line);vertical-align:top}
th{font-weight:600;color:var(--muted)}
.scroll{overflow-x:auto}
.scroll table{margin:0}
.scroll th,.scroll td{white-space:nowrap}
.messages{list-style:none;margin:0;padding:0;display:grid;gap:10px}
.messages li{border:1px solid var(--line);border-radius:6px;padding:12px 14px}
.messages li p{margin:0 0 6px}
.messages li p:last-child{margin:0}
.meta{color:var(--muted);font-size:.85rem}
.meta strong{color:var(--text);font-weight:600}
pre{white-space:pre-wrap;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.85rem;margin:6px 0 16px;padding-left:10px;border-left:2px solid var(--line)}
code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.85em}
a{color:inherit;text-decoration:underline;text-underline-offset:2px}
`;

function cents(value) {
  return `${Number(value ?? 0).toFixed(3)} ¢`;
}

/** One row per provider; two blank rows to add more. A blank key keeps the stored one. */
function providerRows(providers) {
  const rows = [...providers, null, null];
  return rows
    .map((provider) => {
      const type = provider?.type ?? 'openai';
      const typeOptions = PROVIDER_TYPES.map(
        (item) => `<option value="${item.id}"${item.id === type ? ' selected' : ''}>${escapeHtml(item.label)}</option>`,
      ).join('');
      const keyHint = provider?.apiKey ? 'key set (blank keeps it)' : type === 'openrouter' ? 'key (blank: OPENROUTER_API_KEY)' : 'key';
      return `<tr>
<td><input type="hidden" name="providerId" value="${escapeHtml(provider?.id ?? '')}"><input name="providerName" value="${escapeHtml(provider?.name ?? '')}" placeholder="name" size="14"></td>
<td><select name="providerType">${typeOptions}</select></td>
<td><input name="providerBase" value="${escapeHtml(provider?.baseUrl ?? '')}" placeholder="https://… (blank: default)" size="28"></td>
<td><input name="providerKey" type="password" autocomplete="off" placeholder="${escapeHtml(keyHint)}" size="20"></td>
</tr>`;
    })
    .join('\n');
}

/** One row per model; three blank rows to add more. Switched-off models stay in the picker and answer with the upstream error. */
function modelRows(models, providers) {
  const rows = [...models, null, null, null];
  return rows
    .map((model) => {
      const providerOptions = providers
        .map((provider) => `<option value="${escapeHtml(provider.id)}"${model?.provider === provider.id ? ' selected' : ''}>${escapeHtml(provider.name)}</option>`)
        .join('');
      const tagOptions = TAG_KINDS.map(
        (kind) => `<option value="${kind.id}"${(model?.tagKind ?? 'info') === kind.id ? ' selected' : ''}>${escapeHtml(kind.label)}</option>`,
      ).join('');
      return `<tr>
<td><select name="modelEnabled"><option value="on"${!model || model.enabled ? ' selected' : ''}>On</option><option value="off"${model && !model.enabled ? ' selected' : ''}>Off</option></select></td>
<td><input name="modelId" value="${escapeHtml(model?.id ?? '')}" placeholder="model id" size="22"></td>
<td><input name="modelLabel" value="${escapeHtml(model?.label ?? '')}" placeholder="label" size="12"></td>
<td><select name="modelProvider">${providerOptions}</select></td>
<td><input name="modelDescription" value="${escapeHtml(model?.description ?? '')}" placeholder="shown on tap" size="24" maxlength="300"></td>
<td><input name="modelTag" value="${escapeHtml(model?.tag ?? '')}" placeholder="tag" size="8" maxlength="24"></td>
<td><select name="modelTagKind">${tagOptions}</select></td>
<td><input name="modelInputPrice" type="number" min="0" step="any" value="${model?.inputPrice ?? 0}" size="6" title="US dollars per million input tokens (not reported by OpenRouter)"></td>
<td><input name="modelOutputPrice" type="number" min="0" step="any" value="${model?.outputPrice ?? 0}" size="6" title="US dollars per million output tokens (not reported by OpenRouter)"></td>
</tr>`;
    })
    .join('\n');
}

/** Previous and next page of the request log, with the page number. */
function logPager(logs) {
  if (logs.pages <= 1) return '';
  const link = (page, label) => (page < 1 || page > logs.pages || page === logs.page
    ? `<span class="hint">${label}</span>`
    : `<a href="/admin?logPage=${page}#requests">${label}</a>`);
  return `<p class="pager">${link(logs.page - 1, '← Newer')} <span class="hint">Page ${logs.page} of ${logs.pages} (${logs.total} requests)</span> ${link(logs.page + 1, 'Older →')}</p>`;
}

/** The assistant: providers, models, routing, limits, logging, and the paged request log. */
function assistantSection(assistant) {
  if (!assistant) return '';
  const { settings, sorts, routings, efforts, keyConfigured, clientSwitchOn, unknownModels, usage, logs } = assistant;
  const effortOptions = ['', ...efforts]
    .map((effort) => `<option value="${effort}"${effort === settings.defaultEffort ? ' selected' : ''}>${effort || "model's own"}</option>`)
    .join('');
  const routingOptions = routings
    .map((routing) => `<option value="${routing.id}"${routing.id === settings.hiddenRouting ? ' selected' : ''}>${escapeHtml(routing.label)}</option>`)
    .join('');
  const defaultOptions = settings.models
    .map((model) => `<option value="${escapeHtml(model.id)}"${model.id === settings.defaultModel ? ' selected' : ''}>${escapeHtml(model.label)} (${escapeHtml(model.id)})</option>`)
    .join('');
  const sortOptions = sorts
    .map((sort) => `<option value="${sort.id}"${sort.id === settings.providerSort ? ' selected' : ''}>${escapeHtml(sort.label)}</option>`)
    .join('');
  const number = (name, label) =>
    `<label for="${name}">${label}</label><input id="${name}" name="${name}" type="number" min="0" step="any" value="${escapeHtml(String(settings[name]))}">`;
  let warning = '';
  if (!clientSwitchOn) {
    warning = '<p class="hint">Client switch "AI assistant" is off: the app hides the assistant, and the server side is off as well.</p>';
  } else if (!keyConfigured) {
    warning = '<p class="hint">No provider has a key: the assistant is off and the app hides it. Add a key to a provider below (OpenRouter can use OPENROUTER_API_KEY).</p>';
  } else if (unknownModels === null) {
    warning = '<p class="hint">Could not read the OpenRouter model list to check the ids.</p>';
  } else if (unknownModels.length > 0) {
    warning = `<p class="hint">OpenRouter does not know: ${unknownModels.map(escapeHtml).join(', ')}. Requests with these models fail.</p>`;
  }
  const rows = logs.total === 0
    ? '<p class="hint">No requests logged yet.</p>'
    : `<div class="scroll"><table>
<tr><th>Time (UTC)</th><th>Status</th><th>Model</th><th>User</th><th>Cost</th><th>Tokens in/out</th><th>Duration</th></tr>
${logs.entries
  .map(
    (entry) => `<tr><td><a href="/admin/assistant/logs/${escapeHtml(entry.id)}">${escapeHtml(entry.at.replace('T', ' ').slice(0, 19))}</a></td><td>${escapeHtml(entry.status)}</td><td>${escapeHtml(entry.model || '–')}</td><td>${escapeHtml(entry.user)}${entry.paid ? ' (paid)' : ''}</td><td>${cents(entry.costCents)}</td><td>${entry.promptTokens ?? '–'} / ${entry.completionTokens ?? '–'}</td><td>${(entry.durationMs / 1000).toFixed(1)} s</td></tr>`,
  )
  .join('\n')}
</table></div>`;
  return `<h2>AI assistant</h2>
${warning}
<p class="hint">Today: ${usage.users} users, ${cents(usage.cents)} spent. Budgets are per user and UTC day.</p>
<form method="post" action="/admin/assistant">
<p>${checkbox('enabled', settings.enabled, 'Server-side switch: the assistant answers')}</p>
<h3>Providers</h3>
<p class="hint">OpenRouter, or any server with an OpenAI-compatible chat completions API or an Anthropic-compatible messages API. Give the address of the server, not of one endpoint.</p>
<div class="scroll"><table>
<tr><th>Name</th><th>Type</th><th>Address</th><th>API key</th></tr>
${providerRows(settings.providers)}
</table></div>
<h3>Models</h3>
<p class="hint">A model that is Off stays in the app's picker but answers with the upstream error. The tag and description show next to the model in the app: an Info tag is a quiet badge, an Alarm tag is loud, and the description opens when the model is long-pressed in the app. Prices are US dollars per million tokens; set them for providers that do not report a cost, so the budget counts.</p>
<div class="scroll"><table>
<tr><th>On</th><th>Model id</th><th>Label</th><th>Provider</th><th>Description</th><th>Tag</th><th>Tag kind</th><th>Input $/M</th><th>Output $/M</th></tr>
${modelRows(settings.models, settings.providers)}
</table></div>
<p>${checkbox('showModelSwitcher', settings.showModelSwitcher, 'Show the model switcher in the app')}</p>
<p><label>While the switcher is hidden, users get <select name="hiddenRouting">${routingOptions}</select></label></p>
<p><label>Default model <select name="defaultModel">${defaultOptions}</select></label> <span class="hint">Save once after changing the list to pick from it.</span></p>
<p><label>Provider routing (OpenRouter) <select name="providerSort">${sortOptions}</select></label></p>
<p>${checkbox('aiTitles', settings.aiTitles, 'Name new chats with a short AI title (one extra small request per chat, counted in the budget)')}</p>
<p>${checkbox('showEffortSelector', settings.showEffortSelector, 'Show the reasoning-effort picker in the app')}</p>
<p><label>Levels the app offers <span class="hint">(${efforts.join(', ')}; not every model takes every level)</span> <input name="efforts" value="${escapeHtml(settings.efforts.join(', '))}" size="24"></label></p>
<p><label>Default effort <select name="defaultEffort">${effortOptions}</select></label></p>
<p>${checkbox('allowFallbacks', settings.allowFallbacks, 'Allow a fallback to another provider when one fails (OpenRouter)')}</p>
<p>${checkbox('denyDataCollection', settings.denyDataCollection, 'Only providers that do not store or train on prompts (OpenRouter)')}</p>
<h3>Limits</h3>
<div class="grid">
${number('freeCentsPerDay', 'Budget per user and day, free (US cents)')}
${number('paidCentsPerDay', 'Budget per user and day, supporters (US cents)')}
${number('assistantPerMinute', 'Assistant requests per IP and minute')}
${number('updateChecksPerMinute', 'Update checks and status per IP and minute')}
${number('downloadsPerHour', 'Downloads per IP and hour')}
</div>
<p>${checkbox('logContent', settings.logContent, 'Log question, answer and tool call text (needed for the request view)')}</p>
<p><label>Keep the log for <input name="logRetentionDays" type="number" min="0" step="any" value="${escapeHtml(String(settings.logRetentionDays))}"> days (at most 30)</label></p>
<p><button type="submit">Save assistant settings</button></p>
</form>
<h3 id="requests">Requests</h3>
<form method="post" action="/admin/assistant/logs/clear"><button type="submit" class="plain">Clear log</button></form>
${rows}
${logPager(logs)}
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
<style>${STYLE}</style>
</head>
<body>
<main>
<p class="hint"><a href="/admin">Back to the admin page</a></p>`;
  if (!entry) return `${head}<p>That request is not in the log (any more).</p></main></body></html>`;
  const field = (label, value) => `<tr><th>${label}</th><td>${escapeHtml(value === undefined || value === null || value === '' ? '–' : String(value))}</td></tr>`;
  const content = entry.content;
  const messages = content
    ? content.messages
        .map((message) => {
          const extra = [
            ...(message.attachments ?? []).map((a) => `[attached ${a}]`),
            ...(message.toolCalls ?? []).map((c) => `[tool call ${c}]`),
          ].join('\n');
          return `<h3>${escapeHtml(message.role)}</h3><pre>${escapeHtml([message.text, extra].filter(Boolean).join('\n'))}</pre>`;
        })
        .join('\n')
    : '<p class="hint">The text was not logged (logging of content is off).</p>';
  const details = content
    ? `${content.reasoning ? `<h2>Reasoning</h2><pre>${escapeHtml(content.reasoning)}</pre>` : ''}<h2>Answer</h2><pre>${escapeHtml(content.answer || '(no text)')}</pre>
${content.toolCalls?.length ? `<h2>Tool calls</h2><pre>${escapeHtml(content.toolCalls.join('\n'))}</pre>` : ''}
<h2>Chat sent</h2>${messages}
<h2>Timetable summary sent</h2><pre>${escapeHtml(content.context)}</pre>`
    : messages;
  return `${head}
<h1>Assistant request ${escapeHtml(entry.id)}</h1>
<table>
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
</main>
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
<p class="hint">Untick to turn off for everyone.</p>
<form method="post" action="/admin/switches">
<p>${checkbox('updates', switches.updates, 'In-app updates on (update checks, notifications, the update screen and the required-version gate)')}</p>
<p>${checkbox('requiredVersion', switches.requiredVersion, 'Minimum app version on (apps older than it stop at the update screen)')}</p>
<p>${checkbox('messages', switches.messages, 'Messages from this page are shown as pop-ups')}</p>
<h3>Single features</h3>
${features}
<p><button type="submit">Save switches</button></p>
</form>
`;
}

/** The form to check or grant a payment for an unlock code, as the app shows it. */
function supportSection() {
  return `<h2>Support payments</h2>
<p class="hint">A Ko-fi payment is recorded by itself when its message holds the code. Otherwise enter the code the app shows below.</p>
<form method="post" action="/admin/support/check">
<p><label>Unlock code <input name="code" placeholder="DB-K7M2-9QXP" size="16" autocomplete="off" spellcheck="false"></label></p>
<p class="row"><button type="submit">Check code</button> <button type="submit" class="plain" formaction="/admin/support/grant">Grant payment</button></p>
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
    ? '<p class="hint">No messages yet.</p>'
    : `<ul class="messages">${state.messages
        .map((message) => {
          const id = escapeHtml(message.id);
          const status = messageStatus(message, now);
          const ends = message.until ? ` · ends ${escapeHtml(message.until)}` : '';
          return `<li>
<p class="meta"><strong>${escapeHtml(message.level)}</strong> · ${status}${ends} · id ${id}</p>
<p>DE: ${escapeHtml(message.de || '–')}</p>
<p>EN: ${escapeHtml(message.en || '–')}</p>
<div class="row">
<form method="post" action="/admin/messages/${id}/toggle"><button type="submit" class="plain">${message.enabled ? 'Hide' : 'Show'}</button></form>
<form method="post" action="/admin/messages/${id}/delete"><button type="submit" class="danger">Delete</button></form>
</div>
</li>`;
        })
        .join('\n')}</ul>`;

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)} update server admin</title>
<style>${STYLE}</style>
</head>
<body>
<main>
<h1>${escapeHtml(app)}</h1>
<p class="lead">Update server · admin</p>
<p class="notice">${notes ? escapeHtml(notes) : ''}</p>

<h2>Minimum app version</h2>
<p class="hint">Current: ${escapeHtml(formatMinVersion(state.minVersion))}. Apps older than this stop at the "update required" screen.</p>
<p class="hint">Warning: this only applies to apps from version ${GATE_SINCE} on. Older apps keep working.</p>
<form method="post" action="/admin/min-version">
<p class="row"><label>Version <input name="version" value="${escapeHtml(state.minVersion?.name ?? '')}" placeholder="1.2.0" size="10"></label>
<button type="submit">Save minimum version</button></p>
</form>

${switchesSection(state)}
${assistantSection(assistant)}
${supportSection()}
<h2>Add a message</h2>
<p class="hint">Shown once per phone when the app opens. No editing: hide or delete the old one and add a new one.</p>
<form method="post" action="/admin/messages">
<p><label>Level <select name="level">${levelOptions}</select></label></p>
<p><label>Text (German)
<textarea name="de" rows="3"></textarea></label></p>
<p><label>Text (English)
<textarea name="en" rows="3"></textarea></label></p>
<p><label>Ends (optional, e.g. 2026-11-07T18:00:00Z) <input name="until" size="24"></label></p>
<p><button type="submit">Add message</button></p>
</form>

<h2>All messages</h2>
${messages}
</main>
</body>
</html>
`;
}
