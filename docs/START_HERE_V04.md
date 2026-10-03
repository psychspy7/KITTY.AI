# KITTY 0.4 — beginner setup

This version adds Google accounts, admin-managed Groq chat, optional Gemini voice,
account-scoped local memory, optional admin Drive backups, notices and improved
voice follow-ups. KITTY's identity says Virat created the project. The underlying
hosted model is still supplied by its provider; no weights have been fine-tuned.

**Do not uninstall your existing app before backing it up.** Android requires the
same signing certificate for an update. The supplied CI debug APK is a test build;
its temporary signing key may differ from yours. See [signed updates](UPDATES_AND_RELEASES.md).

## 1. Understand the three parts

| Part | What it does | Where it runs |
| --- | --- | --- |
| Android app | Screen commands, wake recognition, local history/memory, playback | Phone |
| KITTY gateway | Verifies Google login, protects keys, isolates accounts, queues backups | Laptop or an always-on VM |
| Groq / Gemini | Chat inference / optional text-to-speech | Provider's cloud |

With Groq, you do **not** run `START_MODEL_FAST.bat` or download a GGUF for cloud
chat. Your laptop no longer does inference. If your gateway is on your laptop,
that laptop must still be awake. To turn the laptop off, move the gateway to an
always-on VM with persistent disk. Turso and Drive store data; neither hosts the
running gateway or language model. A permanently free VM is not guaranteed.

## 2. Prepare the repository and HTTPS address

