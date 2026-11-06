import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ReleaseFileCache } from '../src/release-files.js';
import { ReleaseService } from '../src/releases.js';
import { buildServer } from '../src/server.js';
import { apkBytes, fakeFetch, githubRelease, jsonResponse, msiBytes, msiSha256, releaseWithMsi, testConfig } from './helpers.js';

async function setup({ releasesBody } = {}) {
  const config = await testConfig();
  const fetchImpl = fakeFetch({
    'https://api.github.com/repos/': () =>
      jsonResponse(releasesBody ?? [releaseWithMsi('v1.1.0'), githubRelease('v1.0.0')]),
    'https://github.com/': (url) => (url.endsWith('.msi') ? new Response(msiBytes) : new Response(apkBytes)),
  });
  const { app } = buildServer({
    config,
    releases: new ReleaseService({ config, fetchImpl }),
    apks: new ReleaseFileCache({ config, fetchImpl, kind: 'apk' }),
    msis: new ReleaseFileCache({ config, fetchImpl, kind: 'msi' }),
  });
  return { app };
}

const headers = { host: 'updates.example.com', 'x-forwarded-proto': 'https', 'accept-language': 'en-GB,en;q=0.9' };
const german = { ...headers, 'accept-language': 'de-DE,de;q=0.9' };

test('/desktop offers the Windows installer with its version, size and checksum', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/desktop', headers });

  assert.equal(response.statusCode, 200);
  assert.match(response.headers['content-type'], /text\/html/);
  const page = response.body;
  assert.ok(page.includes('href="/desktop/download"'), 'the download button links to the direct download');
  assert.ok(page.includes('Download for Windows'));
  assert.ok(page.includes('1.1.0'), 'the version is shown');
  assert.ok(page.includes(msiSha256), 'the installer checksum is shown');
  assert.ok(page.includes('aria-current="page"') && page.includes('href="/desktop"'));
});

test('/desktop is in German when the browser prefers German', async () => {
  const { app } = await setup();

  const page = (await app.inject({ method: 'GET', url: '/desktop', headers: german })).body;

  assert.ok(page.includes('Für Windows herunterladen'));
  assert.ok(page.includes('So installierst du die App'));
});

test('/desktop/download redirects to the newest installer', async () => {
  const { app } = await setup();

  const response = await app.inject({ method: 'GET', url: '/desktop/download', headers });

  assert.equal(response.statusCode, 302);
  assert.equal(response.headers.location, '/v1/msi/v1.1.0');
});

test('following /desktop/download gives the installer itself', async () => {
  const { app } = await setup();

  const redirect = await app.inject({ method: 'GET', url: '/desktop/download', headers });
  const file = await app.inject({ method: 'GET', url: redirect.headers.location, headers });

  assert.equal(file.statusCode, 200);
  assert.deepEqual(file.rawPayload, msiBytes);
});

test('the Android page is still the start page and links to the Windows page', async () => {
  const { app } = await setup();

  const page = (await app.inject({ method: 'GET', url: '/', headers })).body;

  assert.ok(page.includes('href="/download"'), 'the Android button is unchanged');
  assert.ok(page.includes('href="/desktop"'), 'the switch leads to the Windows page');
  assert.ok(page.includes('Download for Android'));
});

test('without any installer the Windows page says so, and the direct link is a 404', async () => {
  const { app } = await setup({ releasesBody: [githubRelease('v1.0.0')] });

  const page = await app.inject({ method: 'GET', url: '/desktop', headers });
  assert.equal(page.statusCode, 404);
  assert.ok(page.body.includes('There is no Windows version yet.'));

  const download = await app.inject({ method: 'GET', url: '/desktop/download', headers });
  assert.equal(download.statusCode, 404);
  assert.equal(download.json().error, 'no_release');
});
