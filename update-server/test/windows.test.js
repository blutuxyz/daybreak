import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { access } from 'node:fs/promises';
import { test } from 'node:test';
import { ReleaseFileCache } from '../src/release-files.js';
import { ReleaseService, mapReleases } from '../src/releases.js';
import { buildServer } from '../src/server.js';
import { apkBytes, apkSha256, fakeFetch, githubRelease, jsonResponse, msiBytes, msiSha256, releaseWithMsi, testConfig } from './helpers.js';

async function setup({ releasesBody, msiResponse } = {}) {
  const config = await testConfig();
  const fetchImpl = fakeFetch({
    'https://api.github.com/repos/': () =>
      jsonResponse(releasesBody ?? [releaseWithMsi('v1.1.0'), githubRelease('v1.0.0')]),
    'https://github.com/': (url) =>
      url.endsWith('.msi') ? (msiResponse?.() ?? new Response(msiBytes)) : new Response(apkBytes),
  });
  const releases = new ReleaseService({ config, fetchImpl });
  const apks = new ReleaseFileCache({ config, fetchImpl, kind: 'apk' });
  const msis = new ReleaseFileCache({ config, fetchImpl, kind: 'msi' });
  const server = buildServer({ config, releases, apks, msis });
  return { ...server, config };
}

const headers = { host: 'updates.example.com', 'x-forwarded-proto': 'https' };

test('the Windows installer is found by its name, next to the APK', async () => {
  const config = await testConfig();
  const [withInstaller, apkOnly] = mapReleases([releaseWithMsi('v1.1.0'), githubRelease('v1.0.0')], config);

  assert.equal(withInstaller.msi.name, 'Daybreak-1.1.0.msi');
  assert.equal(withInstaller.apk, true);
  assert.equal(apkOnly.msi, undefined);

  const otherInstaller = githubRelease('v1.2.0', {
    assets: [{ name: 'Setup-1.2.0.msi', state: 'uploaded', size: 1 }],
  });
  assert.equal(mapReleases([otherInstaller], config)[0].msi, undefined);
});

test('/v1/latest offers the Windows installer with its checksum, and the APK is unchanged', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });
  const body = response.json();

  assert.equal(response.statusCode, 200);
  assert.deepEqual(body.windows, {
    msi: {
      url: 'https://updates.example.com/v1/msi/v1.1.0',
      name: 'Daybreak-1.1.0.msi',
      size: msiBytes.length,
      sha256: msiSha256,
    },
  });
  assert.equal(body.apk.sha256, apkSha256);
});

test('/v1/msi/:tag serves the installer byte for byte, with its checksum and ranges', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/v1/msi/v1.1.0', headers });
  assert.equal(response.statusCode, 200);
  assert.equal(response.headers['content-type'], 'application/x-msi');
  assert.equal(response.headers.etag, `"${msiSha256}"`);
  assert.deepEqual(response.rawPayload, msiBytes);

  const partial = await app.inject({ method: 'GET', url: '/v1/msi/v1.1.0', headers: { range: 'bytes=0-9' } });
  assert.equal(partial.statusCode, 206);
  assert.deepEqual(partial.rawPayload, msiBytes.subarray(0, 10));
});

test('a release without an installer has no Windows block and no installer to download', async () => {
  const { app } = await setup({ releasesBody: [githubRelease('v1.1.0'), githubRelease('v1.0.0')] });

  const body = (await app.inject({ method: 'GET', url: '/v1/latest', headers })).json();

  assert.equal(body.windows, null);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/msi/v1.1.0' })).statusCode, 404);
});

test('a failing installer download leaves the APK answer intact and no file behind', async () => {
  const { app, config } = await setup({ msiResponse: () => new Response('down', { status: 503 }) });

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });

  assert.equal(response.statusCode, 200);
  assert.equal(response.json().windows, null);
  assert.equal(response.json().apk.sha256, apkSha256);
  await assert.rejects(() => access(`${config.dataDir}/msis/v1.1.0.msi`));
  await assert.rejects(() => access(`${config.dataDir}/msis/v1.1.0.msi.part`));
});

test('an installer that does not match its published checksum is refused', async () => {
  const { app } = await setup({
    releasesBody: [releaseWithMsi('v1.1.0', { msiDigest: `sha256:${'0'.repeat(64)}` }), githubRelease('v1.0.0')],
  });

  const response = await app.inject({ method: 'GET', url: '/v1/latest', headers });

  assert.equal(response.statusCode, 200);
  assert.equal(response.json().windows, null);
  assert.equal((await app.inject({ method: 'GET', url: '/v1/msi/v1.1.0' })).statusCode, 502);
});
