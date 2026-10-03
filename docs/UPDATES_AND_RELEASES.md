# KITTY 0.5 updates and signed releases

Settings → **Check for updates** reads `release/update.json` over HTTPS. A newer
version opens the immutable official GitHub release page in the browser. Android
handles downloading and installation. KITTY has no APK installer permission.

Follow the complete [Firebase/cloud/release setup guide](START_HERE_V05.md),
including the retained signing key, the `KITTY_SERVER_URL` and
`KITTY_FIREBASE_ANDROID_CONFIG` repository variables, and four signing secrets.
The Firebase variable must be `google-services.json`, never a private Admin SDK key.

For each release:

1. Increase `versionCode` and `versionName` in `android/app/build.gradle`.
2. Commit/push and wait for Verify KITTY to pass.
3. Tag exactly that version and push it, e.g. `git tag v0.5.1` followed by
   `git push origin v0.5.1`.
4. Check Build signed KITTY release. It requires configured Firebase, HTTPS origin
   and the retained signing key; it audits packaged permissions before publishing.
5. Confirm the tagged APK/checksum and `release/update.json` both appeared.
6. Test an in-place update from the previous release on a real phone.

Provider/character changes in Admin console do not require a new APK. Notices go
to Inbox and do not publish an APK. Server changes require updating the cloud VM
container while preserving its data volume.

Debug preview keys can differ across CI runs. Do not promise an in-place update
from an old debug APK. Android requires the same application ID and signing key.
Uninstalling deletes the local archive. Use Google Play tracks if you want store-
managed installs, subject to the publisher account and review requirements.
