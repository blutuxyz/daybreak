import assert from 'node:assert/strict';
import { test } from 'node:test';
import { emptyState, normalizeMessage, publicStatus } from '../src/announcements.js';
import { MessageImageStore, parseSingleFile, sniffImage } from '../src/message-images.js';
import { buildServer } from '../src/server.js';
import { testConfig } from './helpers.js';

const password = 'a-long-random-admin-secret';
const host = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };
const auth = { ...host, authorization: `Basic ${Buffer.from(`admin:${password}`).toString('base64')}` };
const origin = 'https://updates.example.com';
const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(64, 1)]);

async function setup() {
  const config = await testConfig({ adminToken: password });
  const { app } = buildServer({ config });
  return { app, config };
}

const form = (fields) =>
  new URLSearchParams(
    Object.entries(fields).flatMap(([key, value]) => (Array.isArray(value) ? value.map((item) => [key, item]) : [[key, value]])),
  ).toString();

const post = (app, url, fields) =>
  app.inject({ method: 'POST', url, headers: { ...auth, 'content-type': 'application/x-www-form-urlencoded', origin }, payload: form(fields) });

const notice = (response) => new URL(response.headers.location, origin).searchParams.get('notice');

function multipart(name, data) {
  const boundary = '----testboundary123';
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${name}"\r\nContent-Type: image/png\r\n\r\n`),
    data,
    Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  return { body, contentType: `multipart/form-data; boundary=${boundary}` };
}

const upload = (app, name, data) => {
  const { body, contentType } = multipart(name, data);
  return app.inject({ method: 'POST', url: '/admin/images', headers: { ...auth, 'content-type': contentType, origin }, payload: body });
};

test('a plain message stays plain and a sheet keeps its title, picture, look, audience and buttons', () => {
  const plain = normalizeMessage({ level: 'info', de: 'Hallo' });
  assert.equal(plain.message.sheet, undefined);

  const { message, error } = normalizeMessage({
    level: 'info',
    titleEn: 'Now on the Play Store',
    imageKind: 'preset',
    imagePreset: 'playstore',
    style: 'brand',
    platform: 'android',
    installSource: 'sideloaded',
    dismissibleGiven: '1',
    dismissible: 'on',
    buttonDe: ['Laden', 'Weiter', ''],
    buttonEn: ['Download', 'Continue', ''],
    buttonAction: ['url', 'dismiss', 'dismiss'],
    buttonUrl: ['https://play.google.com/store/apps/details?id=x', '', ''],
    buttonStyle: ['filled', 'text', 'tonal'],
    buttonCloses: ['yes', 'yes', 'yes'],
    buttonInfoEn: ['Uninstall first if it fails', 'Auto updates still work', ''],
  });
  assert.equal(error, undefined);
  assert.equal(message.sheet.buttons.length, 2);
  assert.equal(message.sheet.buttons[0].action, 'url');
  assert.equal(message.sheet.buttons[1].infoEn, 'Auto updates still work');
  assert.deepEqual(message.sheet.image, { kind: 'preset', value: 'playstore', shape: 'icon' });
  assert.equal(message.sheet.platform, 'android');
  // The title alone is enough text.
  assert.equal(message.de, '');
});

test('a sheet is refused for a bad link, a missing text, an unknown picture or no way to close it', () => {
  assert.equal(
    normalizeMessage({ level: 'info', titleEn: 'x', buttonEn: ['Go'], buttonAction: ['url'], buttonUrl: ['http://insecure.example'] }).error,
    'bad_url',
  );
  assert.equal(normalizeMessage({ level: 'info', buttonEn: ['Go'] }).error, 'bad_text');
  assert.equal(
    normalizeMessage({ level: 'info', titleEn: 'x', imageKind: 'upload', imageUpload: 'aaaaaaaaaaaa' }, { imageExists: () => false }).error,
    'bad_image',
  );
  assert.equal(normalizeMessage({ level: 'info', titleEn: 'x', imageKind: 'preset', imagePreset: 'nope' }).error, 'bad_image');
  assert.equal(normalizeMessage({ level: 'info', titleEn: 'x', dismissibleGiven: '1' }).error, 'needs_button');
  assert.equal(normalizeMessage({ level: 'info', titleEn: 'x', buttonEn: ['a', 'b', 'c', 'd'] }).error, 'too_many_buttons');
});

test('the app gets the sheet of a message', () => {
  const state = emptyState();
  const { message } = normalizeMessage({ level: 'info', titleEn: 'Hello', buttonEn: ['OK'] });
  state.messages.push(message);
  const status = publicStatus(state);
  assert.equal(status.messages[0].sheet.titleEn, 'Hello');
  assert.equal(status.messages[0].sheet.buttons[0].en, 'OK');
});

