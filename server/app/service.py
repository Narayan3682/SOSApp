"""Alert delivery + escalation, independent of HTTP framework."""
import asyncio
import logging

from . import config, fcm
from .db import Store
from .hub import Hub

log = logging.getLogger("pager.service")


class AlertService:
    def __init__(self, store: Store, hub: Hub):
        self.store, self.hub = store, hub
        self._tasks: dict[str, asyncio.Task] = {}

    def payload(self, alert: dict, sender_name: str, level: int = 0) -> dict:
        return {"type": "alert", "alert_id": alert["id"], "household_id": alert["household_id"],
                "sender_device_id": alert["sender_device_id"], "sender_name": sender_name,
                "trigger": alert["trigger"], "lat": alert["lat"], "lon": alert["lon"],
                "created_at": alert["created_at"], "level": level}

    async def deliver(self, alert: dict, sender_name: str, level: int = 0) -> dict:
        """Fire every path at once: WebSocket and FCM. Returns {device_id: [paths]}."""
        data = self.payload(alert, sender_name, level)
        targets = [d for d in self.store.household_devices(alert["household_id"])
                   if d["id"] != alert["sender_device_id"]]

        async def one(d):
            paths = []
            if await self.hub.send(d["id"], data):
                paths.append("ws")
            if await fcm.send(d["fcm_token"], data):
                paths.append("fcm")
            return d["id"], paths

        return dict(await asyncio.gather(*(one(d) for d in targets)))

    async def broadcast(self, household_id: str, payload: dict) -> None:
        for d in self.store.household_devices(household_id):
            await self.hub.send(d["id"], payload)

    def start_escalation(self, alert_id: str, sender_name: str) -> None:
        if alert_id not in self._tasks:
            self._tasks[alert_id] = asyncio.create_task(self._escalate(alert_id, sender_name))

    def stop_escalation(self, alert_id: str) -> None:
        t = self._tasks.pop(alert_id, None)
        if t:
            t.cancel()

    async def _escalate(self, alert_id: str, sender_name: str) -> None:
        try:
            for level in range(1, config.MAX_REALERTS + 1):
                await asyncio.sleep(config.ESCALATE_AFTER_S)
                a = self.store.get_alert(alert_id)
                if not a or a["status"] != "active" or a["acks"]:
                    return
                self.store.set_escalation(alert_id, level)
                log.warning("alert %s unacknowledged, re-alerting (level %d)", alert_id, level)
                await self.deliver(a, sender_name, level)
                await self.broadcast(a["household_id"], {"type": "escalated", "alert_id": alert_id, "level": level})
        finally:
            self._tasks.pop(alert_id, None)
