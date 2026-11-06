import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { SupportLedger } from '../src/support.js';
import { buildServer } from '../src/server.js';
import { testConfig } from './helpers.js';

const password = 'a-long-random-admin-secret';
const host = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };
const auth = { ...host, authorization: `Basic ${Buffer.from(`admin:${password}`).toString('base64')}` };
const form = { ...auth, 'content-type': 'application/x-www-form-urlencoded', origin: 'https://updates.example.com' };
const code = 'DB-K7M2-9QXP';

async function setup() {
  const config = await testConfig({ adminToken: password });
  const support = new SupportLedger({ config });
  const { app } = buildServer({ config, support });
  return { app, config };
}

const post = (app, url, body) => app.inject({ method: 'POST', url, headers: form, payload: `code=${encodeURIComponent(body)}` });

const notice = (response) => new URL(response.headers.location, 'https://updates.example.com').searchParams.get('notice');

test('the admin can check a code the app shows: unpaid until granted, then paid', async () => {
  const { app } = await setup();

  assert.equal(notice(await post(app, '/admin/support/check', code)), 'code_unpaid');
  assert.equal(notice(await post(app, '/admin/support/grant', code)), 'granted');
  assert.equal(notice(await post(app, '/admin/support/check', code)), 'code_paid');
  assert.deepEqual(
    (await app.inject({ method: 'GET', url: `/v1/support/status?code=${code}`, headers: host })).json(),
    { paid: true },
  );
});

test('a granted payment is recorded as made by hand, not as a Ko-fi payment', async () => {
  const { app, config } = await setup();

  await post(app, '/admin/support/grant', code);

  const ledger = JSON.parse(await readFile(`${config.dataDir}/support-payments.json`, 'utf8'));
  assert.equal(ledger.payments[code].source, 'admin');
  assert.equal(ledger.payments[code].currency, 'manual');
});

test('granting the same code twice says it is already paid and records nothing new', async () => {
  const { app } = await setup();

  assert.equal(notice(await post(app, '/admin/support/grant', code)), 'granted');
  assert.equal(notice(await post(app, '/admin/support/grant', code)), 'already_paid');
});

test('the code is accepted the way the app writes it, with other case and spacing', async () => {
  const { app } = await setup();

  assert.equal(notice(await post(app, '/admin/support/grant', 'db-k7m2-9qxp')), 'granted');
  assert.equal(notice(await post(app, '/admin/support/check', ' DB K7M2 9QXP ')), 'code_paid');
});

test('something that is not an unlock code is refused and changes nothing', async () => {
  const { app } = await setup();

  assert.equal(notice(await post(app, '/admin/support/grant', 'hello')), 'bad_code');
  assert.equal(notice(await post(app, '/admin/support/grant', 'DB-0000-0000')), 'bad_code');
  assert.equal(
    (await app.inject({ method: 'GET', url: `/v1/support/status?code=${code}`, headers: host })).json().paid,
    false,
  );
});

test('the admin page shows the outcome of the last check or grant, and has the code form', async () => {
  const { app } = await setup();

  const page = (await app.inject({ method: 'GET', url: '/admin?notice=granted', headers: auth })).body;

  assert.ok(page.includes('Payment granted for that code.'));
  assert.ok(page.includes('name="code"'));
  assert.ok(page.includes('formaction="/admin/support/grant"'));
  assert.ok(page.includes('action="/admin/support/check"'));
});

test('granting needs the admin password like every other admin action', async () => {
  const { app } = await setup();

  const response = await app.inject({
    method: 'POST',
    url: '/admin/support/grant',
    headers: { ...host, 'content-type': 'application/x-www-form-urlencoded' },
    payload: `code=${code}`,
  });

  assert.equal(response.statusCode, 401);
  assert.equal(
    (await app.inject({ method: 'GET', url: `/v1/support/status?code=${code}`, headers: host })).json().paid,
    false,
  );
});
