package com.homepager.core

import android.content.Context
import android.content.Intent
import com.homepager.ui.CountdownActivity
import com.homepager.ui.MainActivity

/** Single entry point every trigger (button, volume keys, widget, tile, voice) calls. */
object Trigger {
    fun fire(ctx: Context, source: String) {
        val target = if (Prefs(ctx).paired) CountdownActivity::class.java else MainActivity::class.java
        ctx.startActivity(
            Intent(ctx, target).putExtra("trigger", source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
