import { escapeHtml } from './page.js';
import { LEVELS } from './announcements.js';

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
};

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
