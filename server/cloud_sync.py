"""Durable SQLite outbox -> Turso replica. A single brain is the cloud writer.

The worker exclusively owns the Turso connection. Network calls never hold a
lock on the primary SQLite database; restarts replay idempotent operations.
"""
import json
import sqlite3
import threading
import time

SCHEMA = """
CREATE TABLE IF NOT EXISTS memories(id INTEGER PRIMARY KEY, text TEXT NOT NULL UNIQUE, created REAL NOT NULL);
CREATE TABLE IF NOT EXISTS turns(id TEXT PRIMARY KEY, session TEXT, input TEXT, reply TEXT, result TEXT, created REAL);
CREATE TABLE IF NOT EXISTS feedback(turn_id TEXT PRIMARY KEY, rating INTEGER, correction TEXT, created REAL);
CREATE TABLE IF NOT EXISTS documents(id TEXT PRIMARY KEY, source TEXT, text TEXT);
"""


def enqueue(db, sql, args=()):
    db.execute("INSERT INTO _cloud_outbox(sql,args) VALUES(?,?)", (sql, json.dumps(args)))


class CloudSync:
    def __init__(self, path, url, token):
        self.path, self.url, self.token = path, url, token
        self.state, self.last_success = "pending", None
        self.stop = threading.Event()
        self.thread = None

    def start(self):
        if not self.thread:
            self.thread = threading.Thread(target=self.run, name="kitty-turso-sync", daemon=True)
            self.thread.start()

    def run(self):
        connection = None
        delay = 1
        while not self.stop.is_set():
            try:
                if connection is None:
                    import turso.sync
                    connection = turso.sync.connect(str(self.path.with_name("cloud-replica.db")), remote_url=self.url, auth_token=self.token, bootstrap_if_empty=True)
                    connection.executescript(SCHEMA)
                    connection.commit()
                    connection.push()
                count = self.flush(connection)
                self.state = "synced"
                self.last_success = int(time.time())
                delay = 1
                if count:
                    continue
            except Exception:
                # Never print provider exceptions; they may contain credentials.
                self.state = "retrying"
                delay = min(60, delay * 2)
                if connection is not None:
                    try:
                        connection.rollback()
                    except Exception:
                        pass
            self.stop.wait(delay)
        if connection is not None:
            connection.close()

    def flush(self, connection):
        with sqlite3.connect(self.path, timeout=10) as db:
            rows = db.execute("SELECT id,sql,args FROM _cloud_outbox ORDER BY id LIMIT 100").fetchall()
        if not rows:
            return 0
        for _, sql, args in rows:
            connection.execute(sql, json.loads(args))
        connection.commit()
        connection.push()
        with sqlite3.connect(self.path, timeout=10) as db:
            db.execute("DELETE FROM _cloud_outbox WHERE id<=?", (rows[-1][0],))
        return len(rows)

    def status(self):
        with sqlite3.connect(self.path, timeout=10) as db:
            pending = db.execute("SELECT COUNT(*) FROM _cloud_outbox").fetchone()[0]
        return {"state": self.state if pending == 0 else ("retrying" if self.state == "retrying" else "pending"), "pending": pending, "last_success": self.last_success}
