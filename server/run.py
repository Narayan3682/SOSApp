"""Start the pager server over HTTPS on port 8443 (creates the certificate on first run).

    python run.py              # https://<this-pc-ip>:8443
    python run.py --http 8000  # plain http (LAN testing only)
"""
import argparse
import os
import subprocess
import sys

import uvicorn

from make_cert import local_ips, cert_ips, CERT

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8443)
    ap.add_argument("--http", type=int, help="serve plain HTTP on this port instead (not recommended)")
    a = ap.parse_args()
    os.environ.setdefault("PAGER_DB_PATH", "pager.db")
    if a.http:
        uvicorn.run("app.main:app", host="0.0.0.0", port=a.http)
        sys.exit()
    if not os.path.exists(CERT):
        subprocess.check_call([sys.executable, "make_cert.py"])
    else:
        subprocess.check_call([sys.executable, "make_cert.py", "--fingerprint"])
    ips = local_ips()
    print("Phones connect to:  " + "   or   ".join(f"https://{ip}:{a.port}" for ip in ips) if ips else "No LAN address found")
    missing = [ip for ip in ips if ip not in cert_ips()]
    if missing:
        print(f"NOTE: this PC's address changed ({', '.join(missing)} is not in the certificate). Phones still connect "
              "(they pin the certificate, not the address) but you must update the server URL in each phone's "
              "Server settings. Give this PC a fixed IP (DHCP reservation in your router) to stop this happening.")
    print()
    uvicorn.run("app.main:app", host="0.0.0.0", port=a.port,
                ssl_certfile="certs/server.crt", ssl_keyfile="certs/server.key")
