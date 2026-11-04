import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import Fastify from 'fastify';
import { isSafeTag } from './version.js';

/**
 * The HTTP API the app talks to.
 *
 *   GET  /health            liveness check
 *   GET  /v1/latest         the newest release: version, changelog, APK link, size and SHA-256
 *   GET  /v1/releases       the release history with changelogs (?limit=, default 20)
 *   GET  /v1/apk/:tag       the APK of a release; supports Range for resuming a download
 *   POST /v1/refresh        asks GitHub right now (needs REFRESH_TOKEN); CI calls it after a release
 */
export function buildServer({ config, releases, apks, logger = false }) {
  const app = Fastify({ logger, trustProxy: true });

  const baseUrl = (request) => config.publicUrl ?? `${request.protocol}://${request.host}`;

  const describe = async (release, request, { ensure }) => {
    let meta;
    if (release.apk) {
      meta = ensure ? await apks.ensure(release) : await apks.meta(release.tag);
    }
    const digestHex = release.asset?.digest?.startsWith('sha256:')
      ? release.asset.digest.slice(7).toLowerCase()
      : null;
    return {
      tag: release.tag,
      versionName: release.versionName,
      versionCode: release.versionCode,
      publishedAt: release.publishedAt,
      changelog: release.changelog,
      apk: release.apk
        ? {
            url: `${baseUrl(request)}/v1/apk/${release.tag}`,
            size: meta?.size ?? release.asset.size,
            sha256: meta?.sha256 ?? digestHex,
          }
        : null,
    };
  };

  /** Downloads the newest APK ahead of time, so the first app that asks does not wait. */
  const warm = async () => {
    const latest = await releases.latest();
    if (latest) await apks.ensure(latest);
    return latest;
  };

  app.get('/health', async () => ({ ok: true }));

  app.get('/v1/latest', async (request, reply) => {
    let latest;
    try {
      latest = await releases.latest();
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    if (!latest) return reply.code(404).send({ error: 'no_release' });
    try {
      return await describe(latest, request, { ensure: true });
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'apk_unavailable' });
    }
  });

  app.get('/v1/releases', async (request, reply) => {
    const limit = Math.min(Math.max(Number(request.query.limit) || 20, 1), 50);
    let list;
    try {
      list = await releases.list();
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    const described = await Promise.all(
      list.slice(0, limit).map((release) => describe(release, request, { ensure: false })),
    );
    return { releases: described };
  });

  app.get('/v1/apk/:tag', async (request, reply) => {
    const { tag } = request.params;
    if (!isSafeTag(tag)) return reply.code(404).send({ error: 'not_found' });
    let release;
    try {
      release = await releases.find(tag);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    if (!release?.apk) return reply.code(404).send({ error: 'not_found' });

    let meta;
    try {
      meta = await apks.ensure(release);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'apk_unavailable' });
    }

    const total = (await stat(apks.path(tag))).size;
    reply
      .header('Content-Type', 'application/vnd.android.package-archive')
      .header('Content-Disposition', `attachment; filename="${release.asset.name}"`)
      .header('Accept-Ranges', 'bytes')
      .header('ETag', `"${meta.sha256}"`)
      .header('Cache-Control', 'public, max-age=31536000, immutable');

    const range = parseRange(request.headers.range, total);
    if (range === 'invalid') {
      return reply.code(416).header('Content-Range', `bytes */${total}`).send();
    }
    if (range) {
      reply
        .code(206)
        .header('Content-Range', `bytes ${range.start}-${range.end}/${total}`)
        .header('Content-Length', range.end - range.start + 1);
      return reply.send(createReadStream(apks.path(tag), range));
    }
    reply.header('Content-Length', total);
    return reply.send(createReadStream(apks.path(tag)));
  });

  app.post('/v1/refresh', async (request, reply) => {
    if (!config.refreshToken) return reply.code(404).send({ error: 'not_found' });
    if (request.headers.authorization !== `Bearer ${config.refreshToken}`) {
      return reply.code(401).send({ error: 'unauthorized' });
    }
    try {
      await releases.list({ force: true });
      const latest = await warm();
      return { ok: true, latest: latest?.tag ?? null };
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
  });

  return { app, warm };
}

/** Reads a single "bytes=start-end" range. Returns undefined for none, 'invalid' for a bad one. */
export function parseRange(header, total) {
  if (!header) return undefined;
  const match = /^bytes=(\d*)-(\d*)$/.exec(header.trim());
  if (!match || (match[1] === '' && match[2] === '')) return 'invalid';
  let start;
  let end;
  if (match[1] === '') {
    // "bytes=-500" is the last 500 bytes.
    const length = Number(match[2]);
    start = Math.max(total - length, 0);
    end = total - 1;
  } else {
    start = Number(match[1]);
    end = match[2] === '' ? total - 1 : Math.min(Number(match[2]), total - 1);
  }
  if (start > end || start >= total) return 'invalid';
  return { start, end };
}
