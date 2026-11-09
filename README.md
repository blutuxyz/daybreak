<div align="center">

# Daybreak

**A fast, good-looking, unofficial WebUntis timetable app for Android and Windows, with an AI assistant that knows your school week.**

[![Stars](https://img.shields.io/github/stars/blutuxyz/daybreak?style=for-the-badge&logo=github&color=f5c542)](https://github.com/blutuxyz/daybreak/stargazers)
[![Forks](https://img.shields.io/github/forks/blutuxyz/daybreak?style=for-the-badge&logo=github&color=8ab4f8)](https://github.com/blutuxyz/daybreak/network/members)
[![Issues](https://img.shields.io/github/issues/blutuxyz/daybreak?style=for-the-badge&logo=github&color=f28b82)](https://github.com/blutuxyz/daybreak/issues)
[![Pull requests](https://img.shields.io/github/issues-pr/blutuxyz/daybreak?style=for-the-badge&logo=github&color=c58af9)](https://github.com/blutuxyz/daybreak/pulls)

[![Latest release](https://img.shields.io/github/v/release/blutuxyz/daybreak?style=for-the-badge&label=release&color=81c995)](https://github.com/blutuxyz/daybreak/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/blutuxyz/daybreak/total?style=for-the-badge&color=81c995)](https://github.com/blutuxyz/daybreak/releases)
[![Last commit](https://img.shields.io/github/last-commit/blutuxyz/daybreak?style=for-the-badge&color=fdd663)](https://github.com/blutuxyz/daybreak/commits)
[![Repo size](https://img.shields.io/github/repo-size/blutuxyz/daybreak?style=for-the-badge&color=aecbfa)](https://github.com/blutuxyz/daybreak)

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Windows](https://img.shields.io/badge/Windows-10%2F11-0078D6?style=flat-square&logo=windows&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Languages](https://img.shields.io/badge/UI-Deutsch%20%7C%20English-ffb648?style=flat-square)
![No tracking](https://img.shields.io/badge/tracking-none-success?style=flat-square)

</div>

<p align="center">
  <img src="docs/screenshots/tutorial-welcome.png" width="200" alt="Welcome screen of the introduction tour">
  <img src="docs/screenshots/day-view.png" width="200" alt="Day view with lessons, swiping between days">
  <img src="docs/screenshots/lesson-details.png" width="200" alt="Lesson details with a substitute teacher and a room change">
  <img src="docs/screenshots/assistant.png" width="200" alt="The AI assistant with suggested questions">
</p>

> The timetable screenshots come from the app's built-in demo (the introduction tour), so they show sample
> data, not a real student's timetable. They were taken on an emulator from a debug build.

---

## Contents

- [Features](#features)
- [AI assistant](#ai-assistant)
- [Introduction tour](#introduction-tour)
- [Screenshots](#screenshots)
- [Signing in](#signing-in)
- [Build](#build)
- [Project layout](#project-layout)
- [Privacy and security](#privacy-and-security)
- [Licence and disclaimer](#licence-and-disclaimer)

## Features

- **Day and week views**: swipe between days, pinch to zoom, a live "now and next" card, and clear badges for
  cancellations, room changes and substitutions. Exams are framed in amber; the part of a lesson you missed is greyed out.
- **Holidays** show as a block you can open. Rooms, teachers and classes in a lesson link to their own timetable.
- **AI assistant** (optional, served through the developer's update server): ask about your week in your own words,
  attach photos or PDFs, let it find what to study for an exam, or have it draft a message to a teacher that only
  you can send. [More below](#ai-assistant).
- **Introduction tour** in a demo environment, in German and English, shown once to everyone and replayable from More.
- **Homework, exams and school notices** on their own pages, plus your WebUntis **messages** (inbox, sent, drafts)
  with attachments.
- **Absences** with totals that only count time when lessons actually took place, shown as hours, minutes or
  50-minute school lessons.
- **Appearance**: system, light or dark theme, **AMOLED black**, Material You colours or one of five accent
  palettes, per-subject colours, lively or calm motion and three text sizes.
- **Offline first**: an eight-week cache, periodic sync, change notifications, a morning summary and evening
  reminders, and a Glance home-screen widget.
- **Haptics** designed per effect, with presets and fine control.
- **Windows desktop app** (Compose Multiplatform) with a one-click installer.
- German is the default UI language; English can be picked in Settings or on the login screen.

## AI assistant

<p align="center">
  <img src="docs/screenshots/assistant.png" width="220" alt="AI assistant welcome screen">
  <img src="docs/screenshots/assistant-tour.png" width="220" alt="The assistant answering what to study for an exam">
</p>

Tap the ✨ in the timetable bar (or More → AI assistant) and ask, for example:

- *"What do I have tomorrow?"*, *"Is anything cancelled this week?"*
- *"When is my next exam?"*, *"What do I need to study for it?"* (it reads the teachers' lesson texts and info notes)
- *"How much have I been absent?"*
- *"Write to my maths teacher that I'm ill today."* The assistant drafts the message; **it is sent only when you
  tap Send on the draft card**.

You can attach **photos** (scaled down to 1600 px JPEG on the phone, which also drops location data) and **PDF or
text files** up to 5 MB.

How it works:

- The app talks only to the developer's **update server**, which forwards to **[OpenRouter](https://openrouter.ai)**.
  The API key never ships in the APK.
- **Tools run on the phone**: `get_lessons`, `get_exams`, `get_lesson_notes`, `get_homework`, `get_absences`,
  `list_teachers`, `draft_message`. The server passes the model's tool calls to the app, the app answers from your
  WebUntis data, and the chat continues.
- The admin page of the update server sets the **models**, whether users see a **model switcher** (if hidden, users
  get the default or a random model), **provider routing** (cheapest, fastest, lowest latency, best availability),
  **daily budgets per user** (10 ¢ free / 20 ¢ for supporters by default), **per-IP rate limits** (including update
  checks) and a **request log** with a page per request.
- Two switches: a client-side one (hides the assistant in the app) and a server-side one (requests fail with
  "Upstream provider fail"). A wrongly configured model shows the same message.

Server setup and every option: [`update-server/README.md`](update-server/README.md#ai-assistant-openrouter).

## Introduction tour

Every user goes through a short tour once, including people who were already signed in before it existed. It runs in
a **demo environment** inside a phone frame, with sample lessons, absences and a scripted assistant chat, so it works
before sign-in and never shows real data.

- A spotlight glides from one part of the screen to the next; days swipe on their own, the week view pinches, and the
  assistant types, so the gestures are shown, not just described.
- German and English, light, dark and AMOLED.
- Replay any time under **More → Tutorial**. A reworked tour is shown again to everyone by raising
  `TutorialProgress.VERSION`.

## Screenshots

| Day view | Lesson details | Week view | Absences |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/day-view.png" width="190" alt="Day view"> | <img src="docs/screenshots/lesson-details.png" width="190" alt="Lesson details"> | <img src="docs/screenshots/week-view.png" width="190" alt="Week view while zooming"> | <img src="docs/screenshots/absences.png" width="190" alt="Absences with totals"> |

| Light, German | Light, German | AMOLED black | Tour |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/day-view-light-de.png" width="190" alt="Day view in light mode, German"> | <img src="docs/screenshots/lesson-details-light-de.png" width="190" alt="Lesson details in light mode, German"> | <img src="docs/screenshots/amoled.png" width="190" alt="Appearance settings with AMOLED black"> | <img src="docs/screenshots/tutorial-welcome.png" width="190" alt="Welcome step of the tour"> |

## Signing in

### With the Untis Mobile QR code

QR sign-in is the default. Tap **Andere Anmeldung** to show the manual and password methods.

1. Open WebUntis in a browser and sign in.
2. Go to **Profil → Freigaben → Zugriff über Untis Mobile** (Profile → Shares → Access via Untis Mobile).
3. Scan the displayed QR code from Daybreak's login screen.
4. Or paste the complete QR content into the **QR-Code** tab. It looks like this:

   ```text
   untis://setschool?url=https%3A%2F%2Fschool.webuntis.com&school=school&user=me&key=ABCDEFGHIJKLMNOP
   ```

Daybreak uses the `url`, `school`, `user` and `key` values. The key is a TOTP secret; Daybreak generates a fresh
six-digit code for every request, just like the mobile app.

### Manual entry

Use **Andere Anmeldung → Manuell** if you already know the server (e.g. `school.webuntis.com`), the school's short
name, your username and the Untis Mobile key.

### Password fallback

**Andere Anmeldung → Passwort** asks WebUntis once for the mobile access key using your username and password (plus a
verification code if your school uses two-factor authentication). Daybreak stores only the derived key, never your
password.

## Build

Requirements: Windows, JDK 17, Android SDK Platform 37. The project uses the included Gradle wrapper.

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

The update server (Node.js 20+, Fastify) has its own tests:

```powershell
cd update-server; npm test
```

To try the assistant against a local update server on the emulator, run the server with `OPENROUTER_API_KEY` set,
forward the port with `adb reverse tcp:3000 tcp:3000`, and build with
`-Pdaybreak.updateApiUrl=http://localhost:3000`. Debug builds allow cleartext traffic to `localhost` and `10.0.2.2`
only.

## Project layout

- `:untis`: the WebUntis protocol module (JSON-RPC, REST, TOTP, QR parsing, tolerant parsers, normalized models).
- `:shared`: logic shared by the Android and desktop apps (repositories, cache, schedule maths, the assistant's
  protocol, context builder and tools), all unit-tested.
- `:app`: the Compose app (UI, secure session storage, WorkManager jobs, notifications, the widget, the assistant chat
  and the introduction tour).
- `:desktop`: the Windows app (Compose Multiplatform) and its Inno Setup installer.
- `update-server/`: the Fastify service for updates, the minimum version, messages, Ko-fi payments and the assistant.

## Privacy and security

- HTTPS only. Secrets are encrypted with AES/GCM under a key in the Android Keystore; credentials and cached data are
  excluded from backups.
- No account, no ads, no analytics, no crash reporting.
- Without the assistant, school data goes only to your school's WebUntis server.
- **With the assistant**, your question, attachments, a timetable summary and the tools' results go through the
  developer's update server to OpenRouter and the model's provider. No name, password or access key is sent. The
  developer can see a request log for at most 30 days (attachments only by name and size). Messages to teachers are
  sent only after you confirm them.
- The privacy policy and terms of use are part of the app (German and English) and must be accepted once; the app
  asks again only when the text changes (`LegalTerms.VERSION`). They stay readable under More → Privacy and terms.
- Signing out removes the key, the session, the cache and the debug log.

## Licence and disclaimer

Daybreak is a private, unofficial client and is not affiliated with Untis GmbH. WebUntis is a registered trademark of
Untis GmbH. Use it with your own WebUntis account and within your school's rules. The WebUntis mobile interface is
not a public, supported API and may change without notice. AI answers can be wrong; check what matters in your
timetable.
