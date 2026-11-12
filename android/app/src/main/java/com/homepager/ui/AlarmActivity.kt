package com.homepager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.alarm.AlarmController
import com.homepager.net.AlertDispatcher
import kotlin.concurrent.thread

/** Full-screen incoming alert (over lock screen). */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alertId = intent.getStringExtra("alert_id") ?: ""
        val sender = intent.getStringExtra("sender") ?: "Someone"
        val trigger = intent.getStringExtra("trigger") ?: ""
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("EMERGENCY", fontSize = 40.sp)
                    Text("$sender needs help (via $trigger)", fontSize = 20.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(72.dp), onClick = {
                        AlarmController.close(this@AlarmActivity, alertId)
                        thread { AlertDispatcher.ack(applicationContext, alertId) }
                        finish()
                    }) { Text("I'M COMING", fontSize = 22.sp) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { AlarmController.stop(this@AlarmActivity) }) {
                        Text("Silence only")
                    }
                    Spacer(Modifier.height(12.dp))
                    DialButton()
                }
            }
        }
    }
}

@Composable
fun DialButton() {
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        // ACTION_DIAL opens the dialer pre-filled; needs no CALL_PHONE permission.
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text("Call 112") }
}
