# KITTY 0.5: the beginner’s cloud setup guide

**This update removes laptop connections.** Your phone sends messages over the
internet to your cloud service. That service checks Firebase login and calls
Groq using the key you save in the admin console. Your laptop can be turned off.

The delivered verification APK is a **preview** until Firebase and the cloud
address are configured. “Service setup pending” is intentional. Do not present
that preview as a live service. Follow the setup below, build your configured
signed APK, then do the two-account checks at the end.

## What you need, once

| Item | What it does | Who configures it |
|---|---|---|
| Firebase project | Google login and trusted account identity | Virat |
| Small Linux cloud VM with persistent disk | Runs the API gateway all day | Virat |
| Domain/subdomain pointing to the VM | Gives everyone one HTTPS service address | Virat |
| Groq API key | Generates chat replies | Virat, inside the app’s admin console |
| Retained Android signing key | Makes future APK updates install over old versions | Virat |
| Optional Gemini API key | Generates voice playback; the phone TTS is a fallback | Virat |

A VM is a computer rented in the cloud. It runs your gateway, **not a local language
model**: Groq runs the model. A small 1–2 GB Linux VM is a starting point for a demo;
measure traffic before inviting a large audience. VM/domain/API costs and quotas
are separate. This guide does not promise a permanently free public service.

The cloud gateway stores data on its persistent disk. Do not deploy this image
on a host that deletes its disk on restart. A Turso replica does not replace the
accounts database or encrypted key vault.

## 1. Keep or create your Android release signing key

If you already have a production signing key, **use that exact key**. Do not replace
it. Android refuses an in-place update signed by another key. Do not uninstall an
old app just to fix a signing error: uninstalling removes its local chat archive.

If you only used temporary CI debug APKs, their signing keys may be different.
A new production release might require a fresh installation. Preserve/export
important chats before replacing an installation. Old laptop archives are retained
on disk by compatible upgrades but are not attached to a new Firebase user.

For a first production key, install Java 17 or Android Studio on your computer.
Open PowerShell in a private folder and run:

```powershell
keytool -genkeypair -v -keystore kitty-release.jks -alias kitty -keyalg RSA -keysize 3072 -validity 10000
```

Enter a strong password and keep the `.jks` file and password in a safe place.
Never upload the `.jks` to the repository. Make an encrypted offline backup.

Print the certificate fingerprints:

```powershell
keytool -list -v -keystore kitty-release.jks -alias kitty
```

Keep the displayed **SHA-1** and **SHA-256** certificate fingerprints. These are
public fingerprints, not your signing key. You will add both to Firebase next.

## 2. Create Firebase and enable Google login

