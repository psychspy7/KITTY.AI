# KITTY AI 0.4

**[Start here: Google login, Groq, Gemini voice and Drive setup](docs/START_HERE_V04.md).**

The hosted-provider update keeps phone actions and local archives on Android. A
verified admin account manages provider credentials, KITTY character preferences,
optional Drive backup and notices. Users sign in with Google and never enter a
model API key. Memories sync per account, with an offline phone outbox. Short phone
actions use local voice; cloud replies can use cancellable Gemini speech with fallback.

The gateway still needs an HTTPS host, Google OAuth configuration and persistent
storage. A release APK needs the owner's retained signing key. None is created
by the repository ZIP alone. No hosted weights have been fine-tuned. Notice
notifications use scheduled checks rather than instant push.

Legacy laptop mode remains supported; the previous guide below applies only to
that mode. Google mode rejects legacy pairing tokens. A full Kotlin/Compose
migration is not included in this update; the native Java app remains in place.

# KITTY AI — legacy local mode v0.3

**New to KITTY? Follow the [v0.3 beginner guide](docs/START_HERE_V03.md) for Windows, Vivo, wireless HTTPS, guest invitations, offline speech, Turso, web research and updates.** A production-signed v0.3 release requires the retained signing key and a successful release workflow; CI's debug APK is for testing.

**Updating an existing installation? Start with [the v0.3 beginner guide](docs/START_HERE_V03.md).** It explains preserving data and checking the original APK signing key.

