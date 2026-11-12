import os

DB_PATH = os.getenv("PAGER_DB_PATH", "pager.db")
ESCALATE_AFTER_S = float(os.getenv("ESCALATE_AFTER_S", "30"))
MAX_REALERTS = int(os.getenv("MAX_REALERTS", "3"))
FCM_PROJECT_ID = os.getenv("FCM_PROJECT_ID", "")
# If set, creating a new household requires this key (joining uses the invite code).
SETUP_KEY = os.getenv("SETUP_KEY", "")
