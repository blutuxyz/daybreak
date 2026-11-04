import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ReleaseService, mapReleases } from '../src/releases.js';
import { fakeFetch, githubRelease, jsonResponse, testConfig } from './helpers.js';

test('releases are mapped, sorted and filtered', async () => {
  const config = await testConfig();
  const mapped = mapReleases(
    [
      githubRelease('v1.0.0'),
      githubRelease('v1.2.0', { body: 'Line one\r\nLine two\r\n' }),
      githubRelease('v1.1.0', { draft: true }),
      githubRelease('v1.3.0', { prerelease: true }),
      githubRelease('nightly'),
      githubRelease('v1.4.0', { assets: [] }),
      githubRelease('v1.5.0', { assets: [{ name: 'notes.txt', state: 'uploaded', size: 1 }] }),
    ],
    config,
  );

  assert.deepEqual(mapped.map((release) => release.tag), ['v1.5.0', 'v1.4.0', 'v1.2.0', 'v1.0.0']);
  assert.equal(mapped.find((release) => release.tag === 'v1.2.0').changelog, 'Line one\nLine two');
  assert.equal(mapped.find((release) => release.tag === 'v1.4.0').apk, false);
  assert.equal(mapped.find((release) => release.tag === 'v1.2.0').versionCode, 10200);
});

test('the latest release is the newest one that has an APK', async () => {
  const config = await testConfig();
  const fetchImpl = fakeFetch({
    'https://api.github.com/': () =>
      jsonResponse([githubRelease('v1.3.0', { assets: [] }), githubRelease('v1.2.0')]),
  });
  const service = new ReleaseService({ config, fetchImpl });

  assert.equal((await service.latest()).tag, 'v1.2.0');
});

test('GitHub is asked again only after the cache time, and with the ETag', async () => {
  const config = await testConfig({ cacheTtlMs: 1000 });
  let clock = 0;
  const fetchImpl = fakeFetch({
    'https://api.github.com/': (_url, init) =>
      init.headers['If-None-Match'] === '"v1"'
        ? new Response(null, { status: 304 })
        : jsonResponse([githubRelease('v1.0.0')], { etag: '"v1"' }),
  });
  const service = new ReleaseService({ config, fetchImpl, now: () => clock });

  await service.list();
  clock = 500;
  await service.list();
  assert.equal(fetchImpl.calls.length, 1, 'served from the cache');

  clock = 1500;
  const list = await service.list();
  assert.equal(fetchImpl.calls.length, 2);
  assert.equal(fetchImpl.calls[1].init.headers['If-None-Match'], '"v1"');
  assert.equal(list.length, 1, 'a 304 keeps the list');
});

test('force asks GitHub even when the cache is fresh', async () => {
  const config = await testConfig({ cacheTtlMs: 60_000 });
  const fetchImpl = fakeFetch({ 'https://api.github.com/': () => jsonResponse([githubRelease('v1.0.0')]) });
  const service = new ReleaseService({ config, fetchImpl, now: () => 0 });

  await service.list();
  await service.list({ force: true });

  assert.equal(fetchImpl.calls.length, 2);
});

test('the old list is served when GitHub fails, and the failure shows when there is no old list', async () => {
  const config = await testConfig({ cacheTtlMs: 1000 });
  let clock = 0;
  let fail = false;
  const fetchImpl = fakeFetch({
    'https://api.github.com/': () => (fail ? new Response('boom', { status: 500 }) : jsonResponse([githubRelease('v1.0.0')])),
  });
  const service = new ReleaseService({ config, fetchImpl, now: () => clock });

  fail = true;
  await assert.rejects(() => service.list(), /500/);

  fail = false;
  await service.list();
  fail = true;
  clock = 5000;
  const list = await service.list();
  assert.equal(list[0].tag, 'v1.0.0');
});

test('concurrent requests share one call to GitHub', async () => {
  const config = await testConfig();
  const fetchImpl = fakeFetch({ 'https://api.github.com/': () => jsonResponse([githubRelease('v1.0.0')]) });
  const service = new ReleaseService({ config, fetchImpl });

  await Promise.all([service.list(), service.list(), service.latest()]);

  assert.equal(fetchImpl.calls.length, 1);
});

test('the token is sent to GitHub when there is one', async () => {
  const config = await testConfig({ githubToken: 'secret' });
  const fetchImpl = fakeFetch({ 'https://api.github.com/': () => jsonResponse([]) });
  await new ReleaseService({ config, fetchImpl }).list();

  assert.equal(fetchImpl.calls[0].init.headers.Authorization, 'Bearer secret');
});
