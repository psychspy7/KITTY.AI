# KITTY Firebase updates

For current version 0.6, use [START_HERE_V06.md](START_HERE_V06.md), especially steps 7 and 11.

- Admin settings, provider keys and notices update on the next request without an APK rebuild.
- Backend code is deployed to Firebase Functions and Firestore from the project root.
- UI/APK changes require an increased version name/code and a new matching Git tag. The release workflow uses the retained owner signer, publishes the APK and updates the public manifest.
- Settings → Check for updates opens the official release page. Android's normal installer handles installation.
- A source push or preview build alone is not a published app update.
- Do not replace the original signing key or erase local chats to bypass a signature mismatch.

The old VM/laptop deployment guides describe older releases, not the Firebase edition.
