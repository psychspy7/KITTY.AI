# Turso cloud memory for KITTY

KITTY v0.3 keeps `data/kitty.sqlite3` as its primary local SQLite database. When Turso is configured, a durable outbox records every change in that same transaction, and a background worker pushes those changes from a separate Turso replica. Normal replies do not wait for the internet. It is an owner-controlled backup/mirror for one active gateway, not a multi-writer database or model training job.

Turso credentials belong only on the gateway machine. Do not put the database token in the Android app, GitHub repository, APK, or a chat message.

## Enable it on Windows

Install the optional Python package in the repository environment:

```powershell
python -m pip install -r requirements-turso.txt
```

Create a Turso database and token using its dashboard. Copy `cloud/oracle/turso.env.example` into `data/secrets.env` and edit it using Notepad (no quotation marks). Use this file for the new v0.3 launcher so both doctor and the gateway see the same secrets. Alternately set both variables in the same PowerShell window:

```powershell
$env:TURSO_DATABASE_URL = "libsql://YOUR_DATABASE.turso.io"
$env:TURSO_AUTH_TOKEN = "YOUR_DATABASE_TOKEN"
python server/kitty.py --home data doctor
python server/kitty.py --home data serve
```

Doctor shows `database turso-sync`. KITTY v0.3 writes `data/kitty.sqlite3` immediately and uses `data/cloud-replica.db` for its cloud worker. The Android System status screen reports queued operations. Run `py -3 server\kitty.py sync-turso` to wait up to 15 seconds for the queue to clear. A healthy connection says `synced` and `0 pending`.

## What is stored

The same server tables are used for:

- explicit memories;
- model chat turns and archived phone events;
- approved replies and corrections;
- imported documents used for retrieval.

The model weights, pairing token, Android permissions, microphone model, and phone-only screen data are not moved into Turso. The pairing token remains in the gateway's local `config.json`.

## Existing data

An older v0.2 configuration may have stored data in `data/kitty.turso.sqlite3`. **Make a copy of your entire `data` folder before starting v0.3.** Do not delete the old replica. With the old Turso credentials in `data/secrets.env`, stop the gateway and run `py -3 server\kitty.py migrate-legacy-turso` once. This pulls the old replica and merges missing records into `kitty.sqlite3` without overwriting local duplicates. Memory IDs may be renumbered if they collide. Keep the copy of `data`. `export-feedback` exports approved examples for later manual training, not a complete backup:

```powershell
python server/kitty.py --home data export-feedback data/approved-training.jsonl
```

The `sync-turso` command pushes pending v0.3 writes:

```powershell
python server/kitty.py --home data sync-turso
```

If Turso is unavailable, writes continue locally and retry in the background. Do not run two active gateway instances against one Turso database; there is no conflict resolution. Removing both `TURSO_*` variables keeps the same local SQLite database and pauses cloud mirroring.
