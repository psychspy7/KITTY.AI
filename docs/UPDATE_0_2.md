# Update KITTY to 0.2

This is a personal development/test build for Virat's Windows laptop and Android phone. Update **both** the laptop files and APK. The 0.2 app needs the 0.2 gateway for streaming.

## Keep your existing laptop setup

1. Close the KITTY gateway window and model window.
2. Download the updated repository: <https://github.com/psychspy7/KITTY.AI/archive/refs/heads/main.zip>.
3. Extract it. Copy its contents **into your existing KITTY folder**, allowing source files to be replaced. Do not delete your existing folder. Keep `data`, `models`, and `runtime` in place: they contain your token, conversations, personality, model and llama.cpp installation.
4. Double-click `UPGRADE_KITTY.bat`. This saves a timestamped configuration/personality/database backup inside `data`, keeps your token and custom personality, sets unlimited archive retention, and uses only three recent chat turns in model context.
5. Start one model window, then `START_KITTY.bat`. Leave both open.

The model uses CPU inference by default. Four generation threads and up to six prompt-processing threads are starting values, not a measured optimum for your i3-1315U. Try 2, 4 and 6 using `py -3 tools\start_model.py --threads 4`. Close the running model before changing settings. Close memory-heavy applications while testing on 8 GB RAM.

## Optional smaller model

Run `DOWNLOAD_FAST_MODEL.bat` once to download and verify the 2B Q4_K_M model. Then close the 4B model window and use `START_MODEL_FAST.bat`. To switch back, close the 2B window and run `START_MODEL.bat`.

The two files and their checksum locks are separate. They share the gateway alias `kitty`. **Run one model at a time.** The 2B model is a candidate for lower latency and memory use, with possible answer-quality loss; no speedup on your laptop has been measured here. It is not automatically downloaded by the upgrade.

## Install the phone update

Try installing `KITTY-AI-0.2.0.apk` normally first. Android keeps app data when the signing key matches.

Earlier APKs were signed with temporary build-runner keys. Android may say the new package conflicts with the existing package. Do not delete the app to work around this before backing it up.

For that one-time mismatch:

1. Put the supplied `KITTY-AI-0.2.0.apk` next to `INSTALL_UPDATE.bat` in the KITTY folder.
2. Connect the phone by USB, enable USB debugging, and authorize your laptop. Android platform-tools (`adb`) must be on PATH.
3. Run `INSTALL_UPDATE.bat`. It first attempts an ordinary update. If the signing key differs, it stops KITTY and backs up its app files, settings, conversations and previous APK. It checks the backup before offering to reinstall.
4. Read the message, then type `REINSTALL` only if you want that migration. Pressing Enter keeps the old app.
5. Open KITTY. Paste the pairing token again and regrant microphone/notification/contacts/call permissions you use. Re-enable Accessibility if you use screen control.

The helper uses the debug access supported by these **test APKs**; it does not require root. The encrypted pairing token cannot survive uninstall because Android deletes the old Keystore key. It is deliberately omitted from the restore. Get the token from `py -3 server\kitty.py token`. The laptop database is unaffected by phone installation. Keep the backup folder until you have checked the update.

A retained private signing key is used for this deliverable and future signed updates. The separately supplied signing backup is private developer material—keep it out of the public repository. Raw GitHub Actions debug APKs still use runner keys; use the supplied signed download for updates.

## Connect the laptop

Run `adb reverse tcp:8765 tcp:8765` after reconnecting USB. In KITTY Settings, use `http://127.0.0.1:8765` and your pairing token, then Save and Check saved connection.

Cleartext network access is permitted only to loopback. Wi-Fi requires HTTPS with a certificate trusted by Android; a private Wi-Fi IP alone is not encrypted. The gateway already accepts `serve --bind ADDRESS --cert CERT --key KEY`. The app does not disable certificate verification. Android Network Security Configuration takes individual domains/hosts, not CIDR ranges.

## Set up offline speech

In Settings, use **Download offline English speech model**, then import the downloaded ZIP. Try Indian English (`vosk-model-small-en-in-0.4`, 36 MB) first, and compare US English (`vosk-model-small-en-us-0.15`, 40 MB) if it misses your words. Neither is guaranteed best for your voice. These are speech-to-text models, separate from the speaking voice.

Download an offline English TTS voice in Android's text-to-speech settings, then use **Choose an installed voice**. The available voice and gender depend on your installed TTS engine.

For commands, use English with these speech models. A Hindi model by itself does not implement Hindi wake words or command parsing. Hindi/Hinglish typed chat still uses Qwen.

## Retest on your Vivo

- Type **introduce yourself**. KITTY should immediately identify Virat as creator, even with the laptop disconnected.
- Type a normal question. Text should appear progressively. **Stop** stops generation/speech; it is also the reliable interruption control while KITTY is speaking.
- Try **Tap to talk**, say `battery`, and check the **Heard** text.
- Enable **Hey Kitty**, wait for the listening status, then say `Hey Kitty battery`. Also try `Hey Kitty`, wait for `Yes, Sir?` to finish, then say `battery` within ten seconds.
- Play music and request a spoken answer. Check that media ducks where the player supports Android audio focus, and that KITTY does not transcribe herself.
- Close/reopen KITTY and verify chat history. Disconnect the laptop, use a local command, reconnect and open KITTY to sync it.
- If Vivo stops listening with the screen off, set KITTY's battery use to unrestricted and permit background activity in Vivo settings. These settings help but cannot guarantee that an OEM never stops the service. Start microphone listening while KITTY is visible.

The microphone intentionally pauses during a reply and speech output. This release does not provide full-duplex spoken interruption. Use Stop or Tap to talk to interrupt.

## Optional screen control

Accessibility supplies visible-label taps, Unicode typing and scrolling. Optional Shizuku supplies Home, Back, Recents, Lock and `tap X Y`. Install/start Shizuku yourself, then tap **Connect Shizuku** and grant its permission. Shizuku is not a substitute for understanding which screen is visible and does not grant root access to all app data.

## History and training

Accepted typed/voice commands and answers are saved in the app's private SQLite database. Recent archived chats can be opened from Settings. The app keeps an outbox for laptop sync; offline events upload when a connection is available on the next app resume, send, or settings save. The latest 100 turns are displayed; older turns remain stored. The laptop archive defaults to no automatic deletion after the upgrade, while only a short recent context goes into the model.

Ratings/corrections are saved on the phone and synced separately. Use `py -3 server\kitty.py export-feedback training\exports\review.jsonl` after creating the output folder. This exports approved/corrected model answers, not raw phone actions or connection errors. The authored `training/identity_examples.jsonl` adds starter identity/tone examples. Review and deduplicate data before a separate fine-tuning run; no automatic weight training is performed on this 8 GB machine.

Run `BENCHMARK_KITTY.bat` with both laptop servers running to record first-text and total reply times in `data/benchmark-last.json`. Streaming reduces waiting for the complete answer; it does not eliminate prompt-processing time or make the CPU generate tokens faster.
