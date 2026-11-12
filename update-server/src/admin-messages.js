import { STYLE } from './admin.js';
import {
  BUTTON_ACTIONS,
  BUTTON_STYLES,
  IMAGE_SHAPES,
  INSTALL_SOURCES,
  LEVELS,
  PLATFORMS,
  PRESET_IMAGES,
  SHEET_STYLES,
} from './announcements.js';
import { escapeHtml } from './page.js';

/**
 * The message composer of the admin page, the rendered preview of a sheet and the picture library. Plain
 * forms and CSS, no scripts: the preview is a page the server draws from the same fields the app receives,
 * so what is shown here is what the sheet says (the app draws it in its own colours and fonts).
 */

export const MESSAGE_NOTICES = {
  bad_title: 'A title may have up to 80 characters.',
  bad_image: 'Choose a picture from the list.',
  bad_button: 'A button label may have 40 characters and a note under it 200.',
  bad_url: 'A button that opens a link needs an address that starts with https://.',
  too_many_buttons: 'At most three buttons.',
  needs_button: 'A sheet that cannot be swiped away needs at least one button that closes it.',
  image_saved: 'Picture uploaded. You can pick it in the message form below.',
  image_removed: 'Picture removed. Messages that used it show no picture.',
  no_file: 'Choose a picture file first.',
  bad_type: 'Only PNG, JPEG and WebP pictures are accepted.',
  too_large: 'A picture may be 2 MB at most.',
  too_many: 'There are 30 pictures already. Remove one first.',
  demo_added: 'The Play Store announcement was added. It shows on Android installs that did not come from Google Play.',
};

/** The Google Play triangle, drawn from four coloured pieces (a stand-in for the logo, used by the preset "playstore"). */
export const PLAY_STORE_SVG = `<svg viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
<polygon points="8,4 27,24 8,44" fill="#00A3FF"/>
<polygon points="8,4 27,24 35.9,20.4" fill="#00E07A"/>
<polygon points="35.9,20.4 42,24 35.9,27.6 27,24" fill="#FFD500"/>
<polygon points="8,44 27,24 35.9,27.6" fill="#FF3B52"/>
</svg>`;

/** The look of the composer form on the admin page (the preview page includes it as well). */
export const COMPOSER_STYLE = `
fieldset{border:1px solid var(--line);border-radius:6px;margin:0 0 14px;padding:10px 14px 4px}
legend{font-weight:600;font-size:.88rem;padding:0 6px}
.wide{width:100%}
td input,td select{max-width:100%}
`;

const PREVIEW_STYLE = `
.pair{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:20px;margin:18px 0}
.phone{--sheet:#1d1b27;--on:#ece9f5;--muted:#a9a4b8;--primary:#b9c3ff;--on-primary:#1c2461;--tonal:#2e3150;--on-tonal:#dfe1ff;--glow:#7f89d9;
  background:#0e0e13;border:1px solid var(--line);border-radius:26px;padding:26px 14px 0;overflow:hidden;min-height:430px;display:flex;flex-direction:column;justify-content:flex-end}
.phone[data-style=warning]{--glow:#d9a34a}
.phone[data-style=critical]{--glow:#e0645c}
.phone .lang{color:#8d889c;font-size:.75rem;text-align:center;margin:0 0 auto;padding-top:2px}
.sheet{background:var(--sheet);color:var(--on);border-radius:28px 28px 0 0;padding:10px 20px 26px;position:relative;overflow:hidden}
.sheet .handle{width:36px;height:4px;border-radius:2px;background:#5c586b;margin:0 auto 14px}
.sheet .glow{position:absolute;left:50%;top:-70px;width:340px;height:200px;transform:translateX(-50%);background:radial-gradient(closest-side,var(--glow),transparent);opacity:.32;pointer-events:none}
.sheet[data-style=plain] .glow{opacity:.12}
.sheet .banner{position:relative;margin:-10px -20px 16px;height:150px;background:#2a2838 center/cover no-repeat;border-radius:28px 28px 0 0;display:flex;align-items:center;justify-content:center}
.sheet .banner svg{width:64px;height:64px}
.sheet .tile{position:relative;width:76px;height:76px;border-radius:24px;background:#2a2838;display:flex;align-items:center;justify-content:center;margin:6px 0 16px;overflow:hidden}
.sheet .tile svg{width:44px;height:44px}
.sheet .tile img,.sheet .banner img{width:100%;height:100%;object-fit:cover;display:block}
.sheet h3{position:relative;font-size:1.35rem;line-height:1.2;font-weight:650;margin:0 0 8px;color:var(--on)}
.sheet p.body{position:relative;color:var(--muted);margin:0 0 18px;font-size:.95rem;white-space:pre-line}
.sheet .action{position:relative;margin:0 0 12px}
.sheet .btn{display:block;width:100%;text-align:center;border-radius:999px;padding:13px 16px;font-weight:600;font-size:.95rem;border:0}
.sheet .btn.filled{background:var(--primary);color:var(--on-primary)}
.sheet .btn.tonal{background:var(--tonal);color:var(--on-tonal)}
.sheet .btn.text{background:transparent;color:var(--primary)}
.sheet .note{color:var(--muted);font-size:.8rem;line-height:1.35;margin:7px 14px 0;text-align:center}
.sheet .meta{position:relative;color:#8d889c;font-size:.75rem;margin:14px 0 0}
`;

