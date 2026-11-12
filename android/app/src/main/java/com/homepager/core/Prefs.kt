package com.homepager.core

import android.content.Context
import java.util.Base64

/** Pairing credentials and cached household data (needed offline). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("pager", Context.MODE_PRIVATE)

    private fun s(k: String) = sp.getString(k, "") ?: ""
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var serverUrl: String get() = s("server"); set(v) = put("server", v.trim().trimEnd('/'))
    var token: String get() = s("token"); set(v) = put("token", v)
    var deviceId: String get() = s("device"); set(v) = put("device", v)
    var inviteCode: String get() = s("invite"); set(v) = put("invite", v)
    var memberName: String get() = s("member"); set(v) = put("member", v)
    var phone: String get() = s("phone"); set(v) = put("phone", v)
    /** Base64 household secret that signs/verifies offline (LAN/Nearby) packets. */
    var lanKey: String get() = s("lankey"); set(v) = put("lankey", v)
    /** Cached [{"name","phone","priority"}] so SMS fallback works with no internet. */
    var contactsJson: String get() = s("contacts").ifEmpty { "[]" }; set(v) = put("contacts", v)
    var smsAlways: Boolean
        get() = sp.getBoolean("smsAlways", false)
        set(v) = sp.edit().putBoolean("smsAlways", v).apply()

    val paired get() = serverUrl.isNotEmpty() && token.isNotEmpty()

    val lanKeyBytes: ByteArray?
        get() = runCatching { Base64.getDecoder().decode(lanKey) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
