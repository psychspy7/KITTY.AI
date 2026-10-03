# KITTY AI 0.6 — Firebase setup for beginners

KITTY now uses **Firebase Authentication + Cloud Firestore + a Firebase HTTPS Function**. Groq, Gemini or optional OpenAI supplies the model. Your PC is needed for initial setup/builds and future deployments only. After deployment you can close it; users chat from their phones over the internet.

**The downloadable preview is not connected to a Firebase project.** It deliberately shows **Service setup pending**. An APK cannot invent your project, Google sign-in credentials or model keys. Complete the steps below to build your configured APK.

## What does each part do?

| Part | Purpose | Where it runs |
| --- | --- | --- |
| APK | Premium chat interface, Google login, local account archive, optional reply playback | Android phone, Android 10 or newer |
| Firebase Auth | Signs in users with their Google accounts | Firebase |
| `kittyApi` Function | Verifies users, selects a model, builds KITTY's prompt and streams replies | Firebase / Google Cloud |
| Firestore | Saves account chat history, personal memory, encrypted provider keys and admin settings | Firebase |
| Groq / Gemini / OpenAI | Generates model replies | The selected provider |
| Secret Manager | Holds the encryption key used to protect provider keys | Google Cloud |

The core prompt is sent **with the question before generation**. You do not need to pay for a second call to rewrite every answer. This is character/prompt configuration, not fine-tuning. Saving a conversation does not train model weights. Provider policies still apply.

The default creator is **Virat with the help of Kitty Corp**. The default personality is helpful, witty, sassy and mischievous. Only the verified owner can edit the stored creator/character settings. A user's personal memories cannot edit those settings.

## 1. Get the new project folder

Download the repository ZIP, extract it and open the folder containing `firebase.json`, `functions`, `android` and `README.md`. Do not work inside the ZIP.

Old `server`, `cloud`, `deploy` and Python instructions are historical source retained for reference. **Do not run them for version 0.6.** This version does not use a VM, laptop IP, Docker, Caddy, Turso, USB forwarding or a local language model.

For pushing future updates, use Git instead of a ZIP. In PowerShell:

```powershell
git clone https://github.com/psychspy7/KITTY.AI.git
cd KITTY.AI
```

If Git is not installed yet, you can complete Firebase setup from the extracted ZIP. Clone a proper repository later before using `git push`.

## 2. Install setup tools once

Install **Node.js 22 LTS**, **Git** and **Java JDK 17** on Windows. Android Studio is optional if you build APKs using GitHub Actions, as described below.

Open a NEW PowerShell window and check:

```powershell
node --version
npm --version
java -version
```

Node should show `v22...`. From the project root:

```powershell
npm install -g firebase-tools
firebase login
```

A browser will open. Sign in to the Google account that owns your Firebase project. Use `viratanand1221@gmail.com` for the project owner and KITTY admin. Never paste passwords or private API keys into chat or the public repository.

## 3. Create Firebase and enable billing

1. Open https://console.firebase.google.com/ and create a project. Choose a recognizable name such as **KITTY AI**.
2. Google Analytics is optional; KITTY does not require it.
3. In Project settings → General, copy the **Project ID**. This is not the display name. Example: `kitty-ai-12345`.
4. Upgrade to **Blaze** if you decide to deploy this backend. Firebase Functions deployment requires billing. Model calls, Firestore and cloud execution can have costs; this is not a guaranteed free service.
5. In Google Cloud Billing, create a budget and email alerts. **A budget alert is not a spending cap.** This backend starts with zero warm instances, a maximum of three instances, 12 new chat requests per minute and 200 per day per account. Those limits reduce usage but do not guarantee a maximum bill.
6. Firebase → Build → Firestore Database → Create database. Use the **default database**, **Standard edition / Native mode**, and **production mode**. Choose a location near your users; for India a location near Mumbai is suitable. Database location is difficult to change later.
7. Firebase → Build → Authentication → Get started → Sign-in method → Google → Enable. Select your support email and Save.

You do not need Firebase Realtime Database or Cloud Storage for this text-chat version.

## 4. Create and keep your Android signing key

If you have the original signing key for the installed KITTY app, retain that key. Android requires the same signing certificate for in-place updates. Do not replace it casually.

If this is a genuinely fresh installation, create your owner key from PowerShell:

```powershell
keytool -genkeypair -v -keystore kitty-release.jks -alias kitty -keyalg RSA -keysize 2048 -validity 10000
```

