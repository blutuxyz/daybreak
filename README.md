# Daybreak

Daybreak is a focused, unofficial timetable app for **WebUntis**. It uses the same
mobile login mechanism as the official Untis Mobile app (the 2017 JSON-RPC mobile
API) and does **not** require any API key or developer account.

- German is the default UI language; English can be selected in Settings or on the
  login screen.
- Day and week views with horizontal swipe navigation, a live “now” highlight and
  clear cancellation, room-change and substitution badges.
- Offline-first cache, periodic WorkManager sync, change notifications and a
  Glance home-screen widget that follows your wallpaper colours on Android 12+.
- The WebUntis key is encrypted with a key that lives in the Android Keystore and
  is never written to logs or backups.
- Raw API responses are available in a debug screen for troubleshooting. Request
  bodies, passwords and keys are never recorded.

Daybreak is not affiliated with Untis GmbH. WebUntis is a registered trademark of
Untis GmbH. The unofficial mobile API can change at any time.

## Build

Requirements: Windows, JDK 17, Android SDK Platform 37. The project uses the
included Gradle wrapper and Android Studio-compatible Kotlin DSL.

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

Run the unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Logging in with the Untis Mobile QR data

1. Open WebUntis in a browser and sign in.
2. Go to **Profil → Freigaben → Zugriff über Untis Mobile** (Profile → Shares →
   Access via Untis Mobile).
3. Scan the displayed QR code from Daybreak's login screen.
4. Alternatively, paste the complete QR content into the **QR-Code** tab. It looks
   like this:

   ```text
   untis://setschool?url=https%3A%2F%2Fschool.webuntis.com&school=school&user=me&key=ABCDEFGHIJKLMNOP
   ```

Daybreak uses the `url`, `school`, `user` and `key` values. The key is a TOTP secret;
Daybreak generates a fresh six-digit code for every request, just like the mobile app.

### Manual entry

Use the **Manuell** tab if you already know:

- Server, e.g. `school.webuntis.com`
- School short name
- Username
- Untis Mobile key

### Password fallback

The **Passwort** tab asks WebUntis once for the mobile access key using your
username and password (plus a verification code if the school requires two-factor
authentication). Daybreak stores only the derived key, never your password.

## Project layout

- `:untis` — isolated WebUntis protocol module (HTTP, TOTP, QR parsing, tolerant
  timetable parsing and normalized models).
- `:app` — Compose UI, secure session storage, offline cache, WorkManager sync,
  notifications, settings and the Glance widget.

## Privacy and security notes

- Only HTTPS is used.
- Secrets are stored with AES/GCM in the Android Keystore; local backup is disabled
  for credentials and cached data.
- The app is read-only and only stores timetable data on the device.
- Logging out removes the key, session, cache and raw debug responses.

## Licence and disclaimer

This app is a private, unofficial client. Use it with your own WebUntis account and
in accordance with your school's acceptable-use rules. The WebUntis mobile interface
is not a public, supported integration API and may change without notice.