const options = (values, current, label = (value) => value) =>
  values.map((value) => `<option value="${escapeHtml(value)}"${value === current ? ' selected' : ''}>${escapeHtml(label(value))}</option>`).join('');

const LABELS = {
  plain: 'Plain',
  brand: 'Dusk glow (brand colours)',
  warning: 'Warm glow (warning)',
  critical: 'Red glow (critical)',
  icon: 'Round tile',
  banner: 'Banner across the top',
  all: 'Everyone',
  android: 'Android app only',
  desktop: 'Windows app only',
  any: 'Any install',
  sideloaded: 'Only installs from an APK (not Google Play)',
  play: 'Only installs from Google Play',
  dismiss: 'Close the sheet',
  url: 'Open a link',
  filled: 'Filled',
  tonal: 'Tonal',
  text: 'Text only',
};
const label = (value) => LABELS[value] ?? value;

/** The three button rows of the form, filled from [buttons] (stored buttons or none). */
function buttonRows(buttons = []) {
  return [0, 1, 2]
    .map((index) => {
      const button = buttons[index] ?? {};
      return `<tr>
<td><input name="buttonDe" value="${escapeHtml(button.de ?? '')}" placeholder="Beschriftung (DE)" size="16" maxlength="40"><br><input name="buttonEn" value="${escapeHtml(button.en ?? '')}" placeholder="Label (EN)" size="16" maxlength="40"></td>
<td><select name="buttonAction">${options(BUTTON_ACTIONS, button.action ?? 'dismiss', label)}</select><br><input name="buttonUrl" value="${escapeHtml(button.url ?? '')}" placeholder="https://…" size="26"></td>
<td><select name="buttonStyle">${options(BUTTON_STYLES, button.style ?? (index === 0 ? 'filled' : 'tonal'), label)}</select><br><select name="buttonCloses"><option value="yes"${button.closes === false ? '' : ' selected'}>then close</option><option value="no"${button.closes === false ? ' selected' : ''}>keep open</option></select></td>
<td><input name="buttonInfoDe" value="${escapeHtml(button.infoDe ?? '')}" placeholder="Hinweis unter dem Button (DE)" size="28" maxlength="200"><br><input name="buttonInfoEn" value="${escapeHtml(button.infoEn ?? '')}" placeholder="Note under the button (EN)" size="28" maxlength="200"></td>
</tr>`;
    })
    .join('\n');
}

