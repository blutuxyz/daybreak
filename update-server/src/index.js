import { AssistantService } from './assistant.js';
import { AssistantStore } from './assistant-store.js';
import { AnnouncementStore } from './announcements.js';
import { AccountStore } from './accounts.js';
import { StripeClient } from './billing.js';
import { ReleaseFileCache } from './release-files.js';
import { loadConfig } from './config.js';
import { ReleaseService } from './releases.js';
import { buildServer } from './server.js';
import { SupportLedger } from './support.js';

const config = loadConfig();
const releases = new ReleaseService({ config });
const apks = new ReleaseFileCache({ config, kind: 'apk' });
const setups = new ReleaseFileCache({ config, kind: 'setup' });
const support = new SupportLedger({ config });
const accounts = new AccountStore({ config });
const stripe = new StripeClient({ config });
// The assistant (OpenRouter) is on only with OPENROUTER_API_KEY; its settings are on the admin page.
const assistantStore = new AssistantStore({ config });
await assistantStore.settings();
const announcements = new AnnouncementStore({ config });
const assistant = new AssistantService({ config, store: assistantStore, support, notices: announcements });
const { app, warm } = buildServer({
  config,
  releases,
  apks,
  setups,
  support,
  accounts,
  stripe,
  announcements,
  assistant,
  assistantStore,
  logger: true,
});

try {
  await listen(config.host);
} catch (error) {
  app.log.error(error);
  process.exit(1);
}

/** Listens on the host. Where IPv6 is switched off, "::" is not available, so fall back to IPv4. */
async function listen(host) {
  try {
    await app.listen({ port: config.port, host });
  } catch (error) {
    if (host === '::' && ['EAFNOSUPPORT', 'EADDRNOTAVAIL'].includes(error?.code)) {
      app.log.warn('IPv6 is not available, listening on IPv4 only');
      await app.listen({ port: config.port, host: '0.0.0.0' });
      return;
    }
    throw error;
  }
}

// Look for a new release now and then, so the APK is already downloaded when the app asks.
const warmUp = () => warm().catch((error) => app.log.warn({ err: error }, 'warming the APK cache failed'));
warmUp();
setInterval(warmUp, Math.max(config.cacheTtlMs, 60_000)).unref();

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => app.close().then(() => process.exit(0)));
}
