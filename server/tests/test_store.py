import unittest

from app.db import Store


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.s = Store(":memory:")
        self.h = self.s.create_household("Home", "Narayan", "pixel")

    def test_join_and_auth(self):
        j = self.s.join(self.h["invite_code"].lower(), "Mom", "galaxy")
        self.assertEqual(j["household_id"], self.h["household_id"])
        d = self.s.authenticate(j["device_token"])
        self.assertEqual(d["member_name"], "Mom")
        self.assertIsNone(self.s.authenticate("bogus"))
        self.assertIsNone(self.s.join("NOPE1234", "X", "y"))

    def test_no_device_limit(self):
        for i in range(25):
            self.assertIsNotNone(self.s.join(self.h["invite_code"], f"m{i}", f"d{i}"))
        self.assertEqual(len(self.s.household_devices(self.h["household_id"])), 26)

    def test_alert_idempotent_and_ack(self):
        a, created = self.s.create_alert("alert-0001", self.h["household_id"], self.h["device_id"], "volume")
        self.assertTrue(created)
        _, again = self.s.create_alert("alert-0001", self.h["household_id"], self.h["device_id"], "volume")
        self.assertFalse(again)
        j = self.s.join(self.h["invite_code"], "Mom", "galaxy")
        self.s.add_ack("alert-0001", j["device_id"])
        self.s.add_ack("alert-0001", j["device_id"])  # duplicate ack is harmless
        got = self.s.get_alert("alert-0001")
        self.assertEqual(got["status"], "acknowledged")
        self.assertEqual(len(got["acks"]), 1)

    def test_active_unacked_for_restart_resume(self):
        hid, did = self.h["household_id"], self.h["device_id"]
        self.s.create_alert("alert-open1", hid, did, "volume")
        self.s.create_alert("alert-acked", hid, did, "volume")
        self.s.create_alert("alert-shut1", hid, did, "volume")
        j = self.s.join(self.h["invite_code"], "Mom", "galaxy")
        self.s.add_ack("alert-acked", j["device_id"])
        self.s.set_status("alert-shut1", "cancelled")
        ids = [a["id"] for a in self.s.active_unacked()]
        self.assertEqual(ids, ["alert-open1"])
        self.assertEqual(self.s.active_unacked()[0]["sender_name"], "Narayan")
        self.assertEqual(self.s.active_unacked(max_age_s=-1), [])  # too old => not resumed



class LanKeyTests(unittest.TestCase):
    def test_lan_key_issued_shared_and_secret_sized(self):
        import base64
        s = Store(":memory:")
        h = s.create_household("Home", "N", "pixel")
        j = s.join(h["invite_code"], "Mom", "galaxy")
        self.assertEqual(h["lan_key"], j["lan_key"])
        self.assertEqual(len(base64.b64decode(h["lan_key"])), 32)
        other = s.create_household("Other", "X", "y")
        self.assertNotEqual(h["lan_key"], other["lan_key"])
        self.assertEqual(s.household(h["household_id"])["lan_key"], h["lan_key"])

    def test_migrates_old_database_without_lan_key(self):
        import os, sqlite3, tempfile
        path = os.path.join(tempfile.mkdtemp(), "old.db")
        c = sqlite3.connect(path)
        c.execute("CREATE TABLE household (id TEXT PRIMARY KEY, name TEXT NOT NULL, invite_code TEXT UNIQUE NOT NULL, created_at REAL NOT NULL)")
        c.execute("INSERT INTO household VALUES('h1','Old','OLDCODE1',0)")
        c.commit(); c.close()
        s = Store(path)
        self.assertTrue(s.household("h1")["lan_key"])



class MetaTests(unittest.TestCase):
    def test_meta_roundtrip_and_replace(self):
        s = Store(":memory:")
        self.assertIsNone(s.meta("setup_key"))
        s.set_meta("setup_key", "abc")
        s.set_meta("setup_key", "xyz")
        self.assertEqual(s.meta("setup_key"), "xyz")


if __name__ == "__main__":
    unittest.main()
