package com.homepager.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressDetectorTest {
    @Test fun threeQuickPressesTrigger() {
        val p = PressDetector()
        assertFalse(p.onPress(0)); assertFalse(p.onPress(400)); assertTrue(p.onPress(800))
    }

    @Test fun slowPressesDoNotTrigger() {
        val p = PressDetector()
        assertFalse(p.onPress(0)); assertFalse(p.onPress(1_000)); assertFalse(p.onPress(2_000))
        assertFalse(p.onPress(3_000))
    }

    @Test fun windowBoundaryIsInclusive() {
        val p = PressDetector()
        assertFalse(p.onPress(0)); assertFalse(p.onPress(700)); assertTrue(p.onPress(1_500))
    }

    @Test fun resetsAfterFiring() {
        val p = PressDetector()
        p.onPress(0); p.onPress(100); assertTrue(p.onPress(200))
        assertFalse(p.onPress(300))   // needs three fresh presses again
        assertFalse(p.onPress(400)); assertTrue(p.onPress(500))
    }
}
