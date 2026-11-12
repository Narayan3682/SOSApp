package com.homepager.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import com.homepager.core.SmsPlan

/** Tier 4 (family flavor): SMS straight from the sender's SIM. Costs money; used when nothing else works. */
object SmsSender {
    /** @return how many messages were handed to the radio (not a delivery confirmation). Background thread only. */
    fun send(ctx: Context, to: List<SmsPlan.Contact>, text: String): Int {
        if (to.isEmpty()) return 0
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                 else @Suppress("DEPRECATION") SmsManager.getDefault()
        var n = 0
        for (c in to) {
            runCatching {
                sm.sendMultipartTextMessage(c.phone, null, sm.divideMessage(text), null, null)
                n++
            }
            runCatching { Thread.sleep(600) }   // stay under Android's SMS rate limiting
        }
        return n
    }
}
