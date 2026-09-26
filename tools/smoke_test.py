"""Run on your laptop after starting llama.cpp; does not use a paid API."""
import importlib.util
import json
from pathlib import Path
import time

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("kitty",ROOT/"server/kitty.py")
k=importlib.util.module_from_spec(spec);spec.loader.exec_module(k)

if __name__=="__main__":
    k.initialize(ROOT/"data");cfg=json.loads((ROOT/"data/config.json").read_text());base=cfg["model_base_url"].rstrip("/")
    print("Loaded model API:",k.remote_json(base+"/models",timeout=10))
    messages=[{"role":"system","content":k.SYSTEM},{"role":"user","content":"Introduce yourself in one witty sentence."}]
    started=time.perf_counter()
    reply=k.remote_json(base+"/chat/completions",{"model":cfg["model"],"messages":messages,"stream":False,"max_tokens":100,"chat_template_kwargs":{"enable_thinking":False}},timeout=120)
    print(reply["choices"][0]["message"]["content"])
    print(f"Round-trip time: {time.perf_counter()-started:.2f} seconds")
    print("Usage:",reply.get("usage",{}))
    print("This checks generation only. Phone actions need testing on your phone.")
