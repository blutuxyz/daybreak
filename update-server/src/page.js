/**
 * The public download page (GET /). It is plain HTML with inline styles and no scripts, so it works
 * everywhere and the content security policy can forbid scripts altogether.
 */

const TEXT = {
  en: {
    tagline: 'Your WebUntis timetable, absences and exams in one place.',
    download: 'Download for Android',
    version: 'Version',
    released: 'Released',
    size: 'Size',
    checksum: 'SHA-256 checksum',
    installTitle: 'How to install',
    install: [
      'Download the APK and open it.',
      'Android asks whether this browser or file manager may install apps. Allow it. This is needed only for the first install.',
      'Tap Install. If Google Play Protect warns you, choose More details, then Install anyway.',
      'After that the app updates itself. You do not need to come back here.',
    ],
    whatsNew: "What's new",
    noNotes: 'No release notes.',
    older: 'Earlier versions',
    noRelease: 'There is no release yet.',
    noReleaseHint: 'Come back soon.',
    unavailable: 'The releases could not be loaded. Please try again in a moment.',
    locale: 'en-GB',
  },
  de: {
    tagline: 'Dein WebUntis-Stundenplan, deine Fehlzeiten und Prüfungen an einem Ort.',
    download: 'Für Android herunterladen',
    version: 'Version',
    released: 'Veröffentlicht',
    size: 'Größe',
    checksum: 'SHA-256-Prüfsumme',
    installTitle: 'So installierst du die App',
    install: [
      'Lade die APK-Datei herunter und öffne sie.',
      'Android fragt, ob dieser Browser oder Dateimanager Apps installieren darf. Erlaube es. Das ist nur bei der ersten Installation nötig.',
      'Tippe auf Installieren. Warnt dich Google Play Protect, wähle Weitere Details und dann Trotzdem installieren.',
      'Danach aktualisiert sich die App selbst. Du musst nicht mehr hierher zurückkehren.',
    ],
    whatsNew: 'Neuigkeiten',
    noNotes: 'Keine Versionshinweise.',
    older: 'Frühere Versionen',
    noRelease: 'Es gibt noch keine Version.',
    noReleaseHint: 'Schau bald wieder vorbei.',
    unavailable: 'Die Versionen konnten nicht geladen werden. Bitte versuche es gleich noch einmal.',
    locale: 'de-DE',
  },
};

/** Picks German when the browser prefers it, English otherwise. */
export function pickLanguage(acceptLanguage) {
  const first = String(acceptLanguage ?? '').split(',')[0].trim().toLowerCase();
  return first.startsWith('de') ? 'de' : 'en';
}

export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** Bold, code and https links inside one line of release notes. The text is escaped first. */
function inline(text) {
  return escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/https:\/\/[^\s<]+/g, (match) => {
      const url = match.replace(/[.,;:!?)]+$/, '');
      const rest = match.slice(url.length);
      return `<a href="${url}" rel="noopener noreferrer">${url}</a>${rest}`;
    });
}

/** The same small subset of Markdown the app shows: headings, bullets, paragraphs. */
export function renderChangelog(markdown) {
  const html = [];
  let inList = false;
  const closeList = () => {
    if (inList) html.push('</ul>');
    inList = false;
  };
  for (const raw of String(markdown ?? '').split(/\r?\n/)) {
    const line = raw.trim();
    const heading = /^#{1,6}\s+(.*)$/.exec(line);
    const bullet = /^[-*]\s+(.*)$/.exec(line);
    if (heading) {
      closeList();
      html.push(`<h4>${inline(heading[1])}</h4>`);
    } else if (bullet) {
      if (!inList) html.push('<ul>');
      inList = true;
      html.push(`<li>${inline(bullet[1])}</li>`);
    } else if (line === '') {
      closeList();
    } else {
      closeList();
      html.push(`<p>${inline(line)}</p>`);
    }
  }
  closeList();
  return html.join('\n');
}

export function formatSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '';
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso, locale) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(date);
}

