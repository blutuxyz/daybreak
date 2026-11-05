# Daybreak update server

A small Node.js service the Daybreak app asks for updates. It reads the **latest GitHub release** of
the repository (version, release notes, APK), keeps the APK on disk, and serves all of it to the app.
The app never talks to GitHub: it talks to this server, and this server talks to GitHub.

```
GitHub release  ──►  update-server (Coolify)  ──►  Daybreak app
 APK + notes          caches APK, serves JSON        checks, downloads, installs
```

## What the app gets

| Endpoint | Returns |
| --- | --- |
| `GET /v1/latest` | The newest release: `tag`, `versionName`, `versionCode`, `publishedAt`, `changelog` (the release notes, Markdown) and `apk` (`url`, `size`, `sha256`). `404` while there is no release with an APK. |
| `GET /v1/releases?limit=20` | The release history with every changelog. |
| `GET /v1/apk/:tag` | The APK. Supports `Range`, so an interrupted download resumes. |
| `POST /v1/refresh` | Asks GitHub right now. Needs `Authorization: Bearer <REFRESH_TOKEN>`. Off when `REFRESH_TOKEN` is not set. |
| `GET /` | A download page for people who do not have the app yet: a download button, install steps, the latest release notes, and earlier versions. It is German or English, depending on the browser. Share this address. |
| `GET /download` | Redirects to the newest APK, so one stable link always works. |
| `GET /health` | `{ "ok": true }`, for Coolify's health check. |

The server asks GitHub at most every `CACHE_TTL_SECONDS` (5 minutes), uses the ETag so an unchanged
list costs no rate limit, and keeps serving the last list if GitHub is down. It downloads a new APK
as soon as it sees it, so the first phone to ask does not wait.

## Versions

The release **tag** is the version: `v1.2.3` → `versionName 1.2.3`, `versionCode 10203`
(`major * 10000 + minor * 100 + patch`). Minor and patch must stay below 100. The release workflow and
this server use the same formula, so the number the server reports is the number inside the APK.
Drafts, prereleases and tags that are not `vX.Y.Z` are ignored.

## Deploy on Coolify

1. **New resource → Public repository** (or *Private repository (with GitHub App)* if you keep the
   repository private). Pick `blutuxyz/daybreak`, branch `main`.
2. **Build pack: Dockerfile.** Set **Base directory** to `/update-server`. The Dockerfile location is
   `/Dockerfile`.
3. **Port:** `3000`. **Health check path:** `/health`.
4. **Domain:** for example `https://updates.example.com`. Coolify issues the certificate. The app only
   talks to `https`.
5. **Persistent storage:** add a volume mounted at `/data`, so downloaded APKs survive redeploys.
6. **Environment variables** (see `.env.example`):

   | Variable | Needed | Meaning |
   | --- | --- | --- |
   | `GITHUB_REPO` | yes | `owner/name`, default `blutuxyz/daybreak` |
   | `REFRESH_TOKEN` | recommended | A long random secret. Lets the release workflow announce a release at once. |
   | `GITHUB_TOKEN` | only if the repository is private | A token that can read the repository's releases |
   | `PUBLIC_URL` | rarely | Only if links come out wrong behind your proxy |

7. Deploy. Check `https://updates.example.com/health`, then `https://updates.example.com/v1/latest`
   (`404 no_release` is correct until the first release exists).

Coolify redeploys when you push. To redeploy only when the server changes, set **Watch paths** to
`update-server/**`.

## Make a release

One-time setup:

1. **Create a signing key** and keep it safe. **Every update must be signed with the same key as the
   installed app. If you lose it, nobody can update; they have to uninstall and reinstall.**

   ```bash
   keytool -genkeypair -v -keystore daybreak-release.jks -alias daybreak \
     -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 daybreak-release.jks > daybreak-release.jks.base64   # on Windows Git Bash too
   ```

2. In the GitHub repository, **Settings → Secrets and variables → Actions**:

   | Kind | Name | Value |
   | --- | --- | --- |
   | Secret | `ANDROID_KEYSTORE_BASE64` | the content of `daybreak-release.jks.base64` |
   | Secret | `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
   | Secret | `ANDROID_KEY_ALIAS` | `daybreak` |
   | Secret | `ANDROID_KEY_PASSWORD` | the key password |
   | Secret | `UPDATE_SERVER_REFRESH_TOKEN` | the same value as `REFRESH_TOKEN` on Coolify (optional) |
   | **Variable** | `UPDATE_API_URL` | `https://updates.example.com` (baked into the app) |

3. The APK from the first release replaces your debug build: uninstall the debug build once, install
   the release APK, and from then on the app updates itself.

Every release after that:

1. On GitHub, **Releases → Draft a new release**. Tag `v1.1.0` (create it on `main`). Write the
   **release notes**: they are the changelog the app shows. Publish.
2. `.github/workflows/release.yml` runs the tests, builds the signed APK with the version from the tag
   and attaches it to the release.
3. The server sees the APK (immediately if `REFRESH_TOKEN` is set, otherwise within five minutes), and
   the apps are offered the update.

## Run it locally

```bash
cd update-server
npm install
npm test
GITHUB_REPO=blutuxyz/daybreak npm start     # http://localhost:3000/v1/latest
```

To try it from the emulator, run the server on port 3000 and build the app with
`-Pdaybreak.updateApiUrl=http://10.0.2.2:3000`. Debug builds may use plain HTTP for `10.0.2.2` and
`localhost` only. If the emulator cannot reach `10.0.2.2` (its Wi-Fi network sometimes blocks it), run
`adb reverse tcp:3000 tcp:3000` and use `http://localhost:3000` instead.

An update only installs over an app signed with the same key, with a higher `versionCode`. A debug
build is therefore replaced by a debug-signed APK, never by the release-signed one. Google Play Protect
may also block a sideloaded update; the app then shows a card that says so.

## Paid "Hide this popup" (Ko-fi)

The support popup in the app can be hidden for good only by paying on Ko-fi. The server confirms the
payment:

1. The app shows a random unlock code (`DB-XXXX-XXXX`). The supporter pastes it into the message
   field of their Ko-fi donation.
2. Ko-fi calls `POST /v1/kofi` (a webhook). The server checks the verification token, finds the code
   in the message and records it, but only if the amount is at least `SUPPORT_MIN_AMOUNT`.
3. The app asks `GET /v1/support/status?code=...` and gets `{ "paid": true | false }`.

Set it up: in Ko-fi go to Settings > API > Webhooks, set the webhook URL to
`https://<your server>/v1/kofi`, copy the verification token into `KOFI_VERIFICATION_TOKEN`, and make
sure `DATA_DIR` is on a persistent volume (the payments live in `support-payments.json` there; lose it
and paid supporters have to be unlocked again). Without the token the webhook does not exist and the
app's paid hide cannot succeed.

Only the code, amount, currency, time and Ko-fi transaction id are stored. No name, e-mail address or
message text. The status endpoint allows 30 requests per minute per address.

## Security

- The app checks the SHA-256 of every download. Android itself refuses an update that is not signed
  with the installed app's key, so a compromised server cannot push a different app.
- `POST /v1/refresh` is the only write-like endpoint and needs the secret. Nothing else changes state.
- The container runs as an unprivileged user.
