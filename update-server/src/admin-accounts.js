import { STYLE } from './admin.js';
import { escapeHtml } from './page.js';

/** The admin page for accounts and plans (GET /admin/accounts). Plain forms, no scripts. */

const NOTICES = {
  saved: 'Saved.',
  granted: 'Plan granted.',
  revoked: 'The paid plan is removed.',
  not_found: 'No account matches that id. Use at least the first 4 characters of the account id.',
  bad_plan: 'Choose Plus or Pro.',
  bad_until: 'The end is not a date. Use a form such as 2027-01-31 or leave it empty for no end.',
  unbound: 'The WebUntis account is unbound.',
  bad_number: 'The price must be a number of cents, 0 or more.',
};

/** The date input accepts "2027-01-31"; it becomes the end of that day in UTC. Empty means no end. */
export function parseUntil(text) {
  const value = String(text ?? '').trim();
  if (!value) return { until: null };
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { error: 'bad_until' };
  const time = Date.parse(`${value}T23:59:59Z`);
  return Number.isNaN(time) ? { error: 'bad_until' } : { until: time };
}

function planSettings(settings) {
  return ['plus', 'pro']
    .map((plan) => {
      const item = settings[plan] ?? {};
      return `<tr>
<td>${plan === 'plus' ? 'Plus' : 'Pro'}</td>
<td><input name="${plan}Label" value="${escapeHtml(item.label ?? '')}" size="10" maxlength="20"></td>
<td><input name="${plan}Cents" type="number" min="0" step="1" value="${Number(item.monthlyCents ?? 0)}"></td>
<td><input name="${plan}Price" value="${escapeHtml(item.priceId ?? '')}" placeholder="price_…" size="30"></td>
</tr>`;
    })
    .join('\n');
}

function accountSection(account, view) {
  if (!account) return '';
  const id = escapeHtml(account.id);
  const paid = account.paid
    ? `${escapeHtml(account.paid.plan)} (${escapeHtml(account.paid.source)}) until ${escapeHtml(account.paid.until ?? 'no end')}`
    : 'none';
  const bindings = account.webuntis.length
    ? `<table><tr><th>Label</th><th>Added</th><th></th></tr>${account.webuntis
        .map(
          (entry) => `<tr><td>${escapeHtml(entry.label)}</td><td>${escapeHtml(entry.addedAt)}</td>
<td><form method="post" action="/admin/accounts/unbind"><input type="hidden" name="id" value="${id}"><input type="hidden" name="binding" value="${escapeHtml(entry.id)}"><button type="submit" class="danger">Unbind</button></form></td></tr>`,
        )
        .join('')}</table>`
    : '<p class="hint">No WebUntis account bound.</p>';
  return `<h2>Account ${id}</h2>
<table>
<tr><th>Plan now</th><td>${escapeHtml(view.plan)}${view.planUntil ? ` until ${escapeHtml(view.planUntil)}` : ''}</td></tr>
<tr><th>Paid plan</th><td>${paid}</td></tr>
<tr><th>Stripe</th><td>${escapeHtml(account.stripe?.status ?? 'none')}</td></tr>
<tr><th>Old supporter</th><td>${account.grandfathered ? 'yes' : 'no'}</td></tr>
<tr><th>Trial</th><td>${escapeHtml(account.trial?.until ?? 'not started')}</td></tr>
<tr><th>Created</th><td>${escapeHtml(account.createdAt)}</td></tr>
<tr><th>Sign-ins</th><td>${account.tokens.length}</td></tr>
</table>
<h3>Give a plan by hand</h3>
<form method="post" action="/admin/accounts/grant">
<input type="hidden" name="id" value="${id}">
<p class="row"><label>Plan <select name="plan"><option value="plus">Plus</option><option value="pro">Pro</option></select></label>
<label>Until (optional) <input name="until" placeholder="2027-01-31" size="12"></label>
<button type="submit">Grant</button></p>
</form>
<div class="row">
<form method="post" action="/admin/accounts/revoke"><input type="hidden" name="id" value="${id}"><button type="submit" class="danger">Remove paid plan</button></form>
<form method="post" action="/admin/accounts/grandfather"><input type="hidden" name="id" value="${id}"><input type="hidden" name="value" value="${account.grandfathered ? '' : 'on'}"><button type="submit" class="plain">${account.grandfathered ? 'Not an old supporter' : 'Mark as old supporter'}</button></form>
</div>
<h3>WebUntis accounts</h3>
${bindings}`;
}

export function renderAccounts({ app, notice, count, settings, stripeConfigured, account, view }) {
  const notes = NOTICES[notice];
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(app)} accounts</title>
<style>${STYLE}</style>
</head>
<body>
<main>
<h1>${escapeHtml(app)}</h1>
<p class="lead"><a href="/admin">Update server · admin</a> · accounts and plans · ${count} accounts</p>
<p class="notice">${notes ? escapeHtml(notes) : ''}</p>

<h2>Find an account</h2>
<form method="get" action="/admin/accounts">
<p class="row"><label>Account id (the first characters are enough) <input name="id" size="14"></label><button type="submit">Find</button></p>
</form>
${accountSection(account, view)}

<h2>Plans and prices</h2>
<p class="hint">${stripeConfigured ? 'Stripe is connected.' : 'Stripe is not connected (STRIPE_SECRET_KEY is missing): buying is off, plans can still be given by hand.'} The price id is the one from the Stripe dashboard (a recurring monthly price).</p>
<form method="post" action="/admin/billing">
<table><tr><th>Plan</th><th>Name</th><th>Cents per month</th><th>Stripe price id</th></tr>
${planSettings(settings)}
</table>
<p><button type="submit">Save plans</button></p>
</form>
</main>
</body>
</html>
`;
}
