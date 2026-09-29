# KITTY AI v0.3 — beginner setup and presentation checklist

This guide is for Virat's **Windows laptop (i3-1315U, 8 GB RAM)** and a **Vivo Y28 5G**. KITTY runs a small model on the laptop; the Android phone records and recognizes commands locally. The laptop must remain awake and connected to the internet while anyone uses its brain. Turso saves a cloud copy of the selected data; it does **not** host the language model. No USB cable is needed for the final demonstration.

**What is already in the source:** dark green redesigned screen, health and sync status, streaming chat, creator identity, local phone archive, offline Vosk speech, a microphone level/blocked indicator, notification Listen now, guarded numbered YouTube video selection, revocable guest invitations, source-linked web search, and a verified update button. GitHub's Android build/emulator job and an actual Vivo voice test must still pass before treating a build as presentation-ready.

## Part A — prepare the laptop once

1. Make a copy of your **entire old KITTY folder**, especially `data`, `models`, and `runtime`. Never put the `data` folder or tokens on GitHub. Download the newest [repository ZIP](https://github.com/psychspy7/KITTY.AI/archive/refs/heads/main.zip), extract it to `C:\KITTY-AI`, and copy those three folders from the backup into the new folder. If you have no old installation, just use the extracted folder.
2. Install [Python 3.11+ for Windows](https://www.python.org/downloads/windows/) and check `py -3 --version` in PowerShell. Open PowerShell in `C:\KITTY-AI` by clicking the address bar in File Explorer, typing `powershell`, and pressing Enter.
3. Double-click `SETUP_KITTY.bat`. It makes `data\config.json` and prints the owner pairing token. If you never downloaded the smaller model, double-click `DOWNLOAD_FAST_MODEL.bat`. The verified downloader places the GGUF under `models` and saves a lock file under `data`. Your CPU and 8 GB RAM will be much faster with the smaller Qwen profile than a 4B model; output still takes time.
4. Download a compatible **Windows x64 CPU** [official llama.cpp build](https://github.com/ggml-org/llama.cpp/releases), then extract **all** files including DLLs into `C:\KITTY-AI\runtime`. The folder must contain `llama-server.exe`. Double-click `START_DEMO_V03.bat`. It opens a model window and a gateway window. Keep **both** open. You can instead open `START_MODEL_FAST.bat` and `START_KITTY.bat` separately.
5. In PowerShell run `py -3 server\kitty.py doctor`. After the model finishes loading, this should show alias `kitty` and `READY`. Run `py -3 tools\smoke_test.py` for a real model response. If the model is slow, keep the fast profile and avoid a giant chat history; one model request runs at a time on this laptop.

If you previously set up the v0.2 Turso replica `data\kitty.turso.sqlite3`, follow [the deliberate migration steps](TURSO.md) before depending on those old cloud records. Keep your backup.

## Part B — wireless connection, including mobile data

1. Install [Tailscale for Windows](https://tailscale.com/download/windows) and sign in. You only need Tailscale on the **laptop** for this public HTTPS Funnel setup. Tailscale may ask you to enable HTTPS/Funnel in its admin page; follow its prompts. The gateway continues listening only on the laptop's `127.0.0.1:8765`. Do **not** publish port 8080, which belongs to llama.cpp.
2. With the gateway window open, use PowerShell:

   ```powershell
   & "C:\Program Files\Tailscale\tailscale.exe" funnel --bg 8765
   & "C:\Program Files\Tailscale\tailscale.exe" funnel status
   ```

   Copy the `https://YOUR-LAPTOP.YOUR-TAILNET.ts.net` address printed by Tailscale. It is a public HTTPS route to KITTY's **token-protected** gateway; it is not itself a secret. The owner token and guest tokens are secrets. Funnel may take a little time to become reachable.
3. On the phone, turn **Wi-Fi off** temporarily so it uses mobile data. Open that address plus `/health` in the phone browser; you should see `{"status":"ok",...}`. This tests the tunnel only. You enter the token inside KITTY's Settings, not in the browser URL.
4. To stop public sharing after the presentation, run `& "C:\Program Files\Tailscale\tailscale.exe" funnel --https=443 off` and verify with `funnel status`. For owner-only use, Tailscale Serve plus Tailscale on your own phone is a more private option.

**Who can use it?** Anyone you deliberately give an APK, HTTPS URL and an unexpired guest token can reach the laptop from their own phone's internet. Each guest gets separate chat history and cannot read your saved owner memories, documents or custom personality. The owner's phone controls only its own screen; the remote user does not gain control of your phone. One CPU inference runs at a time, so a crowd will see “still answering another request.” The laptop must stay awake. This is a presentation access model, not a high-capacity public service.

## Part C — pair the app without a cable

1. Download the `KITTY-AI-debug-apk` ZIP from the newest **successful** [Verify KITTY run](https://github.com/psychspy7/KITTY.AI/actions/workflows/verify.yml) for the v0.3 commit. Unzip it and copy `app-debug.apk` to your phone. Open it in Files and approve the Android installation prompt. Android 10+ is needed. If Android says **app not installed / signature differs**, do not uninstall the old KITTY: see the signature section below.
2. Open KITTY → **Settings**. Put the exact Funnel `https://…ts.net` address in **Laptop server URL**. Put your **owner pairing token** from `py -3 server\kitty.py token` in **Pairing token**. Do not include `/health`, `/v1`, spaces or quotes. Tap **Save**. The small system status row should say **Brain ready** when the model is online. Tap that row for role, web, Turso, offline model and microphone diagnostics. You can also tap **Check saved connection** in Settings.
3. If another person is testing: run `py -3 server\kitty.py invite PresentationGuest --hours 4` in your laptop PowerShell. Give that person the guest token shown **once**, the URL, and the APK. **Never give them your owner token.** `py -3 server\kitty.py guests` lists IDs; `py -3 server\kitty.py revoke-guest GUEST_ID` revokes one immediately for new requests. Invitations expire. Guest phone actions affect only that person's own phone.
4. Test in this order: `introduce yourself` → `weather in Delhi` → `remember that Virat created KITTY` on the **owner** phone → `show memories` on the owner phone → ask a normal question and watch the response stream. A guest saying `show memories` should get a privacy message. Test again with phone Wi-Fi off.

If you switch the same installed phone from the owner token to a guest token, older unsent chats remain on the phone and are not uploaded to the new connection. For a clean guest demo, use a separate fresh installation or device. Phone and gateway archives save conversations, but they do **not** change model weights or train it automatically; corrected examples require reviewed export and a later fine-tuning job.

## Part D — microphone and YouTube test

1. In KITTY Settings, tap **Download offline English speech model** and choose **Indian English** first (small Vosk pack). Keep the download as a `.zip`. Tap **Import offline speech model ZIP** and select that file. Tap **Grant microphone, contacts and call access** and allow microphone and notifications. Enable **Screen control (Accessibility)** only if you want label and YouTube-result taps; Android shows the system confirmation. Turn off aggressive battery restrictions for KITTY if your Vivo stops background listening.
2. Start **Hey Kitty: off** while KITTY is visible. Wait until the UI says **Listening · say Hey Kitty** and the small microphone bar moves when you speak. Open YouTube and keep the phone unlocked. Say **“Hey Kitty, open YouTube”** or, if already in a results screen, say **“Hey Kitty”**, pause, then **“play the first video”** within ten seconds. A result tap is attempted only when YouTube exposes an identifiable visible video item; otherwise KITTY asks you to use its exact title. This prevents guessing at arbitrary buttons.
3. If it does not react, look at KITTY's persistent microphone notification. Tap **Listen now**, speak the command within ten seconds, and check `Heard:` and the microphone bar in KITTY. If the bar stays at zero or says **Microphone blocked**, close another recording app, check Android's microphone toggle and permissions, and try again. Lower YouTube volume or use earphones: Android can silence or degrade another app's recorder, and sound coming from the speaker can drown out a wake phrase. It cannot be guaranteed on every Android/Vivo build. Tap **Stop listening** in the notification when finished.

Voice commands use **English** speech packs. Hindi/Hinglish typed chat can still go to the laptop model. Offline TTS voice depends on Android's installed voices; pick one in Settings. This is continuous local Vosk recognition, not a dedicated low-power wake-word chip, so measure battery life.

## Part E — optional Turso and live web answers

**Turso:** Install the optional Python package with `py -3 -m pip install -r requirements-turso.txt`. Create a database and token in your [Turso dashboard](https://turso.tech/). Make `C:\KITTY-AI\data\secrets.env` in Notepad with just these lines, replacing the placeholders:

```text
TURSO_DATABASE_URL=libsql://YOUR_DATABASE.turso.io
TURSO_AUTH_TOKEN=YOUR_DATABASE_TOKEN
```

Save it as **All Files**, named `secrets.env` (not `secrets.env.txt`). Restart the KITTY gateway. Run `py -3 server\kitty.py sync-turso` or open the app's System status; wait for `0 pending` and `synced`. The laptop's `data\kitty.sqlite3` is primary; Turso receives memories, chat events, approved feedback and indexed documents in the background. If internet goes away, the local queue retries. Keep only **one active gateway writer** for this database. No Turso key belongs in the phone or GitHub.

**Web:** Get a Brave Search API key from the provider's [developer dashboard](https://api-dashboard.search.brave.com/). Add `BRAVE_SEARCH_API_KEY=YOUR_KEY` as the third line in the same `data\secrets.env` file, restart KITTY, and check **WEB RESEARCH Configured** in System status. Tap the **Web** chip or type `research latest space news`. KITTY returns short live search excerpts with clickable source URLs and a timestamp. Open sources to verify; it does not silently browse or claim that the local Qwen model learned today's news. If the key/quota is missing, it gives an explicit error. `search the web for ...` still opens the phone browser without an API key. Check the provider's current pricing and quota before relying on it.

## Part F — publish an app update later

The **Update** button checks `release/update.json`. A newer signed release must have a larger `versionCode`, a tagged immutable GitHub download URL and SHA-256. KITTY downloads it, verifies checksum/package/version/**same signing certificate**, then Android asks you to install it. No silent update or automatic uninstallation. [The full release guide](UPDATES_AND_RELEASES.md) explains the four GitHub Actions signing secrets and version/tag process. Never publish the `.jks` or passwords in the repository.

**About the current APK:** CI's debug APK is for a fresh test installation. If your installed KITTY 2.0/debug app was signed with another key, Android will reject an in-place update. If you have its original keystore, use that same key for the signed v0.3 release. If the key is lost, use `py -3 tools\update_phone.py --apk C:\path\to\app-debug.apk` with Android platform-tools and USB to back up **before** a confirmed one-time signing migration; read its prompts. That backup helper needs `run-as`, so it can fail for a non-debuggable production APK. If it fails, keep the old app until we have a verified export path. Phone credentials have to be paired again after a reinstall.

## Presentation checklist

- Laptop plugged in and sleep disabled for the demo; both `START_DEMO_V03.bat` windows running; doctor says `READY`.
- Funnel status shows HTTPS; `/health` loads over **mobile data**; owner/guest app System status says **Brain ready**.
- Optional web research shows a source URL and Turso shows `0 pending`, if those services are configured.
- Offline speech ZIP installed; microphone bar moves; notification says Listen now; Accessibility enabled if showing YouTube taps.
- Have a short typed command as backup for a noisy room. Run a rehearsal on the **actual Vivo**; CI emulator audio cannot prove its wake-word behavior.
- Never show your pairing tokens, API keys or signing passwords on the presentation screen.

For troubleshooting, capture **the exact app status, Android version and command that failed**, plus the model and gateway terminal error lines (redact secrets). The repository's [QA guide](QA.md) explains checks and limitations. Existing 0.2 instructions are historical; use this v0.3 page for the current source.
