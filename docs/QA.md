# KITTY Firebase verification

The Verify KITTY workflow checks:

- Backend contracts for Google identity, owner UID/email, encrypted keys, account isolation, core identity, SSE normalization, fallback, idempotent replies, cancellation, quotas and consent-based export.
- Actual Firebase Auth/Firestore emulator integration, denied direct Firestore access, data transactions, isolated history and disabled-user rejection.
- The exported HTTPS handler with emulator-issued Google Firebase tokens: health, unsigned denial, owner role, user-admin denial, hidden provider keys and streamed identity reply.
- Android unit tests, lint, debug/unsigned-release/test APK assembly and packaged permission audit.
- Android API 35 emulator onboarding, pending-configuration state, ignored legacy action extras, restart, large font, absence of legacy services and crash checks.
- Native premium chat renderer fixtures and a real SQLite v5-to-v6 migration with duplicate-ID account/feedback isolation.

UI fixtures only exercise rendering. They do not prove successful Google sign-in, model inference or audio playback. Emulator tokens do not validate production Google OAuth certificates or IAM. No test is a scanner/store certification.

Before presenting or sharing a configured production APK, complete setup and test two real Google accounts, admin-only settings, at least one real provider question, personal memory, streaming/Stop, cloud history, optional speech and notices. Retain the owner signing key for future app updates.

See [START_HERE_V06.md](START_HERE_V06.md). Exact APK identity and final CI evidence are recorded in `docs/validation/V06_APK.md` when packaged.
