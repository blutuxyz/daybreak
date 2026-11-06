import assert from 'node:assert/strict';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { AnnouncementStore, normalizeMessage, parseMinVersion, publicStatus } from '../src/announcements.js';
import { testConfig } from './helpers.js';

const NOW = Date.parse('2026-11-06T12:00:00Z');

test('the minimum version is read like a release tag and becomes a version code', () => {
  assert.deepEqual(parseMinVersion(''), { minVersion: null });
  assert.deepEqual(parseMinVersion('  '), { minVersion: null });
  assert.deepEqual(parseMinVersion('1.2.0'), { minVersion: { name: '1.2.0', code: 10200 } });
  assert.deepEqual(parseMinVersion('v2.0.1'), { minVersion: { name: '2.0.1', code: 20001 } });
  assert.deepEqual(parseMinVersion('1.2'), { error: 'bad_version' });
  assert.deepEqual(parseMinVersion('1.100.0'), { error: 'bad_version' });
  assert.deepEqual(parseMinVersion('latest'), { error: 'bad_version' });
});

test('a message needs a known level and text in at least one language', () => {
  const id = '0123456789ab';
  assert.equal(normalizeMessage({ level: 'loud', de: 'x' }, { id }).error, 'bad_level');
  assert.equal(normalizeMessage({ level: 'info', de: '  ', en: '' }, { id }).error, 'bad_text');
  assert.equal(normalizeMessage({ level: 'info', de: 'x'.repeat(501) }, { id }).error, 'bad_text');

  const { message } = normalizeMessage({ level: 'warning', en: 'WebUntis is down' }, { id, now: NOW });
  assert.deepEqual(message, {
    id,
    level: 'warning',
    de: '',
    en: 'WebUntis is down',
    enabled: true,
    until: null,
    createdAt: '2026-11-06T12:00:00.000Z',
  });
});

test('the end time must be a date, and is kept in UTC', () => {
  const id = '0123456789ab';
  assert.equal(normalizeMessage({ level: 'info', de: 'x', until: 'morgen' }, { id }).error, 'bad_until');
  const { message } = normalizeMessage({ level: 'info', de: 'x', until: '2026-11-07T18:00:00+01:00' }, { id });
  assert.equal(message.until, '2026-11-07T17:00:00.000Z');
});

test('the app only gets messages that are switched on and not over yet', () => {
  const base = { id: '000000000001', level: 'info', de: 'a', en: '', enabled: true, createdAt: 'x' };
  const state = {
    minVersion: { name: '1.2.0', code: 10200 },
    messages: [
      { ...base, id: '000000000001' },
      { ...base, id: '000000000002', enabled: false },
      { ...base, id: '000000000003', until: '2026-11-06T11:00:00.000Z' },
      { ...base, id: '000000000004', until: '2026-11-07T11:00:00.000Z' },
    ],
  };

  const status = publicStatus(state, NOW);

  assert.equal(status.minVersionName, '1.2.0');
  assert.equal(status.minVersionCode, 10200);
  assert.deepEqual(status.messages.map((message) => message.id), ['000000000001', '000000000004']);
  assert.equal(status.messages[0].createdAt, undefined);
});

test('without a minimum version the app is told there is none', () => {
  assert.deepEqual(publicStatus({ minVersion: null, messages: [] }, NOW), {
    minVersionName: null,
    minVersionCode: null,
    switches: { updates: true, requiredVersion: true, messages: true },
    disabledFeatures: [],
    messages: [],
  });
});

test('the store keeps its state between runs and writes nothing it refuses', async () => {
  const config = await testConfig();
  const store = new AnnouncementStore({ config });

  assert.deepEqual(await store.status(NOW), {
    minVersionName: null,
    minVersionCode: null,
    switches: { updates: true, requiredVersion: true, messages: true },
    disabledFeatures: [],
    messages: [],
  });
  assert.deepEqual(await store.setMinVersion('bogus'), { error: 'bad_version' });
  assert.equal((await store.status(NOW)).minVersionCode, null);

  assert.deepEqual(await store.setMinVersion('1.3.0'), { saved: true });
  assert.deepEqual(await store.addMessage({ level: 'critical', de: 'WebUntis ist gerade nicht erreichbar.' }), {
    saved: true,
  });
  const [message] = (await store.snapshot()).messages;

  // A new store on the same folder sees the same state.
  const reopened = new AnnouncementStore({ config });
  assert.equal((await reopened.status(NOW)).minVersionCode, 10300);
  assert.equal((await reopened.status(NOW)).messages.length, 1);

  assert.deepEqual(await reopened.toggleMessage(message.id), { saved: true });
  assert.equal((await reopened.status(NOW)).messages.length, 0);
  assert.deepEqual(await reopened.toggleMessage(message.id), { saved: true });
  assert.deepEqual(await reopened.toggleMessage('ffffffffffff'), { error: 'not_found' });
  assert.deepEqual(await reopened.toggleMessage('../../etc'), { error: 'not_found' });

  assert.deepEqual(await reopened.removeMessage(message.id), { saved: true });
  assert.deepEqual(await reopened.removeMessage(message.id), { error: 'not_found' });
});

test('a file that exists but cannot be read is an error, not an empty state', async () => {
  const dataDir = await mkdtemp(join(tmpdir(), 'daybreak-announcements-'));
  await writeFile(join(dataDir, 'announcements.json'), '{ not json', 'utf8');
  const store = new AnnouncementStore({ config: { dataDir } });

  await assert.rejects(store.status(NOW));
});

test('switches default to everything on, and unknown feature ids are dropped', async () => {
  const { readSwitches, defaultSwitches } = await import('../src/announcements.js');
  assert.deepEqual(readSwitches(undefined), defaultSwitches());
  assert.deepEqual(readSwitches({ updates: false, disabledFeatures: ['reminders', 'made.up'] }), {
    updates: false,
    requiredVersion: true,
    messages: true,
    disabledFeatures: ['reminders'],
  });
});

test('the app is told the switches and the disabled features', async () => {
  const config = await testConfig();
  const store = new AnnouncementStore({ config });

  await store.setSwitches({ requiredVersion: 'on', 'feature:reminders': 'on' });

  assert.deepEqual(await store.status(NOW), {
    minVersionName: null,
    minVersionCode: null,
    switches: { updates: false, requiredVersion: true, messages: false },
    disabledFeatures: ['reminders'],
    messages: [],
  });
  assert.deepEqual((await new AnnouncementStore({ config }).status(NOW)).disabledFeatures, ['reminders']);
});
