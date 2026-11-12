"""In-memory WebSocket registry: device_id -> live connections."""
from collections import defaultdict
from typing import Any


class Hub:
    def __init__(self):
        self._conns: dict[str, set[Any]] = defaultdict(set)

    def add(self, device_id: str, ws) -> None:
        self._conns[device_id].add(ws)

    def remove(self, device_id: str, ws) -> None:
        self._conns[device_id].discard(ws)
        if not self._conns[device_id]:
            del self._conns[device_id]

    def online(self) -> set:
        return set(self._conns)

    async def send(self, device_id: str, payload: dict) -> bool:
        ok = False
        for ws in list(self._conns.get(device_id, ())):
            try:
                await ws.send_json(payload)
                ok = True
            except Exception:
                self.remove(device_id, ws)
        return ok
