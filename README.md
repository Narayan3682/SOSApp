# Home Pager

Emergency pager for the home using Android phones. Any phone can raise an alert; every other phone in the household gets a loud full-screen alarm, can answer "I'm coming", and the alert is re-sent if nobody responds.

> Not a certified emergency system. It never replaces calling 112.

## Layout
- `server/` - Python FastAPI service (pairing, WebSocket hub, SQLite, escalation timer, optional FCM wake-up). Docker-ready.
- `android/` - Kotlin + Jetpack Compose app, two flavors: `family` (sideload, all triggers) and `play` (Play-Store-safe subset).
- `docs/ARCHITECTURE.md` - design, delivery tiers, status.

## Run the server (HTTPS)
```
cd server
python -m venv .venv && .venv\Scripts\activate   # Windows
pip install -r requirements.txt
python run.py
```
First start prints two things you need on the phones: the **certificate fingerprint** and the **setup key**.
Allow the port once (PowerShell as Administrator, Private networks only):
```
New-NetFirewallRule -DisplayName "Home Pager 8443" -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow -Profile Private
```
Find the PC's IP with `ipconfig` (IPv4 Address). Phones use `https://<that-ip>:8443`.
Docker alternative: `docker compose up --build` (http on 8000; add `--profile tls` with a real domain for Let's Encrypt, see docs/TLS.md).
Tests: `python -m unittest discover -s tests`. Security model: docs/SECURITY.md.

## Get the APK
Option A - GitHub Actions (no local tooling): push this folder to a GitHub repo; the `Build` workflow runs server + Android unit tests, builds the debug APK and attaches it as artifact `HomePager-family-debug-apk`. Download, copy to the phone, install (allow "unknown sources").
Option B - Android Studio: open `android/`, choose variant `familyDebug`, Run on a phone or emulator (Build > Build APK(s) gives the file). Run the unit tests with `gradle :app:testFamilyDebugUnitTest`.
Emulator note: emulators can test pairing, alerts, countdown and alarm UI, but not realistic volume-key/battery-killer behaviour; use real phones for that. In an emulator the server URL is `http://10.0.2.2:8000`.

First launch: enter `http://<server-LAN-IP>:8000`, create the household, join from other phones with the invite code.
Then: Settings > Accessibility > Home Pager emergency trigger (on), exempt the app from battery optimization, and on Android 14+ allow "full-screen notifications" for the app.

## Status (Phase 2)
Triggers: SOS button, home-screen widget, Quick Settings tile, volume triple-press / vol-up+down hold (family flavor).
Delivery, all fired at once:
1. Server: WebSocket + FCM (FCM needs your own Firebase project: docs/FCM_SETUP.md).
2. Same Wi-Fi / hotspot: signed UDP broadcast, no internet needed.
3. Nearby (Bluetooth / Wi-Fi Direct): signed alert in the advertisement, no router needed; receivers scan only while the server is unreachable.
4. SMS from the sender's SIM (family flavor): to everyone when the server is unreachable, or to priority contacts if enabled.
Security: per-household signing key on offline packets (forged or replayed-late packets are ignored), setup key to create households, pairing rate-limit, https required off the home network (docs/TLS.md).
In-app readiness checklist: notifications, full-screen alerts, battery, Do Not Disturb, Bluetooth/nearby, accessibility, SMS.

Known limits: offline routes give no delivery confirmation to the sender (receivers can reply "I'm coming" over Wi-Fi only); offline alerts are not re-sent automatically; widget is tap-to-open-countdown (no swipe in widgets); no voice trigger yet; Wi-Fi power-saving can delay broadcasts to idle phones; Nearby/BLE range through walls is short. Not a certified emergency system.

Tests: `server` (python -m unittest) and `android` JVM tests (`gradle :app:testFamilyDebugUnitTest`) run in CI. Offline packet, URL policy and SMS selection logic is plain Java so it is testable without a device.
