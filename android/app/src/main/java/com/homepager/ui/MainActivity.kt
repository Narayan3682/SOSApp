package com.homepager.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.BuildConfig
import com.homepager.core.AlertBus
import com.homepager.core.Prefs
import com.homepager.core.ServerUrlPolicy
import com.homepager.core.Trigger
import com.homepager.net.Api
import com.homepager.push.FcmSync
import com.homepager.service.PagerService
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)   // bumped on resume so the checklist re-evaluates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (prefs.paired) PagerService.start(this)
        setContent { MaterialTheme { Screen(prefs) } }
    }

    override fun onResume() { super.onResume(); tick++ }

    @Composable
    private fun Screen(prefs: Prefs) {
        var paired by remember { mutableStateOf(prefs.paired) }
        var statusLine by remember { mutableStateOf(AlertBus.last) }
        var smsAlways by remember { mutableStateOf(prefs.smsAlways) }
        DisposableEffect(Unit) {
            val l: (String) -> Unit = { statusLine = it }
            AlertBus.listen(l); onDispose { AlertBus.unlisten(l) }
        }
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Home Pager", fontSize = 28.sp)
            Text("Not a certified emergency service. In danger, call 112 first.", fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            if (!paired) {
                Setup(prefs) { paired = true; PagerService.start(this@MainActivity); FcmSync.register(this@MainActivity) }
            } else {
                Button(modifier = Modifier.fillMaxWidth().height(120.dp),
                    onClick = { Trigger.fire(this@MainActivity, "button") }) { Text("SOS", fontSize = 40.sp) }
                Spacer(Modifier.height(12.dp))
                Text("Status: $statusLine")
                if (prefs.inviteCode.isNotEmpty()) Text("Invite code for family: ${prefs.inviteCode}")
                Text("Server: ${prefs.serverUrl}", fontSize = 12.sp)
                if (BuildConfig.DIRECT_SMS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(smsAlways, { smsAlways = it; prefs.smsAlways = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Also SMS priority contacts on every alert", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                ReadinessCard()
                Text("Tip: add the SOS widget to your home screen and the SOS tile to Quick Settings.", fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ReadinessCard() {
        val items = remember(tick) { Readiness.check(this) }
        val missing = items.count { !it.ok }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (missing == 0) "Ready: alarms will ring" else "$missing setup step(s) left - alarms may fail", fontSize = 16.sp)
                for (item in items) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (item.ok) "OK  " else "FIX ") + item.title, fontSize = 14.sp)
                            if (!item.ok) Text(item.why, fontSize = 11.sp)
                        }
                        if (!item.ok) Button(onClick = { item.fix(this@MainActivity) }) { Text("Fix") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("On Xiaomi / Oppo / Vivo / Samsung also allow 'Autostart' and 'Never sleeping apps' in the phone's own battery settings.", fontSize = 11.sp)
                TextButton(onClick = { Readiness.openAppSettings(this@MainActivity) }) { Text("Open app settings") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    @Composable
    private fun Setup(prefs: Prefs, onDone: () -> Unit) {
        var server by remember { mutableStateOf("http://") }
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var household by remember { mutableStateOf("") }
        var setupKey by remember { mutableStateOf("") }
        var invite by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf("") }
        val api = remember { Api(prefs) }
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val urlErr = ServerUrlPolicy.validate(server)

        fun run(block: () -> Unit) {
            busy = true; err = ""
            thread {
                try { block(); runOnUiThread(onDone) } catch (e: Exception) { err = e.message ?: "failed" }
                busy = false
            }
        }
        OutlinedTextField(server, { server = it }, label = { Text("Server URL (e.g. http://192.168.1.10:8000 or https://...)") },
            isError = urlErr != null, modifier = Modifier.fillMaxWidth())
        if (urlErr != null && server.length > 8) Text(urlErr, fontSize = 12.sp)
        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Your phone number (so family can SMS you offline)") }, modifier = Modifier.fillMaxWidth())
        val ready = !busy && urlErr == null && name.isNotBlank()
        Spacer(Modifier.height(12.dp))
        Text("New household")
        OutlinedTextField(household, { household = it }, label = { Text("Household name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(setupKey, { setupKey = it }, label = { Text("Server setup key (if the server requires one)") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = ready && household.isNotBlank(),
            onClick = { run { api.createHousehold(server, household, name, device, phone, setupKey) } }) { Text("Create household") }
        Spacer(Modifier.height(12.dp))
        Text("...or join an existing one")
        OutlinedTextField(invite, { invite = it }, label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = ready && invite.isNotBlank(),
            onClick = { run { api.join(server, invite, name, device, phone) } }) { Text("Join") }
        if (err.isNotEmpty()) Text(err)
    }
}
