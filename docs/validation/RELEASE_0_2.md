# KITTY 0.2 verification — 28 September 2026

This records observed checks for the supplied personal development APK. It does not establish error-free operation or speech accuracy on the Vivo Y28 5G.

| Check | Observed result |
| --- | --- |
| Python gateway and upgrade tools | 33 tests passed |
| Android unit tests | 20 passed, zero failures or skipped tests |
| Android build | Debug and unsigned release APKs compiled |
| Android lint | Passed with five warnings: target SDK 35 is older than the latest SDK, and four text internationalization warnings |
| Android 15 emulator | Phone/gateway flow, partial text, cancellation, persistence, offline identity, real Vosk loading, mic start/stop, rapid toggle/restart and bound tap-to-talk passed; no KITTY crash in the test log |
| Real language models | Both the default 4B and optional 2B Q4_K_M produced streamed replies through the gateway |
| Supplied APK signing | Signature and ZIP alignment verified; app payload matches the CI-tested APK |

## Reproduce and inspect

- App and gateway source: `01a46a8b6d6decb4fe68c330469dd9ad64368d4c`.
- [Successful verification run](https://github.com/psychspy7/KITTY.AI/actions/runs/36376467090).
- [Android test/lint reports](https://github.com/psychspy7/KITTY.AI/actions/runs/36376467090/artifacts/10950829072).
- [Emulator evidence](https://github.com/psychspy7/KITTY.AI/actions/runs/36376467090/artifacts/10950854270): screenshots, UI trees, result and logcat.
- [Real-model streaming run](https://github.com/psychspy7/KITTY.AI/actions/runs/36337358654), gateway/model-test source `82719a395f54620495f8c831cb6d0e23d888ba52`. The subsequent app-only toggle fix did not change this gateway or model configuration.
- Recorded model results: [4B](model-0.2-default.json) and [2B](model-0.2-fast.json).

CI artifacts can require GitHub sign-in and have retention limits. Use the separately supplied signed APK for retained-key updates; raw CI debug APKs use temporary runner keys.

## Download identity

| Field | Supplied value |
| --- | --- |
| Filename | `KITTY-AI-0.2.0.apk` |
| Package | `com.kitty.ai` |
| Version | `0.2.0`, versionCode `20` |
| Build type | Debug development/test APK, supporting the backup helper's `run-as` access |
| Android minimum / target | API 29 / 35 |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| File size | 14,043,355 bytes |
| APK SHA-256 | `f270ab51f2f0775983bc0713b89f509bed92690c0de286773850af1553f71765` |
| Signing certificate SHA-256 | `b0cba7bc352bba1f0daa7a6d9b64631f4da06f613262618a5e366c289b483351` |

The private key/password are excluded from the repository and supplied separately as a private backup. Public metadata is also recorded in [apk-0.2.json](apk-0.2.json). Follow the [update guide](../UPDATE_0_2.md) before replacing an APK signed with an older temporary key.

## Model measurements and their limits

The separate jobs used llama.cpp b11146, two CPU inference threads, a 4096-token context and the prompt “What is two plus two? Reply in one short sentence.” Both produced seven completion tokens from 290 prompt tokens in six streamed chunks.

| Profile | Time to first token | Total gateway time | Reply |
| --- | --- | --- | --- |
| Default 4B | 15.438 s | 16.112 s | Sir, Two plus two equals four. |
| Optional 2B | 5.819 s | 6.155 s | Sir, Two plus two is four. |

These were separate GitHub Ubuntu CPU jobs, not a controlled model comparison or an i3-1315U benchmark. They establish basic compatibility and real streaming, not a guaranteed speedup or answer quality. Run `BENCHMARK_KITTY.bat` on the laptop to measure that installation. Local introduction/creator replies bypass model inference entirely.

## Still needs the owner's device

Speech transcription accuracy, acoustic TTS, media ducking, Vivo background/battery behavior, Shizuku execution, Accessibility across other apps, calls and WhatsApp need physical-device testing. The migration helper's archive sanitization is unit-tested; an end-to-end reinstall of the owner's phone was not performed here. Model fine-tuning was not performed. The Java view layer remains in place; full-duplex speech and robot telemetry are future work.

See [QA.md](../QA.md) for the repeatable phone checks and [ARCHITECTURE.md](../ARCHITECTURE.md) for the changes made in response to the external review.
