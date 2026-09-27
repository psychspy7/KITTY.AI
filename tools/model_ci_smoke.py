"""Linux CI integration check with the real downloaded GGUF; no fine-tuning."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))
from kitty import Brain, initialize, remote_json


def main():
    if os.name != "posix" or os.environ.get("GITHUB_ACTIONS") != "true":
        raise SystemExit("This runner uses a disposable Linux GitHub Actions checkout. On your laptop use tools/smoke_test.py with your running model.")
    parser=argparse.ArgumentParser();parser.add_argument("--profile",choices=["default","fast"],default="default");args=parser.parse_args()
    output = ROOT / "build/model-smoke"
    output.mkdir(parents=True, exist_ok=True)
    home = ROOT / "data"
    path = initialize(home)
    cfg = json.loads(path.read_text())
    cfg.update(max_tokens=128, model_timeout=180, context_window=4096)
    path.write_text(json.dumps(cfg))
    lock = json.loads((home / ("model-fast.lock.json" if args.profile=="fast" else "model.lock.json")).read_text())
    (output / "model.lock.json").write_text(json.dumps(lock, indent=2)+"\n")
    with (output / "llama.log").open("w") as log:
        process = subprocess.Popen([sys.executable, str(ROOT/"tools/start_model.py"), "--threads", "2", "--profile", args.profile],
                                   stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic()+300
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Model process stopped; inspect llama.log")
                try:
                    models = remote_json(cfg["model_base_url"]+"/models", timeout=3)
                    if any(m.get("id") == "kitty" for m in models.get("data", [])):
                        break
                except Exception:
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Model did not become ready within five minutes")
            brain = Brain(home)
            brain.store.remember("The owner prefers concise replies.")
            started = time.perf_counter()
            chunks=[]
            def event(kind,data):
                if kind=="token":chunks.append(data["text"])
            result = brain.chat({"text":"What is two plus two? Reply in one short sentence.",
                                 "session":str(uuid.uuid4()), "request_id":str(uuid.uuid4())}, on_event=event)
            elapsed = time.perf_counter()-started
            report = {"model": lock, "runtime": "llama.cpp b11146", "platform": "GitHub Ubuntu CPU, 2 inference threads",
                      "context_window":4096, "max_tokens":128, "elapsed_seconds":round(elapsed, 3),
                      "streamed_chunks":len(chunks), "profile":args.profile, "result":result, "scope":"Real GGUF loading, tokenizer/template endpoints and streaming generation through KITTY. No phone, fine-tuning or laptop performance certification."}
            (output / "result.json").write_text(json.dumps(report, indent=2)+"\n")
            print(json.dumps(report, indent=2), flush=True)
            assert chunks, "No streamed answer tokens received"
            assert "".join(chunks).strip() in result.get("reply", ""), "Streamed and final reply differ"
            assert result.get("mode") == "model", "The real model did not answer through the gateway"
            assert "Sir" in result.get("reply", ""), "Owner address missing"
            assert not result.get("actions"), "Conversation unexpectedly emitted a device action"
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=5)


if __name__ == "__main__":
    main()
