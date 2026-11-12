package com.homepager.core

/**
 * The same alert can arrive via WebSocket, FCM, LAN and Nearby. Fire once per (alert, escalation level),
 * so a server re-alert (level+1) sounds again, but duplicates never do, and a closed alert stays silent.
 */
class AlertDeduper {
    private val seen = HashSet<String>()
    private val closed = HashSet<String>()

    @Synchronized
    fun shouldFire(alertId: String, level: Int): Boolean =
        alertId !in closed && seen.add("$alertId#$level")

    /** Call when the user acknowledged it, or it was cancelled/resolved. */
    @Synchronized
    fun close(alertId: String) { closed.add(alertId) }
}