Enter passwords when prompted and keep them safe. Back up the `.jks` outside the repository. Losing it prevents normal updates to installed copies. It is ignored by Git but should still be handled as a private file.

Display its Firebase fingerprints:

```powershell
keytool -list -v -keystore kitty-release.jks -alias kitty
```

Copy **SHA1** and **SHA256**. These fingerprints are public; the keystore and passwords are private.

The provided preview is debug-signed and may have a different signature from earlier APKs. It is not a signed production update. Do not uninstall an existing app simply to bypass a signature error and lose its local chats. Keep it until you have a planned migration or the matching original key.

## 5. Register the Android app in Firebase

1. Project settings → General → Your apps → Add app → Android.
2. Android package name: **`com.kitty.ai`** exactly.
3. Nickname: **KITTY Android**.
4. Add the release signing SHA1, then add SHA256 in app settings.
5. Download **`google-services.json`** AFTER Google sign-in is enabled and fingerprints are registered.
6. Put it at `android/app/google-services.json`.

This JSON is public Android project configuration, not a provider secret. It should contain an Android client for `com.kitty.ai` and a Web OAuth client (`client_type: 3`). The build stops if it is incomplete.

If you will run Android Studio debug builds, also add that debug certificate's SHA1/SHA256, then download the JSON again. Do not confuse a service-account private-key JSON with this Android configuration. No service-account file is needed in the APK or this Firebase deployment.

## 6. Deploy Firebase backend once

From the ROOT folder containing `firebase.json`:

```powershell
firebase use --add
```

Select the project you just created and name the alias `default`. This creates a local `.firebaserc`; never guess another person's Project ID.

Install backend dependencies:

```powershell
npm ci --prefix functions
npm test --prefix functions
```

Create a parameters file. Replace `YOUR_PROJECT_ID` with the exact ID, including in the filename:

```powershell
Copy-Item functions/.env.example functions/.env.YOUR_PROJECT_ID
```

Open that new file in Notepad. Set:

```dotenv
KITTY_REGION=asia-south1
KITTY_ADMIN_EMAIL=viratanand1221@gmail.com
KITTY_ADMIN_UID=
```

Leave UID empty temporarily. This means **nobody has app-admin access yet**. You will pin your own UID in step 8.

Create the encryption secret without printing it:

```powershell
node -e "require('fs').writeFileSync('.kitty-vault-key.local',require('crypto').randomBytes(32).toString('hex'))"
firebase functions:secrets:set KITTY_VAULT_KEY --data-file .kitty-vault-key.local
```

Store an encrypted backup of this file safely. Do not commit it. **Do not regenerate the vault key after saving provider keys**, or previously encrypted keys cannot be decrypted. Rotation needs a deliberate decrypt/re-encrypt migration or provider-key reset.

Deploy:

```powershell
firebase deploy --only functions:kittyApi,firestore
```

If prompted about enabling required Google Cloud APIs, let Firebase enable them for this project. The function uses the platform's service identity and Firebase Admin SDK. No always-on PC process is needed.

The API URL will be:

```text
https://asia-south1-YOUR_PROJECT_ID.cloudfunctions.net/kittyApi
```

Open this URL with `/health` appended in your browser. Expect JSON containing `ok: true`, `version: 0.6.0` and `backend: firebase`. `/v1/...` routes require a real Firebase Google session and are not supposed to work anonymously in a browser.

Keep the Firestore rules supplied in this repo. Direct phone reads/writes are denied, including forged admin claims. The function checks identity/ownership itself. Do not switch Firestore to test mode to fix a login problem.

## 7. Build your configured, signed APK using GitHub Actions

The easiest build route is GitHub Actions; you do not need Android Studio or a PC model server.

Repository → Settings → Secrets and variables → Actions:

**Variables tab**:

| Name | Value |
| --- | --- |
| `KITTY_FIREBASE_ANDROID_CONFIG` | Entire contents of your downloaded `google-services.json` |
| `KITTY_FIREBASE_REGION` | `asia-south1` |

The function region and Android build region must match. The APK derives its URL from your Project ID and region; users cannot edit it.

**Secrets tab**:

| Name | Value |
| --- | --- |
| `KITTY_KEYSTORE_B64` | Base64 of your owner `.jks` |
| `KITTY_STORE_PASSWORD` | Keystore password |
| `KITTY_KEY_ALIAS` | `kitty`, or your retained key's actual alias |
| `KITTY_KEY_PASSWORD` | Key password |

To create the Base64 file locally:

```powershell
[IO.File]::WriteAllText('kitty-keystore-base64.local', [Convert]::ToBase64String([IO.File]::ReadAllBytes('kitty-release.jks')))
```

Copy its contents into the **secret**, not a repository variable or source file. Delete the temporary Base64 file once the secret is saved. Keep the original keystore backup.

For the first configured TEST build:

1. Open repository → **Actions** → **Build configured KITTY APK**.
2. Click **Run workflow**, select `main`, then run it.
3. Wait for the green check. This reads your public Firebase config and private signing secrets, builds the APK and audits its permissions. It **does not publish a release**.
4. Open that workflow run. Under **Artifacts**, download **KITTY-configured-signed-apk**.
5. Extract the ZIP and install `app-release.apk` through Android's normal installer. This is the configured test app you use in steps 8 and 9.

Once real login, admin access and API chats have passed those checks, publish the first version from an actual Git clone:

```powershell
git pull --ff-only origin main
git tag v0.6.0
git push origin v0.6.0
```

This triggers **Build signed KITTY release**. If that tag already exists, do not overwrite it; increment the source version name AND version code, then use a new tag. The workflow requires an exact matching tag.

When the release workflow succeeds, open repository → Releases → your version. Download **KITTY-AI-release.apk**. Share that signed APK with users. KITTY itself has no installer permission.

Alternative: with Android Studio/SDK and Java 17 configured, set `KITTY_KEYSTORE_PATH`, `KITTY_STORE_PASSWORD`, `KITTY_KEY_ALIAS`, `KITTY_KEY_PASSWORD` and `KITTY_FIREBASE_REGION` in your local environment, then run `android/gradlew.bat -p android assembleRelease`. Do not build without a signing key and call the unsigned APK an update.

## 8. Pin your admin login, then connect model keys

1. Open the configured APK and tap **Continue with Google**.
2. Select **viratanand1221@gmail.com**. It initially appears as a regular user because UID is empty.
3. Firebase console → Authentication → Users → find your email. Copy its **User UID**.
4. Put that UID into `KITTY_ADMIN_UID` in `functions/.env.YOUR_PROJECT_ID`.
5. Redeploy:

```powershell
firebase deploy --only functions:kittyApi
```

6. Sign out/in in KITTY, then Settings → **Admin console** should appear. Both the verified Google email and pinned UID are required; simply changing a phone setting or email extra cannot grant admin access.
7. Paste a **Groq key** in Admin console. The default chat model is `openai/gpt-oss-20b` on Groq. Set provider `groq`.
8. Optionally paste a **Gemini key**. It can provide fallback chat and optional speech. Default chat `gemini-3.5-flash-lite`, speech `gemini-3.8-flash-lite-tts`, voice `Kore`. Confirm model availability/quota in your own provider account before relying on it.
9. Optional OpenAI key/model is also supported through its fixed official chat API endpoint. Choose a chat-completions-compatible model available to your key. Default is `gpt-4.1-mini`; it is an optional additional paid provider, not required for KITTY.
10. Fallback choices: `groq`, `gemini`, `openai`, or `none`. A fallback is attempted only when the primary fails BEFORE visible reply tokens; partial answers are never silently duplicated using a second model.
11. Edit creator/character if desired. Leave key fields blank to retain saved keys. **Remove provider keys** explicitly disconnects them. Saving settings never returns plaintext keys to the phone.
12. Save and ask a normal question such as **Explain why the sky is blue in two sentences**. This tests a real API call. **Introduce yourself** tests the deterministic core identity, so it does not prove the model key works.

The prompt controls style and identity; it cannot make a model ignore its provider's rules or guarantee perfect accuracy. This APK has typed chat and optional audio playback. Use your keyboard's own dictation if desired. It does not request microphone access, listen to “Hey Kitty”, control YouTube, make calls, or perform live web searches. Those are separate integrations, not capabilities granted just by adding a model key.

## 9. Test with a second user before sharing

Sign in with a different Google account on another phone (or sign out and switch accounts):

- It must see chat, History, Inbox and basic Settings, with **no Admin console and no API-key fields**.
- Ask a real question. Watch the reply stream in.
- Stop a long reply and send another question.
- Ask **Who made you?**. It should identify Virat with the help of Kitty Corp.
- Say **remember that I prefer short explanations**. On the next online chat, its personal memory is supplied as context.
- Open History. It merges up to 100 recent Firebase turns with the local archive. A new phone can view the same account's cloud history. Old devices' chats from a different backend/account scope are not automatically assigned to this Firebase account.
- Switch accounts. Another user's chats and memory must not appear.
- Turn on **Read replies aloud**. Test Gemini speech and Android TTS fallback. Availability depends on Gemini quota and installed device voices. Playback stops when leaving the app.
- Publish a notice in the admin console; the second user should see it after opening Inbox.