1. Keep a backup of your old folder and phone history.
2. Download [the current repository ZIP](https://github.com/psychspy7/KITTY.AI/archive/refs/heads/main.zip).
3. Extract it to `C:\KITTY-AI`. Keep your old `data`, `models` and `runtime` as backups;
   copy `data` into the new folder if you want to retain your laptop archive.
4. Install [Python 3.11 or newer](https://www.python.org/downloads/windows/).
   Check **Add Python to PATH** during installation.
5. Install [Tailscale](https://tailscale.com/download/windows) on the laptop and sign in.
6. Open PowerShell in the KITTY folder: click File Explorer's address bar, type
   `powershell`, press Enter. Run:

   ```powershell
   & "C:\Program Files\Tailscale\tailscale.exe" funnel --bg 8765
   & "C:\Program Files\Tailscale\tailscale.exe" funnel status
   ```

7. Follow any HTTPS/Funnel setup prompts. Keep the printed **HTTPS origin** such as
   `https://v.example-tailnet.ts.net`. This is your service address. Do not add a path
   such as `/v1`. Funnel has its own availability and usage limits. See the
   [official Funnel instructions](https://tailscale.com/kb/1223/funnel).

Never publish the local language-model port 8080. This version needs only the
protected gateway on 8765, behind HTTPS.

## 3. Set up Google login once as the app owner

1. Open [Google Cloud Console](https://console.cloud.google.com/). Sign in as
   `viratanand1221@gmail.com`. Create a project named **KITTY AI**.
2. Open **Google Auth Platform**. Configure the app name, support email and
   developer contact. Use an external audience if other people's Google accounts
   will use the app. In testing, add the admin and each presentation tester to
   the test-user list. Follow Google's publishing/verification requirements before
   offering it publicly; test-mode Drive grants may expire and need reconnecting.
3. In **APIs & Services → Library**, enable **Google Drive API**.
4. Create an OAuth client of type **Web application**. Name it `KITTY gateway`.
   Add this authorized redirect URI, using your actual HTTPS origin:

   ```text
   https://YOUR-KITTY-HOST/oauth/drive/callback
   ```

5. Save the web **client ID** and **client secret** privately. They go on the
   backend; the secret never goes into the APK.
6. Create an OAuth client of type **Android**. Package name: `com.kitty.ai`.
   Enter the **SHA-1 certificate fingerprint of the actual APK installed on your
   phone**. Get it with Android SDK's `apksigner verify --print-certs YOUR.apk`.
   The Actions release build also prints certificate fingerprints in its logs.
   A different signing key needs its own Android OAuth client. Keep the Android
   client and web client in the same Google Cloud project.
7. This app uses the **web client ID** as the server audience. Do not substitute
   the Android client ID in the backend settings.

Official references: [Android Google sign-in](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation),
[server token validation](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token),
[OAuth web-server flow](https://developers.google.com/identity/protocols/oauth2/web-server).

## 4. Configure and start the gateway

1. In PowerShell in the repository folder, run:

   ```powershell
   py -3 server\kitty.py init
   ```

2. Copy `cloud\hosted\secrets.env.example` to `data\secrets.env`.
   Open it in Notepad. Fill the web client ID, web client secret and HTTPS origin:

   ```text
   GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   GOOGLE_WEB_CLIENT_SECRET=YOUR_WEB_CLIENT_SECRET
   KITTY_PUBLIC_URL=https://YOUR-KITTY-HOST
   ```

   Do not include angle brackets or quotes. Do not upload this file or show it in
   your presentation. The Groq/Gemini keys are added in the phone admin console later.
3. Double-click `START_CLOUD_KITTY.bat`. The first run installs the Python dependencies.
   Keep that window open. Google mode rejects the old owner/guest pairing tokens;
   each person signs in with Google instead.
4. Keep Funnel running. On the phone's mobile data, open:
   `https://YOUR-KITTY-HOST/health`. It should show `status: ok` and version `0.4.0`.
5. If this fails, resolve the gateway/tunnel first. If startup says a module is
   missing, run `py -3 -m pip install -r requirements-cloud.txt` again. If Turso
   is already configured, also install `requirements-turso.txt`.

## 5. Install, sign in and connect the providers

1. Install the new APK only if Android accepts its signature. If it rejects the
   update, follow the signing guide and back up before choosing a migration.
2. Open KITTY → **Settings → Sign in with Google · Cloud KITTY**.
3. Enter your gateway HTTPS address if prompted. Choose the admin Google account.
   Other users choose their own account; typing the admin email does not grant access.
4. Open **Settings → Open admin console**. Only the verified admin sees it.
5. Enter your Groq key from [Groq Console](https://console.groq.com/). Save.
6. Tap **Check available Groq models** and use a model available to your account.
   The initial chat model is `llama-3.3-70b-versatile`; replace it if your account
   does not offer it. Test a chat before sharing the app. Provider access and quotas
   can change; a configured key is not proof that billing/model access works.
7. Optional: enter a Gemini key from [Google AI Studio](https://aistudio.google.com/).
   The current speech adapter uses the Interactions API and defaults to
   `gemini-3.8-flash-lite-tts`, voice `Kore`. Check
   [Google's current speech documentation](https://ai.google.dev/gemini-api/docs/speech-generation)
   and your account access. If unavailable, KITTY falls back to the phone's voice.
8. Add personality preferences in the character field. KITTY's base identity already
   credits Virat. Saved instructions and memory customize behavior; they do not
   fine-tune Groq's weights or remove provider rules.
9. Regular users have chat/voice/memory preferences and cannot read or change keys.
   Keys are encrypted at rest on the backend; protect that host and its backups.

## 6. Optional Drive backup and training collection

1. Admin console → **Connect admin Google Drive**. Your browser opens Google's
   consent screen. Choose the same admin account and approve Drive file access.
   Return to KITTY after the success page. A Drive API key is not sufficient.
2. In each user's Settings, that user chooses whether to back up their chats and
   memories to Virat's Drive. Backup and training sharing default to **off**.
3. Backup uses an app-created JSON file named `KITTY-memory-USERID.json` in the
   admin Drive. It contains the latest **500 server-recorded turns** and up to
   **50 explicit memories**. It is not an unlimited archive of every historical turn.
4. The phone retains its own SQLite archive. `remember that I like tea`,
   `show my memories` and `forget memory 1234abcd` work locally after sign-in;
   changes retry when online. The memory display previews ten recent entries.
5. Training consent is separate and requires backup consent. Turning backup off
   queues deletion of the app's Drive copy; a failed network/permission request
   remains queued. It cannot recall files already downloaded by an administrator.
6. Admin console → **Export reviewed training sample** saves JSONL through Android's
   file picker. Only consenting users' positively rated or corrected **model**
   responses qualify. This bounded sample is at most about 200 KB; review it before
   any separate training process. No automatic model training runs.

Chat requests and recent context are processed by the gateway and Groq. Text sent
for cloud speech goes to Gemini. Drive backup is additional optional sharing with
the administrator, not an end-to-end encrypted private user vault. Do not collect
private presentation/tester data without explaining these choices.

## 7. Voice and background commands

1. Settings → **Offline speech setup**. Download the small Indian English Vosk ZIP
   from the shown official model link, then **Import downloaded model ZIP**.
   English/Indian-English commands are supported by this pack; it is not a promise
   of accurate Hindi recognition. Gemini here generates speech, not microphone transcription.
2. Allow microphone and notification permissions. Start **Hey Kitty** while KITTY
   is visible. Keep the foreground listening notification running.
3. For taps, enable KITTY's Accessibility service after reading its disclosure.
   Shizuku remains optional for supported coordinate input; it does not identify
   which video you intended.
4. Say **Hey Kitty, open YouTube**. After KITTY replies, the default ten-second
   follow-up window accepts **okay, play the first video** without repeating the
   wake phrase. Later, use **Hey Kitty** again.
5. **Play that video** uses a focused or sole identifiable result. If there are
   several results, name the title or say first/second; KITTY should not guess.
6. On Vivo, allow KITTY background operation/Auto-start and choose the least
   restrictive battery setting available under the app's system settings. Menu
   names vary by OS. Do not force-stop KITTY; Android stops its services/work then.
7. Other recording apps can take the microphone. Music/YouTube audio can mask
   your voice. KITTY pauses recognition during its own speech and reports a
   blocked microphone; it cannot override Android microphone privacy or guarantee
   wake recognition while another app records. Tap-to-talk is the fallback.
8. Test media playback, background wake and interruptions on **your actual Vivo**.
   Emulator checks do not measure real speech recognition or OEM battery behavior.

## 8. Notices and future app updates

Admin console → enter notice title/message → **Publish notice to all users**.
Users see it in Settings. With notification permission, an open app checks roughly
once a minute; Android WorkManager checks in the background at a requested minimum
of fifteen minutes. Battery/network restrictions may delay this. This is scheduled
notification delivery, not instant FCM push. It does not require microphone listening.

For APK updates follow [UPDATES_AND_RELEASES.md](UPDATES_AND_RELEASES.md). Configure
GitHub's repository **variable** `KITTY_SERVER_URL` to your HTTPS origin for signed
releases. New users then get a Google-first setup and do not type your server URL.
Keep the original signing key. Increment versions, tag and publish using the release
workflow. Users then press **Settings → Check for app updates** and approve Android's
installer. A notice by itself does not publish an APK.

## 9. Move to an always-on VM

Use a VM with Python 3.11+, persistent storage, SSH access and a public HTTPS address.
A small CPU VM can relay Groq; no GPU or local language-model download is needed.
The repository's `cloud/hosted/kitty-cloud.service` runs the gateway as a restricted
`kitty` user. Adapt the paths if your host differs.

On an Ubuntu VM you administer:

```bash
sudo apt update
sudo apt install python3-venv git
sudo useradd --system --home /var/lib/kitty --create-home kitty
sudo git clone https://github.com/psychspy7/KITTY.AI.git /opt/kitty-ai
sudo python3 -m venv /opt/kitty-ai/.venv
sudo /opt/kitty-ai/.venv/bin/pip install -r /opt/kitty-ai/requirements-cloud.txt
sudo -u kitty /opt/kitty-ai/.venv/bin/python /opt/kitty-ai/server/kitty.py --home /var/lib/kitty init
sudo -u kitty cp /opt/kitty-ai/cloud/hosted/secrets.env.example /var/lib/kitty/secrets.env
sudo nano /var/lib/kitty/secrets.env
sudo chmod 600 /var/lib/kitty/secrets.env
sudo cp /opt/kitty-ai/cloud/hosted/kitty-cloud.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now kitty-cloud
sudo systemctl status kitty-cloud
```

Fill `/var/lib/kitty/secrets.env` with the **VM's** HTTPS origin and Google configuration.
Install a trusted HTTPS reverse proxy such as [Caddy](https://caddyserver.com/docs/install)
and adapt `cloud/hosted/Caddyfile.example`. Point DNS at the VM and allow TCP 80/443
in the cloud firewall and OS firewall. Keep 8765 bound to localhost and restrict SSH.
Update Google's redirect URI and the APK's service URL to the new origin.

Before shutting down the laptop, back up and move its gateway data directory to the
VM under `/var/lib/kitty`, with ownership `kitty:kitty`. Move **both** `admin.key` and
`admin.sealed` together; without the key the stored provider/Drive credentials are
unreadable. Also move `accounts.sqlite3` and `kitty.sqlite3` consistently with the
services stopped. Secure the backup. Google grants tied to the old redirect URI may
need reconnecting. After migration, verify `/health`, admin login, second-user
isolation, chat, voice, consented backup and update checks using mobile data.

No VM is provisioned by simply downloading this repository. Its cloud account,
address, OAuth configuration and retained signing key must be supplied by the owner.
