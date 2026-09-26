# KITTY architecture

The phone handles speech, Android permissions and phone actions. The laptop handles language generation, memory, document retrieval and weather retrieval.

| Component | Address | Purpose |
| --- | --- | --- |
| llama.cpp | `http://127.0.0.1:8080/v1` | OpenAI-compatible local model API; alias `kitty` |
| KITTY gateway | `http://127.0.0.1:8765` | Android pairing, conversation, memory and feedback |
| Android client | Native Android 10+ app | Voice, chat, contacts, app intents and optional Accessibility |

The language model is Qwen3.5-4B-Abliterated in GGUF Q4_K_M format. It is a community derivative, not an official Qwen release with that suffix. Start with 4096 tokens. Increase the model process and gateway configuration together to 8192 after measuring memory and latency.

`/v1/chat/completions` and `/v1/models` are served by llama.cpp on port 8080. The phone gateway uses its own `/v1/chat` request contract on port 8765; it does not claim to implement the whole OpenAI API. An OpenAI-compatible protocol does not require an OpenAI account or paid API key.

## Command path

Recognized phone commands run through a local Android router. The app resolves contacts on the phone. The model's conversation output is displayed and spoken, not executed as code. The first alpha uses documented command patterns, rather than a general autonomous screen-planning model.

Accessibility enables explicit commands such as `tap Send`, `type hello`, `scroll down`, `go back`, and `lock the phone`. It inspects the active accessibility tree locally on demand; no screen tree is uploaded. The microphone service runs only after the owner starts listening mode and displays a notification with a stop action. Android can still stop it for resource or battery reasons.

## Data path

The pairing token is generated on the laptop and stored in Android Keystore-encrypted preferences. The laptop stores conversations, memories and feedback in `data/kitty.sqlite3`. Conversation retention is 30 days, purged at gateway startup. Explicit memories persist until deleted. `forget memory N` deletes the memory entry; earlier conversation history may still mention it until that history is deleted or expires. The database is local but not encrypted by this application; use the laptop's disk encryption.

Phone commands processed locally do not go to the laptop. A free-text request that the local router does not recognize does go to the laptop as conversation text. Weather city queries go to Open-Meteo; web search goes through the phone's selected browser. The model is not automatically trained on conversations or the internet.

For the first setup use USB with `adb reverse tcp:8765 tcp:8765`. Optional LAN HTTP is unencrypted and intended only for development on a trusted network. Prefer an encrypted private VPN or trusted HTTPS for wireless use. Do not forward ports 8080 or 8765 from the router to the public internet.

## Growth path

1. Test voice recognition, contacts, calls and screen actions on the actual phone.
2. Collect corrected conversation examples and measure latency, intent errors and battery use.
3. Evaluate a model-based action planner in a separate, observable execution path.
4. Fine-tune a matching full-precision base with LoRA, evaluate it, then convert and quantize a deployment copy to GGUF Q4_K_M.
5. Add PC and robot adapters using the same gateway contract.
