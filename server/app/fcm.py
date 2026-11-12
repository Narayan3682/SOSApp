"""FCM HTTP v1 wake-up path. No-op unless FCM_PROJECT_ID + service-account credentials are set.
Only used to wake sleeping phones; the alert content itself is a data message."""
import asyncio
import logging

from . import config

log = logging.getLogger("pager.fcm")
_session = None


def _get_session():
    global _session
    if _session is None:
        import google.auth
        from google.auth.transport.requests import AuthorizedSession
        creds, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/firebase.messaging"])
        _session = AuthorizedSession(creds)
    return _session


def _send_blocking(token: str, data: dict) -> bool:
    url = f"https://fcm.googleapis.com/v1/projects/{config.FCM_PROJECT_ID}/messages:send"
    body = {"message": {"token": token, "data": {k: str(v) for k, v in data.items()},
                        "android": {"priority": "HIGH", "ttl": "120s"}}}
    r = _get_session().post(url, json=body, timeout=10)
    if r.status_code != 200:
        log.warning("FCM send failed %s %s", r.status_code, r.text[:200])
    return r.status_code == 200


async def send(token: str, data: dict) -> bool:
    if not (config.FCM_PROJECT_ID and token):
        return False
    try:
        return await asyncio.to_thread(_send_blocking, token, data)
    except Exception as e:  # never let push failure break alert delivery
        log.warning("FCM error: %s", e)
        return False
