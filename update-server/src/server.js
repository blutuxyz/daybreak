import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import Fastify from 'fastify';
import { AnnouncementStore, MESSAGE_ID_PATTERN } from './announcements.js';
import { renderAdmin } from './admin.js';
import { pickLanguage, renderPage } from './page.js';
import { RateLimiter, extractCode, normalizeCode, parseKofiBody, sameSecret } from './support.js';
import { isSafeTag } from './version.js';

/**
 * The HTTP API the app talks to.
 *
 *   GET  /                  the download page for people who do not have the app yet
 *   GET  /download         redirects to the newest APK
 *   GET  /health            liveness check
 *   GET  /v1/latest         the newest release: version, changelog, APK link, size and SHA-256
 *   GET  /v1/releases       the release history with changelogs (?limit=, default 20)
 *   GET  /v1/apk/:tag       the APK of a release; supports Range for resuming a download
 *   POST /v1/refresh        asks GitHub right now (needs REFRESH_TOKEN); CI calls it after a release
 *   POST /v1/kofi           Ko-fi's webhook: a payment whose message holds an unlock code (needs
 *                           KOFI_VERIFICATION_TOKEN)
 *   GET  /v1/support/status ?code=DB-XXXX-XXXX  { paid } whether that unlock code was paid for
 *   GET  /v1/status         the minimum app version and the messages for the app (announcements.js)
 *   GET  /admin             the admin page, behind ADMIN_TOKEN: minimum version, switches and messages
 */
