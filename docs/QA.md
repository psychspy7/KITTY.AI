# Version 0.4 verification

The automated hosted-account tests use controlled Google/provider fixtures and
verify nonce consumption, admin denial, masked/encrypted credentials, stable Google
subjects, logout revocation, per-account memory and provider wire formats. They do
not contact a real Google account, Groq key, Gemini key or Drive account.

Before presentation, use the real deployed HTTPS gateway and final signed APK:

1. Register the APK certificate with Google; sign in as admin and as a second user.
2. Confirm the second user cannot see provider controls, admin notices publisher,
   the admin's history or memory. Confirm sign-out stops voice and separates archives.
3. Test a real Groq reply, Stop, a second request, quota/key errors and network loss.
4. Test Gemini voice, local fallback, rapid Stop/voice toggles and media audio focus.
5. Approve Drive only as admin; opt a test user into backup and verify its JSON file.
   Opt out, wait for sync and verify deletion. Do not claim full archival backup:
   this implementation snapshots the last 500 turns and up to 50 explicit memories.
6. Publish a notice, verify user Settings and allowed notifications. Background
   scheduled delivery may be delayed; it is not FCM instant push.
7. On the Vivo, test actual wake audio with YouTube open, follow-up commands within
   ten seconds, several ambiguous video results, screen lock and OEM battery limits.
8. Validate update installation with the same retained certificate and a newer
   version code. A debug artifact cannot establish production update compatibility.

# Verification and personal testing

For the exact supplied APK, checks, CI links and checksum, see [the 0.2 verification record](validation/RELEASE_0_2.md).

The Python suite tests pairing, malformed requests, concurrent retries, memory operations, retrieval replacement, context budgeting, session separation, feedback export, weather failures, streaming/cancellation, archived phone events, upgrade backups and model/API failures. Model HTTP responses are simulated in those unit tests; they do not establish the quality or speed of the real Qwen weights.

The Android build workflow compiles Java/resources, tests command routing, wake/command windows and typed Shizuku arguments, and runs Android lint. It builds debug and unsigned release APKs. A successful build proves those checks passed; it does not prove real-device permission, audio, Accessibility, WhatsApp or battery behavior. Follow the GitHub Actions result for the exact commit you install.

The emulator job boots an Android 15/API 35 emulator and installs the same APK artifact. It checks launch, settings, a local battery command, missing speech-model guidance, pairing to the real gateway, a saved memory, token persistence after process restart, rejection of untrusted action extras, model-offline handling, gateway-offline handling, and local commands after disconnection. It also checks persisted phone history and archive sync, text appearing before completion, cancellation reaching the gateway, and creator identity with the laptop disconnected. Finally it downloads the real small Indian English Vosk model, initializes its native recognizer, and exercises microphone start/stop, rapid toggling and bound tap-to-talk. It does not play spoken test audio or measure recognition accuracy. The gateway uses an isolated temporary database and a controlled streaming fixture; no actual language model is loaded in this job. Screenshots, UI trees, the result and logcat are uploaded as `KITTY-emulator-evidence`. This is a smoke test, not certification of Android 10–16 or manufacturer-specific behavior.

On your own disposable emulator, reproduce it with `python tools/emulator_smoke.py --serial emulator-5554 --apk path/to/app-debug.apk`. It requires ADB, an unlocked fresh emulator, and Python 3.11+. The script refuses physical-device serials; follow the manual checks below on your actual phone.

The separate **Real model smoke** workflow tests the default 4B and optional 2B Q4_K_M profiles in separate jobs, with revision/hash verification and the official llama.cpp b11146 Ubuntu CPU runtime with its published checksum. It exercises the real template/tokenizer endpoints and streamed generation through KITTY with a 4096-token context. It uploads the public model lock, reply, first-token and total time, stream chunk count and runtime logs as `KITTY-real-model-evidence-default` and `KITTY-real-model-evidence-fast`; pairing tokens are excluded. Its elapsed time describes that CI machine, not your laptop. Check its run result before claiming compatibility. It does not fine-tune or benchmark a broad evaluation set.

`Verify KITTY` automatically runs on app/server/test/setup-code changes. `Real model smoke` runs when its workflow or CI runner changes. Both can be started manually from GitHub Actions; use that option after changing workflow configuration alone. Documentation changes do not reinstall an emulator or redownload model weights.

## First phone session

1. Install and launch without permissions. Confirm typing, Settings and local battery status work.
2. Pair over USB and run the laptop smoke test. Confirm a model reply appears and is spoken.
3. Grant microphone permission and test five tap-to-talk commands in a quiet room.
4. Import the offline model. Start listening; verify the Android microphone indicator and the persistent stop notification. Stop it and verify the indicator disappears.
5. Say “Hey Kitty” in quiet/background-noise conditions. Record misses and false activations. The current detector is continuous ASR, not a dedicated wake-word model.
6. Test app opening and browser searches. Confirm missing apps report a useful error.
7. Test contacts with a duplicate name and with multiple saved numbers. Confirm a choice is requested.
8. Use a consenting test contact for direct calls and WhatsApp. Confirm the visible recipient and message before the first send. The app only claims the intent/tap was requested; WhatsApp delivery is not independently verified.
9. Test `tap`, `type`, `scroll`, `back`, `home` and `lock`. Check Accessibility label/typing/scroll commands and Shizuku navigation separately; disabling both removes those screen-control backends.
10. Turn off the laptop gateway. Confirm phone commands still work and conversation reports the disconnected brain.
11. Test rotation, backgrounding, screen lock, reopening, permission revocation and reboot. Always-on reliability has not been established.

## Keep useful measurements for the next few months

Record date, phone/OS/app version, exact command, expected result, actual result, repeatability and time to first response. For voice, log whether the transcription or the action was wrong. For battery, compare equal-length periods with listening off and on; do not guess from a single short trial.

Suggested development gates: no wrong-recipient action in the test set; no accidental duplicate send from one command; explicit handling of offline/model failures; stable listening with visible controls; no crash in repeated permission and lifecycle tests. These are acceptance criteria, not results claimed for this release.

## Signing for long-term use

The supplied 0.2 development APK uses a retained private signing key. Keep its private backup out of GitHub. Later updates must use that key and an increasing versionCode. GitHub Actions' raw debug artifacts still use temporary runner keys. The [0.2 update guide](UPDATE_0_2.md) includes a backup-based migration helper for older test APKs; Android's Keystore token must be paired again after a reinstall.

## Current limits

- English command grammar; general natural-language action planning is not implemented.
- YouTube search is implemented; choosing and verifying playback needs a visible result selection.
- WhatsApp draft opening and an explicit visible Send tap are implemented; no delivery receipt integration.
- Offline wake-phrase recognition requires an imported Vosk model and can use noticeable battery.
- No arbitrary root access, default-assistant role, notification-reading, background location, or universal device-administrator access.
- Web searches open the browser; there is no general web-reading/research agent yet. Weather uses Open-Meteo.
- Retrieved document snippets use lexical search, and long contexts may drop older turns/reference snippets to fit.
- No actual fine-tuning run, broad model evaluation, or physical-phone certification has been completed. The real-model smoke result is recorded in [the alpha verification report](validation/ALPHA_0_1.md).
