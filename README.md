# KITTY AI · Firebase Edition · 0.6.0

An Android companion made by **Virat with the help of Kitty Corp**. A polished native chat app with Google login, streamed model replies, personal memory and an owner-only console.

**Start here: [complete beginner setup guide](docs/START_HERE_V06.md).** The downloadable preview is unconfigured. A working installation requires your Firebase project, billing-enabled Functions, configured signed APK and provider key. Your laptop can be switched off after deployment.

## Current architecture

Android APK → Firebase Auth token → Firebase `kittyApi` HTTPS Function → Groq / Gemini / optional OpenAI → streamed reply. Cloud Firestore stores per-user history/memory and admin settings. Provider keys are AES-GCM encrypted; the vault master key stays in Secret Manager. No provider key is bundled in the APK.

Only verified Google account **viratanand1221@gmail.com**, with its UID pinned by the project owner, can edit provider keys, models, core identity/character, service pause and notices. Regular users sign in and chat. Server authorization is authoritative; no phone email/role extra grants access.

The core prompt shapes replies before generation. This is not model fine-tuning. Personal memory is separate from core settings. Training exports require current consent and a reviewed useful/corrected reply.

## Included

- Premium native Android interface and consistent KITTY icon/splash.
- Firebase Google login and account-isolated local/cloud history.
- Groq streaming, Gemini streaming/fallback and optional OpenAI chat.
- Admin-only creator and character settings; deterministic identity replies.
- Optional Gemini reply audio with Android TTS fallback; typed input.
- Encrypted provider keys, bounded context, request retries and usage limits.
- In-app notices and official signed release update checks.
- Firebase Auth/Firestore emulator integration tests, Android tests and APK permission audit.

## Setup and boundaries

No always-on PC, VM, Docker, Turso, USB or local model is used by version 0.6. Old `server`, `cloud`, `deploy`, Python tests and older guides are historical reference; do not follow them for this edition. The new backend is entirely in `functions/`, with `firebase.json`, `firestore.rules` and `firestore.indexes.json` at the root.

No microphone, accessibility, contacts, calls, installer or foreground-service permission is requested. This version does not listen in the background, control other apps, place calls, browse the live web or send background push notifications. API-generated knowledge alone is not live internet access. Scanner acceptance is not guaranteed.

Firebase Functions requires Blaze billing. Providers/cloud resources may charge; budget alerts are not hard spending caps. Default application limits reduce usage, not all costs. Firebase project owners can administer stored data; chat is not end-to-end encrypted.

## Development

```sh
npm ci --prefix functions
npm test --prefix functions
npm run check --prefix functions
# With Firebase CLI and Java 21 installed (emulators only; no paid deployment):
firebase emulators:exec --project demo-kitty-ci --only firestore,auth "node --test functions/test/emulator.integration.cjs"
```

Android: Java 17, Gradle wrapper, compile SDK 36, minimum Android 10/API 29. Add the public Firebase Android config at `android/app/google-services.json`; set `KITTY_FIREBASE_REGION` if changing from `asia-south1`. `./gradlew -p android testDebugUnitTest lintDebug assembleDebug` builds a preview. Production APK updates must retain the owner signing key.

See [.github/workflows/verify.yml](.github/workflows/verify.yml) for checks and [.github/workflows/release.yml](.github/workflows/release.yml) for signed releases. Never commit model keys, passwords, keystores or vault-key files.
