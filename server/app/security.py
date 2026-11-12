import hmac


def setup_key_ok(configured: str, provided) -> bool:
    """Creating a household requires the server's setup key when one is configured.
    Constant-time comparison. A missing server key never opens creation."""
    if not configured:
        return False
    return hmac.compare_digest(configured.encode(), (provided or "").encode())


MAX_BODY = 16_384


def body_too_large(content_length, limit: int = MAX_BODY) -> bool:
    """True only for a well-formed Content-Length above the limit; a missing header is fine."""
    if content_length is None:
        return False
    try:
        return int(content_length) > limit
    except (TypeError, ValueError):
        return False
