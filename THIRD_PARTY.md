# Third-party components

Keep the licenses/notices shipped with downloaded runtimes and models.

| Component | Project / license reference |
| --- | --- |
| Vosk Android 0.3.75 | [Vosk API](https://github.com/alphacep/vosk-api), Apache-2.0 |
| JNA 5.18.1 | [JNA](https://github.com/java-native-access/jna), dual Apache-2.0 / LGPL-2.1-or-later licensing |
| Gradle wrapper 8.13 | [Gradle](https://github.com/gradle/gradle), Apache-2.0 |
| Qwen3.5 community derivative and GGUF | Publisher model cards linked in docs/SETUP_WINDOWS.md; cards identify Apache-2.0 |
| llama.cpp | [llama.cpp](https://github.com/ggml-org/llama.cpp), MIT |
| Small Vosk English model | [Vosk models](https://alphacephei.com/vosk/models), check the license for the exact model download |
| Open-Meteo | [Open-Meteo](https://open-meteo.com/), weather responses include attribution; review service terms before commercial deployment |

Downloaded model/runtime binaries are not committed. This repository does not grant a new license over third-party components or user data. Choose a license for the original KITTY source deliberately before distributing it as an open-source product.

## Added in 0.2

- [OkHttp](https://github.com/square/okhttp), 4.12.0: Apache License 2.0; cancellable HTTP/SSE transport. Includes Okio and Kotlin runtime dependencies.
- [Shizuku API/provider](https://github.com/RikkaApps/Shizuku-API), 13.1.5: MIT license; optional owner-authorized navigation/coordinate input. Shizuku itself is installed separately by the owner.
- [Vosk model list](https://alphacephei.com/vosk/models): Indian English small 0.4 and US English small 0.15 are separate downloads, listed under Apache 2.0. Language models and Android TTS voices are separate components.
- Optional fast GGUF publisher: [mradermacher/Qwen3.5-2B_Abliterated-GGUF](https://huggingface.co/mradermacher/Qwen3.5-2B_Abliterated-GGUF). The downloader pins the selected publisher revision, file and checksum on first use; inspect its model card/license with the downloaded lock.
