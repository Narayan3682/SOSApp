import asyncio
import unittest

from app import config
from app.db import Store
from app.hub import Hub
from app.service import AlertService


class FakeWS:
    def __init__(self):
        self.sent = []

    async def send_json(self, p):
        self.sent.append(p)


class ServiceTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        config.ESCALATE_AFTER_S, config.MAX_REALERTS = 0.05, 2
        self.s, self.hub = Store(":memory:"), Hub()
        self.svc = AlertService(self.s, self.hub)
        self.h = self.s.create_household("Home", "Narayan", "pixel")
        self.j = self.s.join(self.h["invite_code"], "Mom", "galaxy")
        self.ws = FakeWS()
        self.hub.add(self.j["device_id"], self.ws)
        self.alert, _ = self.s.create_alert("alert-0001", self.h["household_id"], self.h["device_id"], "volume")

    async def test_deliver_skips_sender(self):
        res = await self.svc.deliver(self.alert, "Narayan")
        self.assertEqual(res, {self.j["device_id"]: ["ws"]})
        self.assertEqual(self.ws.sent[0]["alert_id"], "alert-0001")

    async def test_escalates_until_acked(self):
        self.svc.start_escalation("alert-0001", "Narayan")
        await asyncio.sleep(0.12)
        self.s.add_ack("alert-0001", self.j["device_id"])
        n = len(self.ws.sent)
        await asyncio.sleep(0.15)
        self.assertGreaterEqual(n, 1)
        self.assertEqual(len(self.ws.sent), n)  # stopped after ack

    async def test_escalation_capped(self):
        self.svc.start_escalation("alert-0001", "Narayan")
        await asyncio.sleep(0.4)
        alerts = [m for m in self.ws.sent if m["type"] == "alert"]
        self.assertEqual(len(alerts), 2)


if __name__ == "__main__":
    unittest.main()
