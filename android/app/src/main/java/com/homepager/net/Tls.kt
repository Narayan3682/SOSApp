package com.homepager.net

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.net.URL

/**
 * Certificate pinning for a self-hosted server.
 * The phone stores the SHA-256 fingerprint of the server's certificate at setup and afterwards
 * refuses any other certificate, so a fake server or a Wi-Fi eavesdropper gets nothing.
 */
object Tls {
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }

    fun pretty(fp: String) = fp.chunked(2).joinToString(":")

    /** Only accepts a certificate chain whose leaf matches [pin]; also used for wss:// WebSockets. */
    private class PinnedTrust(private val pin: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("no client auth")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
            leaf.checkValidity()
            if (!MessageDigest.isEqual(fingerprint(leaf).toByteArray(), pin.toByteArray()))
                throw CertificateException("Server certificate does not match the pinned fingerprint")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** Captures the leaf certificate only; used once, at setup, so the user can verify the fingerprint. */
    private class Capture : X509TrustManager {
        var leaf: X509Certificate? = null
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { leaf = chain.firstOrNull() }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun base() = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)

    /** HTTP client for [serverUrl]: pinned when https://, plain when http:// (LAN only, see ServerUrlPolicy). */
    fun client(serverUrl: String, pin: String): OkHttpClient {
        if (!serverUrl.startsWith("https://", ignoreCase = true)) return base().build()
        require(pin.isNotEmpty()) { "Server not verified yet: run setup again to pin its certificate" }
        val tm = PinnedTrust(pin)
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
        return base().sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }   // identity is the pinned fingerprint, not the hostname (we use raw IPs)
            .build()
    }

    /** Fetch the server's certificate fingerprint (blocking). Trusts nothing; only reads the cert. */
    fun fetchFingerprint(serverUrl: String): String {
        val cap = Capture()
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(cap), SecureRandom()) }
        val conn = URL(serverUrl.trimEnd('/') + "/health").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = ctx.socketFactory
        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        try { conn.connect(); conn.responseCode } finally { conn.disconnect() }
        return fingerprint(cap.leaf ?: error("Server sent no certificate"))
    }
}
