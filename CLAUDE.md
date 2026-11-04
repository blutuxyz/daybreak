# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Windows with Git Bash, Gradle wrapper: `./gradlew.bat` (PowerShell: `.\gradlew.bat`). Requires JDK 17 and the Android SDK for platform 37 (compileSdk 37, minSdk 26).

- Build debug APK: `./gradlew.bat assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Install on the running emulator/device: `./gradlew.bat :app:installDebug`
- All unit tests: `./gradlew.bat testDebugUnitTest`
- Unit tests of one module: `./gradlew.bat :untis:testDebugUnitTest` or `:app:testDebugUnitTest`
- Single test class: `./gradlew.bat :untis:testDebugUnitTest --tests "com.bdersolutions.daybreak.untis.TotpTest"`
- Single method: append `.methodName` to the `--tests` class name (same pattern).
- Lint: `./gradlew.bat :app:lintDebug` currently **fails** with 22 errors and 67 warnings (report: `app/build/reports/lint-results-debug.html`). Do not pass `--offline` to lint: the lint tool artifact is not cached and the build breaks.
- Other Gradle runs work with `--offline` once dependencies are cached.

There are no instrumented (`androidTest`) tests, although the test dependencies are declared.

Debugging a device: debug builds allow `adb shell run-as com.bdersolutions.daybreak cat files/raw_api_responses.json` (raw WebUntis responses) and `cat files/timetable_cache.json`. The same raw log is shown in Settings → Debug log, which is hidden until the theme option "System" is tapped ten times.

## Architecture

Two Gradle modules. `:untis` is an Android library holding the WebUntis client; `:app` is the Compose application. The `:app` module never sees JSON-RPC payloads, URLs, or TOTP.

**`:untis`** — `UntisApi` is the facade. `JsonRpcClient` posts to the 2017 mobile JSON-RPC endpoints (`getTimetable2017`, `getStudentAbsences2017`, `getExams2017`, …). Login uses a TOTP secret from the Untis QR code, or a password once to fetch the mobile secret (`loginWithPassword`). Parsers (`TimetableParser`, `SchoolDataParser`, `ExamParser`) are deliberately tolerant: they accept several field aliases, skip malformed entries, and produce normalized models (`UntisLesson`, `UntisAbsence`, `UntisExam`, `UntisDirectory`). `RawResponseSink` is how responses reach the debug log. Passwords and the mobile secret are never recorded (`recordRaw = false`).

**`:app` wiring** — `DaybreakApplication` owns `AppContainer`, a hand-written service locator that builds the repositories, stores, and schedulers. `MainActivity` applies the stored language through `AppCompatDelegate` and calls `DaybreakApp`, which wraps everything in `DaybreakTheme`. `DaybreakRoot` (in `ui/DaybreakRoot.kt`) switches between login and `LoggedInContent` by session. The logged-in area has three bottom tabs (Timetable, Absences, Settings). The Settings tab is a list of rows that each open their own screen. The screens are the `SettingsPage` enum (`ui/settings/SettingsPages.kt`: appearance, timetable, lesson colours, language, sync, notifications, about); `LoggedInContent` keeps the open one in a single saveable state, draws it over the tabs with a slide, and handles back with one `BackHandler`. The debug screen is still its own boolean flag. There is no navigation library.

**Own timetable data** — `TimetableRepository` is the single source of truth for the student's own timetable. It keeps a `StateFlow` cache, persisted by `TimetableCacheStore` as `files/timetable_cache.json` (atomic write, `version` field, eight weeks at most). All fetches go through one `Mutex` in `fetchWeeks`. A week counts as fresh for 10 minutes. The same call fetches exams for the whole cached window; an exam failure is isolated and cannot hide the timetable. Subject names are merged from each fetched timetable's master data (`subjectNamesById`) and cached, so exams can be matched to lessons by subject.

**Class, teacher, and room timetables** are not cached in the repository. They are loaded on demand into `TimetableViewModel.elementWeeks`, and `displayCache` decides which cache the screen shows.

**Credentials** — `SecretVault` encrypts the mobile key with AES/GCM under a key in the Android Keystore; the ciphertext lives in the `daybreak_secure` preferences. `SessionStore.isLoggedIn()` requires both the session and a stored secret. Logout clears the key, session, cache, and raw log.

**Sync** — `SyncScheduler` schedules `SyncWorker` (WorkManager) at the interval from `SettingsRepository.SYNC_INTERVALS`. The worker refreshes this week and the next, uses `LessonChangeDetector` to diff them, notifies through `LessonNotificationManager`, and updates the Glance widget through `WidgetUpdater`. Network errors become `Result.retry()`.

**Updates** — `update-server/` is a small Fastify service (own `package.json`, tests with `npm test`, deployed on Coolify with base directory `/update-server`). It mirrors the APK and changelog of the latest GitHub release; the app never talks to GitHub. `.github/workflows/release.yml` builds and signs the APK when a release is published. In the app, `data/update/` holds the client: `UpdateManager` (check, download with SHA-256 check, install through `PackageInstaller` via `ApkInstaller`), `UpdateCheckWorker` (periodic check and notification), and `UpdateStore`. The UI is the `SettingsPage.UPDATES` screen and `WhatsNewSheet`. The server address and version come from Gradle properties `daybreak.updateApiUrl`, `daybreak.versionName`, and `daybreak.versionCode`; without `updateApiUrl` the Updates row is hidden. The version code is `major*10000 + minor*100 + patch` in both the workflow and the server, so minor and patch must stay below 100.

**Settings and theme** — `SettingsRepository` stores plain values in `daybreak_settings` and exposes one `StateFlow<AppSettings>`. Complex values (lesson colours) are stored as JSON with Gson. `ui/theme/Theme.kt` builds the colour scheme (Material You or one of five accent palettes), and provides composition locals: `LocalSubjectColorMode`, `LocalLessonColors`, and the font scale from `TextScale`.

**Lesson rendering** — `ui/timetable/TimetableScreen.kt` is the largest file. It holds the day and week pagers, the overlap layout (`layoutTimeline`), the day and week grids, and `LessonDetailsSheet`. Block colours come from `rememberLessonLook` and `rememberSubjectTone` (`ui/components/DaybreakComponents.kt`), with the OKLCH maths in `SubjectPalette.kt`. `examFor` matches an exam to a lesson by date, overlapping time, and subject when the exam has one.

## Conventions and gotchas

- Material 3 is pinned to `1.5.0-alpha17` (`gradle/libs.versions.toml`). Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, and `ExperimentalMaterial3Api` for others. `MaterialTheme.motionScheme` specs are springs. For fixed-duration motion, use `tween` with an explicit easing.
- Source files in the working copy have CRLF line endings. Git stores them with LF, so warnings about line endings are expected.
- After reinstalling the debug build, the app language may revert to German. Check Settings → Language.
- Lint is not clean (see Commands). Fix new lint errors rather than adding to the baseline.
- `README.md` describes the WebUntis login options (QR, manual, password) and the privacy model; it is the reference for those.
