"""Start the pager server over HTTPS on port 8443 (creates the certificate on first run).

    python run.py              # https://<this-pc-ip>:8443
    python run.py --http 8000  # plain http (LAN testing only)
"""
import argparse
import os
import subprocess
import sys

import uvicorn

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8443)
    ap.add_argument("--http", type=int, help="serve plain HTTP on this port instead (not recommended)")
    a = ap.parse_args()
    os.environ.setdefault("PAGER_DB_PATH", "pager.db")
    if a.http:
        uvicorn.run("app.main:app", host="0.0.0.0", port=a.http)
        sys.exit()
    if not os.path.exists("certs/server.crt"):
        subprocess.check_call([sys.executable, "make_cert.py"])
    else:
        subprocess.check_call([sys.executable, "make_cert.py", "--fingerprint"])
    uvicorn.run("app.main:app", host="0.0.0.0", port=a.port,
                ssl_certfile="certs/server.crt", ssl_keyfile="certs/server.key")
