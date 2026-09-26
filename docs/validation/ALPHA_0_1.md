# KITTY 0.1.0-alpha verification — 26 September 2026

This report records observed results, not a promise of error-free phone operation.

| Check | Observed result / evidence |
| --- | --- |
| Python gateway | 25 unit and HTTP integration tests passed after the Qwen template correction |
| Android | Java/resources compile, 12 routing tests pass, Android lint completes, debug APK builds |
| Emulator | Android 15/API 35 launch, settings, local battery command, pairing, memory persistence, token persistence across process restart, rejection of invalid action extras, model/gateway offline behavior passed; no KITTY crash in the test log |
| Actual model | Requested GGUF loaded and produced a reply through the real KITTY brain, including `/apply-template`, `/tokenize` and `/v1/chat/completions` |
| Fine-tuning | Not performed; preparation and training instructions are in TRAINING.md |
| Physical phone | Pending owner-device testing for audio, permissions, calls, WhatsApp, Accessibility and battery behavior |

## Download and reproduce

- [First verified Android run](https://github.com/psychspy7/KITTY.AI/actions/runs/36250612150), app source commit `6ab52228f7589f9b8bbd05b86b0a74ef71dd1b8c`.
- [APK artifact](https://github.com/psychspy7/KITTY.AI/actions/runs/36250612150/artifacts/10908464630), ZIP containing `app-debug.apk`.
- APK size: **11,511,202 bytes**. SHA-256: `857f9d2a6cd2eedf60d2970b5d9ad04ba9d9af5c197a7f25da7cd82c342ab299`.
- [Successful real-model run](https://github.com/psychspy7/KITTY.AI/actions/runs/36251280211), corrected gateway commit `e5da6794dc26b2926e57ec4de262a6f9029cce9e`.
- [Model evidence](https://github.com/psychspy7/KITTY.AI/actions/runs/36251280211/artifacts/10908854367) and [recorded JSON](model-smoke-2026-09-26.json).

The gateway correction does not change Android source. Use the latest repository checkout for the laptop and the verified APK above, or a later successful build. Different CI debug builds may use different signing keys; see [long-term signing](../QA.md#signing-for-long-term-use).

GitHub artifacts have retention limits and can require sign-in. Source and workflow definitions remain in the repository so builds can be reproduced.

## Exact model and runtime

| Item | Tested value |
| --- | --- |
| Publisher | `mradermacher/Qwen3.5-4B-abliterated-GGUF` |
| Revision | `f3b61227dde75c72d12391c443efd6fa0229e0eb` |
| File | `Qwen3.5-4B-abliterated.Q4_K_M.gguf` |
| Size | 2,707,514,688 bytes |
| SHA-256 | `1ec0b30c75b49223c82542caf2635a7af4edcda80d8c89759cc000d3871bf48e` |
| Runtime | Official llama.cpp b11146 Ubuntu x64 CPU build |
| Context / threads | 4096 tokens / 2 CPU inference threads |

The test prompt was “What is two plus two? Reply in one short sentence.” The reply was **“Sir, two plus two is four.”** The measured gateway round trip was **9.265 seconds** on that CI machine, including prompt processing. This single short response establishes basic compatibility; it is not a laptop speed estimate or a broad model-quality benchmark.

The first real run exposed a template constraint absent from the original mock: Qwen3.5 rejected a second system message. KITTY now combines personality and optional reference data into one leading system message before token counting and generation, with a regression test for this exact failure.
