import assert from 'node:assert/strict';
import { access } from 'node:fs/promises';
import { test } from 'node:test';
import { ApkCache } from '../src/apk-cache.js';
import { ReleaseService } from '../src/releases.js';
import { buildServer, parseRange } from '../src/server.js';
import { apkBytes, apkSha256, fakeFetch, githubRelease, jsonResponse, testConfig } from './helpers.js';

async function setup({ releasesBody, configOverrides, apkResponse } = {}) {
  const config = await testConfig(configOverrides);
  const fetchImpl = fakeFetch({
    'https://api.github.com/repos/': () =>
      jsonResponse(releasesBody ?? [githubRelease('v1.1.0', { body: '- New: updates' }), githubRelease('v1.0.0')]),
    'https://github.com/': () => apkResponse?.() ?? new Response(apkBytes),
  });
  const releases = new ReleaseService({ config, fetchImpl });
  const apks = new ApkCache({ config, fetchImpl });
  const server = buildServer({ config, releases, apks });
  return { ...server, config, fetchImpl };
}

const headers = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };

test('/v1/latest describes the newest release with a link, size and checksum', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });
  const body = response.json();

  assert.equal(response.statusCode, 200);
  assert.equal(body.tag, 'v1.1.0');
  assert.equal(body.versionName, '1.1.0');
  assert.equal(body.versionCode, 10100);
  assert.equal(body.changelog, '- New: updates');
  assert.deepEqual(body.apk, {
    url: 'https://updates.example.com/v1/apk/v1.1.0',
    size: apkBytes.length,
    sha256: apkSha256,
  });
});

test('the public URL setting decides the APK link', async () => {
  const { app } = await setup({ configOverrides: { publicUrl: 'https://downloads.example.org' } });

  const body = (await app.inject({ method: 'GET', url: '/v1/latest' })).json();

  assert.equal(body.apk.url, 'https://downloads.example.org/v1/apk/v1.1.0');
});

test('/v1/latest is a 404 when there is no release with an APK', async () => {
  const { app } = await setup({ releasesBody: [githubRelease('v1.0.0', { assets: [] })] });

  const response = await app.inject({ method: 'GET', url: '/v1/latest' });

  assert.equal(response.statusCode, 404);
  assert.equal(response.json().error, 'no_release');
});

test('/v1/latest is a 502 when GitHub cannot be reached and nothing is cached', async () => {
  const config = await testConfig();
  const fetchImpl = fakeFetch({ 'https://api.github.com/': () => new Response('x', { status: 503 }) });
  const { app } = buildServer({
    config,
    releases: new ReleaseService({ config, fetchImpl }),
    apks: new ApkCache({ config, fetchImpl }),
  });

  const response = await app.inject({ method: 'GET', url: '/v1/latest' });

  assert.equal(response.statusCode, 502);
});

test('the APK is served with the right headers, and downloaded from GitHub only once', async () => {
  const { app, fetchImpl } = await setup();

  const first = await app.inject({ method: 'GET', url: '/v1/apk/v1.1.0' });
  const second = await app.inject({ method: 'GET', url: '/v1/apk/v1.1.0' });

  assert.equal(first.statusCode, 200);
  assert.deepEqual(first.rawPayload, apkBytes);
  assert.equal(first.headers['content-type'], 'application/vnd.android.package-archive');
  assert.equal(first.headers['content-length'], String(apkBytes.length));
  assert.equal(first.headers.etag, `"${apkSha256}"`);
  assert.deepEqual(second.rawPayload, apkBytes);
  assert.equal(fetchImpl.calls.filter((call) => call.url.startsWith('https://github.com/')).length, 1);
});

test('a download can be resumed with a Range request', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/apk/v1.1.0', headers: { range: 'bytes=10-19' } });

  assert.equal(response.statusCode, 206);
  assert.deepEqual(response.rawPayload, apkBytes.subarray(10, 20));
  assert.equal(response.headers['content-range'], `bytes 10-19/${apkBytes.length}`);
});

test('an impossible Range is a 416', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/apk/v1.1.0', headers: { range: 'bytes=99999-' } });

  assert.equal(response.statusCode, 416);
});

test('unknown and unsafe tags are a 404', async () => {
  const { app } = await setup();

  assert.equal((await app.inject({ method: 'GET', url: '/v1/apk/v9.9.9' })).statusCode, 404);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/apk/..%2Fsecret' })).statusCode, 404);
});

test('a download that does not match the size is refused and leaves no file behind', async () => {
  const { app, config } = await setup({ apkResponse: () => new Response(apkBytes.subarray(0, 10)) });

  const response = await app.inject({ method: 'GET', url: '/v1/apk/v1.1.0' });

  assert.equal(response.statusCode, 502);
  await assert.rejects(() => access(`${config.dataDir}/apks/v1.1.0.apk`));
  await assert.rejects(() => access(`${config.dataDir}/apks/v1.1.0.apk.part`));
});

test('/v1/releases lists the history with changelogs', async () => {
  const { app } = await setup();

  const body = (await app.inject({ method: 'GET', url: '/v1/releases?limit=1', headers })).json();

  assert.equal(body.releases.length, 1);
  assert.equal(body.releases[0].tag, 'v1.1.0');
  assert.equal(body.releases[0].changelog, '- New: updates');
});

test('/v1/refresh needs the token, and is off without one', async () => {
  const withToken = await setup({ configOverrides: { refreshToken: 'secret' } });

  assert.equal((await withToken.app.inject({ method: 'POST', url: '/v1/refresh' })).statusCode, 401);
  const ok = await withToken.app.inject({
    method: 'POST',
    url: '/v1/refresh',
    headers: { authorization: 'Bearer secret' },
  });
  assert.equal(ok.statusCode, 200);
  assert.equal(ok.json().latest, 'v1.1.0');

  const without = await setup();
  assert.equal((await without.app.inject({ method: 'POST', url: '/v1/refresh' })).statusCode, 404);
});

test('parseRange reads the forms an HTTP client sends', () => {
  assert.equal(parseRange(undefined, 100), undefined);
  assert.deepEqual(parseRange('bytes=0-9', 100), { start: 0, end: 9 });
  assert.deepEqual(parseRange('bytes=90-', 100), { start: 90, end: 99 });
  assert.deepEqual(parseRange('bytes=-10', 100), { start: 90, end: 99 });
  assert.deepEqual(parseRange('bytes=5-500', 100), { start: 5, end: 99 });
  assert.equal(parseRange('bytes=50-10', 100), 'invalid');
  assert.equal(parseRange('bytes=200-', 100), 'invalid');
  assert.equal(parseRange('items=1-2', 100), 'invalid');
});
