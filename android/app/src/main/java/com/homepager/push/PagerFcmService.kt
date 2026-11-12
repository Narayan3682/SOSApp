package com.homepager.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.homepager.alarm.AlarmController
import com.homepager.core.Prefs
import com.homepager.net.Api
import kotlin.concurrent.thread

/** FCM wake-up path: a high-priority data message wakes a sleeping phone and raises the alarm. */
class PagerFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        if (prefs.paired) thread { runCatching { Api(prefs).sendFcmToken(token) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data
        if (d["type"] != "alert") return
        val id = d["alert_id"] ?: return
        AlarmController.fire(applicationContext, id, d["sender_name"] ?: "Someone",
            d["trigger"] ?: "", d["level"]?.toIntOrNull() ?: 0)
    }
}

object FcmSync {
    /** No-op unless google-services.json was present at build time (see docs/FCM_SETUP.md). */
    fun register(ctx: Context) {
        val app = ctx.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                thread { runCatching { Api(Prefs(app)).sendFcmToken(t) } }
            }
        }
    }
}
