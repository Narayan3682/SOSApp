package com.homepager.core

/** Detects N presses within a time window. Pure logic so it can be unit-tested on the JVM. */
class PressDetector(private val presses: Int = 3, private val windowMs: Long = 1_500) {
    private val times = ArrayDeque<Long>()

    /** @return true when this press completes the pattern. */
    fun onPress(nowMs: Long): Boolean {
        times.addLast(nowMs)
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
        if (times.size >= presses) { times.clear(); return true }
        return false
    }
}
