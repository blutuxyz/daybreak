import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { access } from 'node:fs/promises';
import { test } from 'node:test';
import { ReleaseFileCache } from '../src/release-files.js';
import { ReleaseService, mapReleases } from '../src/releases.js';
import { buildServer } from '../src/server.js';
import { apkBytes, apkSha256, fakeFetch, githubRelease, jsonResponse, setupBytes, setupSha256, releaseWithSetup, testConfig } from './helpers.js';

async function setup({ releasesBody, setupResponse } = {}) {
  const config = await testConfig();
  const fetchImpl = fakeFetch({
    'https://api.github.com/repos/': () =>
      jsonResponse(releasesBody ?? [releaseWithSetup('v1.1.0'), githubRelease('v1.0.0')]),
    'https://github.com/': (url) =>
      url.endsWith('.exe') ? (setupResponse?.() ?? new Response(setupBytes)) : new Response(apkBytes),
  });
  const releases = new ReleaseService({ config, fetchImpl });
  const apks = new ReleaseFileCache({ config, fetchImpl, kind: 'apk' });
  const setups = new ReleaseFileCache({ config, fetchImpl, kind: 'setup' });
  const server = buildServer({ config, releases, apks, setups });
  return { ...server, config };
}

const headers = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };

test('the Windows installer is found by its name, next to the APK', async () => {
  const config = await testConfig();
  const [withInstaller, apkOnly] = mapReleases([releaseWithSetup('v1.1.0'), githubRelease('v1.0.0')], config);

  assert.equal(withInstaller.setup.name, 'Daybreak-1.1.0-setup.exe');
  assert.equal(withInstaller.apk, true);
  assert.equal(apkOnly.setup, undefined);

  const otherInstaller = githubRelease('v1.2.0', {
    assets: [{ name: 'Setup-1.2.0.exe', state: 'uploaded', size: 1 }],
  });
  assert.equal(mapReleases([otherInstaller], config)[0].setup, undefined);
});

test('/v1/latest offers the Windows installer with its checksum, and the APK is unchanged', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });
  const body = response.json();

  assert.equal(response.statusCode, 200);
  assert.deepEqual(body.windows, {
    installer: {
      url: 'https://updates.example.com/v1/setup/v1.1.0',
      name: 'Daybreak-1.1.0-setup.exe',
      size: setupBytes.length,
      sha256: setupSha256,
    },
  });
  assert.equal(body.apk.sha256, apkSha256);
});

test('/v1/setup/:tag serves the installer byte for byte, with its checksum and ranges', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/setup/v1.1.0', headers });
  assert.equal(response.statusCode, 200);
  assert.equal(response.headers['content-type'], 'application/vnd.microsoft.portable-executable');
  assert.equal(response.headers.etag, `"${setupSha256}"`);
  assert.deepEqual(response.rawPayload, setupBytes);

  const partial = await app.inject({ method: 'GET', url: '/v1/setup/v1.1.0', headers: { range: 'bytes=0-9' } });
  assert.equal(partial.statusCode, 206);
  assert.deepEqual(partial.rawPayload, setupBytes.subarray(0, 10));
});

test('a release without an installer has no Windows block and no installer to download', async () => {
  const { app } = await setup({ releasesBody: [githubRelease('v1.1.0'), githubRelease('v1.0.0')] });

  const body = (await app.inject({ method: 'GET', url: '/v1/latest', headers })).json();

  assert.equal(body.windows, null);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/setup/v1.1.0' })).statusCode, 404);
});

test('a failing installer download leaves the APK answer intact and no file behind', async () => {
  const { app, config } = await setup({ setupResponse: () => new Response('down', { status: 503 }) });

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });

  assert.equal(response.statusCode, 200);
  assert.equal(response.json().windows, null);
  assert.equal(response.json().apk.sha256, apkSha256);
  await assert.rejects(() => access(`${config.dataDir}/setups/v1.1.0.exe`));
  await assert.rejects(() => access(`${config.dataDir}/setups/v1.1.0.exe.part`));
});

test('an installer that does not match its published checksum is refused', async () => {
  const { app } = await setup({
    releasesBody: [releaseWithSetup('v1.1.0', { setupDigest: `sha256:${'0'.repeat(64)}` }), githubRelease('v1.0.0')],
  });

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });

  assert.equal(response.statusCode, 200);
  assert.equal(response.json().windows, null);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/setup/v1.1.0' })).statusCode, 502);
});
