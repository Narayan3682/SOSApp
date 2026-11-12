package com.homepager.net

import com.homepager.core.Prefs
import com.homepager.core.ServerUrlPolicy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Blocking REST client — always call from a background thread. */
class Api(private val prefs: Prefs) {
    companion object { private val JSON = "application/json".toMediaType() }

    /** Pinned for https servers; a new pin can be passed during setup before it is saved. */
    private fun http(base: String, pin: String = prefs.certPin) = Tls.client(base, pin)

    private fun raw(method: String, path: String, body: JSONObject?, auth: Boolean, base: String = prefs.serverUrl, pin: String = prefs.certPin): String {
        val b = Request.Builder().url(base + path)
        if (auth) b.header("Authorization", "Bearer ${prefs.token}")
        b.method(method, body?.toString()?.toRequestBody(JSON) ?: if (method == "GET") null else "".toRequestBody(JSON))
        http(base, pin).newCall(b.build()).execute().use { r ->
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

    private fun obj(method: String, path: String, body: JSONObject?, auth: Boolean, base: String = prefs.serverUrl, pin: String = prefs.certPin) =
        JSONObject(raw(method, path, body, auth, base, pin))

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

    /** Reachability test for the setup screen. Throws with a readable message on failure. */
    fun health(server: String, pin: String = prefs.certPin) {
        val base = checked(server)
        val ok = try { obj("GET", "/health", null, auth = false, base = base, pin = pin).optBoolean("ok") }
                 catch (e: javax.net.ssl.SSLException) { error("Certificate mismatch: this is not the server you verified.") }
                 catch (e: java.net.SocketTimeoutException) { error("Timed out. Same Wi-Fi as the PC? Is port 8000 allowed through Windows Firewall?") }
                 catch (e: java.net.ConnectException) { error("Connection refused. Is the server running on that PC and port?") }
                 catch (e: java.net.UnknownHostException) { error("Address not found. Check the IP / hostname.") }
        if (!ok) error("That address answered, but it is not the Home Pager server.")
    }

    fun createHousehold(server: String, household: String, member: String, device: String, phone: String, setupKey: String, pin: String): String {
        val base = checked(server)
        val r = obj("POST", "/api/households", JSONObject()
            .put("household_name", household).put("member_name", member).put("device_name", device)
            .put("phone", phone.ifBlank { null }).put("setup_key", setupKey.ifBlank { null }),
            auth = false, base = base, pin = pin)
        prefs.certPin = pin
        save(base, r, member, phone, r.getString("invite_code"))
        return r.getString("invite_code")
    }

    fun join(server: String, invite: String, member: String, device: String, phone: String, pin: String) {
        val base = checked(server)
        val r = obj("POST", "/api/join", JSONObject()
            .put("invite_code", invite).put("member_name", member).put("device_name", device)
            .put("phone", phone.ifBlank { null }),
            auth = false, base = base, pin = pin)
        prefs.certPin = pin
        save(base, r, member, phone, invite.uppercase())
    }

    fun sendAlert(alertId: String, trigger: String): JSONObject =
        obj("POST", "/api/alerts", JSONObject().put("alert_id", alertId).put("trigger", trigger), auth = true)

    fun ack(alertId: String) = obj("POST", "/api/alerts/$alertId/ack", null, auth = true)
    fun cancel(alertId: String) = obj("POST", "/api/alerts/$alertId/cancel", null, auth = true)

    fun sendFcmToken(token: String) { raw("PUT", "/api/devices/fcm", JSONObject().put("fcm_token", token), auth = true) }

    data class Member(val name: String, val online: Boolean, val lastSeen: Long?, val isMe: Boolean)

    fun members(): List<Member> {
        val rows = JSONArray(raw("GET", "/api/members", null, auth = true))
        return (0 until rows.length()).map { i ->
            val m = rows.getJSONObject(i)
            Member(m.getString("name"), m.optBoolean("online"),
                if (m.isNull("last_seen")) null else (m.getDouble("last_seen") * 1000).toLong(),
                m.optString("device_id") == prefs.deviceId)
        }
    }

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
