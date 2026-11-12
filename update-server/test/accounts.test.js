import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { test } from 'node:test';
import { AccountStore, MAX_WEBUNTIS, effectivePlan, makeRestoreCode, normalizeRestoreCode } from '../src/accounts.js';
import { StripeClient, verifyStripeSignature } from '../src/billing.js';
import { SupportLedger } from '../src/support.js';
import { buildServer } from '../src/server.js';
import { fakeFetch, jsonResponse, testConfig } from './helpers.js';

const host = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };
const oldCode = 'DB-K7M2-9QXP';

async function setup({ now = () => Date.now(), configOverrides = {}, fetchImpl } = {}) {
  const config = await testConfig({ adminToken: 'admin-secret-long-enough', stripeSecretKey: 'sk_test_x', stripeWebhookSecret: 'whsec_test', ...configOverrides });
  const accounts = new AccountStore({ config, now });
  const support = new SupportLedger({ config });
  const stripe = new StripeClient({ config, fetchImpl });
  const { app } = buildServer({ config, accounts, support, stripe, now });
  return { app, accounts, support, config };
}

const call = (app, method, url, { token, body } = {}) =>
  app.inject({
    method,
    url,
    headers: { ...host, ...(token ? { authorization: `Bearer ${token}` } : {}), ...(body ? { 'content-type': 'application/json' } : {}) },
    ...(body ? { payload: JSON.stringify(body) } : {}),
  });

const create = async (app, body = {}) => (await call(app, 'POST', '/v1/account', { body })).json();

test('restore codes have a fixed shape and are read back from messy input', () => {
  const code = makeRestoreCode();
  assert.match(code, /^DA-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/);
  assert.equal(normalizeRestoreCode(` ${code.toLowerCase().replaceAll('-', ' ')} `), code);
  assert.equal(normalizeRestoreCode('DA-1111-2222-3333'), undefined);
  assert.equal(normalizeRestoreCode(undefined), undefined);
});

test('a new account gets a token and a restore code, and a second device can sign in with the code', async () => {
  const { app } = await setup();
  const created = await create(app);
  assert.ok(created.token && created.restoreCode && created.accountId);
  assert.equal(created.plan, 'free');
  assert.equal(created.grandfathered, false);

  assert.equal((await call(app, 'GET', '/v1/account', { token: created.token })).json().accountId, created.accountId);
  assert.equal((await call(app, 'GET', '/v1/account')).statusCode, 401);

  const second = await call(app, 'POST', '/v1/account/restore', { body: { restoreCode: created.restoreCode } });
  assert.equal(second.statusCode, 200);
  assert.equal(second.json().accountId, created.accountId);
  assert.notEqual(second.json().token, created.token);
  assert.equal((await call(app, 'POST', '/v1/account/restore', { body: { restoreCode: 'DA-AAAA-BBBB-CCCC' } })).statusCode, 404);
});

test('the account file keeps no restore code and no token in the clear', async () => {
  const { app, config } = await setup();
  const created = await create(app);
  const { readFile } = await import('node:fs/promises');
  const text = await readFile(`${config.dataDir}/accounts.json`, 'utf8');
  assert.ok(!text.includes(created.restoreCode));
  assert.ok(!text.includes(created.token));
});

test('an old Ko-fi supporter is grandfathered once, and only when the old code was paid', async () => {
  const { app, support } = await setup();
  assert.equal((await create(app, { legacyUnlockCode: oldCode })).grandfathered, false);

  await support.grant({ code: oldCode });
  const first = await create(app, { legacyUnlockCode: oldCode });
  assert.equal(first.grandfathered, true);
  assert.equal(first.supporterExtras, true);
  assert.equal(first.plan, 'free');
  assert.deepEqual(first.proTrial, { offered: true, accepted: false, running: false, until: null });

  const again = await create(app, { legacyUnlockCode: oldCode });
  assert.equal(again.grandfathered, false);
  assert.equal(again.legacy, 'claimed');
});

