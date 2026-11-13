package com.homepager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepager.BuildConfig
import com.homepager.core.AlertBus
import com.homepager.core.Prefs
import com.homepager.core.Trigger
import com.homepager.net.Api
import com.google.firebase.FirebaseApp
import kotlin.concurrent.thread
import com.homepager.push.FcmSync
import com.homepager.service.PagerService

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)   // bumped on resume so the checklist re-evaluates

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        if (prefs.paired) PagerService.start(this)
        setContent {
            PagerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { Root(prefs) }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); tick++ }

    @Composable
    private fun Root(prefs: Prefs) {
        var paired by remember { mutableStateOf(prefs.paired) }
        if (!paired) SetupScreen(prefs) { paired = true; PagerService.start(this); FcmSync.register(this) }
        else HomeScreen(prefs)
    }

    @Composable
    private fun HomeScreen(prefs: Prefs) {
        var status by remember { mutableStateOf(AlertBus.last.ifEmpty { "Connecting…" }) }
        var smsAlways by remember { mutableStateOf(prefs.smsAlways) }
        DisposableEffect(Unit) {
            val l: (String) -> Unit = { status = it }
            AlertBus.listen(l); onDispose { AlertBus.unlisten(l) }
        }
        val online = status.startsWith("Connected")
        val items = remember(tick) { Readiness.check(this) }
        val missing = items.count { !it.ok }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Home Pager", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Muted("Hi ${prefs.memberName.ifEmpty { "there" }}", 14)
                }
                Box(Modifier.clickable { startActivity(Intent(this@MainActivity, ServerSettingsActivity::class.java)) }) {
                    StatusPill(
                        when {
                            online -> "Online"
                            status.startsWith("Server unreachable") -> "Offline · tap"
                            else -> "Connecting · tap"
                        },
                        when {
                            online -> Palette.Ok
                            status.startsWith("Server unreachable") -> Palette.Warn
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (!online) {
                Spacer(Modifier.height(10.dp))
                Surface(Modifier.fillMaxWidth().clickable { startActivity(Intent(this@MainActivity, ServerSettingsActivity::class.java)) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = Palette.Warn.copy(alpha = 0.12f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Not connected to the server", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Warn)
                        Muted("Alerts still go out over Wi-Fi, Bluetooth and SMS, but family far away won't get them. " +
                              "Is the server PC on? Did its address change? Tap to check or reconnect.", 12)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SosButton { Trigger.fire(this@MainActivity, "button") }
            Spacer(Modifier.height(10.dp))
            Text("Tap SOS. You get 5 seconds to cancel before everyone is alerted.",
                fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.contains("coming") || status.contains("re-alerting") || status.startsWith("Alert ")) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StatusPill(status, Palette.Ok) }
            }

            Spacer(Modifier.height(24.dp))
            FamilyCard(prefs, online)

            Spacer(Modifier.height(14.dp))
            ReadinessCard(items, missing)

            Spacer(Modifier.height(14.dp))
            SectionCard {
                Label("INVITE FAMILY")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prefs.inviteCode.chunked(4).joinToString(" ").ifEmpty { "—" }, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { share(prefs) }) { Text("Share") }
                }
                Spacer(Modifier.height(6.dp))
                Muted("Others install the app, enter the same server, then join with this code. Letters only - there is no 0, O, 1 or I. No limit on devices.")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Muted("Server: ${prefs.serverUrl}", 12)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { startActivity(Intent(this@MainActivity, ServerSettingsActivity::class.java)) }, contentPadding = PaddingValues(0.dp)) { Text("Change", fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard {
                Label("HOW TO TRIGGER")
                Spacer(Modifier.height(8.dp))
                TriggerRow("SOS button", "This screen, the home-screen widget, or the SOS tile in Quick Settings.")
                if (BuildConfig.VOLUME_TRIGGER) {
                    TriggerRow("Volume keys", "Press Volume Down 3× quickly, or hold Volume Up + Down for 2 s. Works with the screen off.")
                }
                TriggerRow("Add the widget", "Long-press your home screen → Widgets → Home Pager SOS.")
                if (BuildConfig.DIRECT_SMS) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SMS priority contacts on every alert", fontSize = 15.sp)
                            Muted("Off: SMS only when the server can't be reached.", 12)
                        }
                        Switch(smsAlways, { smsAlways = it; prefs.smsAlways = it })
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard {
                Label("GOOD TO KNOW")
                Spacer(Modifier.height(8.dp))
                Muted("Alerts travel over the internet, your home Wi-Fi, Bluetooth to nearby phones, and SMS at the same time. " +
                      "Someone answering \"I'm coming\" shows up here.")
                Spacer(Modifier.height(6.dp))
                Muted("Home Pager is a household convenience, not a certified emergency service. In danger, call 112 first.", 12)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    @Composable
    private fun SosButton(onClick: () -> Unit) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(220.dp)
                    .shadow(24.dp, CircleShape, ambientColor = Palette.Sos, spotColor = Palette.Sos)
                    .background(Brush.radialGradient(listOf(Palette.Sos, Palette.SosDeep)), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOS", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 4.sp)
                    Text("ALERT FAMILY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f), letterSpacing = 2.sp)
                }
            }
        }
    }

    @Composable
    private fun FamilyCard(prefs: Prefs, online: Boolean) {
        var members by remember { mutableStateOf<List<Api.Member>?>(null) }
        var err by remember { mutableStateOf(false) }
        LaunchedEffect(tick, online) {
            thread { runCatching { Api(prefs).members() }.onSuccess { members = it; err = false }.onFailure { err = true } }
        }
        val pushOff = remember { FirebaseApp.getApps(this).isEmpty() }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("FAMILY"); Spacer(Modifier.weight(1f))
                TextButton(onClick = { tick++ }, contentPadding = PaddingValues(0.dp)) { Text("Refresh", fontSize = 13.sp) }
            }
            val list = members
            when {
                list == null && !err -> Muted("Loading…")
                list == null -> Muted("Can't reach the server right now.")
                else -> for (m in list) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (m.online) Palette.Ok else Palette.Warn, 10)
                        Spacer(Modifier.width(10.dp))
                        Text(m.name + if (m.isMe) " (you)" else "", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Muted(if (m.online) "Online" else "Last seen ${ago(m.lastSeen)}", 12)
                    }
                }
            }
            val offline = list?.count { !it.online && !it.isMe } ?: 0
            if (offline > 0) {
                Spacer(Modifier.height(6.dp))
                Text("$offline phone${if (offline > 1) "s are" else " is"} not connected and would miss an alert right now. " +
                     "On that phone: open Home Pager once, fix every item in its checklist, and set battery to Unrestricted.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Warn)
            }
            if (pushOff) {
                Spacer(Modifier.height(6.dp))
                Muted("Push wake-up (FCM) is not configured in this build, so sleeping phones can take up to 15 minutes to notice an alert. See docs/FCM_SETUP.md.", 12)
            }
        }
    }

    private fun ago(ms: Long?): String {
        if (ms == null) return "never"
        val m = (System.currentTimeMillis() - ms) / 60_000
        return when {
            m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 48 * 60 -> "${m / 60} h ago"; else -> "${m / 1440} days ago"
        }
    }

    @Composable
    private fun ReadinessCard(items: List<Readiness.Item>, missing: Int) {
        var expanded by remember { mutableStateOf(missing > 0) }
        SectionCard {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Dot(if (missing == 0) Palette.Ok else Palette.Warn, 12)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (missing == 0) "Ready — alarms will ring" else "$missing setup step${if (missing > 1) "s" else ""} left",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Muted(if (missing == 0) "All permissions granted." else "Alarms may be silent or delayed until these are fixed.", 12)
                }
                Text(if (expanded) "Hide" else "Show", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                for (item in items) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (item.ok) Palette.Ok else Palette.Warn, 8)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 15.sp)
                            if (!item.ok) Muted(item.why, 12)
                        }
                        if (!item.ok) FilledTonalButton(onClick = { item.fix(this@MainActivity) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Fix") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Muted("Xiaomi / Oppo / Vivo / Samsung: also allow Autostart and set battery to \"No restrictions\" in the phone's own settings.", 12)
                TextButton(onClick = { Readiness.openAppSettings(this@MainActivity) }, contentPadding = PaddingValues(0.dp)) { Text("Open app settings") }
            }
        }
    }

    @Composable
    private fun TriggerRow(title: String, body: String) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Dot(MaterialTheme.colorScheme.primary, 8)
            Spacer(Modifier.width(10.dp))
            Column { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium); Muted(body, 12) }
        }
    }

    private fun share(prefs: Prefs) {
        val text = "Home Pager invite code: ${prefs.inviteCode.chunked(4).joinToString(" ")}\n" +
            "Install the app, enter server ${prefs.serverUrl} and paste this whole message into the invite field."
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("invite", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invite"))
    }
}
