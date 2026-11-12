package com.homepager.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.homepager.PagerApp
import com.homepager.core.AlertDeduper
import com.homepager.ui.AlarmActivity

/** Siren on the ALARM stream (max volume) + vibration + full-screen notification. */
object AlarmController {
    private const val NOTIF_ID = 1001
    private var player: MediaPlayer? = null
    private val deduper = AlertDeduper()
    @Volatile var activeAlertId: String? = null
        private set

    fun fire(ctx: Context, alertId: String, sender: String, trigger: String, level: Int = 0) {
        if (!deduper.shouldFire(alertId, level)) return // same alert via WS + FCM + LAN, or already handled
        activeAlertId = alertId
        val intent = Intent(ctx, AlarmActivity::class.java)
            .putExtra("alert_id", alertId).putExtra("sender", sender).putExtra("trigger", trigger)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, PagerApp.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EMERGENCY from $sender")
            .setContentText("Tap to respond")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        try { ctx.startActivity(intent) } catch (_: Exception) { /* background start blocked: notification covers it */ }
        startSiren(ctx)
    }

    private fun startSiren(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        stopSiren()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            isLooping = true
            prepare(); start()
        }
        val pattern = longArrayOf(0, 800, 400)
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopSiren() { player?.runCatching { stop(); release() }; player = null }

    /** Mark an alert as ours (we sent it) so our own broadcast never alarms us. */
    fun ignore(alertId: String) = deduper.close(alertId)

    /** Alert answered/cancelled/resolved: silence and never re-sound it. */
    fun close(ctx: Context, alertId: String) { deduper.close(alertId); stop(ctx) }

    /** Silence locally (user pressed Silence / acknowledged / alert cancelled). */
    fun stop(ctx: Context) {
        stopSiren()
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        v.cancel()
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        activeAlertId = null
    }
}
