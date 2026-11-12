package com.homepager.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy

/**
 * Tier 3: phone-to-phone over BLE / Wi-Fi Direct, no router and no internet.
 * The signed alert packet rides inside the advertisement (endpoint info), so any number of
 * nearby phones can receive it without connecting. Receivers only scan while the server is
 * unreachable (battery).
 */
object NearbyTransport {
    private const val SERVICE_ID = "com.homepager.alert"
    private val strategy = Strategy.P2P_CLUSTER
    private val main = Handler(Looper.getMainLooper())
    private var stopAdvertising: Runnable? = null
    @Volatile private var discovering = false

    fun advertise(ctx: Context, data: ByteArray, forMs: Long = 90_000) {
        val app = ctx.applicationContext
        runCatching {
            val client = Nearby.getConnectionsClient(app)
            client.stopAdvertising()
            client.startAdvertising(
                data, SERVICE_ID,
                object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                        client.rejectConnection(id)   // data is in the advertisement; no connection needed
                    }
                    override fun onConnectionResult(id: String, result: ConnectionResolution) {}
                    override fun onDisconnected(id: String) {}
                },
                AdvertisingOptions.Builder().setStrategy(strategy).build())
            stopAdvertising?.let { main.removeCallbacks(it) }
            stopAdvertising = Runnable { runCatching { client.stopAdvertising() } }.also { main.postDelayed(it, forMs) }
        }
    }

    fun startDiscovery(ctx: Context) {
        if (discovering) return
        val app = ctx.applicationContext
        discovering = true
        runCatching {
            Nearby.getConnectionsClient(app).startDiscovery(
                SERVICE_ID,
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                        OfflineHandler.onBytes(app, info.endpointInfo)
                    }
                    override fun onEndpointLost(id: String) {}
                },
                DiscoveryOptions.Builder().setStrategy(strategy).build())
                .addOnFailureListener { discovering = false }
        }.onFailure { discovering = false }
    }

    fun stopDiscovery(ctx: Context) {
        runCatching { Nearby.getConnectionsClient(ctx.applicationContext).stopDiscovery() }
        discovering = false
    }
}