test('accepting the trial gives three months of Pro, once', async () => {
  let time = Date.parse('2026-12-01T10:00:00Z');
  const { app, support } = await setup({ now: () => time });
  await support.grant({ code: oldCode });
  const { token } = await create(app, { legacyUnlockCode: oldCode });

  const accepted = await call(app, 'POST', '/v1/account/trial/accept', { token });
  assert.equal(accepted.statusCode, 200);
  assert.equal(accepted.json().plan, 'pro');
  assert.equal(accepted.json().planUntil, '2027-03-01T10:00:00.000Z');
  assert.equal((await call(app, 'POST', '/v1/account/trial/accept', { token })).statusCode, 409);

  time = Date.parse('2027-03-02T00:00:00Z');
  const later = (await call(app, 'GET', '/v1/account', { token })).json();
  assert.equal(later.plan, 'free');
  assert.equal(later.supporterExtras, true);
});

test('the trial is not offered to an account that was not a supporter', async () => {
  const { app } = await setup();
  const { token } = await create(app);
  assert.equal((await call(app, 'POST', '/v1/account/trial/accept', { token })).statusCode, 409);
});

test('an account binds at most three WebUntis accounts and the same one only once', async () => {
  const { app } = await setup();
  const { token } = await create(app);
  const bind = (studentId, extra = {}) =>
    call(app, 'POST', '/v1/account/webuntis', { token, body: { server: 'https://Mese.webuntis.com/', school: 'My School', studentId, label: `Kind ${studentId}`, ...extra } });

  for (let id = 1; id <= MAX_WEBUNTIS; id += 1) assert.equal((await bind(id)).statusCode, 201);
  assert.equal((await bind(4)).json().error, 'limit');
  assert.equal((await bind(2, { server: 'mese.webuntis.com', school: 'my school' })).json().error, 'duplicate');
  assert.equal((await bind('x')).statusCode, 400);

  const account = (await call(app, 'GET', '/v1/account', { token })).json();
  assert.equal(account.webuntis.length, 3);
  assert.deepEqual(Object.keys(account.webuntis[0]).sort(), ['id', 'label']);

  const removed = await call(app, 'DELETE', `/v1/account/webuntis/${account.webuntis[0].id}`, { token });
  assert.equal(removed.json().webuntis.length, 2);
  assert.equal((await bind(4)).statusCode, 201);
});

test('binding a student another account has bound is reported, and no school or id is stored in the clear', async () => {
  const { app, config } = await setup();
  const first = await create(app);
  const second = await create(app);
  const body = { server: 'mese.webuntis.com', school: 'secretschool', studentId: 4711, label: 'Me' };
  assert.equal((await call(app, 'POST', '/v1/account/webuntis', { token: first.token, body })).json().sharedWithOthers, false);
  assert.equal((await call(app, 'POST', '/v1/account/webuntis', { token: second.token, body })).json().sharedWithOthers, true);

  const { readFile } = await import('node:fs/promises');
  const text = await readFile(`${config.dataDir}/accounts.json`, 'utf8');
  assert.ok(!text.includes('secretschool'));
  assert.ok(!text.includes('4711'));
});

test('the better of the paid plan and the trial counts, and an ended plan counts for nothing', () => {
  const now = Date.parse('2026-12-01T00:00:00Z');
  const account = {
    paid: { plan: 'plus', until: '2027-01-01T00:00:00Z', source: 'admin' },
    trial: { until: '2026-12-15T00:00:00Z' },
  };
  assert.equal(effectivePlan(account, now).plan, 'pro');
  assert.equal(effectivePlan(account, Date.parse('2026-12-20T00:00:00Z')).plan, 'plus');
  assert.equal(effectivePlan(account, Date.parse('2027-02-01T00:00:00Z')).plan, 'free');
  assert.equal(effectivePlan({ paid: { plan: 'pro', until: null, source: 'admin' }, trial: null }, now).plan, 'pro');
});

test('sync needs a plan, and a stale write is refused with the current document', async () => {
  const { app, accounts } = await setup();
  const { token, accountId } = await create(app);
  assert.equal((await call(app, 'GET', '/v1/sync', { token })).statusCode, 402);

  await accounts.setPaid(accountId, { plan: 'plus', source: 'admin' });
  assert.deepEqual((await call(app, 'GET', '/v1/sync', { token })).json(), { version: 0, data: null });

  const first = await call(app, 'PUT', '/v1/sync', { token, body: { baseVersion: 0, data: { colours: { math: '#fff' } } } });
  assert.deepEqual(first.json(), { version: 1 });
  const stale = await call(app, 'PUT', '/v1/sync', { token, body: { baseVersion: 0, data: { colours: {} } } });
  assert.equal(stale.statusCode, 409);
  assert.deepEqual(stale.json().data, { colours: { math: '#fff' } });
  assert.equal((await call(app, 'PUT', '/v1/sync', { token, body: { baseVersion: 1, data: 'no' } })).statusCode, 400);
});

