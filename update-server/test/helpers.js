import { createHash } from 'node:crypto';
import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadConfig } from '../src/config.js';

export const apkBytes = Buffer.from('PK-not-really-an-apk-but-bytes-are-bytes-'.repeat(8));
export const apkSha256 = createHash('sha256').update(apkBytes).digest('hex');

/** A GitHub release as the REST API returns it. */
export function githubRelease(tag, { body = `Notes for ${tag}`, assets, draft = false, prerelease = false } = {}) {
  return {
    tag_name: tag,
    draft,
    prerelease,
    body,
    published_at: '2026-11-04T10:00:00Z',
    assets: assets ?? [
      {
        name: `daybreak-${tag}.apk`,
        state: 'uploaded',
        size: apkBytes.length,
        browser_download_url: `https://github.com/example/daybreak/releases/download/${tag}/daybreak-${tag}.apk`,
        url: `https://api.github.com/repos/example/daybreak/releases/assets/${tag.length}`,
      },
    ],
  };
}

/**
 * A fetch that answers from [routes]: a function per URL prefix. It counts the calls, so tests can
 * check how often GitHub was asked.
 */
export function fakeFetch(routes) {
  const calls = [];
  const impl = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    for (const [prefix, handler] of Object.entries(routes)) {
      if (String(url).startsWith(prefix)) return handler(String(url), init);
    }
    return new Response('not found', { status: 404 });
  };
  impl.calls = calls;
  return impl;
}

export function jsonResponse(body, { status = 200, etag } = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...(etag ? { etag } : {}) },
  });
}

export const msiBytes = Buffer.from('MSI-installer-bytes-for-the-windows-desktop-app-'.repeat(40));
export const msiSha256 = createHash('sha256').update(msiBytes).digest('hex');

/** A release with the APK and the Windows installer, as the two release workflows attach them. */
export function releaseWithMsi(tag, { msiDigest = `sha256:${msiSha256}` } = {}) {
  const release = githubRelease(tag);
  const version = tag.replace(/^v/, '');
  release.assets.push({
    name: `Daybreak-${version}.msi`,
    state: 'uploaded',
    size: msiBytes.length,
    digest: msiDigest,
    browser_download_url: `https://github.com/example/daybreak/releases/download/${tag}/Daybreak-${version}.msi`,
    url: `https://api.github.com/repos/example/daybreak/releases/assets/msi-${tag}`,
  });
  return release;
}

export async function testConfig(overrides = {}) {
  const dataDir = await mkdtemp(join(tmpdir(), 'daybreak-update-'));
  return { ...loadConfig({}), dataDir, repo: 'example/daybreak', ...overrides };
}
