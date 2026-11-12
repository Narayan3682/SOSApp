"""Create a self-signed TLS certificate for the home server and print its fingerprint.

    python make_cert.py                 -> certs/server.crt, certs/server.key (10 years, all LAN IPs + hostname)
    python make_cert.py --fingerprint   -> just print the SHA-256 fingerprint of the existing certificate

Phones pin this fingerprint at setup, so they only ever trust *this* server.
"""
import argparse
import datetime as dt
import hashlib
import ipaddress
import os
import socket
import sys

try:
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID
except ImportError:
    sys.exit("pip install cryptography   (it is in requirements.txt)")

CERT, KEY = "certs/server.crt", "certs/server.key"


def fingerprint(pem_path: str) -> str:
    cert = x509.load_pem_x509_certificate(open(pem_path, "rb").read())
    return hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest().upper()


def pretty(fp: str) -> str:
    return ":".join(fp[i:i + 2] for i in range(0, len(fp), 2))


def local_ips():
    ips = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None):
            ips.add(info[4][0])
    except OSError:
        pass
    try:  # the interface used for outbound traffic
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    return sorted(i for i in ips if not i.startswith("127.") and ":" not in i)


def make():
    os.makedirs("certs", exist_ok=True)
    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Home Pager server")])
    sans = [x509.DNSName(socket.gethostname()), x509.DNSName("localhost"),
            x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]
    for ip in local_ips():
        sans.append(x509.IPAddress(ipaddress.ip_address(ip)))
    now = dt.datetime.now(dt.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - dt.timedelta(days=1))
            .not_valid_after(now + dt.timedelta(days=3650))
            .add_extension(x509.SubjectAlternativeName(sans), critical=False)
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .sign(key, hashes.SHA256()))
    with open(KEY, "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                  serialization.NoEncryption()))
    with open(CERT, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    try:
        os.chmod(KEY, 0o600)
    except OSError:
        pass
    print(f"Wrote {CERT} and {KEY} (valid 10 years) for: {', '.join(local_ips()) or 'localhost'}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--fingerprint", action="store_true", help="only print the fingerprint")
    a = ap.parse_args()
    if not a.fingerprint:
        if os.path.exists(CERT):
            sys.exit(f"{CERT} already exists. Delete the certs folder to make a new one "
                     "(every phone would then have to re-pair).")
        make()
    print("\nCertificate fingerprint (SHA-256). Phones must show exactly this during setup:\n")
    print("   " + pretty(fingerprint(CERT)) + "\n")
