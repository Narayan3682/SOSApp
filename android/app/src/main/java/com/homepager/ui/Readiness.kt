package com.homepager.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.homepager.BuildConfig

/** The one-time setup that decides whether an alarm actually rings. Re-evaluated every time the app is shown. */
object Readiness {
    class Item(val title: String, val why: String, val ok: Boolean, val fix: (Activity) -> Unit)

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun open(a: Activity, i: Intent) { runCatching { a.startActivity(i) } }
    private fun pkg(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    private fun nearbyPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES)
        Build.VERSION.SDK_INT >= 31 -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT)
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun check(ctx: Context): List<Item> {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val items = mutableListOf<Item>()

        items += Item("Notifications", "Needed to show the alarm and keep the listener alive.",
            Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        ) { a -> if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }

        items += Item("Full-screen alerts (Android 14+)", "Lets the alarm appear over the lock screen.",
            Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        ) { a -> open(a, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(a))) }

        items += Item("Unrestricted battery", "Stops the phone from killing the listener in the background.",
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        ) { a ->
            if (BuildConfig.VOLUME_TRIGGER) open(a, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(a)))
            else open(a, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        items += Item("Ring through Do Not Disturb", "Lets the siren sound even when DND is on.",
            nm.isNotificationPolicyAccessGranted
        ) { a -> open(a, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }

        val nearby = nearbyPermissions()
        items += Item("Offline alerts (Bluetooth / nearby)", "Alerts between phones with no router or internet.",
            nearby.all { granted(ctx, it) }
        ) { a -> a.requestPermissions(nearby, 2) }

        if (BuildConfig.VOLUME_TRIGGER) {
            val me = android.content.ComponentName(ctx.packageName, "com.homepager.trigger.VolumeKeyService").flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
            items += Item("Volume-button trigger", "Turn on 'Home Pager emergency trigger' in Accessibility.",
                enabled) { a -> open(a, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        if (BuildConfig.DIRECT_SMS) {
            items += Item("Send SMS", "Last-resort alert by text when there is no internet at all.",
                granted(ctx, Manifest.permission.SEND_SMS)
            ) { a -> a.requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 3) }
        }
        return items
    }

    fun openAppSettings(a: Activity) = open(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)))
}
