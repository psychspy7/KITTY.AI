# Verification and personal testing

The Python suite tests pairing, malformed requests, concurrent retries, memory operations, retrieval replacement, context budgeting, session separation, feedback export, weather failures and model/API failures. Model HTTP responses are simulated in those unit tests; they do not establish the quality or speed of the real Qwen weights.

The Android build workflow compiles Java/resources, runs command-routing unit tests, and runs Android lint. A successful build proves those checks passed; it does not prove real-device permission, audio, Accessibility, WhatsApp or battery behavior. Follow the GitHub Actions result for the exact commit you install.

The emulator job boots an Android 15/API 35 emulator and installs the same APK artifact. It checks launch, settings, a local battery command, missing speech-model guidance, pairing to the real gateway, a saved memory, token persistence after process restart, rejection of untrusted action extras, model-offline handling, gateway-offline handling, and local commands after disconnection. The gateway uses an isolated temporary database; no actual language model is loaded. Screenshots, UI trees, the result and logcat are uploaded as `KITTY-emulator-evidence`. This is a smoke test, not certification of Android 10–16 or manufacturer-specific behavior.

On your own disposable emulator, reproduce it with `python tools/emulator_smoke.py --serial emulator-5554 --apk path/to/app-debug.apk`. It requires ADB, an unlocked fresh emulator, and Python 3.11+. The script refuses physical-device serials; follow the manual checks below on your actual phone.

## First phone session

1. Install and launch without permissions. Confirm typing, Settings and local battery status work.
2. Pair over USB and run the laptop smoke test. Confirm a model reply appears and is spoken.
3. Grant microphone permission and test five tap-to-talk commands in a quiet room.
4. Import the offline model. Start listening; verify the Android microphone indicator and the persistent stop notification. Stop it and verify the indicator disappears.
5. Say “Hey Kitty” in quiet/background-noise conditions. Record misses and false activations. The current detector is continuous ASR, not a dedicated wake-word model.
6. Test app opening and browser searches. Confirm missing apps report a useful error.
7. Test contacts with a duplicate name and with multiple saved numbers. Confirm a choice is requested.
8. Use a consenting test contact for direct calls and WhatsApp. Confirm the visible recipient and message before the first send. The app only claims the intent/tap was requested; WhatsApp delivery is not independently verified.
9. Test `tap`, `type`, `scroll`, `back`, `home` and `lock`. Confirm disabling Accessibility disables those features.
10. Turn off the laptop gateway. Confirm phone commands still work and conversation reports the disconnected brain.
11. Test rotation, backgrounding, screen lock, reopening, permission revocation and reboot. Always-on reliability has not been established.

## Keep useful measurements for the next few months

Record date, phone/OS/app version, exact command, expected result, actual result, repeatability and time to first response. For voice, log whether the transcription or the action was wrong. For battery, compare equal-length periods with listening off and on; do not guess from a single short trial.

Suggested development gates: no wrong-recipient action in the test set; no accidental duplicate send from one command; explicit handling of offline/model failures; stable listening with visible controls; no crash in repeated permission and lifecycle tests. These are acceptance criteria, not results claimed for this release.

## Signing for long-term use

GitHub's initial workflow produces a debug APK. Debug keys can differ between build machines, so updates may require uninstalling the previous debug app, which clears the phone's pairing and imported speech model. Laptop data remains separate. For months of updates, create a persistent release signing key on your laptop using Android Studio and keep it backed up privately. Never commit the keystore or password. Use the same application ID and signing key for every update.

## Current limits

- English command grammar; general natural-language action planning is not implemented.
- YouTube search is implemented; choosing and verifying playback needs a visible result selection.
- WhatsApp draft opening and an explicit visible Send tap are implemented; no delivery receipt integration.
- Offline wake-phrase recognition requires an imported Vosk model and can use noticeable battery.
- No arbitrary root access, default-assistant role, notification-reading, background location, or universal device-administrator access.
- Web searches open the browser; there is no general web-reading/research agent yet. Weather uses Open-Meteo.
- Retrieved document snippets use lexical search, and long contexts may drop older turns/reference snippets to fit.
- No actual fine-tuning run, live model benchmark, or physical-phone certification has been completed by the automated test suite.
