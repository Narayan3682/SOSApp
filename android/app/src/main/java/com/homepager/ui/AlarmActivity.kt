package com.homepager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.alarm.AlarmController
import com.homepager.net.AlertDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Full-screen incoming alert over the lock screen. One huge answer button, nothing to misread. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val alertId = intent.getStringExtra("alert_id") ?: ""
        val sender = intent.getStringExtra("sender") ?: "Someone"
        val trigger = intent.getStringExtra("trigger") ?: ""
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        setContent {
            PagerTheme {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    1f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "scale")
                Surface(Modifier.fillMaxSize(), color = Palette.SosDeep) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(140.dp).scale(pulse).background(Color.White.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("!", fontSize = 90.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("EMERGENCY", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("$sender needs help", fontSize = 26.sp, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        Text("$time · via ${label(trigger)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                AlarmController.close(this@AlarmActivity, alertId)
                                thread { AlertDispatcher.ack(applicationContext, alertId) }
                                finish()
                            },
                            Modifier.fillMaxWidth().height(88.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.SosDeep),
                        ) { Text("I'M COMING", fontSize = 26.sp, fontWeight = FontWeight.Black) }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            OutlinedButton(
                                onClick = { AlarmController.stop(this@AlarmActivity) }, Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) { Text("Silence") }
                            Spacer(Modifier.width(10.dp))
                            DialButton(Modifier.weight(1f).height(56.dp), light = true)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    private fun label(t: String) = when (t) {
        "volume-triple", "volume-hold" -> "volume buttons"
        "widget" -> "widget"; "tile" -> "quick settings"; "voice" -> "voice"; else -> "SOS button"
    }
}

@Composable
fun DialButton(modifier: Modifier = Modifier, light: Boolean = false) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        modifier.height(52.dp),
        colors = if (light) ButtonDefaults.outlinedButtonColors(contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(),
    ) { Text("Call 112", fontWeight = FontWeight.SemiBold) }
}
