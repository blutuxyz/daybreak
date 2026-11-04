import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const USER_AGENT = 'daybreak-update-server';

/**
 * Keeps the APK of each release on disk, so the app downloads from this server and GitHub is
 * asked once per release. While an APK is fetched its SHA-256 is computed; the app checks it too.
 */
export class ApkCache {
  constructor({ config, fetchImpl = fetch }) {
    this.config = config;
    this.fetch = fetchImpl;
    this.dir = join(config.dataDir, 'apks');
    this.downloads = new Map();
  }

  path(tag) {
    return join(this.dir, `${tag}.apk`);
  }

  /** What is known about the cached APK of [tag]: { sha256, size }, or undefined. */
  async meta(tag) {
    try {
      const meta = JSON.parse(await readFile(join(this.dir, `${tag}.json`), 'utf8'));
      await stat(this.path(tag));
      return meta;
    } catch {
      return undefined;
    }
  }

  /** The APK of [release] on disk. Downloads it from GitHub the first time. */
  async ensure(release) {
    const existing = await this.meta(release.tag);
    if (existing) return existing;
    let pending = this.downloads.get(release.tag);
    if (!pending) {
      // Two requests for the same APK share one download.
      pending = this.#download(release).finally(() => this.downloads.delete(release.tag));
      this.downloads.set(release.tag, pending);
    }
    return pending;
  }

  async #download(release) {
    const { asset, tag } = release;
    await mkdir(this.dir, { recursive: true });

    const headers = { 'User-Agent': USER_AGENT };
    let url = asset.downloadUrl;
    if (this.config.githubToken) {
      // A private repository's assets are only served through the API, and need the token.
      url = asset.apiUrl;
      headers.Accept = 'application/octet-stream';
      headers.Authorization = `Bearer ${this.config.githubToken}`;
    }
    const response = await this.fetch(url, { headers, redirect: 'follow' });
    if (!response.ok || !response.body) {
      throw new Error(`Downloading ${tag} failed: GitHub answered ${response.status}`);
    }

    const hash = createHash('sha256');
    let size = 0;
    const counter = new Transform({
      transform(chunk, _encoding, callback) {
        hash.update(chunk);
        size += chunk.length;
        callback(null, chunk);
      },
    });
    const part = `${this.path(tag)}.part`;
    try {
      await pipeline(Readable.fromWeb(response.body), counter, createWriteStream(part));
      const sha256 = hash.digest('hex');
      if (asset.size && size !== asset.size) {
        throw new Error(`Downloading ${tag} failed: got ${size} bytes, expected ${asset.size}`);
      }
      const expected = asset.digest?.startsWith('sha256:') ? asset.digest.slice(7).toLowerCase() : undefined;
      if (expected && expected !== sha256) {
        throw new Error(`Downloading ${tag} failed: the checksum does not match GitHub's`);
      }
      await rename(part, this.path(tag));
      const meta = { tag, sha256, size };
      await writeFile(join(this.dir, `${tag}.json`), JSON.stringify(meta));
      await this.#prune();
      return meta;
    } catch (error) {
      await rm(part, { force: true });
      throw error;
    }
  }

  /** Deletes the oldest APKs beyond the number to keep. */
  async #prune() {
    const names = (await readdir(this.dir)).filter((name) => name.endsWith('.apk'));
    const dated = await Promise.all(
      names.map(async (name) => ({ name, time: (await stat(join(this.dir, name))).mtimeMs })),
    );
    dated.sort((a, b) => b.time - a.time);
    for (const { name } of dated.slice(this.config.keepApks)) {
      await rm(join(this.dir, name), { force: true });
      await rm(join(this.dir, name.replace(/\.apk$/, '.json')), { force: true });
    }
  }
}
