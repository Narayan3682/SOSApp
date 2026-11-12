package com.homepager.net

import android.content.Context
import com.homepager.alarm.AlarmController
import com.homepager.core.AlertBus
import com.homepager.core.AlertPacket
import com.homepager.core.Prefs

/** Entry point for packets that arrive without the server (LAN broadcast, Nearby advertisement). */
object OfflineHandler {
    private const val MAX_SKEW_SEC = 600L

    fun onBytes(ctx: Context, data: ByteArray) {
        val key = Prefs(ctx).lanKeyBytes ?: return
        val p = AlertPacket.decode(data, key, System.currentTimeMillis() / 1000, MAX_SKEW_SEC) ?: return
        when (p.type) {
            AlertPacket.TYPE_ALERT -> AlarmController.fire(
                ctx.applicationContext, p.alertId.toString(), p.name, AlertPacket.triggerNameFor(p.triggerCode), p.level)
            AlertPacket.TYPE_ACK -> AlertBus.post("${p.name} is coming")
        }
    }
}
