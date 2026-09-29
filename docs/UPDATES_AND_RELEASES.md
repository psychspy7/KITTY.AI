# App updates and signed releases

KITTY v0.3 has an **Update** button in the Android header and in Settings. It reads `release/update.json` over HTTPS. When a newer version code is published, it downloads the immutable tagged GitHub APK, checks SHA-256, application ID, version and signing certificate against the installed app, then opens Android's installer. Android asks you to approve the install. Incompatible signatures are blocked before installation.

Android accepts an in-place update only when the new APK uses the same application ID and the same signing key. Keep the original `KITTY-AI` keystore in a password manager and make an encrypted offline backup. Never commit the keystore or its passwords.

## First-time GitHub setup

The repository includes `.github/workflows/release.yml`. Add these repository secrets in GitHub → **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KITTY_KEYSTORE_B64` | Base64 of the retained `.jks` signing key |
| `KITTY_STORE_PASSWORD` | Keystore password |
| `KITTY_KEY_ALIAS` | The key alias |
| `KITTY_KEY_PASSWORD` | Key password |

On Windows PowerShell, create the first value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\kitty-release.jks"))
```

An installed v0.2 debug APK can only receive future in-place updates if the signed release uses the **exact same signing key**. Each CI runner may use a different debug key. If no retained key matches the currently installed APK, the update button will explain why it cannot proceed. Back up the phone's data before considering a replacement; do not uninstall without a backup.

## Publish a release

1. Increase `versionCode` and `versionName` in `android/app/build.gradle`.
2. Add the user-facing notes to `release/update.json` only if you want to preview them; the release workflow writes the final checksum and version.
3. Commit and push the version change.
4. Create and push a tag, for example:

```bash
git tag v0.3.0
git push origin v0.3.0
```

The workflow builds a signed APK, publishes `KITTY-AI-release.apk` to the GitHub release, calculates its SHA-256, and updates `release/update.json` on `main`. Open the Actions run and confirm both the release and manifest update succeeded before pressing **Update** in the phone app.

The manifest is published only after the tag's server tests, Android tests/lint, signed build and release publication succeed. Check the Actions run before using Update. The update checker does not silently install or execute anything.
