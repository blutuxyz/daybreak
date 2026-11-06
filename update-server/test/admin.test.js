import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildServer } from '../src/server.js';
import { testConfig } from './helpers.js';

const host = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };
const password = 'a-long-random-admin-secret';
const auth = { ...host, authorization: `Basic ${Buffer.from(`admin:${password}`).toString('base64')}` };
const form = { ...auth, 'content-type': 'application/x-www-form-urlencoded', origin: 'https://updates.example.com' };

async function setup(overrides = {}) {
  const config = await testConfig({ adminToken: password, ...overrides });
  const { app } = buildServer({ config });
  return app;
}

test('without ADMIN_TOKEN there is no admin page', async () => {
  const app = await setup({ adminToken: undefined });

  assert.equal((await app.inject({ method: 'GET', url: '/admin', headers: auth })).statusCode, 404);
  assert.equal((await app.inject({ method: 'POST', url: '/admin/min-version', headers: form, payload: 'version=1.2.0' })).statusCode, 404);
});

test('the admin page asks for the password and refuses a wrong one', async () => {
  const app = await setup();

  const anonymous = await app.inject({ method: 'GET', url: '/admin', headers: host });
  assert.equal(anonymous.statusCode, 401);
  assert.match(anonymous.headers['www-authenticate'], /^Basic /);

  const wrong = await app.inject({
    method: 'GET',
    url: '/admin',
    headers: { ...host, authorization: `Basic ${Buffer.from('admin:guess').toString('base64')}` },
  });
  assert.equal(wrong.statusCode, 401);
});

test('signed in, the page shows the forms with a strict content security policy', async () => {
  const app = await setup();

  const response = await app.inject({ method: 'GET', url: '/admin', headers: auth });

  assert.equal(response.statusCode, 200);
  assert.match(response.headers['content-type'], /text\/html/);
  assert.match(response.headers['content-security-policy'], /default-src 'none'/);
  assert.match(response.body, /action="\/admin\/min-version"/);
  assert.match(response.body, /action="\/admin\/messages"/);
  assert.doesNotMatch(response.body, /<script/i);
});

test('the minimum version and messages change what the app is told', async () => {
  const app = await setup();

  const before = (await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json();
  assert.deepEqual(before, {
    minVersionName: null,
    minVersionCode: null,
    switches: { updates: true, requiredVersion: true, messages: true },
    disabledFeatures: [],
    messages: [],
  });

  const saved = await app.inject({ method: 'POST', url: '/admin/min-version', headers: form, payload: 'version=1.4.0' });
  assert.equal(saved.statusCode, 303);
  assert.equal(saved.headers.location, '/admin?notice=saved');

  await app.inject({
    method: 'POST',
    url: '/admin/messages',
    headers: form,
    payload: 'level=critical&de=WebUntis+ist+gerade+nicht+erreichbar&en=&until=2999-01-01T00%3A00%3A00Z',
  });
  const after = (await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json();
  assert.equal(after.minVersionName, '1.4.0');
  assert.equal(after.minVersionCode, 10400);
  assert.equal(after.messages.length, 1);
  assert.equal(after.messages[0].de, 'WebUntis ist gerade nicht erreichbar');
  assert.equal(after.messages[0].en, '');
  assert.equal(after.messages[0].level, 'critical');

  const id = after.messages[0].id;
  await app.inject({ method: 'POST', url: `/admin/messages/${id}/toggle`, headers: form });
  assert.equal((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().messages.length, 0);

  await app.inject({ method: 'POST', url: `/admin/messages/${id}/toggle`, headers: form });
  await app.inject({ method: 'POST', url: `/admin/messages/${id}/delete`, headers: form });
  assert.equal((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().messages.length, 0);
});

test('a refused input reports why and changes nothing', async () => {
  const app = await setup();

  const badVersion = await app.inject({ method: 'POST', url: '/admin/min-version', headers: form, payload: 'version=soon' });
  assert.equal(badVersion.headers.location, '/admin?notice=bad_version');

  const badMessage = await app.inject({
    method: 'POST',
    url: '/admin/messages',
    headers: form,
    payload: 'level=info&de=&en=',
  });
  assert.equal(badMessage.headers.location, '/admin?notice=bad_text');

  const missing = await app.inject({ method: 'POST', url: '/admin/messages/ffffffffffff/delete', headers: form });
  assert.equal(missing.headers.location, '/admin?notice=not_found');

  assert.deepEqual((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json(), {
    minVersionName: null,
    minVersionCode: null,
    switches: { updates: true, requiredVersion: true, messages: true },
    disabledFeatures: [],
    messages: [],
  });
});

test('a form posted from another site is refused even with the saved password', async () => {
  const app = await setup();

  const response = await app.inject({
    method: 'POST',
    url: '/admin/min-version',
    headers: { ...form, origin: 'https://evil.example' },
    payload: 'version=9.9.9',
  });

  assert.equal(response.statusCode, 403);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().minVersionName, null);
});

test('an ended message is not sent to the app', async () => {
  const app = await setup();
  await app.inject({
    method: 'POST',
    url: '/admin/messages',
    headers: form,
    payload: 'level=info&de=Alt&en=&until=2020-01-01T00%3A00%3A00Z',
  });

  assert.deepEqual((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().messages, []);
});

test('the switches are set from the admin form and reach the app', async () => {
  const app = await setup();

  const saved = await app.inject({
    method: 'POST',
    url: '/admin/switches',
    headers: form,
    payload: 'updates=on&feature%3Areminders=on&feature%3Amessages.send=on',
  });
  assert.equal(saved.headers.location, '/admin?notice=saved');

  const status = (await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json();
  assert.deepEqual(status.switches, { updates: true, requiredVersion: false, messages: false });
  assert.deepEqual(status.disabledFeatures, ['messages.send', 'reminders']);
});

test('the admin page shows the switches as checkboxes', async () => {
  const app = await setup();
  await app.inject({ method: 'POST', url: '/admin/switches', headers: form, payload: 'requiredVersion=on' });

  const body = (await app.inject({ method: 'GET', url: '/admin', headers: auth })).body;

  assert.match(body, /name="requiredVersion" checked/);
  assert.match(body, /name="updates">/);
  assert.match(body, /name="feature:reminders"/);
});