1. Open [Firebase Console](https://console.firebase.google.com/) with your Google
   account. Create a project, for example “Kitty AI”. Analytics is optional and
   is not used by this app.
2. Note the **Project ID** in Project settings. It is a short ID such as
   `kitty-ai-12345`, not the friendly project name.
3. Add an **Android app**. Its package name must be **`com.kitty.ai`**.
4. Add your release certificate SHA-1 and SHA-256 fingerprints in the Android
   app’s settings. Register the fingerprints for the APK you actually distribute.
5. Open **Authentication → Sign-in method**, enable **Google**, select a support
   email, and save. Google sign-in must be enabled before downloading the final
   Android configuration.
6. Return to the Android app settings and download **`google-services.json`**.
   This public Android configuration contains the project IDs and web OAuth
   client ID. It is different from a private service-account JSON key.
7. If Google OAuth consent is in testing mode, add your admin and demonstration
   users as test users in the linked Google Cloud project. Complete any Google
   consent/publishing requirements before broader distribution.

Official references: [Android Google/Firebase login](https://firebase.google.com/docs/auth/android/google-signin),
[Android app registration](https://firebase.google.com/docs/android/setup).
This app initializes Firebase from the downloaded JSON values at build time;
you do not need to add the Google Services Gradle plugin manually.

**Your admin is `viratanand1221@gmail.com`.** The backend verifies the Firebase ID
token, Google provider and verified email; then pins the owner’s Firebase UID.
Changing an email or role in phone preferences does not grant server access.
Do not delete/recreate the admin Firebase account casually: the retained UID pin
will correctly reject a different identity. Restore the original account or perform
an audited owner migration on the host with a data backup.

## 3. Prepare the cloud VM and domain

Choose a cloud provider where you can create an always-running Ubuntu/Debian VM
with a persistent disk and public IP address. Do not use your laptop’s address.
You need access to that provider’s console to create the VM; this repository cannot
create an account or billing plan for you.

1. Create the VM and note its public IP.
2. In your domain provider’s DNS page, create an **A record** named `kitty` that
   points to that IP. Your address becomes `kitty.yourdomain.com`. Use your own
   real domain, not the example. Only add an AAAA record if IPv6 is configured.
3. Allow inbound **80/TCP**, **443/TCP** and optionally **443/UDP** at the provider
   firewall. Allow SSH **22/TCP** only from your own address where possible.
   Do **not** expose port 8765 publicly.
4. Connect to the VM using the provider’s SSH/terminal feature or Windows
   PowerShell: `ssh your-vm-user@YOUR_PUBLIC_IP`.
5. Install Docker Engine with its Compose plugin using the
   [official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/).
   The commands below assume your VM account can run Docker (`sudo docker`
   if your account needs sudo).
6. Install Git if necessary, then download this repository:

```bash
sudo apt-get update
sudo apt-get install -y git
git clone https://github.com/psychspy7/KITTY.AI.git
cd KITTY.AI/deploy
cp env.example .env
nano .env
```

In `nano`,
replace the three example values with your real values:

```dotenv
FIREBASE_PROJECT_ID=your-actual-firebase-project-id
KITTY_DOMAIN=kitty.your-real-domain.com
KITTY_PUBLIC_URL=https://kitty.your-real-domain.com
```

There is no trailing slash in the HTTPS origin. Save with **Ctrl+O**, Enter, then
exit with **Ctrl+X**. These are owner settings. Users will never enter this address.

## 4. Give the gateway access to Firebase verification

1. In Firebase **Project settings → Service accounts → Firebase Admin SDK**,
   generate/download a private service-account JSON key for your project.
2. Transfer that file privately to the VM’s `KITTY.AI/deploy/` directory and name
   it **`firebase-admin.json`**. Use the provider’s file upload or `scp` from your
   computer. Example PowerShell command:

```powershell
scp "C:\private\firebase-admin.json" your-vm-user@YOUR_PUBLIC_IP:KITTY.AI/deploy/firebase-admin.json
```

3. On the VM, set ownership/read permissions for the container user:

```bash
sudo chown 10001:10001 firebase-admin.json
sudo chmod 600 firebase-admin.json
chmod 600 .env
```

This private JSON stays on the host and is mounted read-only. **Never put it in
GitHub variables, an APK, Google Drive chat backups or a chat message.** The file
is ignored by Git. On a Google Cloud VM, Application Default Credentials can also
use an attached service identity; adapt the compose credentials mount if using
that route. The included compose setup uses the private JSON file.

Firebase Admin performs signed-token, project/audience and revocation checks.
The hosted entry point rejects Firebase Auth Emulator configuration.
Reference: [verify Firebase ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

## 5. Start the cloud service

Still in the VM’s `KITTY.AI/deploy` folder, run:

```bash
sudo docker compose up -d --build
sudo docker compose ps
sudo docker compose logs --tail=60 kitty https
```

The `kitty` service should become healthy. Caddy requests a trusted HTTPS
certificate after DNS points to the VM and ports 80/443 are reachable. This can
take a little time. Check:

```bash
curl --fail https://kitty.your-real-domain.com/health
```

Expected: JSON with `status: ok` and version `0.5.0`. No API key is necessary for
this health check. Chat/admin endpoints require a verified Firebase session.

The named `kitty-data` Docker volume retains accounts, chats, personal memories,
admin identity and the encrypted provider-key vault. Do not run
`docker compose down --volumes`: it deletes that data. Back up the **whole**
volume, including `admin.key` and `admin.sealed`, together. For a consistent backup,
stop the gateway while copying the volume and restart it afterward. A disk
snapshot/backup from your VM provider is a simple beginner option.

## 6. Configure GitHub to build your public APK

In [your KITTY repository](https://github.com/psychspy7/KITTY.AI), open
**Settings → Secrets and variables → Actions**.

Under **Variables**, create:

| Variable | Value |
|---|---|
| `KITTY_SERVER_URL` | Your HTTPS origin, e.g. `https://kitty.yourdomain.com` |
| `KITTY_FIREBASE_ANDROID_CONFIG` | The complete contents of `google-services.json` from step 2 |

This JSON is the **Android app configuration**, not `firebase-admin.json`.
The release workflow rejects service-account private keys in this field.

Under **Secrets**, create:

| Secret | Value |
|---|---|
| `KITTY_KEYSTORE_B64` | Base64-encoded contents of your retained `.jks` file |
| `KITTY_STORE_PASSWORD` | Keystore password |
| `KITTY_KEY_ALIAS` | `kitty`, or your actual existing alias |
| `KITTY_KEY_PASSWORD` | Key password |

In Windows PowerShell, print the Base64 value using:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\private\kitty-release.jks"))
```

Paste that output only into the repository secret. Never into a public issue.
Groq/Gemini keys are **not** GitHub variables; you will add them inside the app.

## 7. Build and install the configured release

The v0.5 source version is `0.5.0`, code `50`. Verify KITTY on main first. In a
local Git checkout connected to your repository, publish the version tag once:

```bash
git pull origin main
git tag v0.5.0
git push origin v0.5.0
```

A source ZIP has no Git history. If you downloaded a ZIP, first install Git and
use `git clone https://github.com/psychspy7/KITTY.AI.git` for these commands.
Do not reuse a tag that already has a release. If you need another public build,
increase the version and use a new tag, as explained below.

Open **GitHub → Actions → Build signed KITTY release** and wait for success.
The workflow restores Firebase config, embeds the fixed cloud URL, builds with
your retained signing key, audits permissions and publishes the release APK plus
checksum. It also updates the public update metadata.

Download **`KITTY-AI-release.apk`** from that tagged GitHub Release to your phone.
Android’s browser/file manager may ask you to allow installation from that source.
KITTY itself does not request installer permission. For wider distribution, Google
Play testing/production tracks provide a familiar signed install experience and
require their own publisher setup and review.

## 8. Connect Groq as admin

1. Open the configured release on your phone.
2. Tap **Continue with Google** and choose **`viratanand1221@gmail.com`**.
3. Let the app verify the account with the cloud service.
4. Open **••• → Settings → Admin console**.
5. Paste your Groq API key in the Groq key field. Choose a model available to
   your Groq project; **Available Groq models** reads your actual model list.
6. The default is `openai/gpt-oss-20b`, with low reasoning effort and streamed
   replies. `openai/gpt-oss-120b` is another supported choice when available.
   Older Llama defaults are no longer ordinary free/developer-tier choices;
   check [Groq’s model list](https://console.groq.com/docs/models) and
   [deprecations](https://console.groq.com/docs/deprecations).
7. Optional: add a Gemini key and speech model/voice. Test its access before
   enabling voice playback. If Gemini is unavailable, the app attempts phone TTS.
8. Save KITTY configuration, close the console and send **“Introduce yourself.”**
9. Send a normal question too. The creator reply is deterministic and does not
   test Groq connectivity; a normal question verifies real inference.

API key fields stay blank after saving; configured flags show their status. Keys
are encrypted on the host, never returned in API responses. The administrator
can replace the core creator and character. Defaults credit **Virat with the help
of Kitty Corp**, with a sassy, candid, mischievous character and occasional dark
wit. This is personality prompting, not fine-tuning or removal of provider rules.

## 9. What regular users see

Users install the same configured APK, tap Google login, then chat. They have
chat history, personal memory, Inbox, update checks, optional playback, privacy
choices and sign-out. They cannot choose a server, enter API keys, edit core
identity/personality, publish notices, connect Drive or export training data.

- **History** is the most recent 100 turns for that account on that phone.
- **“Remember that …”** stores personal memory and syncs it online.
- **“Show my memory”** lists it; **“Forget memory ID”** removes one.
- Google account switches keep local archives isolated. Signing out does not
  erase the archive. Uninstalling does.
- Chats are sent to the cloud gateway/provider for replies. Drive backup and
  training export sharing are explicit, optional privacy choices, off by default.
- **Inbox** fetches owner notices when opened. There is no background push or
  notification permission in this version.
- KITTY does not record your microphone, wake to “Hey Kitty”, tap other apps,
  read contacts or make calls. Those features were removed with their permissions.

## 10. Notices, optional Drive and later updates

**Notices:** Admin console → title/message → Publish to user inboxes. Users fetch
it from Inbox. A notice does not build or publish an APK.

**Drive:** Optional. Enable Google Drive API in your Google Cloud project. Create
/configure a web OAuth client with authorized redirect URI:
`https://your-kitty-domain/oauth/drive/callback`. Add its web client ID and secret
to the host’s `.env` as `GOOGLE_WEB_CLIENT_ID` and `GOOGLE_WEB_CLIENT_SECRET`.
Restart the gateway with `sudo docker compose up -d`. Use **Connect admin Drive**
in the app, with the same admin Google account. OAuth consent grants `drive.file`;
a Drive “API key” cannot authorize writing private user backups. Only users who
opt in are backed up. Exported training includes only consenting users’ positively
rated/corrected examples. Review that export before any separate training job.

**APK update:** Change `versionName` and increase `versionCode` in
`android/app/build.gradle`, commit/push, wait for verification, tag the new version
(e.g. `v0.5.1`) and push it. The signed release workflow uses the same retained
key, publishes the APK, and refreshes `release/update.json`. Users choose
**Settings → Check for updates → Open release**. Their browser/Android installer
handles downloading/installing. No silent install is implemented.

**Cloud code update:** On the VM, from the repository folder:

```bash
git pull --ff-only origin main
cd deploy
sudo docker compose up -d --build
sudo docker compose ps
```

Keep `.env`, `firebase-admin.json` and the data volume. Changing API keys or
character in the admin console takes effect without an APK update.

## 11. Before your presentation

Run these with the configured, production-signed APK and real cloud service:

1. Admin Google login succeeds. Admin console is visible.
2. A second Google account logs in. No admin controls, pairing or key fields appear.
3. A normal chat streams a real reply. Turn on playback and test audio separately.
4. “Who made you?” gives the requested attribution. A personal memory cannot
   change that core creator. Only the admin’s core editor can.
5. Save a memory as user A; switch to user B. Neither A’s memory nor history appears.
6. Publish a notice as admin. Check Inbox from the other account.
7. Turn your laptop off and use mobile data. Chat still works through the cloud.
8. Stop a reply, reopen the app, and check history. Rotate the phone and check
   that the composer/reply remain usable.
9. Check updates after a **newer signed release** is published. Android must
   accept the original signing key. Do not claim a debug APK is a production update.
10. Verify the APK scans you actually use. Removing sensitive permissions reduces
    exposure; it cannot certify every scanner or remove ordinary sideload warnings.

CI covers server tests, mocked Firebase boundaries, Android unit tests/lint,
packaged permissions, emulator onboarding and test-only native layout fixtures.
It does not log in to your real Google account or verify your real API quota,
cloud domain, Gemini acoustics or a physical Vivo. Those require this final live
check after you create the project/host.

## Troubleshooting

| What you see | What to check |
|---|---|
| Service setup pending | You installed the unconfigured preview. Set both GitHub variables and build a configured release. |
| Google login fails | Google Auth enabled, correct package/fingerprints, JSON re-downloaded, Play services updated, test users allowed. |
| Chat cannot connect | `/health` works through HTTPS, VM running, DNS correct, internet available. |
| Admin console absent | Correct Google account; backend Project ID matches app; Firebase service credentials can verify tokens; original admin UID retained. |
| Chat asks admin to connect service | Save a valid Groq key/model in admin console; test a normal question. |
| Quota or model error | Check Groq billing/limits and available models; retired model IDs can fail. |
| No audio | Enable read-aloud, turn up media volume, test Gemini access, check Android TTS voice availability. No microphone is used. |
| Update check shows no newer release | Editing code alone does not publish an APK. Complete a newer signed tag/release. |
| Android rejects installation | Same app ID and signing key are required; do not delete your old archive to bypass the error. |
