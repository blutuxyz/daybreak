import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { test } from 'node:test';
import { buildServer } from '../src/server.js';
import { RateLimiter, SupportLedger, extractCode, normalizeCode } from '../src/support.js';
import { testConfig } from './helpers.js';

const CODE = 'DB-K7M2-9QXP';

async function setup({ configOverrides, now } = {}) {
  const config = await testConfig({ kofiToken: 'secret-token', ...configOverrides });
  const support = new SupportLedger({ config });
  // The release routes are not used here.
  const { app } = buildServer({ config, releases: {}, apks: {}, support, now });
  return { app, config, support };
}

/** What Ko-fi posts: a form with one field, "data", holding JSON. */
function kofiRequest(payment, token = 'secret-token') {
  const data = JSON.stringify({
    verification_token: token,
    type: 'Donation',
    kofi_transaction_id: 'tx-1',
    amount: '3.00',
    currency: 'EUR',
    message: `Thanks! ${CODE}`,
    from_name: 'Somebody',
    email: 'somebody@example.com',
    ...payment,
  });
  return {
    method: 'POST',
    url: '/v1/kofi',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    payload: new URLSearchParams({ data }).toString(),
  };
}

const status = (app, code, headers = {}) =>
  app.inject({ method: 'GET', url: `/v1/support/status?code=${encodeURIComponent(code)}`, headers });

test('codes are found in a message however they were typed', () => {
  assert.equal(extractCode('here you go DB-K7M2-9QXP, enjoy'), CODE);
  assert.equal(extractCode('db k7m2 9qxp'), CODE);
  assert.equal(extractCode('DBK7M29QXP'), CODE);
  assert.equal(extractCode('no code here'), undefined);
  assert.equal(extractCode(undefined), undefined);
});

test('codes with the look-alike characters 0, 1, I, L, O are not valid', () => {
  assert.equal(normalizeCode('DB-K7M2-9QX0'), undefined);
  assert.equal(normalizeCode('DB-K7M2-9QXI'), undefined);
  assert.equal(normalizeCode('DB-K7M2-9QX'), undefined);
});

test('a payment with the code in its message unlocks that code', async () => {
  const { app } = await setup();
  assert.deepEqual((await status(app, CODE)).json(), { paid: false });

  const response = await app.inject(kofiRequest({}));

  assert.equal(response.statusCode, 200);
  assert.equal(response.json().unlocked, true);
  assert.deepEqual((await status(app, CODE)).json(), { paid: true });
  assert.deepEqual((await status(app, 'db-k7m2-9qxp')).json(), { paid: true });
});

test('a payment with the wrong token is refused and unlocks nothing', async () => {
  const { app } = await setup();

  const response = await app.inject(kofiRequest({}, 'not-the-token'));

  assert.equal(response.statusCode, 401);
  assert.deepEqual((await status(app, CODE)).json(), { paid: false });
});

test('the webhook does not exist when no token is configured', async () => {
  const { app } = await setup({ configOverrides: { kofiToken: undefined } });

  assert.equal((await app.inject(kofiRequest({}))).statusCode, 404);
});

test('a payment without a code, or below the minimum, unlocks nothing but is answered with 200', async () => {
  const { app } = await setup({ configOverrides: { supportMinAmount: 2 } });

  const none = await app.inject(kofiRequest({ message: 'Good luck with the integration!' }));
  const small = await app.inject(kofiRequest({ amount: '1.00', kofi_transaction_id: 'tx-2' }));

  assert.equal(none.statusCode, 200);
  assert.equal(none.json().reason, 'no_code');
  assert.equal(small.statusCode, 200);
  assert.equal(small.json().reason, 'too_small');
  assert.deepEqual((await status(app, CODE)).json(), { paid: false });
});

test('a malformed webhook body is a 400', async () => {
  const { app } = await setup();

  const response = await app.inject({
    method: 'POST',
    url: '/v1/kofi',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    payload: 'data=not-json',
  });

  assert.equal(response.statusCode, 400);
});

test('the same transaction delivered twice is recorded once', async () => {
  const { app, support } = await setup();

  await app.inject(kofiRequest({}));
  await app.inject(kofiRequest({}));

  assert.equal(Object.keys(await support.entries).length, 1);
});

test('the ledger keeps no name, e-mail address or message, and survives a restart', async () => {
  const { app, config } = await setup();
  await app.inject(kofiRequest({}));

  const text = await readFile(join(config.dataDir, 'support-payments.json'), 'utf8');
  assert.ok(!text.includes('Somebody'));
  assert.ok(!text.includes('example.com'));
  assert.ok(!text.includes('Thanks'));

  const reloaded = new SupportLedger({ config });
  assert.equal(await reloaded.isPaid(CODE), true);
});

test('a status request with a bad code is a 400', async () => {
  const { app } = await setup();

  assert.equal((await status(app, 'hello')).statusCode, 400);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/support/status' })).statusCode, 400);
});

test('the status endpoint slows down a caller that keeps asking', async () => {
  let time = 0;
  const { app } = await setup({ now: () => time });

  for (let i = 0; i < 30; i += 1) assert.equal((await status(app, CODE)).statusCode, 200);
  assert.equal((await status(app, CODE)).statusCode, 429);

  time += 61_000;
  assert.equal((await status(app, CODE)).statusCode, 200);
});

test('the rate limiter counts each caller separately', () => {
  const limiter = new RateLimiter({ limit: 1, windowMs: 1000, now: () => 0 });

  assert.equal(limiter.allow('a'), true);
  assert.equal(limiter.allow('a'), false);
  assert.equal(limiter.allow('b'), true);
});
