package com.homepager.net

import android.content.Context
import com.homepager.BuildConfig
import com.homepager.alarm.AlarmController
import com.homepager.core.AlertPacket
import com.homepager.core.Prefs
import com.homepager.core.SmsPlan
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Sender side. Fires every route at once: server (WebSocket + FCM), LAN broadcast, Nearby,
 * then SMS when the server could not be reached (or the user opted in for priority contacts).
 */
object AlertDispatcher {
    data class Result(
        val serverOk: Boolean, val reached: Int, val total: Int,
        val offlineBroadcast: Boolean, val smsSent: Int,
    )

    /** Blocking (server call + SMS pacing): call from a background thread. */
    fun send(ctx: Context, alertId: String, trigger: String): Result {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        AlarmController.ignore(alertId)                      // never alarm our own phone

        val key = prefs.lanKeyBytes
        val packet = key?.let {
            AlertPacket(AlertPacket.TYPE_ALERT, UUID.fromString(alertId), 0,
                AlertPacket.triggerCodeFor(trigger), System.currentTimeMillis() / 1000, prefs.memberName).encode(it)
        }
        if (packet != null) {
            thread { LanTransport.send(packet) }
            NearbyTransport.advertise(app, packet)
        }

        var ok = false; var reached = 0; var total = 0
        try {
            val d = Api(prefs).sendAlert(alertId, trigger).getJSONObject("delivery")
            total = d.length()
            reached = d.keys().asSequence().count { d.getJSONArray(it).length() > 0 }
            ok = true
        } catch (_: Exception) { /* offline: the other routes already fired */ }

        var sms = 0
        if (BuildConfig.DIRECT_SMS) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val text = "EMERGENCY at home: ${prefs.memberName} sent an alert at $time (Home Pager). " +
                "Call them or go now. If life is in danger call 112."
            sms = SmsSender.send(app, SmsPlan.select(contacts(prefs), ok, prefs.smsAlways, prefs.phone), text)
        }
        return Result(ok, reached, total, packet != null, sms)
    }

    /** "I'm coming": tell the server (if reachable) and everyone on the LAN. */
    fun ack(ctx: Context, alertId: String) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        runCatching { Api(prefs).ack(alertId) }
        prefs.lanKeyBytes?.let { key ->
            LanTransport.send(AlertPacket(AlertPacket.TYPE_ACK, UUID.fromString(alertId), 0, 0,
                System.currentTimeMillis() / 1000, prefs.memberName).encode(key))
        }
    }

    private fun contacts(prefs: Prefs): List<SmsPlan.Contact> = runCatching {
        val a = JSONArray(prefs.contactsJson)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SmsPlan.Contact(o.getString("name"), o.getString("phone"), o.optBoolean("priority"))
        }
    }.getOrDefault(emptyList())
}