export function buildServer({
  config,
  releases,
  apks,
  support,
  announcements,
  logger = false,
  now = () => Date.now(),
}) {
  const app = Fastify({ logger, trustProxy: true });
  const notices = announcements ?? new AnnouncementStore({ config });
  const adminLimiter = new RateLimiter({ limit: 60, windowMs: 60_000, now });

  // Ko-fi posts a form ("data=<json>"). Reading it needs no extra package.
  app.addContentTypeParser('application/x-www-form-urlencoded', { parseAs: 'string', bodyLimit: 65_536 }, (_, body, done) => {
    done(null, Object.fromEntries(new URLSearchParams(body)));
  });
  const statusLimiter = new RateLimiter({ limit: 30, windowMs: 60_000, now });

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

  app.get('/', async (request, reply) => {
    const lang = pickLanguage(request.headers['accept-language']);
    reply
      .header('Content-Type', 'text/html; charset=utf-8')
      .header('Cache-Control', 'no-cache')
      .header('Vary', 'Accept-Language')
      .header(
        'Content-Security-Policy',
        "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
      )
      .header('X-Content-Type-Options', 'nosniff');
    let list;
    try {
      list = (await releases.list()).filter((release) => release.apk);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send(renderPage({ app: config.appName, lang, status: 'unavailable' }));
    }
    if (list.length === 0) {
      return reply.code(404).send(renderPage({ app: config.appName, lang, status: 'empty' }));
    }
    const [latest, ...older] = await Promise.all(
      list.slice(0, 11).map((release) => describe(release, request, { ensure: false })),
    );
    return reply.send(renderPage({ app: config.appName, lang, latest, older }));
  });

  app.get('/download', async (request, reply) => {
    let latest;
    try {
      latest = await releases.latest();
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    if (!latest) return reply.code(404).send({ error: 'no_release' });
    return reply.header('Cache-Control', 'no-cache').redirect(`/v1/apk/${latest.tag}`, 302);
  });

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

  app.post('/v1/kofi', async (request, reply) => {
    if (!config.kofiToken || !support) return reply.code(404).send({ error: 'not_found' });
    const payment = parseKofiBody(request.body);
    if (!payment) return reply.code(400).send({ error: 'bad_request' });
    if (!sameSecret(payment.verification_token, config.kofiToken)) {
      return reply.code(401).send({ error: 'unauthorized' });
    }
    // Everything below answers 200: Ko-fi would only retry a payment that has no code in it.
    const code = extractCode(payment.message);
    const amount = Number(payment.amount);
    if (!code) return { ok: true, unlocked: false, reason: 'no_code' };
    if (!Number.isFinite(amount) || amount < config.supportMinAmount) {
      return { ok: true, unlocked: false, reason: 'too_small' };
    }
    await support.record({
      code,
      transactionId: typeof payment.kofi_transaction_id === 'string' ? payment.kofi_transaction_id : undefined,
      amount,
      currency: typeof payment.currency === 'string' ? payment.currency.slice(0, 8) : undefined,
    });
    // record() ignores a transaction id it has already seen (Ko-fi repeats deliveries). Report what is
    // true now, not what was asked: the code is unlocked only if the ledger has it.
    if (!(await support.isPaid(code))) return { ok: true, unlocked: false, reason: 'duplicate_transaction' };
    return { ok: true, unlocked: true };
  });

  app.get('/v1/support/status', async (request, reply) => {
    if (!support) return reply.code(404).send({ error: 'not_found' });
    reply.header('Cache-Control', 'no-store');
    if (!statusLimiter.allow(request.ip)) return reply.code(429).send({ error: 'too_many_requests' });
    const code = normalizeCode(request.query.code);
    if (!code) return reply.code(400).send({ error: 'bad_code' });
    return { paid: await support.isPaid(code) };
  });

  app.get('/v1/status', async (request, reply) => {
    reply.header('Cache-Control', 'no-cache');
    try {
      return await notices.status(now());
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'unavailable' });
    }
  });

  // The admin page. Without ADMIN_TOKEN none of these routes answer, so nobody can find them.
  // Signing in is HTTP Basic: the password is the token, the user name is ignored.
  const guardAdmin = (request, reply, { write = false } = {}) => {
    if (!config.adminToken) {
      reply.code(404).send({ error: 'not_found' });
      return false;
    }
    if (!adminLimiter.allow(request.ip)) {
      reply.code(429).send({ error: 'too_many_requests' });
      return false;
    }
    if (!sameSecret(basicPassword(request.headers.authorization), config.adminToken)) {
      reply
        .code(401)
        .header('WWW-Authenticate', 'Basic realm="Daybreak admin", charset="UTF-8"')
        .type('text/plain; charset=utf-8')
        .send('Sign in with the admin password.');
      return false;
    }
    // A form posted from another site would carry the browser's saved password too: refuse it.
    if (write && request.headers.origin !== undefined && request.headers.origin !== baseUrl(request)) {
      reply.code(403).send({ error: 'forbidden' });
      return false;
    }
    return true;
  };
  const adminHeaders = (reply) =>
    reply
      .header('Cache-Control', 'no-store')
      .header('Content-Security-Policy', "default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
      .header('X-Content-Type-Options', 'nosniff');
  const backToAdmin = (reply, notice) => reply.code(303).header('Location', `/admin?notice=${notice}`).send();

  app.get('/admin', async (request, reply) => {
    if (!guardAdmin(request, reply)) return reply;
    const state = await notices.snapshot();
    const notice = typeof request.query.notice === 'string' ? request.query.notice : undefined;
    adminHeaders(reply).type('text/html; charset=utf-8');
    return reply.send(renderAdmin({ app: config.appName, state, notice, now: now() }));
  });

  app.post('/admin/min-version', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    const result = await notices.setMinVersion(request.body?.version);
    return backToAdmin(reply, result.error ?? 'saved');
  });

  app.post('/admin/switches', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    const result = await notices.setSwitches(request.body ?? {});
    return backToAdmin(reply, result.error ?? 'saved');
  });

  app.post('/admin/messages', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    const result = await notices.addMessage(request.body ?? {}, { now: now() });
    return backToAdmin(reply, result.error ?? 'saved');
  });

  app.post('/admin/messages/:id/toggle', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    const result = MESSAGE_ID_PATTERN.test(request.params.id)
      ? await notices.toggleMessage(request.params.id)
      : { error: 'not_found' };
    return backToAdmin(reply, result.error ?? 'saved');
  });

  app.post('/admin/messages/:id/delete', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    const result = MESSAGE_ID_PATTERN.test(request.params.id)
      ? await notices.removeMessage(request.params.id)
      : { error: 'not_found' };
    return backToAdmin(reply, result.error ?? 'saved');
  });

  return { app, warm };
}

/** The password from a "Basic" Authorization header, or undefined. */
function basicPassword(header) {
  const match = /^Basic\s+(.+)$/i.exec(String(header ?? ''));
  if (!match) return undefined;
  const decoded = Buffer.from(match[1], 'base64').toString('utf8');
  const colon = decoded.indexOf(':');
  return colon === -1 ? undefined : decoded.slice(colon + 1);
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
