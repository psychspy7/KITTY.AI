# KITTY AI v0.6.0 Firebase Edition — preview APK validation

Validated on 2026-10-03. This is an **unconfigured, debug-signed preview**, not a deployed production release. Google login and live AI replies require the owner's Firebase configuration and deployed backend.

## Exact build

| Item | Value |
| --- | --- |
| Source commit | `6cd58f5a00d759015e7f42faf89ddade67e36ec0` |
| Verification run | [GitHub Actions 37143506421](https://github.com/psychspy7/KITTY.AI/actions/runs/37143506421) |
| Package | `com.kitty.ai` |
| Version | `0.6.0`, version code `60` |
| Minimum / target Android API | 29 / 35 |
| APK filename | `KITTY-AI-v0.6.0-Firebase-preview.apk` |
| APK size | 15,340,204 bytes |
| APK SHA-256 | `1a52b0f2cb04e46a41905a846921bcd7c37ddb94a4e954d46c885a3c63bfa1cd` |
| Certificate SHA-256 | `efcc174ffaaab6e1c2c38cf010b52535dde8be97d27891a8005c09fd7c55a0a4` |
| Certificate SHA-1 | `2529e52c5c6f9d293d8285028c7adb1c3e9aab66` |
| Signer | Android Debug |

The distributed APK was extracted from this run's debug APK artifact. Its manifest and DEX were inspected to confirm version 0.6.0, the Firebase API configuration field and the account-scoped SQLite schema. The unsigned release build also compiled successfully; it is not an installable signed release.

## Checks passed

- 24 backend unit/contract tests: authorization, administrator boundaries, encrypted provider-key storage, core identity, request lifecycle, streaming and fallback behavior.
- One Firebase Auth/Firestore integration test suite using real emulator SDK tokens, Firestore transactions and the exported HTTP handler. It checks unauthenticated denial, user/admin separation, hidden secrets and deterministic chat streaming.
- Four Android unit tests.
- Two Android instrumentation tests on an API 35 emulator: premium home/conversation/composer layout and real SQLite migration with duplicate identifiers isolated by account.
- Emulator startup, explicit missing-configuration state, process restart, large-font startup and rejection of obsolete action extras. No KITTY crash was recorded.
- Android lint: zero errors, eight warnings.
- Audit of the merged manifest and the actual built APK permissions.

Authenticated visual fixtures use the production renderer with test-only data injection. They demonstrate layout, not successful production Google login or real provider responses.

## Exact packaged permissions

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
com.google.android.providers.gsf.permission.READ_GSERVICES
com.kitty.ai.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

READ_GSERVICES is a normal SDK configuration permission. The final entry is the app's own signature-protected receiver permission. The APK has no microphone, contacts, phone, notification, accessibility, foreground-service or installer permission. There is no always-listening wake-word or background phone-control service. Notices appear in the app's Inbox; update checks open the official release page.

## What still needs real-project verification

No production Firebase project was supplied. The preview intentionally displays **Service setup pending**. Production Google OAuth, certificate registration, deployment, runtime IAM, actual Groq/Gemini/OpenAI requests, reply-audio playback and two-user production isolation have not been tested against the owner's accounts. Emulator tests do not establish production IAM correctness.

Follow [START_HERE_V06.md](../START_HERE_V06.md) to configure Firebase, deploy the backend, create a configured signed test build and complete the real-device acceptance checklist before publishing. Cloud Functions deployment requires the owner's billing-enabled Firebase project; this work did not activate billing or deploy to an account.

The debug signer differs from earlier APKs. Do not uninstall an existing app and erase its local chats to bypass a signer mismatch. Production updates must retain the owner's signing key. The guide explains signing, test artifacts and release publication.

The core prompt sets Kitty's identity and style; it is not model fine-tuning. Saved history does not automatically train model weights. Training export is limited to reviewed responses from users with current consent.

This report is evidence of the checks above, not a guarantee of scanner certification or flawless behavior on every device.