/** The form that makes a message or a sheet. [values] fills it again (after a preview or an error). */
export function messageComposer({ images }) {
  const upload = images.length
    ? images.map((image) => `<option value="${escapeHtml(image.id)}">${escapeHtml(image.name)} (${image.id})</option>`).join('')
    : '<option value="">(upload a picture first)</option>';
  return `<form method="post" action="/admin/messages/preview">
<input type="hidden" name="dismissibleGiven" value="1">
<fieldset><legend>Words</legend>
<p><label>Title (German) <input class="wide" name="titleDe" maxlength="80" placeholder="Daybreak ist jetzt im Play Store"></label></p>
<p><label>Title (English) <input class="wide" name="titleEn" maxlength="80" placeholder="Daybreak is now on the Play Store"></label></p>
<p><label>Text (German)<textarea name="de" rows="3" maxlength="500"></textarea></label></p>
<p><label>Text (English)<textarea name="en" rows="3" maxlength="500"></textarea></label></p>
<p class="hint">With a title, the text may stay empty. Without a title, the sheet is headed by its level (Hinweis, Warnung, Wichtig).</p>
</fieldset>
<fieldset><legend>Picture</legend>
<p class="row"><label>Source <select name="imageKind"><option value="none">No picture</option><option value="preset">Built in</option><option value="upload">Uploaded</option></select></label>
<label>Built in <select name="imagePreset">${options(PRESET_IMAGES, 'playstore', (value) => (value === 'playstore' ? 'Google Play' : value))}</select></label>
<label>Uploaded <select name="imageUpload">${upload}</select></label>
<label>Shape <select name="imageShape">${options(IMAGE_SHAPES, 'icon', label)}</select></label></p>
</fieldset>
<fieldset><legend>Look and audience</legend>
<p class="row"><label>Look <select name="style">${options(SHEET_STYLES, 'brand', label)}</select></label>
<label>Level <select name="level">${options(LEVELS, 'info')}</select></label>
<label><input type="checkbox" name="dismissible" value="on" checked> Can be swiped away</label></p>
<p class="row"><label>Platform <select name="platform">${options(PLATFORMS, 'all', label)}</select></label>
<label>Install <select name="installSource">${options(INSTALL_SOURCES, 'any', label)}</select></label>
<label>Ends (optional, e.g. 2026-12-31T23:59:00Z) <input name="until" size="22"></label></p>
</fieldset>
<fieldset><legend>Buttons (up to three, empty rows are ignored)</legend>
<div class="scroll"><table>
<tr><th>Label</th><th>What it does</th><th>Look</th><th>Note under it</th></tr>
${buttonRows()}
</table></div>
</fieldset>
<p><button type="submit">Preview</button></p>
</form>`;
}

/** One sheet, drawn for one language. [message] is a stored or normalized message. */
export function sheetPreview(message, lang) {
  const sheet = message.sheet ?? null;
  const pick = (de, en) => (lang === 'de' ? de || en : en || de) || '';
  const levelTitle = { info: lang === 'de' ? 'Hinweis' : 'Notice', warning: lang === 'de' ? 'Warnung' : 'Warning', critical: lang === 'de' ? 'Wichtig' : 'Important' }[message.level] ?? '';
  const title = sheet ? pick(sheet.titleDe, sheet.titleEn) || levelTitle : levelTitle;
  const body = pick(message.de, message.en);
  const style = sheet?.style ?? (message.level === 'critical' ? 'critical' : message.level === 'warning' ? 'warning' : 'plain');
  const image = sheet?.image;
  const inner = image?.kind === 'preset' && image.value === 'playstore'
    ? PLAY_STORE_SVG
    : image?.kind === 'upload'
      ? `<img alt="" src="/admin/message-image/${escapeHtml(image.value)}">`
      : '';
  const picture = !image
    ? ''
    : image.shape === 'banner'
      ? `<div class="banner">${inner}</div>`
      : `<div class="tile">${inner}</div>`;
  const buttons = (sheet?.buttons?.length ? sheet.buttons : [{ de: 'OK', en: 'OK', style: 'filled' }])
    .map((button) => {
      const note = pick(button.infoDe, button.infoEn);
      return `<div class="action"><span class="btn ${escapeHtml(button.style ?? 'filled')}">${escapeHtml(pick(button.de, button.en))}</span>${note ? `<p class="note">${escapeHtml(note)}</p>` : ''}</div>`;
    })
    .join('');
  const meta = [
    sheet?.platform && sheet.platform !== 'all' ? label(sheet.platform) : '',
    sheet?.installSource && sheet.installSource !== 'any' ? label(sheet.installSource) : '',
    sheet && !sheet.dismissible ? 'Cannot be swiped away' : '',
  ].filter(Boolean).join(' · ');
  return `<div class="phone" data-style="${escapeHtml(style)}">
<p class="lang">${lang === 'de' ? 'Deutsch' : 'English'}</p>
<div class="sheet" data-style="${escapeHtml(style)}"><div class="glow"></div><div class="handle"></div>
${picture}
<h3>${escapeHtml(title)}</h3>
${body ? `<p class="body">${escapeHtml(body)}</p>` : ''}
${buttons}
${meta ? `<p class="meta">${escapeHtml(meta)}</p>` : ''}
</div></div>`;
}