[Recorded 0.2 verification](docs/validation/RELEASE_0_2.md) covers the previous release. Check the [latest workflow](https://github.com/psychspy7/KITTY.AI/actions/workflows/verify.yml) for v0.3 build, emulator and APK artifacts. Physical-phone audio and speed still need a real Vivo test.


[![Verify KITTY](https://github.com/psychspy7/KITTY.AI/actions/workflows/verify.yml/badge.svg)](https://github.com/psychspy7/KITTY.AI/actions/workflows/verify.yml)

**Your phone. Your laptop brain. At your service, Sir.**

KITTY is an early personal-use Android assistant with local phone commands and a laptop-hosted language model. Virat created the KITTY project. Her personality is female, candid, witty, occasionally darkly humorous, and addresses her owner as **Sir**. No paid AI API key is required.

**Start here: [v0.3 Windows + Android setup](docs/START_HERE_V03.md)** · [Cloud VM setup](cloud/oracle/README.md) · [Turso memory backend](docs/TURSO.md) · [App updates](docs/UPDATES_AND_RELEASES.md) · [Personalization and training](docs/TRAINING.md) · [Architecture](docs/ARCHITECTURE.md) · [Testing and limitations](docs/QA.md)

<img src="docs/images/kitty-welcome.png" width="300" alt="KITTY AI running in an Android 15 emulator">

Earlier alpha emulator screenshot. [Recorded alpha verification](docs/validation/ALPHA_0_1.md); [current 0.2 verification](docs/validation/RELEASE_0_2.md).

## Current test build

| Feature | Implemented behavior |
| --- | --- |
| Voice and chat | Streamed text, sentence-by-sentence speech, Stop, and imported Vosk for offline tap-to-talk / “Hey Kitty” |
| Apps and YouTube | Opens installed apps and YouTube search results; can select a numbered identifiable visible video with Accessibility |
| Calls | Resolves contacts on the phone; dialer by default, optional direct calling; asks when names/numbers are ambiguous |
| WhatsApp | Opens an addressed draft; an explicit `Hey Kitty, tap Send` can press a unique visible Send control |
| Screen control | Accessibility for labels, typing and scrolling; optional Shizuku for navigation and coordinate taps |
| Internet | Browser search and live weather; optional Brave Search API excerpts and source links via `research …` |
| Wireless guests | Tailscale Funnel HTTPS to the laptop gateway, with expiring guest tokens and private session history |
| Cloud copy | Durable local outbox to Turso if configured; a network delay does not block each reply |
| Personalization | Virat creator identity, editable personality, saved chat/feedback, explicit memory, retrieval and reviewed export |

This is a starting point for months of real-device testing. It does not yet have general autonomous screen planning, verified WhatsApp delivery, a low-power wake-word engine, or universal administrator/root access. Android permissions remain under your control. Model responses are conversation; only supported explicit user commands trigger phone actions.

## Requested brain configuration

| Setting | Value |
| --- | --- |
| Model | Qwen3.5-4B-Abliterated, community derivative |
| Selected GGUF publisher | `mradermacher/Qwen3.5-4B-abliterated-GGUF`, derived from `wangzhang/Qwen3.5-4B-abliterated` |
| Format / quantization | GGUF / Q4_K_M |
| Runtime | llama.cpp |
| Local model API | OpenAI-compatible `http://127.0.0.1:8080/v1`, alias `kitty` |
| Context | 4096 on the 8 GB laptop; short recent history, full archive retained |
| Phone gateway | Python 3.11+, `http://127.0.0.1:8765`, pairing-token authentication |

The downloader resolves and records the exact model revision and SHA-256 on first download. The model is not bundled in the APK and has not been fine-tuned by this project. The separate model compatibility workflow downloads and tests the real weights. An OpenAI-compatible local API does not require an OpenAI account.

## Setup sequence

1. Download this repository and install Python 3.11+ on your laptop.
2. Run `SETUP_KITTY.bat`, then `DOWNLOAD_FAST_MODEL.bat` for this 8 GB laptop.
3. Extract a compatible official llama.cpp Windows runtime into `runtime/`.
4. Run `START_DEMO_V03.bat`; keep both windows open.
5. Install the supplied signed test APK. For source builds, `KITTY-AI-debug-apk` is available from successful [Verify KITTY runs](https://github.com/psychspy7/KITTY.AI/actions/workflows/verify.yml); their temporary keys are not interchangeable with the retained signing key. See the update guide before replacing an existing installation.
6. For wireless use, connect the laptop gateway to Tailscale Funnel HTTPS; enter that URL and a pairing token in KITTY Settings.
7. Approve permissions for the features you want, and import the offline speech ZIP for continuous listening.

To keep KITTY available when the laptop is off, follow the [Oracle Cloud VM guide](cloud/oracle/README.md). The Android header's **Update** button follows the signed-release process in [UPDATES_AND_RELEASES.md](docs/UPDATES_AND_RELEASES.md).

Follow the [v0.3 setup guide](docs/START_HERE_V03.md) for exact commands, downloads and troubleshooting. Start with `battery`, `open YouTube`, `weather in Delhi`, and `remember that I prefer short replies`.

## Verification

```sh
python -m unittest discover -s tests -v
cd android
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

CI runs the Python server tests, Android command tests, lint and APK compilation. It also runs an Android 15 emulator smoke flow against the real Python gateway, retaining screenshots, UI trees and logs as `KITTY-emulator-evidence`. Check the result for the exact commit you install. These checks do not establish physical-phone audio reliability or the quality/speed of the actual model.

Run `python tools/smoke_test.py` on your laptop after starting llama.cpp to check actual model inference. Use [QA.md](docs/QA.md) for phone testing and persistent APK signing before months of updates.

The separate [Real model smoke workflow](https://github.com/psychspy7/KITTY.AI/actions/workflows/model-smoke.yml) checks the actual downloaded GGUF with a pinned official llama.cpp CPU runtime. Its evidence is a compatibility check on CI hardware; your laptop's speed still needs measurement.

## Personal data and training

Generated pairing tokens, model weights, memories, credentials, signing keys and training exports are excluded from Git. Conversations stay in a local laptop database with a configurable retention period. Phone contacts and on-demand screen inspection stay on the phone. The server is for a private connection; use USB initially or encrypted private networking for wireless access.

Feedback collection does not silently update model weights. [TRAINING.md](docs/TRAINING.md) explains source weights → LoRA/QLoRA → evaluation → merged model → GGUF Q4_K_M. Choose training settings after checking your GPU, VRAM, RAM and dataset.

See [third-party components](THIRD_PARTY.md) for license/source references.
