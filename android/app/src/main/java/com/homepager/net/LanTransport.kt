package com.homepager.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/** Tier 2: UDP broadcast on the local network (Wi-Fi or a phone hotspot). Works with no internet. */
object LanTransport {
    const val PORT = 47474

    /** Fire-and-forget; sent 3 times because UDP can drop. Call from a background thread. */
    fun send(data: ByteArray) {
        val targets = linkedSetOf("255.255.255.255")
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.hostAddress?.let { targets += it }
            }
        }
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                repeat(3) { round ->
                    for (t in targets) runCatching {
                        s.send(DatagramPacket(data, data.size, InetAddress.getByName(t), PORT))
                    }
                    if (round < 2) Thread.sleep(250)
                }
            }
        }
    }

    /** Listens for broadcasts for as long as the foreground service lives. */
    class Receiver(ctx: Context) : Thread("lan-receiver") {
        private val app = ctx.applicationContext
        @Volatile private var running = true
        @Volatile private var sock: DatagramSocket? = null

        override fun run() {
            val lock = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("homepager").apply { setReferenceCounted(false); runCatching { acquire() } }
            while (running) {
                try {
                    val s = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(PORT)) }
                    sock = s
                    val buf = ByteArray(256)
                    while (running) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        OfflineHandler.onBytes(app, p.data.copyOf(p.length))
                    }
                } catch (e: Exception) {
                    if (running) runCatching { sleep(3_000) }
                } finally {
                    sock?.close()
                }
            }
            runCatching { lock.release() }
        }

        fun shutdown() { running = false; sock?.close(); interrupt() }
    }
}
