package com.homepager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.homepager.R
import com.homepager.ui.CountdownActivity

/** Home-screen SOS button. A tap opens the 5-second cancel countdown (RemoteViews can't do swipe/hold). */
class SosWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val intent = Intent(ctx, CountdownActivity::class.java).putExtra("trigger", "widget")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            mgr.updateAppWidget(id, RemoteViews(ctx.packageName, R.layout.widget_sos).apply {
                setOnClickPendingIntent(R.id.sos, pi)
            })
        }
    }
}
