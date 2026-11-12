"""Remove a member (and their devices) from the household by name.

    python remove_member.py "PC test"

Stop the server first on Windows (SQLite file locking), then start it again afterwards.
"""
import os
import sqlite3
import sys

if len(sys.argv) != 2:
    sys.exit(__doc__)
name = sys.argv[1]
c = sqlite3.connect(os.getenv("PAGER_DB_PATH", "pager.db"))
ids = [r[0] for r in c.execute("SELECT id FROM member WHERE name=?", (name,))]
if not ids:
    sys.exit(f"No member named {name!r}. Members: {[r[0] for r in c.execute('SELECT name FROM member')]}")
for m in ids:
    c.execute("DELETE FROM ack WHERE device_id IN (SELECT id FROM device WHERE member_id=?)", (m,))
    c.execute("DELETE FROM device WHERE member_id=?", (m,))
    c.execute("DELETE FROM member WHERE id=?", (m,))
c.commit()
print(f"Removed {len(ids)} member(s) named {name!r}. Remaining:", [r[0] for r in c.execute("SELECT name FROM member")])
