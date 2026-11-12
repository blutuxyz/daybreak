import { escapeHtml } from './page.js';

/**
 * The public plans page (GET /subscribe): what Free, Plus and Pro include, and the "Donate" button that
 * leads to Ko-fi. Buying happens inside the app (it opens Stripe Checkout), so the page has no forms.
 */

const TEXT = {
  en: {
    title: (app) => `${app} plans`,
    lead: 'Daybreak is free to use. Plans add comfort and more of Dawn, the assistant.',
    perMonth: 'per month',
    free: 'Free',
    freeItems: [
      'Timetable, absences, exams, homework, messages and search',
      'Notifications, reminders and the basic widget',
      'Dawn with a few questions a day, photos and PDFs included',
    ],
    plusItems: [
      'Everything in Free',
      'More questions a day and more models in Dawn',
      'App name and icon, calendar export',
      'Widget options and sharing the timetable as a picture',
      'Your settings and notes on all your devices',
    ],
    proItems: [
      'Everything in Plus',
      'The most questions a day and every model in Dawn',
      'Reasoning level and message drafts in Dawn',
      'Haptics studio, accent colours and AMOLED black',
    ],
    how: 'Subscribe in the app: More, then Account. Payment is handled by Stripe; you can change or cancel at any time from the same place.',
    donateTitle: 'Just want to say thanks?',
    donateText: 'A donation does not unlock anything. It only helps.',
    donate: 'Donate on Ko-fi',
    done: 'Thank you! Your plan is active in a moment. You can go back to the app now.',
  },
  de: {
    title: (app) => `${app} Tarife`,
    lead: 'Daybreak ist kostenlos nutzbar. Die Tarife bringen mehr Komfort und mehr von Dawn, dem Assistenten.',
    perMonth: 'pro Monat',
    free: 'Kostenlos',
    freeItems: [
      'Stundenplan, Fehlzeiten, Prüfungen, Hausaufgaben, Nachrichten und Suche',
      'Benachrichtigungen, Erinnerungen und das einfache Widget',
      'Dawn mit ein paar Fragen am Tag, Fotos und PDFs inklusive',
    ],
    plusItems: [
      'Alles aus Kostenlos',
      'Mehr Fragen am Tag und mehr Modelle in Dawn',
      'App-Name und Symbol, Kalenderexport',
      'Widget-Optionen und Stundenplan als Bild teilen',
      'Deine Einstellungen und Notizen auf allen Geräten',
    ],
    proItems: [
      'Alles aus Plus',
      'Die meisten Fragen am Tag und alle Modelle in Dawn',
      'Denkstufe und Nachrichtenentwürfe in Dawn',
      'Haptik-Studio, Akzentfarben und AMOLED-Schwarz',
    ],
    how: 'Abonniere in der App: Mehr, dann Konto. Die Zahlung läuft über Stripe; Ändern oder Kündigen geht jederzeit am selben Ort.',
    donateTitle: 'Du möchtest einfach Danke sagen?',
    donateText: 'Eine Spende schaltet nichts frei. Sie hilft nur.',
    donate: 'Auf Ko-fi spenden',
    done: 'Danke! Dein Tarif ist gleich aktiv. Du kannst jetzt zur App zurückkehren.',
  },
};

const STYLE = `
:root{color-scheme:light dark;--bg:#fbf8f4;--card:#fff;--text:#1d1b22;--muted:#6a6572;--line:#e6e0d8;--accent:#6a4fb3;--on-accent:#fff}
@media (prefers-color-scheme:dark){:root{--bg:#121116;--card:#1b1a21;--text:#ece9f1;--muted:#a19cab;--line:#2c2a34;--accent:#b9a6f2;--on-accent:#1d1534}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:880px;margin:0 auto;padding:48px 20px 72px}
h1{font-size:1.7rem;margin:0 0 6px;letter-spacing:-.01em}
.lead{color:var(--muted);margin:0 0 28px}
.plans{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:14px}
.plan{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:20px}
.plan.pro{border-color:var(--accent)}
.plan h2{font-size:1.05rem;margin:0}
.price{font-size:1.6rem;font-weight:650;margin:6px 0 2px}
.price small{font-size:.8rem;font-weight:400;color:var(--muted)}
ul{padding-left:1.1rem;margin:12px 0 0}
li{margin:0 0 6px}
.how{color:var(--muted);margin:28px 0 0}
.donate{margin-top:36px;padding-top:24px;border-top:1px solid var(--line)}
.donate h2{font-size:1rem;margin:0 0 4px}
.donate p{color:var(--muted);margin:0 0 14px}
a.button{display:inline-block;background:var(--accent);color:var(--on-accent);text-decoration:none;font-weight:600;border-radius:999px;padding:10px 20px}
a.button:hover{opacity:.9}
.done{background:var(--card);border:1px solid var(--accent);border-radius:12px;padding:12px 16px;margin:0 0 20px}
`;

/** "2,99 €" in German, "€2.99" in English. */
function money(cents, lang) {
  const euros = (Number(cents) / 100).toFixed(2);
  return lang === 'de' ? `${euros.replace('.', ',')} €` : `€${euros}`;
}

export function renderSubscribe({ app, lang = 'en', settings, donateUrl, done = false }) {
  const t = TEXT[lang] ?? TEXT.en;
  const list = (items) => `<ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
  const paid = (key, items, extra = '') => {
    const plan = settings[key] ?? {};
    return `<section class="plan${extra}">
<h2>${escapeHtml(plan.label || key)}</h2>
<p class="price">${money(plan.monthlyCents, lang)} <small>${t.perMonth}</small></p>
${list(items)}
</section>`;
  };
  // Only an https link is ever put in the page, whatever the setting says.
  const donate = /^https:\/\//.test(donateUrl ?? '') ? donateUrl : undefined;
  return `<!doctype html>
<html lang="${lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(t.title(app))}</title>
<style>${STYLE}</style>
</head>
<body>
<main>
<h1>${escapeHtml(t.title(app))}</h1>
${done ? `<p class="done">${escapeHtml(t.done)}</p>` : ''}
<p class="lead">${escapeHtml(t.lead)}</p>
<div class="plans">
<section class="plan">
<h2>${escapeHtml(t.free)}</h2>
<p class="price">${money(0, lang)} <small>${t.perMonth}</small></p>
${list(t.freeItems)}
</section>
${paid('plus', t.plusItems)}
${paid('pro', t.proItems, ' pro')}
</div>
<p class="how">${escapeHtml(t.how)}</p>
${
  donate
    ? `<div class="donate"><h2>${escapeHtml(t.donateTitle)}</h2><p>${escapeHtml(t.donateText)}</p><a class="button" href="${escapeHtml(donate)}" rel="noopener noreferrer">${escapeHtml(t.donate)}</a></div>`
    : ''
}
</main>
</body>
</html>
`;
}