test('checkout asks Stripe for a subscription page for the signed-in account', async () => {
  const fetchImpl = fakeFetch({ 'https://api.stripe.com/v1/checkout/sessions': () => jsonResponse({ url: 'https://checkout.stripe.com/c/pay/cs_test' }) });
  const { app, accounts } = await setup({ fetchImpl });
  const { token, accountId } = await create(app);

  assert.equal((await call(app, 'POST', '/v1/billing/checkout', { token, body: { plan: 'plus' } })).json().error, 'payments_off');

  await accounts.saveSettings({ plus: { label: 'Plus', monthlyCents: 299, priceId: 'price_plus' }, pro: { label: 'Pro', monthlyCents: 599, priceId: 'price_pro' } });
  const response = await call(app, 'POST', '/v1/billing/checkout', { token, body: { plan: 'pro' } });
  assert.equal(response.json().url, 'https://checkout.stripe.com/c/pay/cs_test');

  const sent = new URLSearchParams(fetchImpl.calls[0].init.body);
  assert.equal(sent.get('line_items[0][price]'), 'price_pro');
  assert.equal(sent.get('client_reference_id'), accountId);
  assert.equal(sent.get('mode'), 'subscription');
  assert.equal(fetchImpl.calls[0].init.headers.Authorization, 'Bearer sk_test_x');
  assert.equal((await call(app, 'POST', '/v1/billing/checkout', { token, body: { plan: 'gold' } })).statusCode, 400);
});

const sign = (payload, secret, time) => `t=${time},v1=${createHmac('sha256', secret).update(`${time}.${payload}`).digest('hex')}`;

test('a webhook signature must match the body, the secret and the time', () => {
  const payload = '{"id":"evt_1"}';
  const now = Date.parse('2026-12-01T00:00:00Z');
  const time = Math.floor(now / 1000);
  assert.equal(verifyStripeSignature({ payload, header: sign(payload, 'whsec_test', time), secret: 'whsec_test', now }), true);
  assert.equal(verifyStripeSignature({ payload: `${payload} `, header: sign(payload, 'whsec_test', time), secret: 'whsec_test', now }), false);
  assert.equal(verifyStripeSignature({ payload, header: sign(payload, 'other', time), secret: 'whsec_test', now }), false);
  assert.equal(verifyStripeSignature({ payload, header: sign(payload, 'whsec_test', time - 3600), secret: 'whsec_test', now }), false);
  assert.equal(verifyStripeSignature({ payload, header: undefined, secret: 'whsec_test', now }), false);
});

test('Stripe events set and end the plan; a plan given by hand survives a Stripe cancellation', async () => {
  const now = Date.parse('2026-12-01T00:00:00Z');
  const { app, accounts } = await setup({ now: () => now });
  const { token, accountId } = await create(app);
  await accounts.saveSettings({ plus: { label: 'Plus', monthlyCents: 299, priceId: 'price_plus' }, pro: { label: 'Pro', monthlyCents: 599, priceId: 'price_pro' } });

  const send = (event) => {
    const payload = JSON.stringify(event);
    return app.inject({
      method: 'POST',
      url: '/v1/stripe/webhook',
      headers: { ...host, 'content-type': 'application/json', 'stripe-signature': sign(payload, 'whsec_test', Math.floor(now / 1000)) },
      payload,
    });
  };
  const periodEnd = Math.floor(Date.parse('2027-01-01T00:00:00Z') / 1000);
  const subscription = (type, status) => ({
    type,
    data: { object: { id: 'sub_1', customer: 'cus_1', status, current_period_end: periodEnd, metadata: { accountId }, items: { data: [{ price: { id: 'price_pro' } }] } } },
  });

  assert.equal((await send({ type: 'checkout.session.completed', data: { object: { client_reference_id: accountId, customer: 'cus_1', subscription: 'sub_1' } } })).statusCode, 200);
  await send(subscription('customer.subscription.created', 'active'));
  const active = (await call(app, 'GET', '/v1/account', { token })).json();
  assert.equal(active.plan, 'pro');
  assert.equal(active.planUntil, '2027-01-04T00:00:00.000Z');

  await send(subscription('customer.subscription.deleted', 'canceled'));
  assert.equal((await call(app, 'GET', '/v1/account', { token })).json().plan, 'free');

  await accounts.setPaid(accountId, { plan: 'plus', source: 'admin' });
  await send(subscription('customer.subscription.deleted', 'canceled'));
  assert.equal((await call(app, 'GET', '/v1/account', { token })).json().plan, 'plus');

  const forged = await app.inject({ method: 'POST', url: '/v1/stripe/webhook', headers: { ...host, 'content-type': 'application/json', 'stripe-signature': 't=1,v1=00' }, payload: '{}' });
  assert.equal(forged.statusCode, 400);
});

