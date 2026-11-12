"""SQLite store. Framework-independent so it can be unit-tested with the stdlib only."""
import base64
import hashlib
import secrets
import sqlite3
import threading
import time
import uuid
from typing import Optional

SCHEMA = """
CREATE TABLE IF NOT EXISTS household (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, invite_code TEXT UNIQUE NOT NULL, created_at REAL NOT NULL,
  lan_key TEXT);
CREATE TABLE IF NOT EXISTS member (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  name TEXT NOT NULL, phone TEXT, is_priority INTEGER NOT NULL DEFAULT 0, created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS device (
  id TEXT PRIMARY KEY, member_id TEXT NOT NULL REFERENCES member(id),
  household_id TEXT NOT NULL REFERENCES household(id), name TEXT NOT NULL,
  token_hash TEXT UNIQUE NOT NULL, fcm_token TEXT, last_seen REAL, created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS alert (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  sender_device_id TEXT NOT NULL, trigger TEXT NOT NULL, lat REAL, lon REAL,
  status TEXT NOT NULL DEFAULT 'active', escalation_level INTEGER NOT NULL DEFAULT 0,
  created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS ack (
  alert_id TEXT NOT NULL REFERENCES alert(id), device_id TEXT NOT NULL,
  via TEXT NOT NULL DEFAULT 'app', created_at REAL NOT NULL, PRIMARY KEY (alert_id, device_id));
"""
_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O/1/I


def _new_lan_key() -> str:
    """Per-household secret that signs offline (LAN/Nearby) packets so strangers can't forge alarms."""
    return base64.b64encode(secrets.token_bytes(32)).decode()


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


