import asyncio
import json
import logging
import secrets
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request, WebSocket, WebSocketDisconnect
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from .security import body_too_large
from pydantic import BaseModel, Field

from . import config
from .security import setup_key_ok
from .db import Store, normalize_code
from .hub import Hub
from .ratelimit import RateLimiter
from .service import AlertService


class HouseholdIn(BaseModel):
    household_name: str = Field(min_length=1, max_length=80)
    member_name: str = Field(min_length=1, max_length=80)
    device_name: str = Field(default="phone", max_length=80)
    phone: Optional[str] = None
    fcm_token: Optional[str] = None
    setup_key: Optional[str] = None


class JoinIn(BaseModel):
    invite_code: str
    member_name: str = Field(min_length=1, max_length=80)
    device_name: str = Field(default="phone", max_length=80)
    phone: Optional[str] = None
    fcm_token: Optional[str] = None


class FcmIn(BaseModel):
    fcm_token: str


class AlertIn(BaseModel):
    alert_id: str = Field(min_length=8, max_length=64)  # client-generated => idempotent
    trigger: str = "button"
    lat: Optional[float] = None
    lon: Optional[float] = None


def create_app(store: Optional[Store] = None, setup_key: Optional[str] = None) -> FastAPI:
    required_key = config.SETUP_KEY if setup_key is None else setup_key

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.store = store or Store(config.DB_PATH)
        # Creating a household always needs a key: use SETUP_KEY, else one generated once and kept in the DB.
        key = required_key or app.state.store.meta("setup_key")
        if not key:
            key = secrets.token_urlsafe(12)
            app.state.store.set_meta("setup_key", key)
        app.state.setup_key = key
        logging.getLogger("pager").warning("Setup key (needed once, to create the household): %s", key)
        app.state.hub = Hub()
        app.state.svc = AlertService(app.state.store, app.state.hub)
        # A restart must not silently drop alerts that nobody has answered yet.
        for a in app.state.store.active_unacked():
            app.state.svc.start_escalation(a["id"], a["sender_name"])
        yield

    app = FastAPI(title="Home Pager", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    class Headers(BaseHTTPMiddleware):
        async def dispatch(self, request, call_next):
            if body_too_large(request.headers.get("content-length")):
                return JSONResponse({"detail": "payload too large"}, status_code=413)
            r = await call_next(request)
            r.headers["X-Content-Type-Options"] = "nosniff"
            r.headers["Cache-Control"] = "no-store"
            r.headers["Referrer-Policy"] = "no-referrer"
            if request.url.scheme == "https":
                r.headers["Strict-Transport-Security"] = "max-age=31536000"
            return r

    app.add_middleware(Headers)
    limiter = RateLimiter(limit=10, window_s=60)

    def throttle(request: Request) -> None:
        ip = request.client.host if request.client else "unknown"
        if not limiter.allow(ip):
            raise HTTPException(429, "too many attempts, slow down")

    def device(authorization: str = Header(default="")) -> dict:
        token = authorization.removeprefix("Bearer ").strip()
        d = app.state.store.authenticate(token) if token else None
        if not d:
            raise HTTPException(401, "invalid device token")
        app.state.store.touch(d["id"])
        return d

    @app.get("/health")
    def health():
        return {"ok": True}

    @app.post("/api/households")
    def create_household(body: HouseholdIn, _: None = Depends(throttle)):
        if not setup_key_ok(app.state.setup_key, body.setup_key):
            raise HTTPException(403, "invalid setup key")
        return app.state.store.create_household(
            body.household_name, body.member_name, body.device_name, body.fcm_token, body.phone)

    @app.post("/api/join")
    def join(body: JoinIn, _: None = Depends(throttle)):
        r = app.state.store.join(body.invite_code, body.member_name, body.device_name,
                                 body.fcm_token, body.phone)
        if not r:
            # Wrong codes are not secrets; showing exactly what arrived is the fastest way to spot a typo or stray character.
            logging.getLogger("pager").warning(
                "join refused: received invite code %r -> normalised %r (name %r). Look-alikes: B/8, V/U, 6/G, S/5, Z/2",
                body.invite_code, normalize_code(body.invite_code), body.member_name)
            raise HTTPException(404, "unknown invite code")
        return r

    @app.get("/api/household")
    def household(d: dict = Depends(device)):
        return app.state.store.household(d["household_id"])

    @app.get("/api/members")
    def members(d: dict = Depends(device)):
        online = app.state.hub.online()
        rows = app.state.store.members(d["household_id"])
        for r in rows:
            r["online"] = r["device_id"] in online
        return rows

    @app.put("/api/devices/fcm")
    def set_fcm(body: FcmIn, d: dict = Depends(device)):
        app.state.store.set_fcm_token(d["id"], body.fcm_token)
        return {"ok": True}

    @app.post("/api/alerts")
    async def create_alert(body: AlertIn, d: dict = Depends(device)):
        alert, created = app.state.store.create_alert(
            body.alert_id, d["household_id"], d["id"], body.trigger, body.lat, body.lon)
        if alert["household_id"] != d["household_id"]:
            raise HTTPException(403, "alert belongs to another household")
        delivery = {}
        if created:
            delivery = await app.state.svc.deliver(alert, d["member_name"])
            app.state.svc.start_escalation(alert["id"], d["member_name"])
        return {"alert": alert, "created": created, "delivery": delivery}

    def _own_alert(alert_id: str, d: dict) -> dict:
        a = app.state.store.get_alert(alert_id)
        if not a or a["household_id"] != d["household_id"]:
            raise HTTPException(404, "alert not found")
        return a

    @app.get("/api/alerts/{alert_id}")
    def get_alert(alert_id: str, d: dict = Depends(device)):
        return _own_alert(alert_id, d)

    @app.post("/api/alerts/{alert_id}/ack")
    async def ack(alert_id: str, d: dict = Depends(device)):
        a = _own_alert(alert_id, d)
        app.state.store.add_ack(alert_id, d["id"])
        app.state.svc.stop_escalation(alert_id)
        await app.state.svc.broadcast(a["household_id"], {
            "type": "ack", "alert_id": alert_id, "device_id": d["id"], "member_name": d["member_name"]})
        return app.state.store.get_alert(alert_id)

    @app.post("/api/alerts/{alert_id}/{action}")
    async def close(alert_id: str, action: str, d: dict = Depends(device)):
        if action not in ("cancel", "resolve"):
            raise HTTPException(404)
        a = _own_alert(alert_id, d)
        status = "cancelled" if action == "cancel" else "resolved"
        app.state.store.set_status(alert_id, status)
        app.state.svc.stop_escalation(alert_id)
        await app.state.svc.broadcast(a["household_id"], {"type": status, "alert_id": alert_id})
        return app.state.store.get_alert(alert_id)

    @app.websocket("/ws")
    async def ws(sock: WebSocket):
        await sock.accept()
        # First message must authenticate (keeps tokens out of URLs/logs).
        try:
            msg = json.loads(await asyncio.wait_for(sock.receive_text(), timeout=10))
            d = app.state.store.authenticate(msg.get("token", "")) if msg.get("type") == "auth" else None
        except Exception:
            d = None
        if not d:
            await sock.close(code=4401)
            return
        app.state.hub.add(d["id"], sock)
        app.state.store.touch(d["id"])
        await sock.send_json({"type": "ready", "device_id": d["id"]})
        try:
            while True:
                m = json.loads(await sock.receive_text())
                if m.get("type") == "ping":
                    app.state.store.touch(d["id"])
                    await sock.send_json({"type": "pong"})
        except (WebSocketDisconnect, ValueError):
            pass
        finally:
            app.state.hub.remove(d["id"], sock)

    return app


app = create_app()
