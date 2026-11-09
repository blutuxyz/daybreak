// Starts the update server for local testing: `npm run dev` (in update-server/).
//
// Defaults: port 3000 (or PORT) on all interfaces, the fake OpenRouter on PORT + 1000, data in ./data-dev (git-ignored), admin password "dev".
// Without OPENROUTER_API_KEY a fake OpenRouter (scripts/fake-openrouter.mjs) answers the assistant, so it
// works offline and costs nothing. Set OPENROUTER_API_KEY to talk to the real OpenRouter instead.
// Every default can be overridden by setting the variable before starting.
//
// For the Android emulator: adb -s emulator-5554 reverse tcp:3000 tcp:3000, and build the app with
// -Pdaybreak.updateApiUrl=http://localhost:3000 (debug builds allow cleartext to localhost only).
import { startFakeOpenRouter } from './fake-openrouter.mjs';

const env = process.env;
env.PORT ??= '3000';
env.LISTEN_HOST ??= '0.0.0.0';
env.DATA_DIR ??= './data-dev';
env.ADMIN_TOKEN ??= 'dev';

if (!env.OPENROUTER_API_KEY) {
  // Next to the server's own port (3000 -> 4000), so a second dev server on another port does not clash.
  const port = Number(env.FAKE_OPENROUTER_PORT ?? Number(env.PORT) + 1000);
  startFakeOpenRouter(port);
  env.OPENROUTER_API_URL = `http://127.0.0.1:${port}/api/v1`;
  env.OPENROUTER_API_KEY = 'dev-fake-openrouter';
  console.log('[dev] no OPENROUTER_API_KEY: the assistant answers from the local fake OpenRouter');
} else {
  console.log('[dev] OPENROUTER_API_KEY is set: the assistant uses the real OpenRouter (this costs money)');
}
console.log(`[dev] admin page: http://localhost:${env.PORT}/admin (any user name, password "${env.ADMIN_TOKEN}")`);

await import('../src/index.js');
