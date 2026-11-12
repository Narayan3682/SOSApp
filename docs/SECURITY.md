# Security model

## Transport
- `python run.py` serves **HTTPS on 8443** with a certificate it generates itself (10 years, your PC's LAN IPs).
- Phones **pin that certificate's SHA-256 fingerprint** at setup. You compare the fingerprint the app shows with the one the server prints. After that a phone refuses any other certificate, so a fake server, a swapped router, or a Wi-Fi eavesdropper gets nothing. Nothing has to be installed on the phones.
- Plain `http://` is accepted by the app only for home-network addresses (and never in the `play` flavor). Use it only for quick tests.
- If you ever regenerate the certificate (`certs/` deleted), every phone must re-pair.

## Authentication
- **Setup key**: needed once, to create a household. Set `SETUP_KEY` in `.env` or use the one the server generates on first start (printed in the terminal, stored in the DB). Never open.
- **Invite code**: 8 characters from a 32-symbol alphabet (~1.1 trillion combinations); pairing endpoints are rate-limited to 10 attempts/minute per IP.
- **Device token**: 256-bit random, issued at pairing, stored **hashed** (SHA-256) on the server, sent as a Bearer header, never in URLs. WebSockets authenticate with it in the first message (10 s timeout).
- Every alert/ack/cancel is checked against the caller's household.

## Offline paths
- LAN broadcast and Nearby packets carry a truncated HMAC-SHA256 under a **per-household key** issued at pairing. Packets with a bad signature or a timestamp more than 10 minutes off are dropped, so strangers cannot ring your alarms or replay old ones.

## Server hardening
- API docs endpoints disabled; `X-Content-Type-Options`, `Cache-Control: no-store`, `Referrer-Policy`, HSTS on https; request bodies capped at 16 KB.
- SQLite file and `certs/server.key` live only on the server PC; both are git-ignored.
- Runs as your user; keep the PC's OS and Python updated.

## What this does NOT protect against
- Someone with physical access to an unlocked paired phone (they can trigger alerts as you). Use a screen lock.
- A compromised server PC.
- Denial of service on your LAN or ISP outage (that is what the offline tiers are for).

## Port
8443 is only "secure" because what listens on it is TLS; the number itself is not a secret. Keep the Windows Firewall rule limited to **Private** networks so the port is not exposed when the laptop joins public Wi-Fi.