Do not present the app as live until these checks pass against your real Firebase project and provider accounts. Emulator tests cannot validate your actual Google certificate setup, IAM billing, model quota or phone vendor behaviour.

## 10. Data and privacy

Firestore stores per-user turns under `users/UID/turns`, request retry records under `users/UID/requests`, consent on `users/UID`, and bounded explicit memory under `users/UID/state/memory`. Provider/core configuration lives in `private/config` with encrypted key material; the decryption secret is in Secret Manager.

Users' app sessions cannot read other accounts. **Firebase project owners remain database administrators** and can access project data through the cloud console/IAM. This is not end-to-end encrypted chat. Tell users what you store before sharing the app.

Cloud chat persistence is part of the service. Training export is a separate opt-in, disabled by default. Only server-generated replies marked Useful or corrected AND from currently consenting users are exportable. A JSONL export is a reviewed dataset; running a training job is a separate task. No automatic fine-tuning or Drive synchronization runs in this version.

No microphone, contacts, call, notification, foreground-service, package-install, accessibility or Shizuku permissions are requested. Removing those permissions cannot guarantee Play Protect/store acceptance. Notices are fetched in the in-app Inbox; this permission-light build does not send background push notifications.

## 11. Push updates later

There are two kinds of update:

**Model key / model choice / core character / creator / pause / notice**: save in Admin console. These server settings affect the next request; no new APK is needed.

**Backend code**: edit `functions`, run its tests, then `firebase deploy --only functions:kittyApi,firestore` from the project root. Firestore/rules changes should be reviewed before deploying.

**APK/UI code**: edit source and increase both `versionCode` and `versionName` in `android/app/build.gradle`, commit/push, wait for Verify KITTY, then push the matching NEW `vX.Y.Z` tag. The release workflow builds with your retained signer, publishes the APK/checksum and updates `release/update.json`. Users tap Settings → Check for updates → open the official release page → download/install. There is no silent installer or automatic APK update just because you pushed any commit.

Example for a future `0.6.1` / code `61`:

```powershell
git add android functions docs
git commit -m "Improve KITTY chat experience"
git push origin main
# Wait until Verify KITTY succeeds, then:
git tag v0.6.1
git push origin v0.6.1
```

If this folder came from a ZIP, `git push` will not work until you use a real Git clone. Do not delete your cloud data or signing key when updating source.

## Troubleshooting

| What you see | Check |
| --- | --- |
| Service setup pending | APK was built without complete `google-services.json`. Add the public configuration and rebuild; users cannot repair this in Settings. |
| Google login fails | Correct package `com.kitty.ai`, Google provider enabled, Web client ID present, exact APK certificate SHA1/SHA256 added, Play services updated, Google account present. Download config again after changes. |
| Login works, no Admin console | Verified email AND pinned Firebase UID match, parameters file has the exact Project ID, function redeployed, then sign out/in. |
| Can't reach KITTY | `/health` works over HTTPS, matching project/region, Functions deployment succeeded, internet works, no proxy blocking `cloudfunctions.net`. |
| Model error | Correct key/model/provider, provider quota/billing, allowed model list. Deterministic intro alone does not test inference. |
| Memory/context unavailable | Sign in, allow time for memory outbox sync, same Firebase UID. Core settings remain separate. |
| Function deploy fails | Blaze enabled, Node 22, correct Firebase project, necessary IAM permission, Secret Manager secret created, Firestore default database exists. |
| Permission denied in Firestore console/client | Client SDK denial is intentional; app uses the function. Verify function service identity/IAM instead of allowing public writes. |
| APK cannot update installed version | Signing key mismatch or lower version code. Retain the original key. Don't erase history to work around it. |
| Check updates reports none | Only a successful newer signed release updates public metadata. A source commit or preview build is not a release. |

Official references: [Firebase Functions setup](https://firebase.google.com/docs/functions/get-started), [secret parameters](https://firebase.google.com/docs/functions/config-env), [Google login on Android](https://firebase.google.com/docs/auth/android/google-signin), [Groq models](https://console.groq.com/docs/models), [Gemini models](https://ai.google.dev/gemini-api/docs/models).
