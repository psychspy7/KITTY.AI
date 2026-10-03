# KITTY AI v0.5.0 preview validation

This APK is an **unconfigured preview**, not a deployed cloud release. It shows **Service setup pending** until the owner supplies Firebase Android configuration and a public HTTPS backend. Follow [START_HERE_V05.md](../START_HERE_V05.md) before testing live sign-in or presenting the app.

## Build identity

- Source commit: `9eaa617f293382de3e8bc33a7973d3eb2bddf5d3`.
- [Successful CI run](https://github.com/psychspy7/KITTY.AI/actions/runs/37123588818).
- Filename: `KITTY-AI-v0.5.0-preview.apk`.
- Version: `0.5.0` / code `50`; package `com.kitty.ai`.
- Size: 15,340,204 bytes.
- SHA-256: `a0892a24fce1c1559cae8faad59d8e347a9c5c14822f4ba07e2b2534427da226`.
- Debug signing certificate SHA-256: `6a79d3a92eb41c9da8187c7e4e8b2e8b62a51d563117e6a3a1beca215a313329`.
- Minimum Android API 29; target API 35.

## Checks completed

- 75 backend tests passed, including Firebase verification contracts, role enforcement, account isolation and admin configuration access.
- Four Android unit tests and one native layout instrumentation test passed.
- Android debug and unsigned release builds succeeded. Lint reported zero errors and seven warnings.
- Packaged APK permission audit passed.
- Android API 35 emulator checks passed for pending-setup onboarding, restart, large text, ignored legacy action extras, absence of legacy services and crash-log checks.
- Production chat renderer screenshots were inspected using **test-only local display fixtures**. These do not demonstrate authenticated chat or provider replies.

## APK permissions

Only the following permissions are present:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `com.google.android.providers.gsf.permission.READ_GSERVICES` (normal Google SDK configuration permission)
- `com.kitty.ai.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (app-owned signature permission)

No microphone, contacts, calls, notification, foreground-service, installer, accessibility or Shizuku permissions remain. Background wake-word listening and phone control have been removed. Chat input is typed; optional speech playback uses Gemini or device TTS. Scanner acceptance cannot be guaranteed.

## What remains unverified

Live Google sign-in, production Firebase configuration, deployed HTTPS hosting, real Groq inference, Gemini audio playback and optional Google Drive authorization require owner-provided configuration and have not been tested end to end. Test doubles validate backend Firebase integration contracts; Firebase Admin SDK performs production token signature, audience and revocation checks.

The core creator attribution is **Virat with the help of Kitty Corp**. The admin can edit the server personality prompt; normal users cannot edit stored core settings. This is prompt configuration, not fine-tuning, and does not bypass provider policies. Chat storage does not automatically train model weights.

Notices appear in the in-app Inbox; background push notifications are not implemented. Update checks open the official release page in a browser rather than silently installing APKs.

## Signing and updates

This preview uses a debug certificate different from the previous v0.4 build. It cannot install as an in-place update over that APK. Do not uninstall an existing app merely to bypass a signing error and lose its local history. Production updates must use the original owner signing key, or be delivered as a deliberately separate installation with a planned data migration. No retained owner signing key was available for this build.