/** The page behind "Preview": both languages, and the same fields again as hidden inputs to save them. */
export function renderPreviewPage({ app, message, fields, error, canSave }) {
  const hidden = Object.entries(fields ?? {})
    .flatMap(([key, value]) => (Array.isArray(value) ? value : [value]).map((item) => `<input type="hidden" name="${escapeHtml(key)}" value="${escapeHtml(item)}">`))
    .join('\n');
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)} message preview</title>
<style>${STYLE}${COMPOSER_STYLE}${PREVIEW_STYLE}</style>
</head>
<body>
<main style="max-width:900px">
<h1>${escapeHtml(app)}</h1>
<p class="lead"><a href="/admin">Update server · admin</a> · message preview</p>
${error ? `<p class="notice">${escapeHtml(error)}</p>` : '<p class="hint">This is how the sheet reads. The app draws it in its own colours and fonts, in the language of the phone.</p>'}
${message ? `<div class="pair">${sheetPreview(message, 'de')}${sheetPreview(message, 'en')}</div>` : ''}
<div class="row">
${canSave ? `<form method="post" action="/admin/messages">${hidden}<button type="submit">Save and publish</button></form>` : ''}
<a href="/admin#compose">Back to the admin page</a>
</div>
</main>
</body>
</html>
`;
}

/** The picture library: an upload form and the pictures with their ids. */
export function imagesSection(images) {
  const list = images.length
    ? `<div class="scroll"><table><tr><th></th><th>Name</th><th>Size</th><th></th></tr>${images
        .map(
          (image) => `<tr><td><img alt="" src="/admin/message-image/${escapeHtml(image.id)}" width="44" height="44" style="object-fit:cover;border-radius:10px"></td>
<td>${escapeHtml(image.name)}<br><span class="meta">${escapeHtml(image.id)}</span></td><td>${Math.max(1, Math.round(image.size / 1024))} KB</td>
<td><form method="post" action="/admin/images/${escapeHtml(image.id)}/delete"><button type="submit" class="danger">Remove</button></form></td></tr>`,
        )
        .join('')}</table></div>`
    : '<p class="hint">No pictures yet.</p>';
  return `<h2>Pictures for messages</h2>
<p class="hint">PNG, JPEG or WebP, up to 2 MB. A square picture of about 512 × 512 pixels fits the round tile; a banner works best at about 1200 × 600.</p>
<form method="post" action="/admin/images" enctype="multipart/form-data">
<p class="row"><input type="file" name="file" accept="image/png,image/jpeg,image/webp"> <button type="submit">Upload</button></p>
</form>
${list}`;
}

/** The ready-made announcement for the Play Store release, as a prepared message. */
export function playStoreMessage({ id, now = Date.now(), playUrl }) {
  return {
    id,
    level: 'info',
    de: 'Hol dir Daybreak im Play Store: Updates kommen dann über Google Play.',
    en: 'Get Daybreak from Google Play: updates then come through Google Play.',
    enabled: true,
    until: null,
    createdAt: new Date(now).toISOString(),
    sheet: {
      titleDe: 'Daybreak gibt es jetzt im Play Store',
      titleEn: 'Daybreak is now available on the Play Store',
      image: { kind: 'preset', value: 'playstore', shape: 'icon' },
      style: 'brand',
      dismissible: true,
      platform: 'android',
      installSource: 'sideloaded',
      buttons: [
        {
          de: 'Im Play Store laden',
          en: 'Download from the Play Store',
          action: 'url',
          url: playUrl,
          style: 'filled',
          closes: true,
          infoDe: 'Wenn die Installation aus dem Play Store fehlschlägt, deinstalliere bitte zuerst die aktuelle Daybreak-Version.',
          infoEn: 'Please uninstall the current Daybreak version first if installation from the Play Store fails.',
        },
        {
          de: 'Mit der APK weitermachen',
          en: 'Continue using the APK',
          action: 'dismiss',
          url: '',
          style: 'text',
          closes: true,
          infoDe: 'Automatische Updates funktionieren weiterhin wie gewohnt.',
          infoEn: 'Auto updates still work as usual.',
        },
      ],
    },
  };
}
