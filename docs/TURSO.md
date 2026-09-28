# Turso cloud memory for KITTY

KITTY still works with ordinary local SQLite by default. Turso is an optional cloud backend for the gateway's memories, chat history, feedback, and indexed documents. The gateway keeps a local Turso replica and pushes changes to the cloud, so normal replies do not wait for a remote database request.

Turso credentials belong only on the gateway machine. Do not put the database token in the Android app, GitHub repository, APK, or a chat message.

## Enable it on Windows

Install the optional Python package in the repository environment:

```powershell
python -m pip install -r requirements-turso.txt
```

Create a Turso database and token using the Turso CLI or dashboard, then set both variables in the same PowerShell window before starting KITTY:

```powershell
$env:TURSO_DATABASE_URL = "libsql://YOUR_DATABASE.turso.io"
$env:TURSO_AUTH_TOKEN = "YOUR_DATABASE_TOKEN"
python server/kitty.py --home data doctor
python server/kitty.py --home data serve
```

The doctor output should include `database turso-sync`. KITTY uses `data/kitty.turso.sqlite3` for this replica and leaves the old `data/kitty.sqlite3` untouched. This protects the old local history while you verify the cloud database.

## What is stored

The same server tables are used for:

- explicit memories;
- model chat turns and archived phone events;
- approved replies and corrections;
- imported documents used for retrieval.

The model weights, pairing token, Android permissions, microphone model, and phone-only screen data are not moved into Turso. The pairing token remains in the gateway's local `config.json`.

## Existing data

KITTY does not copy the old SQLite file automatically because silently merging histories can create duplicates or overwrite newer corrections. Use the existing server export/import commands after you have tested the new replica:

```powershell
python server/kitty.py --home data export-feedback data/approved-training.jsonl
```

For a full history migration, keep the old `kitty.sqlite3` as a backup and migrate it deliberately after confirming the Turso database is reachable. The `sync-turso` command pushes pending replica writes:

```powershell
python server/kitty.py --home data sync-turso
```

If Turso is unavailable, the local replica remains readable and new writes stay local until the next successful push. Removing both `TURSO_*` variables returns KITTY to the original SQLite backend.
