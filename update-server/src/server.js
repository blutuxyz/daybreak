import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import Fastify from 'fastify';
import { AnnouncementStore, MESSAGE_ID_PATTERN } from './announcements.js';
import { parseAssistantRequest, parseTitleRequest } from './assistant.js';
import { DEFAULT_SETTINGS, EFFORTS, HIDDEN_ROUTINGS, PROVIDER_SORTS, parseSettingsForm } from './assistant-store.js';
import { renderAdmin, renderLogEntry } from './admin.js';
import { pickLanguage, renderDesktopPage, renderPage } from './page.js';
import { ReleaseFileCache } from './release-files.js';
import { RateLimiter, extractCode, normalizeCode, parseKofiBody, sameSecret } from './support.js';
import { isSafeTag } from './version.js';

/**
 * The HTTP API the app talks to.
 *
 *   GET  /                  the download page for people who do not have the app yet
 *   GET  /download         redirects to the newest APK
 *   GET  /desktop           the download page for the Windows app
 *   GET  /desktop/download  redirects to the newest Windows installer
 *   GET  /health            liveness check
 *   GET  /v1/latest         the newest release: version, changelog, APK link, size and SHA-256
 *   GET  /v1/releases       the release history with changelogs (?limit=, default 20)
 *   GET  /v1/apk/:tag       the APK of a release; supports Range for resuming a download
 *   GET  /v1/setup/:tag       the Windows installer of a release, the same way (desktop app)
 *   POST /v1/refresh        asks GitHub right now (needs REFRESH_TOKEN); CI calls it after a release
 *   POST /v1/kofi           Ko-fi's webhook: a payment whose message holds an unlock code (needs
 *                           KOFI_VERIFICATION_TOKEN)
 *   GET  /v1/support/status ?code=DB-XXXX-XXXX  { paid } whether that unlock code was paid for
 *   GET  /v1/status         the minimum app version and the messages for the app (announcements.js)
 *   GET  /admin             the admin page, behind ADMIN_TOKEN: minimum version, switches and messages
 *   GET  /v1/assistant/models  the models the app may offer (404 while the assistant is off)
 *   POST /v1/assistant      the in-app assistant through OpenRouter: the chat in, the answer out as
 *                           server-sent events (needs OPENROUTER_API_KEY; assistant.js)
 *   GET  /admin/assistant/logs/:id  one logged assistant request, behind ADMIN_TOKEN
 */
