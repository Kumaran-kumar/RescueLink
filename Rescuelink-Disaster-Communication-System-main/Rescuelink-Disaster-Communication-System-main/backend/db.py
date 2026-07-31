"""SQLite persistence for RescueLink backend.

One table, `alerts`, keyed by the mesh message UUID so ingest is idempotent end-to-end.
Status changes are also timestamped so the two-way reassurance path (Phase 4) can return
"updates since T".
"""
import sqlite3
import threading
import time
from contextlib import contextmanager

DB_PATH = "rescuelink.db"

# Single writer lock — SQLite + a low-traffic disaster backend; keep it simple and correct.
_lock = threading.Lock()

_SCHEMA = """
CREATE TABLE IF NOT EXISTS alerts (
    uuid              TEXT PRIMARY KEY,
    sender_name       TEXT,
    sender_id         TEXT,
    lat               REAL,
    lng               REAL,
    emergency_type    TEXT,
    medical_note      TEXT,
    blood_group       TEXT,
    battery_level     INTEGER,
    hop_count         INTEGER,
    original_timestamp INTEGER,   -- ms epoch, from the victim device
    first_bridged_at  INTEGER,    -- ms epoch, when the backend first saw it
    status            TEXT DEFAULT 'ACTIVE',
    status_updated_at INTEGER,    -- ms epoch, last status change
    priority_tier     TEXT,       -- CRITICAL | HIGH | MODERATE (Phase 3)
    priority_reason   TEXT
);
"""


def now_ms() -> int:
    return int(time.time() * 1000)


@contextmanager
def _conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db():
    with _conn() as c:
        c.executescript(_SCHEMA)


def upsert_alert(msg: dict, tier: str, reason: str) -> str:
    """Idempotent UPSERT by uuid. Returns 'inserted' or 'updated'.

    Dedup rules (spec): keep the EARLIEST original_timestamp; update battery/hopCount
    when a newer copy arrives. Never regress status.
    """
    uuid = msg["uuid"]
    with _lock, _conn() as c:
        row = c.execute("SELECT * FROM alerts WHERE uuid = ?", (uuid,)).fetchone()
        if row is None:
            c.execute(
                """INSERT INTO alerts (uuid, sender_name, sender_id, lat, lng,
                    emergency_type, medical_note, blood_group, battery_level, hop_count,
                    original_timestamp, first_bridged_at, status, status_updated_at,
                    priority_tier, priority_reason)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (uuid, msg.get("senderName"), msg.get("senderId"), msg.get("latitude"),
                 msg.get("longitude"), msg.get("emergencyType"), msg.get("medicalNote"),
                 msg.get("bloodGroup"), msg.get("batteryLevel"), msg.get("hopCount", 0),
                 msg.get("timestamp"), now_ms(), "ACTIVE", now_ms(), tier, reason),
            )
            return "inserted"
        # Existing: keep earliest original_timestamp; refresh volatile fields.
        earliest = min(
            [t for t in (row["original_timestamp"], msg.get("timestamp")) if t is not None],
            default=row["original_timestamp"],
        )
        c.execute(
            """UPDATE alerts SET battery_level = ?, hop_count = ?, original_timestamp = ?,
                priority_tier = ?, priority_reason = ? WHERE uuid = ?""",
            (msg.get("batteryLevel", row["battery_level"]),
             msg.get("hopCount", row["hop_count"]),
             earliest, tier, reason, uuid),
        )
        return "updated"


def set_status(uuid: str, status: str) -> bool:
    with _lock, _conn() as c:
        cur = c.execute(
            "UPDATE alerts SET status = ?, status_updated_at = ? WHERE uuid = ?",
            (status, now_ms(), uuid),
        )
        return cur.rowcount > 0


def get_alert(uuid: str):
    with _conn() as c:
        return c.execute("SELECT * FROM alerts WHERE uuid = ?", (uuid,)).fetchone()


def all_alerts():
    with _conn() as c:
        return c.execute("SELECT * FROM alerts ORDER BY original_timestamp DESC").fetchall()


def status_updates_since(since_ms: int):
    """Phase 4: alerts whose status changed at/after `since_ms`."""
    with _conn() as c:
        return c.execute(
            "SELECT * FROM alerts WHERE status_updated_at >= ? ORDER BY status_updated_at ASC",
            (since_ms,),
        ).fetchall()
