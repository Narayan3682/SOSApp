import unittest

try:
    from fastapi.testclient import TestClient
    from app.db import Store
    from app.main import create_app
    HAVE = True
except Exception:  # fastapi/httpx not installed
    HAVE = False


@unittest.skipUnless(HAVE, "fastapi + httpx required")
class ApiTests(unittest.TestCase):
    def test_flow(self):
        with TestClient(create_app(Store(":memory:"), setup_key="t")) as c:
            h = c.post("/api/households", json={"household_name": "Home", "member_name": "N", "setup_key": "t"}).json()
            j = c.post("/api/join", json={"invite_code": h["invite_code"], "member_name": "Mom"}).json()
            H = lambda r: {"Authorization": "Bearer " + r["device_token"]}
            with c.websocket_connect("/ws") as ws:
                ws.send_json({"type": "auth", "token": j["device_token"]})
                self.assertEqual(ws.receive_json()["type"], "ready")
                r = c.post("/api/alerts", headers=H(h), json={"alert_id": "alert-0001", "trigger": "volume"}).json()
                self.assertEqual(r["delivery"][j["device_id"]], ["ws"])
                self.assertEqual(ws.receive_json()["type"], "alert")
                c.post("/api/alerts/alert-0001/ack", headers=H(j))
                self.assertEqual(ws.receive_json()["type"], "ack")
            self.assertEqual(c.get("/api/members").status_code, 401)

    def test_setup_key_enforced_and_lan_key_returned(self):
        with TestClient(create_app(Store(":memory:"), setup_key="k1")) as c:
            body = {"household_name": "Home", "member_name": "N"}
            self.assertEqual(c.post("/api/households", json=body).status_code, 403)
            self.assertEqual(c.post("/api/households", json={**body, "setup_key": "bad"}).status_code, 403)
            h = c.post("/api/households", json={**body, "setup_key": "k1"}).json()
            self.assertTrue(h["lan_key"])
            j = c.post("/api/join", json={"invite_code": h["invite_code"], "member_name": "Mom"}).json()
            self.assertEqual(j["lan_key"], h["lan_key"])   # joining needs only the invite code

    def test_pairing_is_rate_limited(self):
        with TestClient(create_app(Store(":memory:"), setup_key="t")) as c:
            codes = [c.post("/api/join", json={"invite_code": "NOPE1234", "member_name": "x"}).status_code
                     for _ in range(12)]
            self.assertEqual(codes[:10], [404] * 10)
            self.assertEqual(codes[10:], [429, 429])


if __name__ == "__main__":
    unittest.main()