test('pictures are recognised by their first bytes, and a multipart body gives its file', () => {
  assert.equal(sniffImage(png), 'image/png');
  assert.equal(sniffImage(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"></svg>')), undefined);
  assert.equal(sniffImage(Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WEBPVP8 ')])), 'image/webp');
  const { body, contentType } = multipart('logo.png', png);
  const file = parseSingleFile(body, contentType);
  assert.equal(file.name, 'logo.png');
  assert.ok(file.data.equals(png));
  assert.equal(parseSingleFile(Buffer.from('nothing'), contentType), undefined);
});

test('an uploaded picture is stored, listed, served with safe headers and removable', async () => {
  const { app } = await setup();
  assert.equal(notice(await upload(app, 'logo.png', png)), 'image_saved');
  const page = (await app.inject({ method: 'GET', url: '/admin', headers: auth })).body;
  const id = /\/admin\/message-image\/([0-9a-f]{12})/.exec(page)[1];

  const served = await app.inject({ method: 'GET', url: `/v1/message-image/${id}`, headers: host });
  assert.equal(served.statusCode, 200);
  assert.equal(served.headers['content-type'], 'image/png');
  assert.equal(served.headers['x-content-type-options'], 'nosniff');
  assert.ok(Buffer.from(served.rawPayload).equals(png));
  assert.equal((await app.inject({ method: 'GET', url: '/v1/message-image/ffffffffffff', headers: host })).statusCode, 404);

  assert.equal(notice(await post(app, `/admin/images/${id}/delete`, {})), 'image_removed');
  assert.equal((await app.inject({ method: 'GET', url: `/v1/message-image/${id}`, headers: host })).statusCode, 404);
});

test('an upload that is not a PNG, JPEG or WebP picture is refused, and so is a too large one', async () => {
  const { app } = await setup();
  const script = Buffer.from('<svg onload="alert(1)"></svg>'.padEnd(64, ' '));
  assert.equal(notice(await upload(app, 'evil.png', script)), 'bad_type');
  assert.equal(notice(await upload(app, 'big.png', Buffer.concat([png, Buffer.alloc(2 * 1024 * 1024)]))), 'too_large');
  const anonymous = await app.inject({
    method: 'POST',
    url: '/admin/images',
    headers: { ...host, 'content-type': 'multipart/form-data; boundary=x' },
    payload: 'x',
  });
  assert.equal(anonymous.statusCode, 401);
});

const playFields = {
  dismissibleGiven: '1',
  dismissible: 'on',
  level: 'info',
  style: 'brand',
  platform: 'android',
  installSource: 'sideloaded',
  titleDe: 'Daybreak gibt es im Play Store',
  titleEn: 'Daybreak is on the Play Store',
  de: '',
  en: '',
  imageKind: 'preset',
  imagePreset: 'playstore',
  imageShape: 'icon',
  buttonDe: ['Laden', '', ''],
  buttonEn: ['Download', '', ''],
  buttonAction: ['url', 'dismiss', 'dismiss'],
  buttonUrl: ['https://play.google.com/store/apps/details?id=x', '', ''],
  buttonStyle: ['filled', 'tonal', 'tonal'],
  buttonCloses: ['yes', 'yes', 'yes'],
  buttonInfoDe: ['', '', ''],
  buttonInfoEn: ['Uninstall first if it fails', '', ''],
};

test('the preview shows both languages and saves only from the preview page', async () => {
  const { app } = await setup();
  const preview = await post(app, '/admin/messages/preview', playFields);
  assert.equal(preview.statusCode, 200);
  assert.ok(preview.body.includes('Daybreak gibt es im Play Store'));
  assert.ok(preview.body.includes('Daybreak is on the Play Store'));
  assert.ok(preview.body.includes('Uninstall first if it fails'));
  assert.ok(preview.body.includes('<svg'));
  assert.ok(preview.body.includes('action="/admin/messages"'));

  // Nothing was published by looking at it.
  assert.equal((await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().messages.length, 0);
  assert.equal(notice(await post(app, '/admin/messages', playFields)), 'saved');
  const status = (await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json();
  assert.equal(status.messages[0].sheet.buttons[0].url, 'https://play.google.com/store/apps/details?id=x');

  const bad = await post(app, '/admin/messages/preview', { ...playFields, buttonUrl: ['ftp://nope', '', ''] });
  assert.ok(bad.body.includes('https://'));
  assert.ok(!bad.body.includes('action="/admin/messages"'));
});

test('text in a message is escaped in the preview', async () => {
  const { app } = await setup();
  const response = await post(app, '/admin/messages/preview', {
    level: 'info',
    dismissibleGiven: '1',
    dismissible: 'on',
    titleEn: '<img src=x onerror=alert(1)>',
    en: '<b>hi</b>',
  });
  assert.ok(!response.body.includes('<img src=x'));
  assert.ok(response.body.includes('&lt;img src=x'));
});

test('one click adds the Play Store announcement, for Android installs from an APK', async () => {
  const { app } = await setup();
  assert.equal(notice(await post(app, '/admin/messages/demo-playstore', {})), 'demo_added');
  const sheet = (await app.inject({ method: 'GET', url: '/v1/status', headers: host })).json().messages[0].sheet;
  assert.equal(sheet.platform, 'android');
  assert.equal(sheet.installSource, 'sideloaded');
  assert.equal(sheet.image.value, 'playstore');
  assert.equal(sheet.buttons[0].action, 'url');
  assert.ok(sheet.buttons[0].url.startsWith('https://play.google.com/'));
  assert.match(sheet.buttons[0].infoEn, /uninstall the current Daybreak version first/i);
  assert.equal(sheet.buttons[1].en, 'Continue using the APK');
  assert.equal(sheet.buttons[1].infoEn, 'Auto updates still work as usual.');
});

test('the image store keeps at most thirty pictures', async () => {
  const config = await testConfig();
  const store = new MessageImageStore({ config });
  for (let index = 0; index < 30; index += 1) assert.ok((await store.add({ name: `p${index}`, data: png })).image);
  assert.equal((await store.add({ name: 'one more', data: png })).error, 'too_many');
});
