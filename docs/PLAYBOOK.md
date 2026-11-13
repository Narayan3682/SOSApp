# Home Pager — Playbook & lessons learned

Everything that mattered while getting this from idea to two phones ringing each other, written so the next project (or a future you) does not rediscover it.

## 1. What the system is
A household emergency pager on Android phones with a self-hosted Python server.
- **Trigger** (SOS button / widget / Quick Settings tile / volume-key pattern) → 5 s cancel ring → **every delivery route fires at once**: server (WebSocket + optional FCM), signed LAN broadcast, signed Nearby/BLE advertisement, SMS from the sender's SIM.
- **Receivers** de-duplicate by `alert_id` + escalation level, ring on the alarm stream through silent/DND, show a full-screen alarm over the lock screen with one huge **I'M COMING** button.
- **Server** escalates unanswered alerts (re-sends up to 3×), replays unanswered alerts to phones that reconnect, and shows who is online.
- Not a certified emergency system; the app says so and offers "Call 112" everywhere.

Files: `README.md` (run it), `ARCHITECTURE.md` (routes, API), `SECURITY.md` (threat model), `TLS.md`, `FCM_SETUP.md`.

## 2. Timeline of what actually went wrong (and the fix)
| Symptom | Real cause | Fix |
|---|---|---|
| `git push` → 403 | Git credential cached for a different GitHub account | `cmdkey /delete:git:https://github.com`, push again, sign in as repo owner |
| CI: `setup-android` action crashed | Action broke on a newer SDK tool | Removed it; GitHub's Ubuntu runner already ships the Android SDK |
| CI: `onRequestPermissionsResult overrides nothing` | Signature mismatch with the library version resolved | Dropped the override (checklist re-evaluates in `onResume` anyway) |
| Browser: `0.0.0.0:8000` fails | `0.0.0.0` is a *listen* address, not a reachable one | Use `localhost` on the PC, the PC's LAN IPv4 from phones; app now explains this inline |
| `/health` → 500 | Body-size guard did `headers["content-length"]` after a `.get(..., "0")` default; header absent on GET | Dedicated `body_too_large()` with tests; missing header is fine |
| Phone 2 join → 404 although code "was correct" | User pasted the whole share message into the code field | Server + app now extract the 8-char code from any pasted text; share text leads with the code |
| Alert "reached 0 of 1" after a few hours | Phone 2's background connection died; nothing woke it (no FCM) | Reconnect on network change, 15-min exact alarm + WorkManager keepalive, server replays unanswered alerts on reconnect, Family card shows Online/Last seen. **FCM is still the real fix.** |
| App says Offline the next day | PC got a new IP from the router overnight | `run.py` now prints the current address and warns when it changed; app has Server settings (tap the status pill) to test/change the address and Reconnect now. Permanent fix: DHCP reservation for the PC in the router |
| PowerShell choked on nested quotes / `-SkipCertificateCheck` | Windows PowerShell 5 limitations | Put one-off DB tasks in small Python scripts (`remove_member.py`), use `httpx` with `verify=False` for probes |

## 3. Decisions and why
- **Android-only, Kotlin + Compose**: iOS cannot listen to hardware buttons or run an always-on listener; a cross-platform UI would have bought nothing.
- **Two flavors**: `family` (sideload: accessibility key filter, direct SMS, cleartext allowed on LAN) and `play` (Play-policy-safe subset). One codebase, Gradle `productFlavors`.
- **Python/FastAPI** over Go: workload is tiny, readability wins. SQLite, single process, Docker-ready.
- **Hybrid transport**: a self-hosted WebSocket alone is unreliable on sleeping phones; FCM is Google acting only as a wake-up pipe, sent from *your* server.
- **Fire all routes in parallel, never fall back sequentially**: an emergency cannot wait for timeouts.
- **Client-generated `alert_id` (UUID)**: makes every route idempotent and lets receivers de-duplicate across routes.
- **Self-signed TLS + certificate pinning** instead of installing a CA on six phones or needing a public domain: setup shows the fingerprint, user confirms it matches the server's printout, phone refuses anything else afterwards.
- **Per-household HMAC key** on offline packets so strangers on the Wi-Fi/BLE cannot ring your alarms; 10-min timestamp window against replay.
- **Setup key always required** to create a household (auto-generated and printed if not configured); invite code (32-symbol alphabet, 8 chars, no 0/O/1/I) + rate limit for joining.
- **Pure-Java core for anything testable** (packet codec, URL policy, SMS selection): compiles and runs with `javac` alone, so logic was verified before an Android toolchain existed anywhere.
- **Honest delivery UI**: per-route result, amber when not everyone was reached, never a single green tick.

## 4. Operating checklist (every phone)
1. Install the `family` APK (GitHub Actions artifact).
2. Setup: server `https://<pc-ip>:8443` → **Test connection** → compare fingerprint → tick "It matches" → name/number → join with code (or create with the setup key printed by the server).
3. Fix every item in the in-app checklist: notifications, full-screen alerts, battery **Unrestricted**, DND access, Bluetooth/nearby, accessibility (volume trigger), SMS, exact alarms (Android 12–13).
4. Phone-maker extras: Motorola/Xiaomi/Oppo/Vivo/Samsung "Autostart", "Background activity", "Never sleeping apps" — turn all on for Home Pager.
5. Add the SOS widget and the Quick Settings tile.
6. Verify on the **Family card** that the phone shows Online an hour later with the screen off. If it shows "Last seen", step 3/4 is incomplete.

Server: `python run.py` (prints fingerprint, setup key and the address phones must use). **Give the PC a fixed IP** (router → DHCP reservation) or phones will lose it when the address changes, firewall rule for TCP 8443 on **Private** networks only, keep the PC awake (power settings) or move to a mini PC/VPS.

## 5. Test it like QA
- **Happy path**: SOS on A with B locked, screen off → B rings ≤ 2 s (WebSocket) and shows over the lock screen; "I'm coming" on B appears on A.
- **Sleep test**: leave B untouched 2 h → Family card still Online; then SOS.
- **Router off, internet off**: SOS on A → B rings via LAN broadcast (same Wi-Fi/hotspot) or Nearby (BLE) — expect shorter range through walls.
- **Server down**: SOS → result screen says "Server unreachable", Wi-Fi/nearby broadcast sent, SMS sent (family flavor).
- **Cancel**: during the 5 s ring → nothing sent; after sending → receivers' alarms stop.
- **Escalation**: nobody answers → re-rings at 30 s, 60 s, 90 s.
- **Replay**: B offline during SOS → B reconnects within 10 min → B rings with the replayed alert.
- **Security**: wrong invite code → 404 and a server log line; 11 pairing attempts in a minute → 429; a different certificate on the server → app refuses to connect.

## 6. Still open / next
- FCM project per deployment (`FCM_SETUP.md`) — do this before trusting the app.
- Offline routes give no delivery receipt to the sender, and are not re-sent automatically.
- Voice wake word not built (openWakeWord/Vosk; mind Porcupine licensing for a published app).
- Remove-member in the app (today: `python remove_member.py "<name>"` with the server stopped).
- Play-flavor hardening and policy review before any Play Store submission (accessibility, full-screen intent, SMS, foreground-service declarations).
- Move the server to a VPS/Tailscale node if remote-reach-during-home-outage matters.
