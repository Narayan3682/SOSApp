package com.homepager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.homepager.core.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Prefs(ctx).paired) { PagerService.start(ctx, reason = "boot"); Keepalive.schedule(ctx) }
    }
}
