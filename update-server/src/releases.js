import { parseTag } from './version.js';

const USER_AGENT = 'daybreak-update-server';

/**
 * Asks GitHub for the repository's releases and keeps the answer for a while.
 *
 * - The list is reused for [cacheTtlMs]. After that GitHub is asked again with the ETag, so an
 *   unchanged list costs no rate limit.
 * - If GitHub cannot be reached, the last list is served instead of an error.
 * - Drafts, prereleases and tags that are not versions are skipped. A release without an APK
 *   attached (the workflow is still building it) is listed but never offered as the latest.
 */
export class ReleaseService {
  constructor({ config, fetchImpl = fetch, now = () => Date.now() }) {
    this.config = config;
    this.fetch = fetchImpl;
    this.now = now;
    this.releases = [];
    this.etag = undefined;
    this.fetchedAt = undefined;
    this.inFlight = undefined;
  }

  /** The releases, newest first. */
  async list({ force = false } = {}) {
    const fresh = this.fetchedAt !== undefined && this.now() - this.fetchedAt < this.config.cacheTtlMs;
    if (fresh && !force) return this.releases;
    if (!this.inFlight) {
      this.inFlight = this.#load().finally(() => {
        this.inFlight = undefined;
      });
    }
    await this.inFlight;
    return this.releases;
  }

  /** The newest release that has an APK, or undefined. */
  async latest(options) {
    return (await this.list(options)).find((release) => release.apk);
  }

  /** The release with [tag], or undefined. */
  async find(tag) {
    return (await this.list()).find((release) => release.tag === tag);
  }

  async #load() {
    const headers = {
      Accept: 'application/vnd.github+json',
      'User-Agent': USER_AGENT,
      'X-GitHub-Api-Version': '2022-11-28',
    };
    if (this.config.githubToken) headers.Authorization = `Bearer ${this.config.githubToken}`;
    if (this.etag) headers['If-None-Match'] = this.etag;

    let response;
    try {
      response = await this.fetch(
        `${this.config.githubApiUrl}/repos/${this.config.repo}/releases?per_page=30`,
        { headers },
      );
    } catch (error) {
      return this.#keepOld(error);
    }
    if (response.status === 304) {
      this.fetchedAt = this.now();
      return;
    }
    if (!response.ok) {
      return this.#keepOld(new Error(`GitHub answered ${response.status}`));
    }
    const body = await response.json();
    this.releases = mapReleases(body, this.config);
    this.etag = response.headers.get('etag') ?? undefined;
    this.fetchedAt = this.now();
  }

  #keepOld(error) {
    // Nothing to fall back on: let the caller see the failure.
    if (this.fetchedAt === undefined) throw error;
    // Serve the old list, and try again after a short pause instead of on every request.
    this.fetchedAt = this.now() - this.config.cacheTtlMs + Math.min(30_000, this.config.cacheTtlMs);
  }
}

/** The parts of a GitHub asset the server needs, or undefined when there is no asset. */
function toAsset(asset) {
  if (!asset) return undefined;
  return {
    name: asset.name,
    size: asset.size,
    // GitHub fills this for newer uploads, as "sha256:<hex>".
    digest: typeof asset.digest === 'string' ? asset.digest : undefined,
    downloadUrl: asset.browser_download_url,
    apiUrl: asset.url,
  };
}

/** Maps GitHub's release objects to what the server serves. */
export function mapReleases(body, config) {
  if (!Array.isArray(body)) return [];
  return body
    .filter((release) => !release.draft && !release.prerelease)
    .map((release) => {
      const version = parseTag(release.tag_name ?? '');
      if (!version) return undefined;
      const find = (pattern) => (release.assets ?? []).find(
        (candidate) => candidate.state === 'uploaded' && pattern.test(candidate.name),
      );
      const apk = find(config.apkPattern);
      const setup = find(config.setupPattern);
      return {
        tag: release.tag_name,
        versionName: version.versionName,
        versionCode: version.versionCode,
        publishedAt: release.published_at ?? release.created_at ?? null,
        changelog: (release.body ?? '').replace(/\r\n/g, '\n').trim(),
        asset: toAsset(apk),
        apk: Boolean(apk),
        setup: toAsset(setup),
      };
    })
    .filter(Boolean)
    .sort((a, b) => b.versionCode - a.versionCode);
}
