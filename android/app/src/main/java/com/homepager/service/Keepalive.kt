package com.homepager.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.homepager.core.Prefs
import java.util.concurrent.TimeUnit

/**
 * Belt and braces against Android killing the listener:
 *  - an exact alarm every 15 min (allowed while idle) that restarts the service and pokes the connection,
 *  - a WorkManager periodic job as a backstop if the process was killed entirely.
 */
object Keepalive {
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(ctx: Context) {
        if (!Prefs(ctx).paired) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "pager-keepalive", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<KeepaliveWorker>(15, TimeUnit.MINUTES).build())
    }

    class TickReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            PagerService.start(ctx, reason = "alarm")
            schedule(ctx)   // re-arm: exact alarms are one-shot
        }
    }

    class KeepaliveWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
        override fun doWork(): Result {
            runCatching { PagerService.start(applicationContext, reason = "work") }
            return Result.success()
        }
    }
}
