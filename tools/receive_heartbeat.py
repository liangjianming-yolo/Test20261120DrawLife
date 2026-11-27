#!/usr/bin/env python3
"""Receive ALIVE batches from the DrawLife Debug app through adb reverse."""

from contextlib import closing
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import sqlite3
from uuid import UUID


class HeartbeatReceiver(BaseHTTPRequestHandler):
    timeout = 5

    def do_POST(self):
        if self.path != "/heartbeat":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 32_768:
                raise ValueError
            payload = json.loads(self.rfile.read(size))
            installation = str(UUID(payload["installation_id"]))
            events = payload["events"]
            if not isinstance(events, list) or not 1 <= len(events) <= 100:
                raise ValueError
            rows = []
            for event in events:
                event_id, timestamp = event["id"], event["at_ms"]
                if event["type"] != "ALIVE" or any(
                    type(value) is not int or not 0 < value < 2**63 for value in (event_id, timestamp)
                ):
                    raise ValueError
                rows.append((installation, event_id, timestamp))
        except (AttributeError, KeyError, TypeError, ValueError):
            self.send_error(400, "Invalid heartbeat batch")
            return

        db = self.server.db
        try:
            with db:
                added = db.executemany("INSERT OR IGNORE INTO heartbeats VALUES (?, ?, ?)", rows).rowcount
        except sqlite3.Error:
            self.send_error(503, "Could not store heartbeat")
            return
        print(f"ALIVE installation={installation} received={len(rows)} new={added} last_id={rows[-1][1]}", flush=True)
        self.send_response(204)
        self.end_headers()


def main():
    database = Path.home() / ".config" / "drawlife-heartbeat" / "received.sqlite"
    database.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with closing(sqlite3.connect(database)) as db, HTTPServer(("127.0.0.1", 8765), HeartbeatReceiver) as server:
        db.execute("CREATE TABLE IF NOT EXISTS heartbeats (installation TEXT, id INTEGER, at_ms INTEGER, PRIMARY KEY (installation, id))")
        server.db = db
        print(f"Listening on http://127.0.0.1:8765/heartbeat | {database}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
