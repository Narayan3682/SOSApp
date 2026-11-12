import unittest

from app.security import setup_key_ok


class SetupKeyTests(unittest.TestCase):
    def test_never_open_without_a_configured_key(self):
        self.assertFalse(setup_key_ok("", None))
        self.assertFalse(setup_key_ok("", "anything"))

    def test_required_when_configured(self):
        self.assertTrue(setup_key_ok("s3cret", "s3cret"))
        self.assertFalse(setup_key_ok("s3cret", "wrong"))
        self.assertFalse(setup_key_ok("s3cret", None))
        self.assertFalse(setup_key_ok("s3cret", ""))

    def test_non_ascii_keys_do_not_crash(self):
        self.assertTrue(setup_key_ok("कुंजी", "कुंजी"))
        self.assertFalse(setup_key_ok("कुंजी", "key"))


if __name__ == "__main__":
    unittest.main()
