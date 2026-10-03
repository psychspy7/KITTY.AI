# KITTY AI 0.5 — cloud chat

Made by Virat with the help of Kitty Corp.

**[Beginner setup: Firebase, cloud hosting and signed releases](docs/START_HERE_V05.md)**

KITTY is now an API-powered Android chat app. Users sign in with Google through
Firebase Authentication, then chat without entering API keys or connection details.
The verified owner, `viratanand1221@gmail.com`, configures Groq, optional Gemini
speech, core identity/personality, consented Drive backups and shared Inbox notices.
Provider keys are encrypted on the cloud server and never returned to phones.

The Android app has a navy/ivory/champagne design, a new adaptive cat emblem,
streamed replies, account-separated local history, personal-memory sync, feedback,
optional voice playback and Settings → Check for updates. Updates open an immutable
official GitHub release page in the browser; KITTY does not install APKs itself.

**No laptop pairing, microphone, background listener, accessibility control,
contacts, calling, Shizuku, foreground service or installer permission remains in
the Android app.** It does not control YouTube or other apps. Notices appear in
Inbox when fetched; there are no background push notifications in this release.

## Before it can go live

Create your Firebase project and Google sign-in configuration, deploy the persistent
HTTPS backend, and build using your retained release signing key. Add the public
Firebase Android JSON and cloud URL as GitHub repository variables. The release
workflow checks these before publishing. No live Firebase project, cloud host or
provider key is created by downloading this repository.

Unconfigured verification APKs display “Service setup pending.” They are UI test
builds, not a working public chat service. Debug signing keys may differ between
CI runs; production updates require the same retained release signing key.

## Development

- Android: Java 17, Android Gradle Plugin 8.9.2, compile SDK 36 / target 35,
  Android 10+, Credential Manager, Firebase Auth, OkHttp. No Kotlin/Compose rewrite.
- Cloud gateway: Python 3.11+, Firebase Admin SDK with revoked-token checks, Groq
  SSE, optional Gemini TTS, SQLite on a persistent cloud volume. Docker/Caddy setup
  is in `deploy/`; `python -m server.hosted` refuses local-token fallback.
- Optional Turso remains a conversation archive replica; account records,
  explicit personal memories and the encrypted key vault require the persistent
  cloud volume. It is not a model-inference host.
- Tests: `python -m unittest discover -s tests -v`; Android unit tests, lint,
  built APK permission audit, emulator startup and native layout fixtures in CI.
- Production releases: [release guide](docs/UPDATES_AND_RELEASES.md).

Historical model utilities and old guides remain for reference/testing, but the
v0.5 Android app cannot connect to a laptop. Old Windows launcher scripts are
removed. New installs must follow the v0.5 guide.

Hosted models retain their provider behavior. KITTY’s sassy character is an
admin-controlled prompt, not fine-tuning or a promise of unrestricted compliance.
Only explicitly consented, reviewed examples are exported for a separate training
workflow. Chat archives do not automatically change model weights.
