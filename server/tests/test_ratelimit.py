import unittest

from app.ratelimit import RateLimiter


class RateLimiterTests(unittest.TestCase):
    def test_blocks_after_limit_and_recovers(self):
        t = [0.0]
        rl = RateLimiter(3, 60, clock=lambda: t[0])
        self.assertTrue(all(rl.allow("ip") for _ in range(3)))
        self.assertFalse(rl.allow("ip"))
        self.assertTrue(rl.allow("other"))      # keys are independent
        t[0] = 61
        self.assertTrue(rl.allow("ip"))         # window slid


if __name__ == "__main__":
    unittest.main()
