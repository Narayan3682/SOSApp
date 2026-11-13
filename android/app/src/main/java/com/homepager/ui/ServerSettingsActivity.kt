package com.homepager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.core.AlertBus
import com.homepager.core.Prefs
import com.homepager.core.ServerUrlPolicy
import com.homepager.net.Api
import com.homepager.net.Tls
import com.homepager.service.PagerService
import kotlin.concurrent.thread

/**
 * View, test and change the server address after pairing (e.g. the PC got a new IP).
 * The device token and household stay the same; only the URL (and, with explicit consent, the pinned certificate) change.
 */
class ServerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val api = Api(prefs)
        setContent {
            PagerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var url by remember { mutableStateOf(prefs.serverUrl) }
                    var status by remember { mutableStateOf(AlertBus.last) }
                    var busy by remember { mutableStateOf(false) }
                    var msg by remember { mutableStateOf("") }
                    var ok by remember { mutableStateOf(false) }
                    var newPin by remember { mutableStateOf("") }      // set when the tested server's cert differs
                    var trustNew by remember { mutableStateOf(false) }
                    DisposableEffect(Unit) { val l: (String) -> Unit = { status = it }; AlertBus.listen(l); onDispose { AlertBus.unlisten(l) } }
                    val urlErr = ServerUrlPolicy.validate(url)
                    val changed = url.trim().trimEnd('/') != prefs.serverUrl
                    val isHttps = url.startsWith("https://", ignoreCase = true)

                    fun test() {
                        busy = true; msg = ""; ok = false; newPin = ""; trustNew = false
                        thread {
                            try {
                                val fp = if (isHttps) Tls.fetchFingerprint(url.trim()) else ""
                                val pinToUse = if (isHttps && fp != prefs.certPin) fp else prefs.certPin
                                api.health(url, pinToUse)
                                runOnUiThread {
                                    ok = true
                                    if (isHttps && fp != prefs.certPin) { newPin = fp; msg = "Reachable, but this server shows a DIFFERENT certificate." }
                                    else msg = if (isHttps) "Secure connection OK (same certificate)" else "Reachable (unencrypted)"
                                    busy = false
                                }
                            } catch (e: Exception) { runOnUiThread { msg = e.message ?: "Failed"; busy = false } }
                        }
                    }
                    fun save() {
                        prefs.serverUrl = url
                        if (newPin.isNotEmpty()) prefs.certPin = newPin
                        PagerService.start(this@ServerSettingsActivity, reason = "settings")
                        finish()
                    }

                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp).imePadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { finish() }, contentPadding = PaddingValues(0.dp)) { Text("‹ Back") }
                            Spacer(Modifier.weight(1f))
                        }
                        Text("Server", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Muted("Your household and pairing stay the same when you change the address.")
                        Spacer(Modifier.height(16.dp))

                        SectionCard {
                            Label("CONNECTION")
                            Spacer(Modifier.height(8.dp))
                            StatusPill(status.ifEmpty { "Unknown" }, if (status.startsWith("Connected")) Palette.Ok else Palette.Warn)
                            Spacer(Modifier.height(12.dp))
                            BigButton("Reconnect now", tonal = true) {
                                AlertBus.post("Reconnecting…")
                                PagerService.start(this@ServerSettingsActivity, reason = "manual")
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        SectionCard {
                            Label("ADDRESS")
                            Spacer(Modifier.height(8.dp))
                            Field(url, { url = it; ok = false; msg = ""; newPin = "" }, "Server URL",
                                error = if (url.length > 8) urlErr else null, help = ServerUrlPolicy.hint(url)
                                    ?: "If the PC's address changed, run ipconfig there (or read the first lines python run.py prints).")
                            Spacer(Modifier.height(10.dp))
                            if (msg.isNotEmpty()) { StatusPill(msg, if (ok && newPin.isEmpty()) Palette.Ok else if (ok) Palette.Warn else Palette.Sos); Spacer(Modifier.height(10.dp)) }
                            if (newPin.isNotEmpty()) {
                                Muted("New certificate fingerprint:")
                                Text(Tls.pretty(newPin), fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.height(6.dp))
                                Muted("Only accept this if YOU regenerated the server certificate (deleted the certs folder) and this matches what the server prints. Otherwise someone may be impersonating your server.")
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(trustNew, { trustNew = it }); Text("I verified it, trust the new certificate") }
                                Spacer(Modifier.height(6.dp))
                            }
                            BigButton(if (busy) "Testing…" else "Test connection", enabled = !busy && urlErr == null, tonal = true) { test() }
                            Spacer(Modifier.height(10.dp))
                            BigButton("Save & reconnect", enabled = ok && (newPin.isEmpty() || trustNew) && (changed || newPin.isNotEmpty())) { save() }
                        }
                        Spacer(Modifier.height(14.dp))

                        SectionCard {
                            Label("DETAILS")
                            Spacer(Modifier.height(8.dp))
                            Detail("Current address", prefs.serverUrl)
                            Detail("Pinned certificate", if (prefs.certPin.isEmpty()) "none (plain http)" else Tls.pretty(prefs.certPin))
                            Detail("Member", prefs.memberName)
                            Detail("Device id", prefs.deviceId.take(8) + "…")
                            Detail("Invite code", prefs.inviteCode.chunked(4).joinToString(" "))
                            Spacer(Modifier.height(6.dp))
                            Muted("Tip: give the server PC a fixed IP (DHCP reservation in the router) so this never changes.", 12)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Detail(k: String, v: String) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Muted(k, 12)
            Text(v, fontSize = 14.sp, fontFamily = if (k.contains("certificate") || k.contains("code")) FontFamily.Monospace else FontFamily.Default)
        }
    }
}
