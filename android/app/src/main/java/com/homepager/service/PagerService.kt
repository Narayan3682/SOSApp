package com.homepager.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.homepager.PagerApp
import com.homepager.alarm.AlarmController
import com.homepager.core.AlertBus
import com.homepager.core.Prefs
import com.homepager.net.Api
import com.homepager.net.Tls
import com.homepager.net.LanTransport
import com.homepager.net.NearbyTransport
import com.homepager.push.FcmSync
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Keeps a WebSocket to the server open (alerts in ~1 s), always listens for LAN broadcasts, and
 * starts scanning for Nearby alerts only while the server is unreachable.
 */
class PagerService : Service() {
    private val handler = Handler(android.os.Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var lan: LanTransport.Receiver? = null
    private var backoff = 1_000L
    private var stopped = false
    private val startNearby = Runnable { NearbyTransport.startDiscovery(this) }
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var connected = false
    private var lastError = ""

    /** Reconnect the instant a network comes (back) up instead of waiting for the back-off timer. */
    private fun watchNetwork() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { handler.post { poke() } }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) handler.post { poke() }
            }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            netCallback = cb
        }
    }

    /** Make sure a connection attempt is in flight; cheap to call often. */
    private fun poke() {
        if (stopped) return
        if (socket == null) { backoff = 1_000L; connect() }
        else if (!connected) { socket?.cancel(); socket = null; connect() }
        else runCatching { socket?.send("{\"type\":\"ping\"}") }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, PagerApp.CH_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Home Pager is listening").setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (lan == null) lan = LanTransport.Receiver(this).also { it.start() }
        watchNetwork()
        Keepalive.schedule(this)
        val reason = intent?.getStringExtra("reason")
        if (reason == "settings" || reason == "manual") {
            handler.removeCallbacksAndMessages(null)
            socket?.cancel(); socket = null; connected = false; backoff = 1_000L
            connect()
        } else poke()
        return START_STICKY
    }

    private fun connect() {
        val prefs = Prefs(this)
        if (!prefs.paired || stopped) return
        val url = prefs.serverUrl.replaceFirst("http", "ws") + "/ws"   // https -> wss
        val client = try {
            Tls.client(prefs.serverUrl, prefs.certPin).newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(25, TimeUnit.SECONDS).build()
        } catch (e: IllegalArgumentException) { AlertBus.post("Server not verified - open the app and set up again"); return }
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                ws.send(JSONObject().put("type", "auth").put("token", prefs.token).toString())
                connected = true
                backoff = 1_000L
                handler.removeCallbacks(startNearby)
                NearbyTransport.stopDiscovery(this@PagerService)   // server reachable: no need to scan
                AlertBus.post("Connected")
                thread {   // refresh offline essentials + push token
                    runCatching { Api(prefs).syncHousehold() }
                    FcmSync.register(this@PagerService)
                }
            }
            override fun onMessage(ws: WebSocket, text: String) { runCatching { handle(JSONObject(text)) } }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) = retry()
            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                lastError = when (t) {
                    is javax.net.ssl.SSLException -> "certificate mismatch"
                    is java.net.ConnectException -> "connection refused (server not running?)"
                    is java.net.SocketTimeoutException -> "timed out (wrong address or firewall?)"
                    is java.net.UnknownHostException -> "address not found"
                    else -> t.javaClass.simpleName
                }
                retry()
            }
        })
    }

    private fun handle(m: JSONObject) {
        when (m.optString("type")) {
            "alert" -> AlarmController.fire(this, m.getString("alert_id"),
                m.optString("sender_name", "Someone"), m.optString("trigger"), m.optInt("level", 0))
            "ack" -> AlertBus.post("${m.optString("member_name")} is coming")
            "cancelled", "resolved" -> {
                AlarmController.close(this, m.optString("alert_id"))
                AlertBus.post("Alert ${m.optString("type")}")
            }
            "escalated" -> AlertBus.post("No response yet - re-alerting")
        }
    }

    private fun retry() {
        connected = false
        socket = null
        if (stopped) return
        AlertBus.post("Server unreachable" + (if (lastError.isNotEmpty()) ": $lastError" else "") + " - listening on Wi-Fi and nearby")
        handler.removeCallbacks(startNearby)
        handler.postDelayed(startNearby, 10_000)      // scan only if still offline after 10 s
        handler.postDelayed({ connect() }, backoff)
        backoff = (backoff * 2).coerceAtMost(30_000L)
    }

    override fun onDestroy() {
        stopped = true
        netCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        socket?.cancel()
        lan?.shutdown()
        handler.removeCallbacksAndMessages(null)
        NearbyTransport.stopDiscovery(this)
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, reason: String = "app") {
            runCatching { ctx.startForegroundService(Intent(ctx, PagerService::class.java).putExtra("reason", reason)) }
        }
    }
}
