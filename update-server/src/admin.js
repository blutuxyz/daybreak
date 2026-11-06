import { escapeHtml } from './page.js';
import { FEATURES, LEVELS, readSwitches } from './announcements.js';

/**
 * The admin page (GET /admin). Deliberately plain: no CSS and no scripts, just forms, so it works in
 * any browser and the content security policy can forbid everything.
 */

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
};

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

export function renderAdmin({ app, state, notice, now = Date.now() }) {
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
<form method="post" action="/admin/min-version">
<p><label>Version <input name="version" value="${escapeHtml(state.minVersion?.name ?? '')}" placeholder="1.2.0"></label>
<button type="submit">Save minimum version</button></p>
</form>

${switchesSection(state)}
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