const STYLE = `
:root{color-scheme:light dark;--bg:#f5f6fb;--card:#fff;--text:#16181f;--muted:#5d6272;--line:#e1e4ee;--accent:#4a55a2;--on-accent:#fff;--tint:#e8eaf8}
@media (prefers-color-scheme:dark){:root{--bg:#0b0c10;--card:#13151c;--text:#e6e8f2;--muted:#9aa0b4;--line:#242838;--accent:#b9c3f0;--on-accent:#1d2447;--tint:#1b2033}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:640px;margin:0 auto;padding:40px 16px 64px}
h1{font-size:2.4rem;line-height:1.1;margin:0 0 8px;letter-spacing:-.02em}
.tagline{color:var(--muted);margin:0 0 28px}
.card{background:var(--card);border:1px solid var(--line);border-radius:24px;padding:20px;margin-bottom:16px}
.button{display:block;text-align:center;background:var(--accent);color:var(--on-accent);text-decoration:none;font-weight:600;font-size:1.05rem;padding:16px 20px;border-radius:999px}
.button:focus-visible{outline:3px solid var(--text);outline-offset:3px}
.facts{display:flex;flex-wrap:wrap;gap:8px 24px;margin:16px 2px 0;color:var(--muted);font-size:.9rem}
.facts b{color:var(--text);font-weight:600}
h2{font-size:1.1rem;margin:0 0 12px}
ol{margin:0;padding-left:1.3rem}
li{margin:6px 0}
.notes h4{margin:16px 0 4px;font-size:1rem}
.notes ul{margin:0;padding-left:1.2rem}
.notes p{margin:8px 0}
code{background:var(--tint);padding:1px 6px;border-radius:6px;font-size:.9em}
a{color:var(--accent);overflow-wrap:anywhere}
details{border-top:1px solid var(--line);padding:12px 0}
details:first-of-type{border-top:0}
summary{cursor:pointer;font-weight:600}
summary small{color:var(--muted);font-weight:400;margin-left:8px}
.hash{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.78rem;word-break:break-all;color:var(--muted);margin:8px 0 0}
.empty{text-align:center;color:var(--muted);padding:32px 20px}
`;

function shell({ app, lang, body }) {
  const t = TEXT[lang];
  return `<!doctype html>
<html lang="${lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)}</title>
<meta name="description" content="${escapeHtml(t.tagline)}">
<style>${STYLE}</style>
</head>
<body>
<main>
<h1>${escapeHtml(app)}</h1>
<p class="tagline">${escapeHtml(t.tagline)}</p>
${body}
</main>
</body>
</html>
`;
}

/**
 * @param app     the app's name
 * @param latest  the newest release as the API describes it (tag, versionName, publishedAt,
 *                changelog, apk), or undefined when there is none
 * @param older   earlier releases, newest first
 * @param status  'ok' | 'empty' | 'unavailable'
 */
export function renderPage({ app, lang = 'en', latest, older = [], status = 'ok' }) {
  const t = TEXT[lang];
  if (status !== 'ok' || !latest) {
    const message = status === 'unavailable' ? t.unavailable : `${t.noRelease} ${t.noReleaseHint}`;
    return shell({ app, lang, body: `<div class="card empty">${escapeHtml(message)}</div>` });
  }

  const size = formatSize(latest.apk.size);
  const facts = [
    `<span>${t.version} <b>${escapeHtml(latest.versionName)}</b></span>`,
    latest.publishedAt
      ? `<span>${t.released} <b>${escapeHtml(formatDate(latest.publishedAt, t.locale))}</b></span>`
      : '',
    size ? `<span>${t.size} <b>${escapeHtml(size)}</b></span>` : '',
  ].join('');
  const checksum = latest.apk.sha256
    ? `<p class="hash">${t.checksum}:<br>${escapeHtml(latest.apk.sha256)}</p>`
    : '';
  const notes = latest.changelog?.trim()
    ? renderChangelog(latest.changelog)
    : `<p>${escapeHtml(t.noNotes)}</p>`;

  const history = older
    .map((release) => {
      const date = release.publishedAt ? formatDate(release.publishedAt, t.locale) : '';
      const body = release.changelog?.trim() ? renderChangelog(release.changelog) : '';
      return `<details><summary>${escapeHtml(release.versionName)}<small>${escapeHtml(date)}</small></summary>
<div class="notes">${body}<p><a href="${escapeHtml(`/v1/apk/${release.tag}`)}">${escapeHtml(release.tag)}.apk</a></p></div></details>`;
    })
    .join('\n');

  const body = `
<div class="card">
<a class="button" href="/download">${escapeHtml(t.download)}</a>
<div class="facts">${facts}</div>
${checksum}
</div>
<div class="card">
<h2>${escapeHtml(t.installTitle)}</h2>
<ol>${t.install.map((step) => `<li>${escapeHtml(step)}</li>`).join('')}</ol>
</div>
<div class="card notes">
<h2>${escapeHtml(t.whatsNew)}</h2>
${notes}
</div>
${history ? `<div class="card"><h2>${escapeHtml(t.older)}</h2>\n${history}</div>` : ''}`;
  return shell({ app, lang, body });
}
