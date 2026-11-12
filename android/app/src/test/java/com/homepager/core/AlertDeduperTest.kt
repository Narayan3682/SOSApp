package com.homepager.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDeduperTest {
    @Test fun duplicateFromAnotherPathIsIgnored() {
        val d = AlertDeduper()
        assertTrue(d.shouldFire("a1", 0))
        assertFalse(d.shouldFire("a1", 0))
    }

    @Test fun serverReAlertAtHigherLevelFiresAgain() {
        val d = AlertDeduper()
        assertTrue(d.shouldFire("a1", 0))
        assertTrue(d.shouldFire("a1", 1))
        assertFalse(d.shouldFire("a1", 1))
    }

    @Test fun closedAlertNeverFiresAgain() {
        val d = AlertDeduper()
        assertTrue(d.shouldFire("a1", 0))
        d.close("a1")
        assertFalse(d.shouldFire("a1", 1))
        assertFalse(d.shouldFire("a1", 0))
    }

    @Test fun differentAlertsAreIndependent() {
        val d = AlertDeduper()
        assertTrue(d.shouldFire("a1", 0))
        assertTrue(d.shouldFire("a2", 0))
    }
}
