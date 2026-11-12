package com.homepager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class PagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Pager connection", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Emergency alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen home emergency alerts"
                setSound(null, null) // AlarmController plays the siren itself (alarm stream)
                setBypassDnd(true)   // honoured only if the user grants DND access
            })
    }

    companion object {
        const val CH_SERVICE = "service"
        const val CH_ALERT = "alert"
    }
}
