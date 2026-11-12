package com.homepager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** 5-second cancel ring, then fires every route and shows exactly who was (not) reached. */
class CountdownActivity : ComponentActivity() {
    private val alertId = UUID.randomUUID().toString()
    private val total = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (!prefs.paired) { startActivity(Intent(this, MainActivity::class.java)); finish(); return }
        val trigger = intent.getStringExtra("trigger") ?: "button"
        val app = applicationContext
        setContent {
            PagerTheme {
                var left by remember { mutableIntStateOf(total) }
                var phase by remember { mutableStateOf("count") }   // count -> sending -> done
                var result by remember { mutableStateOf<AlertDispatcher.Result?>(null) }
                var acks by remember { mutableStateOf(listOf<String>()) }
                DisposableEffect(Unit) {
                    val l: (String) -> Unit = { if (it.endsWith("is coming") && it !in acks) acks = acks + it }
                    AlertBus.listen(l); onDispose { AlertBus.unlisten(l) }
                }
                LaunchedEffect(Unit) {
                    while (left > 0) { delay(1000); left-- }
                    phase = "sending"
                    thread { val r = AlertDispatcher.send(app, alertId, trigger); runOnUiThread { result = r; phase = "done" } }
                }
                val bg = if (phase == "count") Palette.SosDeep else MaterialTheme.colorScheme.background
                Surface(Modifier.fillMaxSize(), color = bg) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        when (phase) {
                            "count" -> Counting(left)
                            "sending" -> { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("Sending to everyone…", fontSize = 20.sp) }
                            else -> result?.let { Outcome(it, acks) }
                        }
                        Spacer(Modifier.weight(1f))
                        val serverOk = result?.serverOk == true
                        if (phase == "count") {
                            Button(
                                onClick = { finish() }, Modifier.fillMaxWidth().height(72.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.SosDeep),
                            ) { Text("CANCEL — false alarm", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        } else {
                            BigButton(if (serverOk) "Cancel this alert" else "Close", tonal = true) {
                                if (serverOk) thread { runCatching { Api(Prefs(app)).cancel(alertId) } }
                                finish()
                            }
                            Spacer(Modifier.height(10.dp))
                            DialButton(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Counting(left: Int) {
        val progress by animateFloatAsState(left / total.toFloat(), tween(900), label = "ring")
        Text("EMERGENCY ALERT", color = Color.White.copy(alpha = 0.85f), letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, Modifier.size(240.dp), color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f), strokeWidth = 10.dp, strokeCap = StrokeCap.Round)
            Text("$left", fontSize = 110.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Text("Your family will be alerted when this reaches zero", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
    }

    @Composable
    private fun Outcome(r: AlertDispatcher.Result, acks: List<String>) {
        val nobody = !r.serverOk && !r.offlineBroadcast && r.smsSent == 0
        Text(if (nobody) "Nobody could be alerted" else "Alert sent", fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = if (nobody) MaterialTheme.colorScheme.error else Palette.Ok)
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Line(r.serverOk, if (r.serverOk) "Server: reached ${r.reached} of ${r.total} other phones instantly" else "Server unreachable")
            Line(r.offlineBroadcast, if (r.offlineBroadcast) "Wi-Fi & nearby phones: broadcast sent (no receipt possible)" else "Wi-Fi & nearby: not available (not yet synced)")
            if (BuildConfig.DIRECT_SMS) Line(r.smsSent > 0,
                if (r.smsSent > 0) "SMS: sent to ${r.smsSent} contact${if (r.smsSent > 1) "s" else ""}"
                else if (r.serverOk) "SMS: not needed" else "SMS: none sent (permission missing or no numbers saved)")
        }
        Spacer(Modifier.height(14.dp))
        if (acks.isEmpty()) Muted(if (r.serverOk) "Waiting for someone to answer…" else "Offline: answers only arrive over Wi-Fi.", 14)
        else for (a in acks) { StatusPill(a, Palette.Ok); Spacer(Modifier.height(6.dp)) }
        if (!r.serverOk) { Spacer(Modifier.height(10.dp)); Text("If life is in danger, call 112 now.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
    }

    @Composable
    private fun Line(ok: Boolean, text: String) {
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(if (ok) Palette.Ok else Palette.Warn, 9); Spacer(Modifier.width(10.dp)); Text(text, fontSize = 14.sp)
        }
    }
}
