package com.homepager.trigger

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.homepager.core.PressDetector
import com.homepager.core.Trigger

/**
 * Fires on: Volume-Down pressed 3 times within 1.5 s, OR Volume-Down + Volume-Up held together for 2 s.
 * Normal single presses still change volume (events are passed through, never swallowed).
 * Works with the screen off once the user enables this service in Settings > Accessibility.
 */
class VolumeKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val detector = PressDetector(presses = 3, windowMs = 1_500)
    private var downHeld = false
    private var upHeld = false
    private val holdFire = Runnable { if (downHeld && upHeld) Trigger.fire(this, "volume-hold") }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                downHeld = down
                if (down && e.repeatCount == 0) countPress()
            }
            KeyEvent.KEYCODE_VOLUME_UP -> upHeld = down
            else -> return false
        }
        handler.removeCallbacks(holdFire)
        if (downHeld && upHeld) handler.postDelayed(holdFire, 2_000)
        return false // never consume: normal volume behaviour is preserved
    }

    private fun countPress() {
        if (detector.onPress(System.currentTimeMillis())) Trigger.fire(this, "volume-triple")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
