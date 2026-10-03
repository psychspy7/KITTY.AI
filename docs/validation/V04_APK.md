# KITTY AI 0.4 — delivered test APK

This record describes the APK delivered after the successful [Verify KITTY run](https://github.com/psychspy7/KITTY.AI/actions/runs/37100783641).

| Field | Value |
| --- | --- |
| App source commit | `0535de350e8ace3ce41370ee2f17033a69e31bd0` |
| Package | `com.kitty.ai` |
| Version | 0.4.0 (code 40) |
| APK | `KITTY-AI-v0.4.0-debug.apk` |
| Bytes | 24410377 |
| SHA-256 | `50db7310c96927f45876f31a9ed937be68c4b3da072ac47454a6235ead79a9dc` |
| Google Android OAuth SHA-1 | `77:95:21:37:7C:7C:87:21:A0:DA:E6:5F:A8:20:6D:69:90:13:9F:A3` |
| Signing | Temporary Android Debug certificate from this CI run |

Use the SHA-1 above only for **this delivered test APK** in the Android OAuth client, alongside package `com.kitty.ai`. A future debug build may use a different certificate. For the production release, use the retained release keystore and register its certificate instead. This debug file does not establish in-place update compatibility with your installed app.

## Verification

- 65 backend tests passed, including account isolation, admin denial, nonce consumption, encrypted settings, scoped memory, consent and provider payload fixtures.
- 24 Android unit tests passed.
- Debug and unsigned release builds passed; Android lint had no errors and five English UI text localization warnings.
- Android 15/API 35 emulator checks passed: launch, Google HTTPS setup/cancel, local phone command, pairing persistence, phone archive, gateway memory, streaming before completion, cancellation, disconnected commands and creator identity.
- A real Indian English Vosk model initialized; microphone start/stop, rapid toggling and bound tap-to-talk passed. The captured crash log is empty.

## Remaining live checks

These checks use a temporary gateway and controlled inference/authentication/provider fixtures. They do not verify a real Google sign-in, live Groq response, live Gemini voice or Drive upload. Those need your configured HTTPS host, OAuth project, account access and provider keys. Physical speech accuracy, media playback interactions and Vivo battery/background behavior still need testing on your phone. No VM was deployed and no model weights were fine-tuned.

Follow [the beginner setup guide](../START_HERE_V04.md) and [the final phone checklist](../QA.md) before the presentation. Notices use scheduled checks; background delivery is not instant push. Drive snapshots contain up to 500 server-recorded turns and 50 explicit memories, with separate user consent for reviewed training exports.

