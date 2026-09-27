# Set up KITTY on Windows and Android

This is the first personal-use alpha. Keep the laptop awake while using its brain. The phone can still run the implemented local commands when the laptop is unavailable.

## 1. Get the project and install Python

Download the repository ZIP from [KITTY.AI](https://github.com/psychspy7/KITTY.AI) using **Code → Download ZIP**, then extract it. Use a simple path such as `C:\KITTY-AI`. Every command below assumes a terminal opened in that extracted folder, unless another folder is named.

Install Python 3.11 or newer from [python.org](https://www.python.org/downloads/windows/). Check:

```powershell
py -3 --version
```

The KITTY gateway needs no pip packages. Run `SETUP_KITTY.bat`. It creates `data/config.json`, a pairing token, and an editable `data/personality.txt`. The first diagnostic will say the model is offline; the next steps start it. Keep the token private. It belongs in your phone's KITTY settings, never in GitHub.

## 2. Download the requested model

Run `DOWNLOAD_MODEL.bat`. The selected publisher is:

[mradermacher/Qwen3.5-4B-abliterated-GGUF](https://huggingface.co/mradermacher/Qwen3.5-4B-abliterated-GGUF), quantized from [wangzhang/Qwen3.5-4B-abliterated](https://huggingface.co/wangzhang/Qwen3.5-4B-abliterated).

This is one community derivative matching your requested model family, not an official Qwen model with the “Abliterated” suffix. The publisher lists the Q4_K_M download at approximately 2.8 GB; running it needs additional memory for the context and runtime. RAM/VRAM and speed must be measured on your laptop.

The downloader queries the publisher, selects exactly one unsplit Q4_K_M file, records the repository revision and expected SHA-256 in `data/model.lock.json`, and verifies the downloaded bytes. The exact revision is resolved at your first download; it has not been hardcoded to an unverified hash. Re-running the downloader reuses that lock. Do not download executable “model installers” from model repost sites.

The first real compatibility check passed with publisher revision `f3b61227dde75c72d12391c443efd6fa0229e0eb` and llama.cpp `b11146` on a Linux CPU runner. See [the recorded result](validation/ALPHA_0_1.md). A later publisher revision or a Windows/GPU runtime still needs the laptop smoke check below.

## 3. Install llama.cpp and start the model

Get a Windows build from the [official llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases). Use a current build that supports Qwen3.5. Start with the Windows x64 CPU build if your GPU is unknown. Extract **all** runtime files, including DLLs, into a `runtime` folder inside the KITTY project. The launcher searches that folder for `llama-server.exe`.

Run `START_MODEL.bat`. It starts the GGUF using:

| Setting | Initial value |
| --- | --- |
| Alias | `kitty` |
| API | `http://127.0.0.1:8080/v1` |
| Context | 4096 tokens |
| Parallel slots | 1 |
| GPU layers | 0, for the initial CPU configuration |
| Thinking | Disabled through the chat template for responsive conversation |

Leave the model window open. For a compatible GPU runtime, start it from a terminal instead:

```powershell
py -3 tools\start_model.py --gpu-layers 99
```

Use the CUDA runtime for a supported NVIDIA GPU or a suitable Vulkan build for supported hardware. The CPU build cannot use the GPU simply because you set `--gpu-layers`. If memory runs out, reduce GPU layers and keep the context at 4096. Once stable, you can set `context_window` to `8192` in `data/config.json` and restart both model and gateway.

Verify actual generation:

```powershell
py -3 tools\smoke_test.py
```

This prints a short KITTY reply and its elapsed time. It has to be run on your laptop; a mock API test is not a substitute for this check.

## 4. Start the phone gateway

Run `START_KITTY.bat`. Leave that window open too. In another terminal:

```powershell
py -3 server\kitty.py doctor
```

You should see the loaded model alias `kitty` and `READY`. The model API stays on port 8080; the phone connects to the KITTY gateway on port 8765.

## 5. Get the Android APK

Open the repository's [Actions page](https://github.com/psychspy7/KITTY.AI/actions), choose the latest **successful** “Verify KITTY” run for the app/server code you want to test, and download the `KITTY-AI-debug-apk` artifact. Documentation-only commits do not create a new APK. Sign in to GitHub if the download requires it. Extract `app-debug.apk` from that artifact ZIP. The first verified download is also linked in [the alpha report](validation/ALPHA_0_1.md).

Android 10 or newer is required. This is a debug APK for personal testing. You can copy it to your phone and install it using Files, approving installation from that source when Android asks. You can also install it over USB in the next step.

The app does not contain the language model or the offline speech model. Those are downloaded separately to avoid silently bundling gigabytes of weights.

## 6. Connect over USB first

Install Google's [Android SDK Platform Tools for Windows](https://developer.android.com/tools/releases/platform-tools). Extract them and open PowerShell in the resulting `platform-tools` folder.

On the phone, enable Developer options (usually by tapping Build number seven times in About phone), then enable USB debugging. Connect your own laptop by USB and approve its debugging prompt on the phone.

```powershell
.\adb.exe devices
.\adb.exe reverse tcp:8765 tcp:8765
```

If using ADB to install, place `app-debug.apk` in that `platform-tools` folder and run:

```powershell
.\adb.exe install -r .\app-debug.apk
```

If more than one device is listed, choose your phone with `adb -s SERIAL ...`. Repeat `adb reverse` after rebooting or reconnecting when necessary.

Open KITTY → Settings:

- Laptop server URL: `http://127.0.0.1:8765`
- Pairing token: the output of `py -3 server\kitty.py token` run in the KITTY folder
- Country calling code: `91` for Indian local phone numbers

Tap **Save**, reopen Settings and tap **Check saved connection**. The `127.0.0.1` address works on the phone here because ADB forwards this port to your laptop.

## 7. Approve phone features

Use KITTY's Settings buttons to grant microphone, contacts, call and notification permissions. Contacts stay on the phone. Turn on **Direct calls after a clear command** if you want `call Mom` to initiate a call; otherwise KITTY opens the dialer.

Use **Enable screen control (Accessibility)** to open Android's Accessibility page, then enable KITTY AI. This grants the implemented tap, type, scroll and navigation abilities. Some phones place additional prompts around sideloaded Accessibility services; follow your phone's standard settings flow only for this app you installed. If your phone or an administrator blocks a permission, report the exact message; KITTY cannot override it.

Android has no universal “allow all / full administrator” permission for ordinary apps. This build does not root the phone, enroll as device owner, or request unrelated device-administrator powers. It asks for the permissions its implemented features use.

Internet permission is included in the app and normally has no runtime permission prompt. YouTube, WhatsApp and browser searches use their own internet connections; live weather is fetched by the laptop gateway.

## 8. Enable offline voice

Tap-to-talk uses Android's on-device recognizer when available. For continuous “Hey Kitty” listening, use the Vosk model:

1. In KITTY Settings, tap **Download offline speech model**.
2. Keep the downloaded `vosk-model-small-en-in-0.4.zip` as a ZIP. This lightweight Indian English model is the recommended starting point for English commands spoken with an Indian accent.
3. Return to Settings → **Import offline speech model ZIP** and choose it.
4. Tap **Hey Kitty: off** on the main screen to turn listening on.
5. Say “Hey Kitty, open YouTube.” You can also say “Hey Kitty,” pause, and give a command within ten seconds.

Listening is opt-in, uses the microphone continuously while on, and displays a notification with **Stop listening**. This alpha uses continuous local speech recognition to detect the wake phrase, rather than a dedicated low-power wake-word engine. Battery use and false activations need measurement on your phone. It does not automatically restart after a reboot or Android force-stopping it.

Choose an installed offline TTS voice in Settings. Android voice names and available female voices vary by engine and device. English voice commands are the starting configuration. You can type Hindi/Hinglish to the laptop model; mixed-language speech recognition is not validated yet.

Speech recognition is local in the implemented voice paths. Spoken replies use the installed Android TTS engine; if no offline voice is available, its default voice may need internet. Install and select an offline voice before relying on fully offline speech output.

## 9. Try these commands

| Say or type | Current behavior |
| --- | --- |
| `open YouTube` | Opens the installed app |
| `play Interstellar music on YouTube` | Opens search results; does not silently pick a result |
| `call Mom` | Resolves a saved contact; a picker appears if several numbers match |
| `WhatsApp Mom saying I will be ten minutes late` | Opens the WhatsApp draft for the selected number |
| `Hey Kitty, tap Send` | Taps one exact visible Send control through Accessibility |
| `tap <visible title>` | Taps a single exact text/description match |
| `type Hello there` | Replaces text in the focused editable field |
| `scroll down`, `go back`, `go home`, `open recent apps` | Accessibility navigation |
| `lock the phone` | Requests Android's Accessibility lock action |
| `battery` | Reports phone battery percentage |
| `search the web for Qwen documentation` | Opens a browser search |
| `weather in Delhi` | Speaks an attributed and timestamped Open-Meteo estimate |
| `remember that I prefer short replies` | Saves an explicit laptop memory |
| `show memories`, `forget memory 1` | Lists or removes an explicit memory entry |

Generic screen commands must be spoken in listening mode while the target app is visible. Typing `tap Send` inside KITTY targets KITTY's own screen, not a background WhatsApp window. If Accessibility is off, a background command that needs to open another app may produce a notification you must tap. Unlock the phone before controlling other apps.

## 10. Optional wireless connection

Start the gateway with `py -3 server\kitty.py serve --bind 0.0.0.0` and use the laptop's private IPv4 address in the app. Both devices must be on the same reachable network. If Windows asks about network access, allow your private network. Never disable the firewall wholesale.

LAN HTTP sends the token and conversation in cleartext. Use USB for the initial setup, or use an encrypted private VPN/trusted HTTPS for wireless use. Do not expose the model or gateway to the public internet. If using a VPN, use its private IPv4 address and restrict the Windows firewall to the appropriate private interface/peers.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `py` is not recognized | Install Python with its Windows launcher; reopen the terminal |
| `llama-server.exe` missing | Extract the complete official runtime into `runtime`, or use `--exe` |
| Missing DLL | Keep all files from the runtime ZIP together; install its documented runtime dependencies |
| Unknown architecture / model load error | Use a current llama.cpp build with Qwen3.5 support |
| Download checksum failed | Do not run that file; rerun the downloader and check its error |
| Server connected but model unavailable | Start the model window first and confirm alias `kitty` |
| Phone cannot connect | Keep both laptop windows open; rerun `adb reverse`; check URL and token |
| Voice button errors | Download offline language support or import the Vosk ZIP |
| “Hey Kitty” mishears names | Try a saved full contact name; test tap-to-talk; keep a log of failures |
| WhatsApp opens but does not send | Wait for the draft; say `Hey Kitty, tap Send` while WhatsApp is visible |
| Two contacts or buttons match | Select explicitly; KITTY will not guess which one you meant |
| Listening stops later | Check battery settings and restart from KITTY; this varies by phone |
| Installing a later debug APK says signatures differ | Build with your own persistent signing key for long-term tests; CI debug signing can change between runs. See QA.md |

Your first setup feedback should include laptop CPU, RAM, GPU/VRAM, Windows version, phone model, Android version, and the exact failing command or error. Do not include your pairing token.