test('the admin can find an account and grant, end and re-offer plans', async () => {
  const { app, accounts } = await setup();
  const { token, accountId } = await create(app);
  const auth = { ...host, authorization: `Basic ${Buffer.from('admin:admin-secret-long-enough').toString('base64')}` };
  const form = { ...auth, 'content-type': 'application/x-www-form-urlencoded', origin: 'https://updates.example.com' };
  const post = (url, fields) => app.inject({ method: 'POST', url, headers: form, payload: new URLSearchParams(fields).toString() });

  const page = await app.inject({ method: 'GET', url: `/admin/accounts?id=${accountId.slice(0, 6)}`, headers: auth });
  assert.equal(page.statusCode, 200);
  assert.ok(page.body.includes(`Account ${accountId}`));
  assert.equal((await app.inject({ method: 'GET', url: '/admin/accounts', headers: host })).statusCode, 401);

  assert.match((await post('/admin/accounts/grant', { id: accountId, plan: 'pro', until: '2099-01-31' })).headers.location, /notice=granted/);
  assert.equal((await call(app, 'GET', '/v1/account', { token })).json().plan, 'pro');
  assert.match((await post('/admin/accounts/grant', { id: accountId, plan: 'gold', until: '' })).headers.location, /notice=bad_plan/);
  assert.match((await post('/admin/accounts/grant', { id: accountId, plan: 'plus', until: 'tomorrow' })).headers.location, /notice=bad_until/);

  await post('/admin/accounts/revoke', { id: accountId });
  assert.equal((await call(app, 'GET', '/v1/account', { token })).json().plan, 'free');

  await post('/admin/accounts/grandfather', { id: accountId, value: 'on' });
  assert.equal((await call(app, 'GET', '/v1/account', { token })).json().proTrial.offered, true);

  assert.match((await post('/admin/billing', { plusLabel: 'Plus', plusCents: '399', plusPrice: 'price_a', proLabel: 'Pro', proCents: '799', proPrice: 'price_b' })).headers.location, /notice=saved/);
  assert.equal((await accounts.settings()).pro.monthlyCents, 799);
  assert.match((await post('/admin/billing', { plusLabel: 'Plus', plusCents: 'free', plusPrice: '', proLabel: 'Pro', proCents: '1', proPrice: '' })).headers.location, /notice=bad_number/);
});

test('the plans page shows the prices and an https donate link, and nothing else is linked', async () => {
  const { app, accounts } = await setup();
  await accounts.saveSettings({ plus: { label: 'Plus', monthlyCents: 299, priceId: '' }, pro: { label: 'Pro', monthlyCents: 599, priceId: '' } });
  const en = await app.inject({ method: 'GET', url: '/subscribe', headers: { ...host, 'accept-language': 'en' } });
  assert.equal(en.statusCode, 200);
  assert.ok(en.body.includes('€2.99'));
  assert.ok(en.body.includes('https://ko-fi.com/blutuxyz'));
  const de = await app.inject({ method: 'GET', url: '/subscribe?done=1', headers: { ...host, 'accept-language': 'de' } });
  assert.ok(de.body.includes('5,99 €'));
  assert.ok(de.body.includes('Danke!'));
  assert.ok(!/update-server|stripe-signature/i.test(en.body));
});
