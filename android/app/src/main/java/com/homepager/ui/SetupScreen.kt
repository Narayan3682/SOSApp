package com.homepager.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.core.Prefs
import com.homepager.core.ServerUrlPolicy
import com.homepager.net.Api
import com.homepager.net.Tls
import kotlin.concurrent.thread

/** Three steps: connect to the server -> who you are -> create or join. Each step validates before the next. */
@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val api = remember { Api(prefs) }
    var step by remember { mutableIntStateOf(1) }
    var server by remember { mutableStateOf("https://") }
    var pin by remember { mutableStateOf("") }          // fingerprint fetched from the server
    var pinConfirmed by remember { mutableStateOf(false) }
    var serverTested by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("join") }
    var household by remember { mutableStateOf("") }
    var setupKey by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val device = "${Build.MANUFACTURER} ${Build.MODEL}"
    val urlErr = ServerUrlPolicy.validate(server)
    val urlHint = ServerUrlPolicy.hint(server)

    fun bg(block: () -> Unit, after: () -> Unit = {}) {
        busy = true; err = ""
        thread {
            try { block(); activity.runOnUiThread { busy = false; after() } }
            catch (e: Exception) { activity.runOnUiThread { busy = false; err = e.message ?: "Failed" } }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).imePadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Set up Home Pager", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Muted("Three quick steps. Takes about a minute.")
        Spacer(Modifier.height(16.dp))
        StepHeader(step)
        Spacer(Modifier.height(16.dp))

        when (step) {
            1 -> SectionCard {
                Label("STEP 1 · SERVER")
                Spacer(Modifier.height(6.dp))
                Text("Where is your pager server?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Muted("On the PC running the server, run python run.py and note the address it prints, e.g. https://192.168.1.23:8443. (ipconfig shows the PC's IPv4 address.)")
                Spacer(Modifier.height(14.dp))
                Field(server, { server = it; serverTested = false; testMsg = ""; pin = ""; pinConfirmed = false }, "Server URL",
                    placeholder = "https://192.168.1.23:8443",
                    error = if (server.length > 8) urlErr else null, help = urlHint)
                Spacer(Modifier.height(12.dp))
                val isHttps = server.startsWith("https://", ignoreCase = true)
                if (isHttps && pin.isNotEmpty()) {
                    Label("SERVER CERTIFICATE")
                    Spacer(Modifier.height(4.dp))
                    Text(Tls.pretty(pin), fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Muted("Compare with the fingerprint printed in the server's terminal. If it differs, someone may be impersonating your server - do not continue.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pinConfirmed, { pinConfirmed = it })
                        Text("It matches", fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (testMsg.isNotEmpty()) {
                    StatusPill(testMsg, if (serverTested) Palette.Ok else Palette.Sos)
                    Spacer(Modifier.height(12.dp))
                }
                BigButton(if (busy) "Testing…" else if (isHttps && pin.isEmpty()) "Fetch certificate & test" else "Test connection",
                    enabled = !busy && urlErr == null, tonal = true) {
                    bg({
                        val p = if (isHttps) Tls.fetchFingerprint(server.trim()) else ""
                        api.health(server, p)
                        activity.runOnUiThread { pin = p }
                    }) { serverTested = true; testMsg = if (isHttps) "Secure connection OK" else "Reachable (unencrypted - LAN only)" }
                }
                if (err.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                BigButton("Continue", enabled = serverTested && (!isHttps || pinConfirmed)) { err = ""; step = 2 }
            }
            2 -> SectionCard {
                Label("STEP 2 · YOU")
                Spacer(Modifier.height(6.dp))
                Text("Who is this phone?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Muted("Your name is what family will see in the alarm. The number lets them reach you by SMS when there is no internet.")
                Spacer(Modifier.height(14.dp))
                Field(name, { name = it }, "Your name", placeholder = "e.g. Narayan")
                Spacer(Modifier.height(10.dp))
                Field(phone, { phone = it }, "Mobile number (optional)", placeholder = "+91 98765 43210")
                Spacer(Modifier.height(16.dp))
                Row {
                    GhostButton("Back", Modifier.weight(1f)) { step = 1 }
                    Spacer(Modifier.width(10.dp))
                    BigButton("Continue", Modifier.weight(1f), enabled = name.isNotBlank()) { step = 3 }
                }
            }
            else -> SectionCard {
                Label("STEP 3 · HOUSEHOLD")
                Spacer(Modifier.height(6.dp))
                Text("Join your family, or start a new household", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(mode == "join", { mode = "join" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Join with code") }
                    SegmentedButton(mode == "create", { mode = "create" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Create new") }
                }
                Spacer(Modifier.height(14.dp))
                if (mode == "join") {
                    Muted("Ask the person who created the household for the 8-letter invite code shown on their home screen.")
                    Spacer(Modifier.height(10.dp))
                    Field(invite, { invite = it.uppercase() }, "Invite code", placeholder = "e.g. KX7PQ2MN")
                } else {
                    Muted("Only one person per family creates the household. Everyone else joins with the invite code you get afterwards.")
                    Spacer(Modifier.height(10.dp))
                    Field(household, { household = it }, "Household name", placeholder = "e.g. Kaluri home")
                    Spacer(Modifier.height(10.dp))
                    Field(setupKey, { setupKey = it }, "Server setup key", help = "Printed in the server terminal at startup (\"Setup key: …\"). Needed only once, by whoever creates the household.")
                }
                if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Spacer(Modifier.height(16.dp))
                Row {
                    GhostButton("Back", Modifier.weight(1f)) { err = ""; step = 2 }
                    Spacer(Modifier.width(10.dp))
                    val ready = !busy && if (mode == "join") invite.length >= 6 else household.isNotBlank()
                    BigButton(if (busy) "Please wait…" else if (mode == "join") "Join" else "Create", Modifier.weight(1f), enabled = ready) {
                        if (mode == "join") bg({ api.join(server, invite, name, device, phone, pin) }, onDone)
                        else bg({ api.createHousehold(server, household, name, device, phone, setupKey, pin) }, onDone)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Muted("Home Pager is not a certified emergency service. In danger, call 112 first.", 12)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepHeader(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            val active = i <= step
            LinearProgressIndicator(
                progress = { if (active) 1f else 0f },
                modifier = Modifier.weight(1f).height(6.dp),
                color = if (i == step) MaterialTheme.colorScheme.primary else Palette.Ok,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (i < 3) Spacer(Modifier.width(6.dp))
        }
    }
}
