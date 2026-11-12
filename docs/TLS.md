# HTTPS for the pager server

The app enforces this rule: plain `http://` is accepted only for home-network addresses
(192.168.x.x, 10.x.x.x, 172.16-31.x.x, Tailscale 100.64-127.x.x, localhost, *.local). Anything else must be `https://`.
The `play` flavor allows no cleartext at all. WebSocket upgrades to `wss://` automatically.

Pick one:

## A. Tailscale (recommended for a home server; free, real certificates, reachable from anywhere)
1. Install Tailscale on the server PC and on every family phone, same account / shared node.
2. On the server: `tailscale serve --bg 8000` then note the URL `https://<machine>.<tailnet>.ts.net`.
3. In the app use that https URL. No port forwarding, works through CGNAT.
Note: Tailscale must be running on each phone, and the server must be on.

## B. Your own domain + bundled Caddy (VPS or home with port forwarding)
1. Point a DNS record at the machine; open ports 80 and 443.
2. In `server/.env` set `DOMAIN=pager.example.com`, `SETUP_KEY=...`, and `FORWARDED_ALLOW_IPS` to your docker network range (e.g. `172.16.0.0/12`).
3. `docker compose --profile tls up -d --build`. Caddy fetches and renews the certificate itself.
4. Use `https://pager.example.com` in the app.

## C. LAN only
`http://192.168.x.x:8000` is accepted. Anyone on your Wi-Fi can sniff the device token, so use a Wi-Fi password you trust.