export function buildServer({
  config,
  releases,
  apks,
  setups = new ReleaseFileCache({ config, kind: 'setup' }),
  support,
  announcements,
  assistant,
  assistantStore,
  logger = false,
  now = () => Date.now(),
}) {
  const app = Fastify({ logger, trustProxy: true });
  const notices = announcements ?? new AnnouncementStore({ config });
  const adminLimiter = new RateLimiter({ limit: 60, windowMs: 60_000, now });

  // Ko-fi posts a form ("data=<json>"). Reading it needs no extra package.
  // A field repeated in the form (one per row of the provider and model tables) becomes an array of its values.
  app.addContentTypeParser('application/x-www-form-urlencoded', { parseAs: 'string', bodyLimit: 262_144 }, (_, body, done) => {
    const fields = {};
    for (const [key, value] of new URLSearchParams(body)) {
      fields[key] = key in fields ? [].concat(fields[key], value) : value;
    }
    done(null, fields);
  });
  const statusLimiter = new RateLimiter({ limit: 30, windowMs: 60_000, now });
  // Limits per IP address, set on the admin page (they apply at once, without a restart).
  const limits = () => assistantStore?.currentSettings() ?? DEFAULT_SETTINGS;
  const assistantLimiter = new RateLimiter({ limit: () => limits().assistantPerMinute, windowMs: 60_000, now });
  const updateLimiter = new RateLimiter({ limit: () => limits().updateChecksPerMinute, windowMs: 60_000, now });
  const downloadLimiter = new RateLimiter({ limit: () => limits().downloadsPerHour, windowMs: 3_600_000, now });
  app.addHook('onRequest', async (request, reply) => {
    const path = request.url.split('?')[0];
    const limiter = /^\/v1\/(latest|releases|status|assistant\/models)$/.test(path)
      ? updateLimiter
      : /^\/(v1\/(apk|setup)\/[^/]+|download|desktop\/download)$/.test(path)
        ? downloadLimiter
        : undefined;
    if (limiter && !limiter.allow(request.ip)) {
      return reply.code(429).header('Retry-After', '60').send({ error: 'too_many_requests' });
    }
  });

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
      windows: release.setup ? await describeSetup(release, request, { ensure }) : null,
    };
  };

  /**
   * The Windows installer of a release: where to get it and its checksum. A failure to get the
   * installer leaves it out (null), so the Android app's answer is never affected by it.
   */
  const describeSetup = async (release, request, { ensure }) => {
    let meta;
    try {
      meta = ensure ? await setups.ensure(release) : await setups.meta(release.tag);
    } catch (error) {
      request.log.error(error);
      return null;
    }
    const digestHex = release.setup.digest?.startsWith('sha256:') ? release.setup.digest.slice(7).toLowerCase() : null;
    return {
      installer: {
        url: `${baseUrl(request)}/v1/setup/${release.tag}`,
        name: release.setup.name,
        size: meta?.size ?? release.setup.size,
        sha256: meta?.sha256 ?? digestHex,
      },
    };
  };

  /** Downloads the newest APK and installer ahead of time, so the first app that asks does not wait. */
  const warm = async () => {
    const latest = await releases.latest();
    if (latest) await apks.ensure(latest);
    const newestSetup = (await releases.list()).find((release) => release.setup);
    if (newestSetup) {
      await setups.ensure(newestSetup).catch((error) => app.log.warn({ err: error }, 'warming the installer cache failed'));
    }
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

  /** The headers of the public download pages: HTML, never scripted. */
  const pageHeaders = (reply) =>
    reply
      .header('Content-Type', 'text/html; charset=utf-8')
      .header('Cache-Control', 'no-cache')
      .header('Vary', 'Accept-Language')
      .header(
        'Content-Security-Policy',
        "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
      )
      .header('X-Content-Type-Options', 'nosniff');

  /** The download page for the Windows desktop app. */
  app.get('/desktop', async (request, reply) => {
    const lang = pickLanguage(request.headers['accept-language']);
    pageHeaders(reply);
    let list;
    try {
      list = (await releases.list()).filter((release) => release.setup);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send(renderDesktopPage({ app: config.appName, lang, status: 'unavailable' }));
    }
    if (list.length === 0) {
      return reply.code(404).send(renderDesktopPage({ app: config.appName, lang, status: 'empty' }));
    }
    const [latest, ...older] = await Promise.all(
      list.slice(0, 11).map((release) => describe(release, request, { ensure: false })),
    );
    return reply.send(renderDesktopPage({ app: config.appName, lang, latest, older }));
  });

  /** A direct link to the newest Windows installer, as /download is for the Android app. */
  app.get('/desktop/download', async (request, reply) => {
    let latest;
    try {
      latest = (await releases.list()).find((release) => release.setup);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    if (!latest) return reply.code(404).send({ error: 'no_release' });
    return reply.header('Cache-Control', 'no-cache').redirect(`/v1/setup/${latest.tag}`, 302);
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

  /**
   * Serves the cached file of a release with Range support. The APK and the Windows installer are
   * served the same way; only the type, the error code and the release field differ.
   */
  const serveReleaseFile = ({ cache, field, contentType, errorCode }) => async (request, reply) => {
    const { tag } = request.params;
    if (!isSafeTag(tag)) return reply.code(404).send({ error: 'not_found' });
    let release;
    try {
      release = await releases.find(tag);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: 'upstream_unavailable' });
    }
    if (!release?.[field]) return reply.code(404).send({ error: 'not_found' });

    let meta;
    try {
      meta = await cache.ensure(release);
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({ error: errorCode });
    }

    const total = (await stat(cache.path(tag))).size;
    reply
      .header('Content-Type', contentType)
      .header('Content-Disposition', `attachment; filename="${release[field].name}"`)
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
      return reply.send(createReadStream(cache.path(tag), range));
    }
    reply.header('Content-Length', total);
    return reply.send(createReadStream(cache.path(tag)));
  };

  app.get('/v1/apk/:tag', serveReleaseFile({
    cache: apks,
    field: 'asset',
    contentType: 'application/vnd.android.package-archive',
    errorCode: 'apk_unavailable',
  }));

  app.get('/v1/setup/:tag', serveReleaseFile({
    cache: setups,
    field: 'setup',
    contentType: 'application/vnd.microsoft.portable-executable',
    errorCode: 'setup_unavailable',
  }));

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

  /** The models the app may offer, or 404 while the assistant is off (the app then hides it). */
  app.get('/v1/assistant/models', async (request, reply) => {
    reply.header('Cache-Control', 'no-store');
    const models = await assistant?.models();
    if (!models) return reply.code(404).send({ error: 'not_found' });
    return models;
  });

  /** A short AI title for a new chat: { title } or 204 when none is made (the app keeps its own name). */
  app.post('/v1/assistant/title', { bodyLimit: 16_384, logLevel: 'warn' }, async (request, reply) => {
    if (!assistant?.configured) return reply.code(404).send({ error: 'not_found' });
    reply.header('Cache-Control', 'no-store');
    if (!assistantLimiter.allow(request.ip)) return reply.code(429).send({ error: 'too_many_requests' });
    const installId = String(request.headers['x-daybreak-install'] ?? '');
    if (!/^[A-Za-z0-9-]{16,64}$/.test(installId)) return reply.code(400).send({ error: 'no_install_id' });
    const parsed = parseTitleRequest(request.body);
    if (parsed.error) return reply.code(400).send({ error: parsed.error });
    const title = await assistant.title({ request: parsed.value, installId });
    return title ? { title } : reply.code(204).send();
  });

  /**
   * The assistant. The answer streams as server-sent events, one JSON object per event:
   *   {"type":"reasoning","text":"…"}               a piece of the model's reasoning (models that show it)
   *   {"type":"text","text":"…"}                    a piece of the answer
   *   {"type":"tool_calls","calls":[{id,name,arguments}]}  tools the app has to run, then post again
   *   {"type":"done"}                               the turn is complete
   *   {"type":"error","code":"…"}                   upstream, busy, limit or unavailable
   * The user is the app's random install id (header X-Daybreak-Install), kept only as a hash.
   */
  app.post('/v1/assistant', { bodyLimit: 16 * 1024 * 1024, logLevel: 'warn' }, async (request, reply) => {
    if (!assistant?.configured) return reply.code(404).send({ error: 'not_found' });
    reply.header('Cache-Control', 'no-store');
    if (!assistantLimiter.allow(request.ip)) return reply.code(429).send({ error: 'too_many_requests' });
    const installId = String(request.headers['x-daybreak-install'] ?? '');
    if (!/^[A-Za-z0-9-]{16,64}$/.test(installId)) return reply.code(400).send({ error: 'no_install_id' });
    const parsed = parseAssistantRequest(request.body);
    if (parsed.error) return reply.code(400).send({ error: parsed.error });

    const abort = new AbortController();
    reply.hijack();
    const out = reply.raw;
    // The response closes early only when the app went away (stopped, or lost the connection).
    out.on('close', () => {
      if (!out.writableFinished) abort.abort();
    });
    out.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-store',
      'X-Accel-Buffering': 'no',
      Connection: 'keep-alive',
    });
    const send = (event) => {
      if (!out.writableEnded && !out.destroyed) out.write(`data: ${JSON.stringify(event)}\n\n`);
    };
    try {
      await assistant.run({ request: parsed.value, installId, send, signal: abort.signal });
    } catch (error) {
      request.log.warn({ name: error?.name }, 'assistant request failed');
      send({ type: 'error', code: 'unavailable' });
    } finally {
      out.end();
    }
    return reply;
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
      .header(
        'Content-Security-Policy',
        "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
      )
      .header('X-Content-Type-Options', 'nosniff');
  const backToAdmin = (reply, notice) => reply.code(303).header('Location', `/admin?notice=${notice}`).send();

  app.get('/admin', async (request, reply) => {
    if (!guardAdmin(request, reply)) return reply;
    const state = await notices.snapshot();
    const notice = typeof request.query.notice === 'string' ? request.query.notice : undefined;
    let assistantState;
    if (assistantStore) {
      const settings = await assistantStore.settings();
      assistantState = {
        settings,
        sorts: PROVIDER_SORTS,
        routings: HIDDEN_ROUTINGS,
        efforts: EFFORTS,
        keyConfigured: Boolean(assistant?.configured),
        clientSwitchOn: assistant ? await assistant.clientSwitchOn() : true,
        unknownModels: assistant?.configured
          ? await assistant.unknownModels(settings.models.filter((model) => settings.providers.find((provider) => provider.id === model.provider)?.type === 'openrouter').map((model) => model.id))
          : null,
        usage: await assistantStore.usageToday(),
        logs: await assistantStore.logPage({ page: request.query?.logPage }),
      };
    }
    adminHeaders(reply).type('text/html; charset=utf-8');
    return reply.send(renderAdmin({ app: config.appName, state, notice, now: now(), assistant: assistantState }));
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

  app.post('/admin/assistant', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    if (!assistantStore) return reply.code(404).send({ error: 'not_found' });
    const parsed = parseSettingsForm(request.body, assistantStore.currentSettings());
    if (parsed.error) return backToAdmin(reply, parsed.error);
    await assistantStore.saveSettings(parsed.value);
    return backToAdmin(reply, 'assistant_saved');
  });

  app.post('/admin/assistant/logs/clear', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    if (!assistantStore) return reply.code(404).send({ error: 'not_found' });
    await assistantStore.clearLogs();
    return backToAdmin(reply, 'logs_cleared');
  });

  app.get('/admin/assistant/logs/:id', async (request, reply) => {
    if (!guardAdmin(request, reply)) return reply;
    const entry = /^[0-9a-f]{12}$/.test(request.params.id) ? await assistantStore?.log(request.params.id) : undefined;
    adminHeaders(reply).type('text/html; charset=utf-8');
    if (!entry) return reply.code(404).send(renderLogEntry({ app: config.appName, entry: undefined }));
    return reply.send(renderLogEntry({ app: config.appName, entry }));
  });

  /** Tells whether an unlock code the app shows has a payment on record. Changes nothing. */
  app.post('/admin/support/check', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    if (!support) return reply.code(404).send({ error: 'not_found' });
    const code = normalizeCode(request.body?.code);
    if (!code) return backToAdmin(reply, 'bad_code');
    return backToAdmin(reply, (await support.isPaid(code)) ? 'code_paid' : 'code_unpaid');
  });

  /** Grants the payment for an unlock code by hand. The app learns of it when its user taps "Check payment". */
  app.post('/admin/support/grant', async (request, reply) => {
    if (!guardAdmin(request, reply, { write: true })) return reply;
    if (!support) return reply.code(404).send({ error: 'not_found' });
    const code = normalizeCode(request.body?.code);
    if (!code) return backToAdmin(reply, 'bad_code');
    const granted = await support.grant({ code, at: new Date(now()) });
    return backToAdmin(reply, granted ? 'granted' : 'already_paid');
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