class Store:
    def __init__(self, path: str = ":memory:"):
        self._lock = threading.RLock()
        self._c = sqlite3.connect(path, check_same_thread=False)
        self._c.row_factory = sqlite3.Row
        with self._lock:
            self._c.executescript(SCHEMA)
            # migrate databases created before lan_key existed
            cols = [r[1] for r in self._c.execute("PRAGMA table_info(household)")]
            if "lan_key" not in cols:
                self._c.execute("ALTER TABLE household ADD COLUMN lan_key TEXT")
            for r in self._c.execute("SELECT id FROM household WHERE lan_key IS NULL").fetchall():
                self._c.execute("UPDATE household SET lan_key=? WHERE id=?", (_new_lan_key(), r[0]))
            self._c.commit()

    def meta(self, key: str) -> Optional[str]:
        r = self._one("SELECT value FROM meta WHERE key=?", (key,))
        return r["value"] if r else None

    def set_meta(self, key: str, value: str):
        self._exec("INSERT OR REPLACE INTO meta VALUES(?,?)", (key, value))

    # -- helpers
    def _one(self, sql, args=()) -> Optional[dict]:
        with self._lock:
            r = self._c.execute(sql, args).fetchone()
        return dict(r) if r else None

    def _all(self, sql, args=()) -> list:
        with self._lock:
            return [dict(r) for r in self._c.execute(sql, args).fetchall()]

    def _exec(self, sql, args=()):
        with self._lock:
            self._c.execute(sql, args)
            self._c.commit()

    # -- onboarding
    def _add_device(self, member_id, household_id, device_name, fcm_token):
        device_id, token = uuid.uuid4().hex, secrets.token_urlsafe(32)
        self._exec(
            "INSERT INTO device(id,member_id,household_id,name,token_hash,fcm_token,created_at)"
            " VALUES(?,?,?,?,?,?,?)",
            (device_id, member_id, household_id, device_name, _hash(token), fcm_token, time.time()),
        )
        return device_id, token

    def create_household(self, name, member_name, device_name, fcm_token=None, phone=None):
        hid, mid = uuid.uuid4().hex, uuid.uuid4().hex
        code = "".join(secrets.choice(_CODE_ALPHABET) for _ in range(8))
        now = time.time()
        lan_key = _new_lan_key()
        self._exec("INSERT INTO household VALUES(?,?,?,?,?)", (hid, name, code, now, lan_key))
        self._exec("INSERT INTO member VALUES(?,?,?,?,?,?)", (mid, hid, member_name, phone, 1, now))
        did, token = self._add_device(mid, hid, device_name, fcm_token)
        return {"household_id": hid, "invite_code": code, "member_id": mid,
                "device_id": did, "device_token": token, "lan_key": lan_key}

    def join(self, invite_code, member_name, device_name, fcm_token=None, phone=None, is_priority=False):
        h = self._one("SELECT id, lan_key FROM household WHERE invite_code=?", (invite_code.strip().upper(),))
        if not h:
            return None
        mid = uuid.uuid4().hex
        self._exec("INSERT INTO member VALUES(?,?,?,?,?,?)",
                   (mid, h["id"], member_name, phone, int(is_priority), time.time()))
        did, token = self._add_device(mid, h["id"], device_name, fcm_token)
        return {"household_id": h["id"], "member_id": mid, "device_id": did,
                "device_token": token, "lan_key": h["lan_key"]}

    # -- auth / devices
    def authenticate(self, token: str) -> Optional[dict]:
        return self._one(
            "SELECT d.*, m.name AS member_name FROM device d JOIN member m ON m.id=d.member_id"
            " WHERE d.token_hash=?", (_hash(token),))

    def touch(self, device_id):
        self._exec("UPDATE device SET last_seen=? WHERE id=?", (time.time(), device_id))

    def set_fcm_token(self, device_id, fcm_token):
        self._exec("UPDATE device SET fcm_token=? WHERE id=?", (fcm_token, device_id))

    def household(self, household_id):
        return self._one("SELECT id, name, invite_code, lan_key FROM household WHERE id=?", (household_id,))

    def members(self, household_id):
        return self._all(
            "SELECT m.id, m.name, m.phone, m.is_priority, d.id AS device_id, d.name AS device_name,"
            " d.last_seen FROM member m LEFT JOIN device d ON d.member_id=m.id"
            " WHERE m.household_id=? ORDER BY m.created_at", (household_id,))

    def household_devices(self, household_id):
        return self._all("SELECT * FROM device WHERE household_id=?", (household_id,))

    # -- alerts
    def create_alert(self, alert_id, household_id, sender_device_id, trigger, lat=None, lon=None):
        """Idempotent on alert_id (client-generated), so retries/multi-path delivery never duplicate."""
        existing = self.get_alert(alert_id)
        if existing:
            return existing, False
        self._exec("INSERT INTO alert(id,household_id,sender_device_id,trigger,lat,lon,created_at)"
                   " VALUES(?,?,?,?,?,?,?)",
                   (alert_id, household_id, sender_device_id, trigger, lat, lon, time.time()))
        return self.get_alert(alert_id), True

    def get_alert(self, alert_id):
        a = self._one("SELECT * FROM alert WHERE id=?", (alert_id,))
        if a:
            a["acks"] = self._all("SELECT device_id, via, created_at FROM ack WHERE alert_id=?", (alert_id,))
        return a

    def add_ack(self, alert_id, device_id, via="app"):
        self._exec("INSERT OR IGNORE INTO ack VALUES(?,?,?,?)", (alert_id, device_id, via, time.time()))
        self._exec("UPDATE alert SET status='acknowledged' WHERE id=? AND status='active'", (alert_id,))

    def set_status(self, alert_id, status):
        self._exec("UPDATE alert SET status=? WHERE id=?", (status, alert_id))

    def active_unacked(self, max_age_s: float = 600):
        """Alerts still waiting for a response (used to resume escalation after a server restart)."""
        return self._all(
            "SELECT a.*, m.name AS sender_name FROM alert a"
            " JOIN device d ON d.id=a.sender_device_id JOIN member m ON m.id=d.member_id"
            " WHERE a.status='active' AND a.created_at>? AND NOT EXISTS"
            " (SELECT 1 FROM ack k WHERE k.alert_id=a.id)", (time.time() - max_age_s,))

    def set_escalation(self, alert_id, level):
        self._exec("UPDATE alert SET escalation_level=? WHERE id=?", (level, alert_id))
