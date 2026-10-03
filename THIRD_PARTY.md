# Third-party components · KITTY Firebase Edition

Current direct Android dependencies are declared in `android/app/build.gradle`: Firebase Authentication, OkHttp, AndroidX Core and Credential Manager, Google ID and Android test libraries. Google Play services availability is required for Google sign-in. Device text-to-speech voices are provided by the user's Android installation, not bundled model weights.

The Firebase function runtime dependencies are locked in `functions/package-lock.json`:

- Firebase Admin SDK: Apache-2.0.
- Firebase Functions SDK: MIT.
- Firebase JavaScript SDK and Firebase rules test helpers: Apache-2.0; development/test use.

Transitive dependency license notices remain applicable. Review the upstream package/Maven license files when distributing a release. Groq, Gemini and OpenAI are optional hosted APIs with their own service terms and quotas; KITTY does not bundle their model weights.

No llama.cpp runtime, GGUF model, Vosk language model, Shizuku client or background microphone engine is included in the current APK/backend. Earlier editions remain in Git history.
