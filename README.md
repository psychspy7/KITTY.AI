# KITTY AI

Personal Android assistant with a laptop-hosted brain.

## Runtime target

- Model: Qwen3.5-4B-Abliterated (community derivative; exact artifact recorded in the model manifest).
- Format: GGUF, Q4_K_M.
- Runtime: llama.cpp, OpenAI-compatible API on 127.0.0.1:8080/v1.
- Initial context: 4096 tokens; 8192 after checking laptop memory and latency.
- Phone gateway: private Python service on port 8765, authenticated with a locally generated pairing token.

## Development checkpoints

This repository is being populated in tested stages. The Android client, laptop gateway, setup instructions, and test results will be committed separately. This is an early personal-use alpha, not a completed general-purpose phone agent.

## Data

Model weights, pairing tokens, personal memories, message contents, credentials, and local training exports must remain outside Git. No cloud AI key is required for the local model.
