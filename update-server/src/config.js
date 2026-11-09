/** Reads the server settings from environment variables. Every setting has a default. */
export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT ?? 3000),
    /**
     * The address to listen on. "::" accepts IPv4 and IPv6, which matters because a health check that
     * says "localhost" often resolves to the IPv6 address first. The variable is not called HOST
     * because hosting platforms such as Coolify set HOST themselves, to something that is not meant
     * for this.
     */
    host: env.LISTEN_HOST ?? '::',
    /** The app's name on the download page. */
    appName: env.APP_NAME ?? 'Daybreak',
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
    /**
     * Which release asset is the Windows setup program. The desktop workflow names it
     * Daybreak-1.2.3-setup.exe (Inno Setup, desktop/packaging/windows); a release without one is
     * served without it. Releases before the one-click setup carried an .msi, which is not offered.
     */
    setupPattern: new RegExp(env.SETUP_ASSET_PATTERN ?? '^Daybreak-\\d+\\.\\d+\\.\\d+-setup\\.exe$', 'i'),
    /** How many APK versions to keep on disk. */
    keepApks: Number(env.KEEP_APKS ?? 3),
    /**
     * Optional. The verification token from Ko-fi (Settings > API > Webhooks). With it the server
     * accepts Ko-fi's payment notifications on POST /v1/kofi; without it that route does not exist.
     */
    kofiToken: env.KOFI_VERIFICATION_TOKEN || undefined,
    /** The smallest payment that unlocks "Hide this popup", in whatever currency Ko-fi reports. */
    supportMinAmount: Number(env.SUPPORT_MIN_AMOUNT ?? 1),
    /**
     * Optional. The password for the admin page (/admin): minimum app version and messages for the app.
     * Without it the admin page does not exist. Any user name works with it; use a long random value.
     */
    adminToken: env.ADMIN_TOKEN || undefined,
    /**
     * Optional. The OpenRouter API key for the in-app assistant (POST /v1/assistant). Without it the
     * assistant is off and the app hides it. The key never leaves this server. Models, provider routing
     * and limits are set on the admin page.
     */
    openRouterApiKey: env.OPENROUTER_API_KEY || undefined,
    /** The OpenRouter API address. Change it to point tests at a fake. */
    openRouterUrl: (env.OPENROUTER_API_URL ?? 'https://openrouter.ai/api/v1').replace(/\/+$/, ''),
  };
}
