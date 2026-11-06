/**
 * The public download pages: GET / for Android and GET /desktop for Windows. They are plain HTML
 * with inline styles and no scripts, so they work everywhere and the content security policy can
 * forbid scripts altogether.
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
    android: 'Android',
    windows: 'Windows',
    desktopHeading: (app) => `${app} for Windows`,
    desktopTagline: 'Your WebUntis timetable, absences and exams on your computer.',
    desktopDownload: 'Download for Windows',
    desktopInstall: [
      'Download the installer (Daybreak-…-setup.exe) and open it.',
      'Windows may warn that the publisher is unknown, because this installer is not signed yet. Choose More info, then Run anyway. This is needed only for the first install.',
      'That is all: there are no pages to click through. Daybreak installs for your user (no administrator needed), is added to the Start menu and opens when it is done. Run the installer again to update; an older MSI version is removed for you.',
      'Later versions are offered inside the app. It checks for them by itself, downloads them in the background and asks before it installs.',
    ],
    noDesktopRelease: 'There is no Windows version yet.',
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
    android: 'Android',
    windows: 'Windows',
    desktopHeading: (app) => `${app} für Windows`,
    desktopTagline: 'Dein WebUntis-Stundenplan, deine Fehlzeiten und Prüfungen auf dem Computer.',
    desktopDownload: 'Für Windows herunterladen',
    desktopInstall: [
      'Lade den Installer (Daybreak-…-setup.exe) herunter und öffne ihn.',
      'Windows warnt womöglich, dass der Herausgeber unbekannt ist, weil der Installer noch nicht signiert ist. Wähle Weitere Informationen und dann Trotzdem ausführen. Das ist nur bei der ersten Installation nötig.',
      'Mehr ist nicht nötig: Es gibt keine Seiten zum Durchklicken. Daybreak wird für deinen Benutzer installiert (ohne Administratorrechte), im Startmenü eingetragen und öffnet sich am Ende. Zum Aktualisieren führst du den Installer einfach noch einmal aus; eine ältere MSI-Version wird dabei entfernt.',
      'Neuere Versionen werden in der App angeboten. Sie sucht selbst danach, lädt sie im Hintergrund herunter und fragt, bevor sie installiert.',
    ],
    noDesktopRelease: 'Es gibt noch keine Windows-Version.',
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
.tagline{color:var(--muted);margin:0 0 20px}
.switch{display:flex;gap:6px;margin:0 0 28px;padding:4px;background:var(--card);border:1px solid var(--line);border-radius:999px;width:max-content}
.switch a{text-decoration:none;color:var(--muted);padding:6px 16px;border-radius:999px;font-size:.92rem}
.switch a[aria-current="page"]{background:var(--accent);color:var(--on-accent);font-weight:600}
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

/** The switch between the Android page and the Windows page; the open one is marked. */
function platformSwitch(lang, current) {
  const t = TEXT[lang];
  const link = (href, label, key) =>
    `<a href="${href}"${current === key ? ' aria-current="page"' : ''}>${escapeHtml(label)}</a>`;
  return `<nav class="switch" aria-label="${escapeHtml(t.android)} / ${escapeHtml(t.windows)}">${link('/', t.android, 'android')}${link('/desktop', t.windows, 'windows')}</nav>`;
}

function shell({ app, lang, heading, current, body }) {
  const t = TEXT[lang];
  return `<!doctype html>
<html lang="${lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(heading ?? app)}</title>
<meta name="description" content="${escapeHtml(current === 'windows' ? t.desktopTagline : t.tagline)}">
<style>${STYLE}</style>
</head>
<body>
<main>
${platformSwitch(lang, current)}
<h1>${escapeHtml(heading ?? app)}</h1>
<p class="tagline">${escapeHtml(current === 'windows' ? t.desktopTagline : t.tagline)}</p>
${body}
</main>
</body>
</html>
`;
}

/** The facts and the checksum of one file: version, release date, size, SHA-256. */
function fileFacts(lang, release, file) {
  const t = TEXT[lang];
  const size = formatSize(file?.size);
  const facts = [
    `<span>${t.version} <b>${escapeHtml(release.versionName)}</b></span>`,
    release.publishedAt
      ? `<span>${t.released} <b>${escapeHtml(formatDate(release.publishedAt, t.locale))}</b></span>`
      : '',
    size ? `<span>${t.size} <b>${escapeHtml(size)}</b></span>` : '',
  ].join('');
  const checksum = file?.sha256 ? `<p class="hash">${t.checksum}:<br>${escapeHtml(file.sha256)}</p>` : '';
  return { facts, checksum };
}

/** The earlier releases as folded sections, each linking to its own file. */
function olderReleases(lang, older, fileUrl) {
  const t = TEXT[lang];
  return older
    .map((release) => {
      const date = release.publishedAt ? formatDate(release.publishedAt, t.locale) : '';
      const body = release.changelog?.trim() ? renderChangelog(release.changelog) : '';
      return `<details><summary>${escapeHtml(release.versionName)}<small>${escapeHtml(date)}</small></summary>
<div class="notes">${body}<p><a href="${escapeHtml(fileUrl(release))}">${escapeHtml(release.tag)}</a></p></div></details>`;
    })
    .join('\n');
}

/**
 * The Android page.
 *
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
    return shell({ app, lang, current: 'android', body: `<div class="card empty">${escapeHtml(message)}</div>` });
  }

  const { facts, checksum } = fileFacts(lang, latest, latest.apk);
  const notes = latest.changelog?.trim()
    ? renderChangelog(latest.changelog)
    : `<p>${escapeHtml(t.noNotes)}</p>`;
  const history = olderReleases(lang, older, (release) => `/v1/apk/${release.tag}`);

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
  return shell({ app, lang, current: 'android', body });
}

/**
 * The Windows page. It offers the installer of the newest release that has one.
 *
 * @param latest  the newest release with an installer, as /v1/latest describes it (its `windows.installer`
 *                holds the installer's size and checksum), or undefined
 * @param older   earlier releases with an installer, newest first
 * @param status  'ok' | 'empty' | 'unavailable'
 */
export function renderDesktopPage({ app, lang = 'en', latest, older = [], status = 'ok' }) {
  const t = TEXT[lang];
  const heading = t.desktopHeading(app);
  if (status !== 'ok' || !latest) {
    const message = status === 'unavailable' ? t.unavailable : t.noDesktopRelease;
    return shell({ app, lang, heading, current: 'windows', body: `<div class="card empty">${escapeHtml(message)}</div>` });
  }

  const file = latest.windows?.installer;
  const { facts, checksum } = fileFacts(lang, latest, file);
  const notes = latest.changelog?.trim()
    ? renderChangelog(latest.changelog)
    : `<p>${escapeHtml(t.noNotes)}</p>`;
  const history = olderReleases(lang, older, (release) => `/v1/setup/${release.tag}`);

  const body = `
<div class="card">
<a class="button" href="/desktop/download">${escapeHtml(t.desktopDownload)}</a>
<div class="facts">${facts}</div>
${checksum}
</div>
<div class="card">
<h2>${escapeHtml(t.installTitle)}</h2>
<ol>${t.desktopInstall.map((step) => `<li>${escapeHtml(step)}</li>`).join('')}</ol>
</div>
<div class="card notes">
<h2>${escapeHtml(t.whatsNew)}</h2>
${notes}
</div>
${history ? `<div class="card"><h2>${escapeHtml(t.older)}</h2>\n${history}</div>` : ''}`;
  return shell({ app, lang, heading, current: 'windows', body });
}
