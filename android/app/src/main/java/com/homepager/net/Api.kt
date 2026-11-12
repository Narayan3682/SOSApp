package com.homepager.net

import com.homepager.core.Prefs
import com.homepager.core.ServerUrlPolicy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Blocking REST client — always call from a background thread. */
class Api(private val prefs: Prefs) {
    companion object {
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
        private val JSON = "application/json".toMediaType()
    }

    private fun raw(method: String, path: String, body: JSONObject?, auth: Boolean, base: String = prefs.serverUrl): String {
        val b = Request.Builder().url(base + path)
        if (auth) b.header("Authorization", "Bearer ${prefs.token}")
        b.method(method, body?.toString()?.toRequestBody(JSON) ?: if (method == "GET") null else "".toRequestBody(JSON))
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error(
                when (r.code) {
                    403 -> "Wrong setup key"
                    404 -> "Not found (check the invite code)"
                    429 -> "Too many attempts, wait a minute"
                    else -> "HTTP ${r.code}: $text"
                })
            return text
        }
    }

    private fun obj(method: String, path: String, body: JSONObject?, auth: Boolean, base: String = prefs.serverUrl) =
        JSONObject(raw(method, path, body, auth, base))

    private fun checked(server: String): String {
        ServerUrlPolicy.validate(server)?.let { error(it) }
        return server.trim().trimEnd('/')
    }

    private fun save(base: String, r: JSONObject, member: String, phone: String, invite: String) {
        prefs.serverUrl = base
        prefs.token = r.getString("device_token")
        prefs.deviceId = r.getString("device_id")
        prefs.lanKey = r.optString("lan_key")
        prefs.memberName = member
        prefs.phone = phone
        prefs.inviteCode = invite
    }

    fun createHousehold(server: String, household: String, member: String, device: String, phone: String, setupKey: String): String {
        val base = checked(server)
        val r = obj("POST", "/api/households", JSONObject()
            .put("household_name", household).put("member_name", member).put("device_name", device)
            .put("phone", phone.ifBlank { null }).put("setup_key", setupKey.ifBlank { null }),
            auth = false, base = base)
        save(base, r, member, phone, r.getString("invite_code"))
        return r.getString("invite_code")
    }

    fun join(server: String, invite: String, member: String, device: String, phone: String) {
        val base = checked(server)
        val r = obj("POST", "/api/join", JSONObject()
            .put("invite_code", invite).put("member_name", member).put("device_name", device)
            .put("phone", phone.ifBlank { null }),
            auth = false, base = base)
        save(base, r, member, phone, invite.uppercase())
    }

    fun sendAlert(alertId: String, trigger: String): JSONObject =
        obj("POST", "/api/alerts", JSONObject().put("alert_id", alertId).put("trigger", trigger), auth = true)

    fun ack(alertId: String) = obj("POST", "/api/alerts/$alertId/ack", null, auth = true)
    fun cancel(alertId: String) = obj("POST", "/api/alerts/$alertId/cancel", null, auth = true)

    fun sendFcmToken(token: String) { raw("PUT", "/api/devices/fcm", JSONObject().put("fcm_token", token), auth = true) }

    /** Refresh what is needed while offline: household signing key + member phone numbers. */
    fun syncHousehold() {
        val h = obj("GET", "/api/household", null, auth = true)
        h.optString("lan_key").takeIf { it.isNotEmpty() }?.let { prefs.lanKey = it }
        h.optString("invite_code").takeIf { it.isNotEmpty() }?.let { prefs.inviteCode = it }
        val rows = JSONArray(raw("GET", "/api/members", null, auth = true))
        val out = JSONArray()
        val seen = HashSet<String>()
        for (i in 0 until rows.length()) {
            val m = rows.getJSONObject(i)
            val phone = m.optString("phone")
            if (phone.isEmpty() || !seen.add(m.getString("id"))) continue
            out.put(JSONObject().put("name", m.getString("name")).put("phone", phone)
                .put("priority", m.optInt("is_priority") == 1))
        }
        prefs.contactsJson = out.toString()
    }
}
