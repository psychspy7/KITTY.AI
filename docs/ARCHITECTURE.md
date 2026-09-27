# KITTY 0.2 architecture and review decisions

The Android app owns offline speech recognition (Vosk), TTS, deterministic phone actions and a local SQLite archive. The laptop owns language-model inference (llama.cpp), explicit memory, document retrieval, weather lookup and the aggregate archive. This distributes different tasks across the devices; it does not shard one model across phone/laptop RAM.

## Android state and networking

`KittyApp` owns `ChatController`, durable storage, a single `SpeechOutput`, and optional `ShizukuControl`. `MainActivity` observes state and renders views; rotating or closing the activity does not discard the active conversation. The view layer remains Java/Android Views. An incremental Compose migration can follow without blocking these functional fixes.

`VoiceService` is a user-started microphone foreground service, reached through a local binder. Its loading, listening, paused and stopping states are separate from model generation. Native load-completion callbacks close resources even when the service has already been destroyed. The service does not auto-start on boot or silently restart microphone recording after OS termination. Start failures are reported and retry starts from the visible app.

Final recognition results pass through `WakeGate`. Partial words appear in the UI but cannot authorize an action. The wake word accepts Kitty/Kitti, not the generic word “cutie”. A wake-only utterance opens a ten-second command window after speech output ends. Tap to talk uses the same recognizer without requiring the wake phrase. The microphone pauses during generation and TTS; Stop/Tap to talk provide interruption controls. Genuine full-duplex acoustic echo cancellation/barge-in is not implemented.

OkHttp makes independent cancellable calls. `/v1/chat/stream` returns SSE status, token and final-result events. `/v1/cancel` closes the gateway's upstream model socket; client disconnect also cancels it. SSE carries server-to-phone events, while POST endpoints carry commands and feedback. SSE itself is not a bidirectional protocol. Future robot audio/telemetry may justify a separate WebSocket channel.

`SpeechOutput` queues complete sentences as model text arrives and owns Android audio focus. It abandons focus on completion, stop or error. Other media players determine how they honor ducking. Only installed offline TTS voices appear in the picker. Voice accuracy, acoustic echo behaviour and OEM process management still require testing on the physical phone.

## Commands and screen control

User text reaches the deterministic router before the model. General model replies never directly become executable phone commands. Contact/app disambiguation uses the foreground activity. Background actions requiring a foreground launch use an immutable PendingIntent targeting a private `ActionActivity`; the exported launcher ignores action extras.

Accessibility reads visible nodes for label taps, scrolling and Unicode text entry. Shizuku is optional and requires the owner's explicit Shizuku authorization. Its typed user service supplies navigation and numeric coordinate taps using argument arrays, without a shell interpreter. It is not a complete Accessibility replacement: it cannot infer a button's location from its label, and `input text` is not a universal Unicode text-entry solution. Privileges vary with Shizuku's startup identity and Android version. No claim of full system-control parity is made.

## Connection boundary

The manifest references a Network Security Configuration that denies cleartext by default and permits exactly `127.0.0.1` for USB forwarding. HTTPS uses normal Android certificate trust. The client also validates the configured base URL and disables redirects; loopback bypasses system proxies. Android's domain configuration cannot express CIDR ranges as proposed in the external review.

The gateway uses a pairing token, bounded HTTP handlers, bounded bodies/streams and one model generation at a time. It is a single-owner local development server. Network hardening does not make it a public multi-tenant service.

`POST_NOTIFICATIONS` permission is useful for visible controls but is not a prerequisite for starting an Android foreground service. The app still supplies the mandatory foreground notification and handles microphone permission/start restrictions. Battery-setting shortcuts are assistance for owner setup, not a guarantee against OEM termination.

## Persistence and training

The phone's SQLite archive stores accepted turns before processing, then saves their outcomes. Pending requests at process restart become interrupted turns; commands are not automatically replayed. A durable outbox synchronizes archived phone events to the laptop with UUID deduplication. Ratings and corrections have a separate durable queue. On resume, send or saved connection changes the app retries sync; it does not run a perpetual background synchronization job.

The laptop keeps `data/kitty.sqlite3`. The 0.2 upgrade sets `history_days` to zero (no automatic purge). Only three recent eligible turns, a small memory selection and one document excerpt are sent as context by default. The actual llama.cpp tokenizer enforces the context budget. Archiving more conversations therefore does not grow every prompt without bound.

Identity responses run locally on the phone/gateway. The custom personality file is preserved on upgrade, with creator identity prepended only if missing. Authored starter examples and approved/corrected model turns can be exported for a separate training run. Neither saved chat nor retrieval changes the model's weights. Raw phone commands, errors and weather are excluded from model-training exports.

The app encrypts its pairing token using Android Keystore. App databases use Android private storage; the laptop SQLite file is not independently encrypted. Phone uninstall destroys the old Keystore key, so a signing migration restores other settings/data but requires pairing again. Keep developer signing material private and use the retained key for subsequent updates.
