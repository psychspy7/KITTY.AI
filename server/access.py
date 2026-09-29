"""Local invitation management. No raw guest tokens are persisted."""
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import time
import uuid


def load_env(path):
    """Read only known keys, without shell evaluation or overriding process env."""
    if not path.exists():
        return
    allowed = {"TURSO_DATABASE_URL", "TURSO_AUTH_TOKEN", "BRAVE_SEARCH_API_KEY"}
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or key.strip() not in allowed:
            raise ValueError("Secrets file contains an unsupported key or malformed line")
        values[key.strip()] = value.strip().strip('"').strip("'")
    for key, value in values.items():
        os.environ.setdefault(key, value)


class Access:
    def __init__(self, home):
        self.path = Path(home) / "guests.json"

    def entries(self):
        return json.loads(self.path.read_text()) if self.path.exists() else []

    def save(self, entries):
        temp = self.path.with_suffix(".tmp")
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            json.dump(entries, out, indent=2)
        temp.replace(self.path)

    def invite(self, label, hours=24):
        if not 1 <= hours <= 720 or not label.strip() or len(label) > 60:
            raise ValueError("Use a label under 60 characters and 1–720 hours")
        token = secrets.token_urlsafe(32)
        entry = {"id": uuid.uuid4().hex, "label": label.strip(), "hash": hashlib.sha256(token.encode()).hexdigest(), "expires": time.time() + hours * 3600}
        entries = self.entries()
        entries.append(entry)
        self.save(entries)
        return entry, token

    def revoke(self, guest_id):
        entries = self.entries()
        retained = [e for e in entries if e["id"] != guest_id]
        if len(retained) == len(entries):
            raise ValueError("Guest ID not found")
        self.save(retained)

    def authenticate(self, token, owner_token):
        if token and hmac.compare_digest(token.encode(), owner_token.encode()):
            return "owner"
        digest = hashlib.sha256(token.encode()).hexdigest()
        for entry in self.entries():
            if entry["expires"] > time.time() and hmac.compare_digest(entry["hash"], digest):
                return entry["id"]
        return None


def scope_id(actor, value):
    value = str(uuid.UUID(value))
    return value if actor == "owner" else str(uuid.uuid5(uuid.UUID(actor), value))


def scope_body(actor, body):
    if actor == "owner":
        return body
    session = body.get("session", "default")
    if not isinstance(session, str) or not 1 <= len(session) <= 80:
        raise ValueError("Invalid session")
    return {**body, "request_id": scope_id(actor, body.get("request_id", "")),
            "session": "guest_" + actor + "_" + hashlib.sha256(session.encode()).hexdigest()[:24]}
