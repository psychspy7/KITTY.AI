"""Start the downloaded GGUF in a separately installed llama.cpp runtime."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--gpu-layers",type=int,default=0,help="0 for CPU; use 99 with a supported GPU runtime")
    p.add_argument("--threads",type=int,default=max(1,min(4,os.cpu_count() or 4)),help="Starting point; benchmark 2, 4 and 6 on your CPU")
    p.add_argument("--profile",choices=["default","fast"],default="default")
    p.add_argument("--threads-batch",type=int,default=max(1,min(6,os.cpu_count() or 4)))
    p.add_argument("--exe",type=Path)
    p.add_argument("--model",type=Path,help="Use your explicitly selected custom GGUF instead of the downloaded model lock")
    args=p.parse_args()
    subprocess.run([sys.executable,str(ROOT/"server/kitty.py"),"init"],check=True)
    config=json.loads((ROOT/"data/config.json").read_text())
    lock=ROOT/"data"/("model-fast.lock.json" if args.profile=="fast" else "model.lock.json")
    if args.model:
        model=args.model.expanduser().resolve()
    else:
        if not lock.exists():raise SystemExit("Run DOWNLOAD_FAST_MODEL.bat for fast, or DOWNLOAD_MODEL.bat for default, first.")
        meta=json.loads(lock.read_text());model=ROOT/"models"/Path(meta["filename"]).name
    if not model.exists():raise SystemExit("The model file is missing; run the downloader.")
    exe=args.exe or shutil.which("llama-server")
    if not exe:
        candidates=list((ROOT/"runtime").rglob("llama-server.exe" if os.name=="nt" else "llama-server")) if (ROOT/"runtime").exists() else []
        if len(candidates)==1:exe=candidates[0]
    if not exe:raise SystemExit("Extract an official llama.cpp build, including all its DLLs/libraries, into runtime/. See docs/SETUP_WINDOWS.md.")
    import socket
    with socket.socket() as probe:
        probe.settimeout(1)
        if probe.connect_ex(('127.0.0.1',8080))==0:raise SystemExit('Port 8080 is already in use. Close the other model window before changing profiles.')
    if args.threads<1 or args.threads_batch<1:raise SystemExit('Thread counts must be positive.')
    context=config.get("context_window",4096)
    if context not in (4096,8192):raise SystemExit("For this alpha set context_window to 4096 or 8192 in data/config.json.")
    command=[str(exe),"--model",str(model),"--alias",config.get("model","kitty"),"--host","127.0.0.1","--port","8080","--ctx-size",str(context),"--parallel","1","--n-gpu-layers",str(args.gpu_layers),"--threads",str(args.threads),"--threads-batch",str(args.threads_batch),"--jinja","--chat-template-kwargs",'{"enable_thinking":false}']
    print("Starting KITTY's model on http://127.0.0.1:8080/v1; leave this window open.",flush=True)
    subprocess.run(command,check=True)

if __name__=="__main__":main()

