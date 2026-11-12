import hmac


def setup_key_ok(configured: str, provided) -> bool:
    """Creating a household requires the server's setup key when one is configured.
    Constant-time comparison. A missing server key never opens creation."""
    if not configured:
        return False
    return hmac.compare_digest(configured.encode(), (provided or "").encode())
