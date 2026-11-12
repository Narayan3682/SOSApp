package com.homepager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.BuildConfig
import com.homepager.core.AlertBus
import com.homepager.core.Prefs
import com.homepager.net.AlertDispatcher
import com.homepager.net.Api
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.concurrent.thread

/** 5-second cancel window, then fires every route. Reports honestly what was (not) delivered. */
class CountdownActivity : ComponentActivity() {
    private val alertId = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (!prefs.paired) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        val trigger = intent.getStringExtra("trigger") ?: "button"
        val app = applicationContext
        setContent {
            MaterialTheme {
                var left by remember { mutableIntStateOf(5) }
                var phase by remember { mutableStateOf("count") }       // count -> sending -> done
                var summary by remember { mutableStateOf("") }
                var serverOk by remember { mutableStateOf(false) }
                var ackLine by remember { mutableStateOf("") }
                DisposableEffect(Unit) {
                    val l: (String) -> Unit = { if ("coming" in it) ackLine = it }
                    AlertBus.listen(l); onDispose { AlertBus.unlisten(l) }
                }
                LaunchedEffect(Unit) {
                    while (left > 0) { delay(1000); left-- }
                    phase = "sending"
                    thread {
                        val r = AlertDispatcher.send(app, alertId, trigger)
                        serverOk = r.serverOk
                        summary = describe(r)
                        phase = "done"
                    }
                }
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (phase) {
                        "count" -> { Text("Sending emergency alert in", fontSize = 20.sp); Text("$left", fontSize = 96.sp) }
                        "sending" -> Text("Sending...", fontSize = 22.sp)
                        else -> {
                            Text(summary, fontSize = 18.sp)
                            if (ackLine.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(ackLine, fontSize = 22.sp) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (phase != "count" && serverOk) thread { runCatching { Api(Prefs(app)).cancel(alertId) } }
                        finish()
                    }) { Text(if (phase == "count") "CANCEL - false alarm" else if (serverOk) "Cancel alert" else "Close") }
                    Spacer(Modifier.height(12.dp))
                    DialButton()
                }
            }
        }
    }

    private fun describe(r: AlertDispatcher.Result): String = buildString {
        if (r.serverOk) appendLine("Server: delivered instantly to ${r.reached} of ${r.total} other devices.")
        else appendLine("Server unreachable.")
        if (r.offlineBroadcast) appendLine("Also broadcasting over Wi-Fi and to nearby phones (cannot confirm receipt).")
        if (r.smsSent > 0) appendLine("SMS sent to ${r.smsSent} contact(s).")
        else if (BuildConfig.DIRECT_SMS && !r.serverOk) appendLine("No SMS sent (SMS permission missing, or no contacts with phone numbers).")
        if (!r.serverOk && !r.offlineBroadcast && r.smsSent == 0) append("NOBODY WAS ALERTED. Call 112 or shout for help.")
        else if (!r.serverOk) append("Nearby phones may or may not have received it. If in danger, call 112.")
    }.trim()
}
