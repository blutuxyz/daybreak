import { ApkCache } from './apk-cache.js';
import { loadConfig } from './config.js';
import { ReleaseService } from './releases.js';
import { buildServer } from './server.js';

const config = loadConfig();
const releases = new ReleaseService({ config });
const apks = new ApkCache({ config });
const { app, warm } = buildServer({ config, releases, apks, logger: true });

try {
  await app.listen({ port: config.port, host: config.host });
} catch (error) {
  app.log.error(error);
  process.exit(1);
}

// Look for a new release now and then, so the APK is already downloaded when the app asks.
const warmUp = () => warm().catch((error) => app.log.warn({ err: error }, 'warming the APK cache failed'));
warmUp();
setInterval(warmUp, Math.max(config.cacheTtlMs, 60_000)).unref();

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => app.close().then(() => process.exit(0)));
}
