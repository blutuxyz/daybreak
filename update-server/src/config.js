/** Reads the server settings from environment variables. Every setting has a default. */
export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT ?? 3000),
    host: env.HOST ?? '0.0.0.0',
    /** The GitHub repository whose releases are served, as "owner/name". */
    repo: env.GITHUB_REPO ?? 'blutuxyz/daybreak',
    /** The GitHub API address. Change it for GitHub Enterprise, or to point tests at a fake. */
    githubApiUrl: (env.GITHUB_API_URL ?? 'https://api.github.com').replace(/\/+$/, ''),
    /** Optional. Lifts the GitHub rate limit and lets the server read a private repository. */
    githubToken: env.GITHUB_TOKEN || undefined,
    /** Where downloaded APKs are kept. Mount a volume here so they survive redeploys. */
    dataDir: env.DATA_DIR ?? './data',
    /** How long the release list is reused before GitHub is asked again. */
    cacheTtlMs: Number(env.CACHE_TTL_SECONDS ?? 300) * 1000,
    /** Optional secret for POST /v1/refresh, so CI can announce a new release at once. */
    refreshToken: env.REFRESH_TOKEN || undefined,
    /** Optional. The public base URL used in links, when it cannot be read from the request. */
    publicUrl: env.PUBLIC_URL ? env.PUBLIC_URL.replace(/\/+$/, '') : undefined,
    /** Which release asset is the APK. */
    apkPattern: new RegExp(env.APK_ASSET_PATTERN ?? '\\.apk$', 'i'),
    /** How many APK versions to keep on disk. */
    keepApks: Number(env.KEEP_APKS ?? 3),
  };
}
