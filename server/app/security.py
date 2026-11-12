import hmac


def setup_key_ok(configured: str, provided) -> bool:
    """Creating a household requires the server's setup key when one is configured.
    Constant-time comparison; an unset server key leaves creation open (LAN-only use)."""
    if not configured:
        return True
    return hmac.compare_digest(configured.encode(), (provided or "").encode())
