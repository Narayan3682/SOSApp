# Architecture

## Delivery tiers (all fired in parallel, receivers de-duplicate by `alert_id` + escalation level)
| Tier | Path | Works when | Status |
|---|---|---|---|
| 1 | Server WebSocket + FCM high-priority push | Sender has internet; reaches anywhere | Done (FCM needs your Firebase project) |
| 2 | UDP broadcast on the LAN / hotspot, port 47474 | Router or hotspot on, internet down | Done |
| 3 | Nearby Connections advertisement (BLE / Wi-Fi Direct) | No router, no internet | Done, needs real-device testing |
| 4 | Direct SMS from the sender's SIM (family flavor) | No data, cell signal | Done |

Offline packets (tiers 2 and 3) are 58 bytes max: `ver type uuid level trigger ts nameLen name mac8`, where mac is a truncated HMAC-SHA256 under a per-household key issued by the server at pairing. Receivers drop packets with a bad MAC or a timestamp more than 10 minutes off.
SMS recipients come from a contact list cached on the phone (synced whenever it reaches the server).

## Server
REST: `POST /api/households`, `POST /api/join`, `GET /api/members`, `PUT /api/devices/fcm`, `POST /api/alerts`, `POST /api/alerts/{id}/ack|cancel|resolve`.
WebSocket `/ws`: first message `{"type":"auth","token":...}`; server pushes `alert`, `ack`, `cancelled`, `resolved`, `escalated`.
Auth: per-device bearer token, stored hashed (SHA-256). Alerts are idempotent on the client-generated `alert_id`.
Escalation: no ack after `ESCALATE_AFTER_S` -> re-send, up to `MAX_REALERTS`; resumed after a server restart. On WebSocket connect the server replays any unanswered alert from the last 10 min to that phone (`replayed: true`).
Transport: HTTPS on 8443 with a self-signed certificate (`run.py`); phones pin its SHA-256 fingerprint at setup.

## Android
`Trigger.fire()` is the one entry point for every trigger -> `CountdownActivity` (5 s cancel) -> `POST /api/alerts`.
`PagerService` (foreground) holds the WebSocket (reconnects on network change; 15-min exact alarm + WorkManager keepalive) -> `AlarmController` (alarm-stream siren, vibration, full-screen intent) -> `AlarmActivity`.
Flavors: `family` adds `VolumeKeyService` (accessibility key filter) and cleartext HTTP; `play` omits both.

## Open risks
Accessibility/full-screen-intent/SMS policies on Play; OEM battery killers; wake-word licensing; server uptime (home server needs Tailscale or a tunnel for remote phones, and is down during power cuts).
