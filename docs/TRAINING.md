> 0.2 saves phone turns and feedback locally, with an outbox for laptop sync. Starter authored examples are in `training/identity_examples.jsonl`; they have not been used to modify model weights.

# Personalize and train KITTY

No model weights have been fine-tuned by this project yet. The implemented personalization features are a system prompt, explicit memory, local document retrieval, and collection of approved/corrected answers. These are useful immediately; they are not weight training.

## Make the personality yours now

After setup, edit `data/personality.txt`. It already describes KITTY as female, calls the owner Sir, and asks for candour, wit, occasional dark existential humour, and natural English/Hindi/Hinglish. The gateway reloads this file for each model conversation request. It enforces the “Sir” address in displayed model replies. Voice gender depends on your installed TTS voice, not on the language-model weights.

Try changes to tone before training. Keep jokes occasional, and preserve the instruction to admit uncertainty and distinguish a requested phone action from a completed one. A model's “abliterated” label describes a community modification; it does not certify quality, factual accuracy, or compatibility with every task.

## Teach facts without retraining

Use `remember that ...` for small preferences. For your own or properly licensed text notes:

```powershell
py -3 server\kitty.py ingest .\my-notes.md
```

The gateway supports UTF-8 `.txt` and `.md` files up to 5 MB. It indexes chunks and retrieves matching excerpts using SQLite full-text search. This is a simple lexical retrieval system, not a semantic vector database. Re-ingesting the same path replaces that document's indexed chunks. It does not crawl the internet or learn facts permanently in the weights.

## Collect an actual training set

Use **Good reply** and **Correct it** under model answers. Corrections should contain the full ideal answer. The 0.2 upgrade keeps the archive until you explicitly change its retention. Export reviewed examples:

```powershell
mkdir training\exports
py -3 server\kitty.py export-feedback training\exports\review.jsonl
```

Only approved/corrected model conversations are exported. Deterministic phone actions, weather replies and unavailable-model errors are excluded. Review the file before training: remove passwords, private third-party messages, unreliable claims and examples that reward “done” without an observed action result. Publicly available text is not automatically licensed for training; record its source and license if you add external material.

Begin with a few hundred varied, carefully reviewed examples as an experiment, not a guaranteed quality threshold. Cover normal conversation, humour, Hindi/Hinglish, uncertainty, corrections and neutral practical tasks. Avoid making every answer sarcastic. Keep a separate set of prompts that never enters training, including phone-command accuracy checks. A low training loss alone does not tell you whether KITTY got better.

## Fine-tuning workflow

The GGUF Q4_K_M file is the deployment format. For the ordinary PEFT/TRL workflow, train an adapter on the matching Hugging Face weights, then produce a new GGUF. Do not treat the downloaded 4-bit GGUF as a normal Transformers training checkpoint.

1. Record the exact source model revision and its license. The selected quantization is derived from `wangzhang/Qwen3.5-4B-abliterated`; use compatible source weights and tokenizer. Keep the original untouched.
2. Create a separate training environment with a current, mutually compatible PyTorch, Transformers, PEFT, TRL, Datasets and Accelerate stack that supports the source model's Qwen3.5 architecture. Confirm the correct model class from its configuration. For QLoRA, use a supported bitsandbytes setup and `prepare_model_for_kbit_training`; hardware/OS support needs checking before installation.
3. Load the reviewed JSONL as a conversational dataset. Split by conversation/topic before training; deduplicate repeated prompts so near-identical answers do not leak into evaluation. Keep around 10–20% held out as an initial experiment.
4. Use TRL's `SFTTrainer` with PEFT LoRA. Starting experiment values: rank 16, alpha 32, dropout 0.05, learning rate `1e-4`, one epoch, sequence length 512–1024, micro-batch 1 and gradient accumulation 8. These are starting values, not a laptop-specific recommendation. QLoRA uses a training-compatible quantized source model, not the GGUF deployment file.
5. Train on assistant completions when the chat template supports the required masks. Inspect a rendered sample and masked tokens first; an incorrect chat template can make an apparently successful run train the wrong text.
6. Compare the adapter against the untouched base on held-out prompts. Measure factual correctness, personality consistency, unwanted verbosity and latency. Evaluate device-action routing separately; changing language weights does not add Android permissions or new executable tools.
7. Save the adapter. Reload the original source model in an appropriate precision and merge the adapter into a separate output directory, retaining tokenizer/chat-template files. Check the merged model before export.
8. Convert with a Qwen3.5-compatible llama.cpp checkout, then quantize:

```powershell
python .\llama.cpp\convert_hf_to_gguf.py .\training\runs\kitty-merged --outfile .\models\kitty-personal-f16.gguf --outtype f16
.\runtime\llama-quantize.exe .\models\kitty-personal-f16.gguf .\models\kitty-personal-Q4_K_M.gguf Q4_K_M
```

9. Test the new GGUF with `py -3 tools\start_model.py --model .\models\kitty-personal-Q4_K_M.gguf` (add GPU flags only for a compatible runtime). The alias and context stay consistent. Record the new file's checksum with your training run; keep the downloaded baseline and its publisher lock untouched for rollback.

For a modest laptop, inference can be practical while fine-tuning is not. GPU model, VRAM, RAM and free disk space determine whether to use LoRA, QLoRA, a smaller training experiment, or a separate training machine. Actual fine-tuning has therefore not been launched on the supplied i3-1315U / 8 GB laptop. The repository includes authored identity examples; collecting and reviewing a broader dataset is still required.

## Sources and further instructions

- [TRL SFTTrainer](https://huggingface.co/docs/trl/en/sft_trainer): dataset formats, loss masking and PEFT integration.
- [PEFT quantization guide](https://huggingface.co/docs/peft/en/developer_guides/quantization): training-compatible quantization and adapter preparation.
- [PEFT model merging](https://huggingface.co/docs/peft/en/developer_guides/model_merging): adapter merging options.
- [llama.cpp conversion and quantization](https://github.com/ggml-org/llama.cpp/tree/master/tools/quantize).
- [Original Qwen3.5-4B model card](https://huggingface.co/Qwen/Qwen3.5-4B).

All exported personal examples and training runs belong in the ignored `training/exports` and `training/runs` directories. No automatic training job runs in the background.
