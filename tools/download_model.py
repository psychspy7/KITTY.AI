"""Download the requested GGUF and lock its publisher revision and SHA-256."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT=Path(__file__).resolve().parents[1]
REPO="mradermacher/Qwen3.5-4B-abliterated-GGUF"

def sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):h.update(chunk)
    return h.hexdigest()

def select(info):
    revision=info.get("sha","")
    if not re.fullmatch(r"[0-9a-f]{40}",revision):raise ValueError("Publisher revision missing")
    files=[f for f in info.get("siblings",[]) if f["rfilename"].lower().endswith("q4_k_m.gguf") and "mmproj" not in f["rfilename"].lower()]
    if len(files)!=1:raise ValueError("Expected exactly one unsplit Q4_K_M GGUF; inspect the model repository")
    item=files[0];lfs=item.get("lfs",{})
    digest=lfs.get("sha256",lfs.get("oid",""))
    if not re.fullmatch(r"[0-9a-f]{64}",digest):raise ValueError("Publisher SHA-256 missing; download was not started")
    return {"repository":REPO,"revision":revision,"filename":item["rfilename"],"sha256":digest,"size_bytes":lfs.get("size",item.get("size")),"quantization":"Q4_K_M","base":"wangzhang/Qwen3.5-4B-abliterated"}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--refresh-lock",action="store_true");args=p.parse_args()
    (ROOT/"data").mkdir(exist_ok=True);(ROOT/"models").mkdir(exist_ok=True)
    lock=ROOT/"data/model.lock.json"
    if lock.exists() and not args.refresh_lock:
        meta=json.loads(lock.read_text())
    else:
        req=Request("https://huggingface.co/api/models/"+REPO+"?blobs=true",headers={"User-Agent":"KittyAI/0.1"})
        with urlopen(req,timeout=30) as r:meta=select(json.load(r))
        lock.write_text(json.dumps(meta,indent=2)+"\n",encoding="utf-8")
    if meta.get("repository")!=REPO or not re.fullmatch(r"[0-9a-f]{40}",meta.get("revision","")) or not re.fullmatch(r"[0-9a-f]{64}",meta.get("sha256","")):
        raise ValueError("Invalid model lock; inspect data/model.lock.json")
    target=ROOT/"models"/Path(meta["filename"]).name
    if target.exists() and sha256(target)==meta["sha256"]:
        print("Model already verified:",target);return
    url="https://huggingface.co/"+REPO+"/resolve/"+meta["revision"]+"/"+quote(meta["filename"],safe="/")+"?download=true"
    print("Publisher:",REPO);print("Revision:",meta["revision"]);print("File:",meta["filename"])
    print("Downloading the roughly 2.8 GB model. Leave this window open.")
    partial=target.with_suffix(".gguf.part");total=0;last=-1
    try:
        with urlopen(Request(url,headers={"User-Agent":"KittyAI/0.1"}),timeout=120) as response,open(partial,"wb") as out:
            while chunk:=response.read(1024*1024):
                out.write(chunk);total+=len(chunk);step=total//(128*1024*1024)
                if step!=last:print(f"{total/1024**2:.0f} MiB received",flush=True);last=step
        if meta.get("size_bytes") and total!=meta["size_bytes"]:raise ValueError("Download size mismatch")
        if sha256(partial)!=meta["sha256"]:raise ValueError("Checksum mismatch; model was not installed")
        os.replace(partial,target);print("SHA-256 verified. Model ready:",target)
    finally:
        if partial.exists():partial.unlink()

if __name__=="__main__":main()
