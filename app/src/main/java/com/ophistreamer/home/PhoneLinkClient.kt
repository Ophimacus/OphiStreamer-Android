package com.ophistreamer.home

import android.os.Build
import android.os.SystemClock
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.abs
import org.json.JSONObject

/** What the OphiStreamer Windows app reports about the PC. */
data class PcStatus(
    val gaming: Boolean,
    val trusted: Boolean,
    val apolloLogin: Boolean,
    val host: String,
    val avgLatencyMs: Double,
    val spikes: Int,
    val drops: Int,
)

/** One speed-test step: PC sends UDP at [mbps] to the phone, the phone counts what arrives. */
data class SpeedStep(val mbps: Int, val lossPct: Double, val jitterMs: Double) {
    val clean get() = lossPct < 1.0 && jitterMs < 3.0
}

/**
 * Client for the OphiStreamer Windows app's phone link (TCP 47900). Pairing, Repair and Gaming-mode
 * changes are only accepted from the phone that hosts the hotspot; the PC enforces that.
 */
object PhoneLinkClient {
    const val PORT = 47900

    fun status(host: String): PcStatus? {
        val (code, body) = request(host, "GET", "/status", null, 1500) ?: return null
        if (code != 200) return null
        return try {
            val o = JSONObject(body)
            val lat = o.optJSONObject("latency")
            PcStatus(
                gaming = o.optBoolean("gaming"),
                trusted = o.optBoolean("trusted"),
                apolloLogin = o.optBoolean("apolloLogin"),
                host = o.optString("host"),
                avgLatencyMs = lat?.optDouble("avgMs") ?: 0.0,
                spikes = lat?.optInt("spikes") ?: 0,
                drops = o.optInt("drops"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Returns null on success, otherwise a short reason. */
    fun post(host: String, path: String, json: JSONObject = JSONObject(), timeoutMs: Int = 10_000): String? {
        val (code, body) = request(host, "POST", path, json.toString(), timeoutMs)
            ?: return "OphiStreamer on the PC isn't answering"
        if (code == 200) return null
        return runCatching { JSONObject(body).optString("error") }.getOrNull()?.ifBlank { null } ?: "PC answered $code"
    }

    /**
     * Called from Artemis's pairing (PcView.doPair) right after it makes up the PIN: sends the PIN to
     * the PC app, which types it into Apollo. Retries while Apollo waits for the pairing request.
     */
    @JvmStatic
    fun submitPinAsync(host: String?, pin: String) {
        if (host.isNullOrBlank()) return
        Thread {
            val body = JSONObject().put("pin", pin).put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            repeat(4) {
                if (post(host, "/pair", body) == null) return@Thread
                SystemClock.sleep(1500)
            }
        }.start()
    }

    fun repair(host: String) = post(host, "/repair", timeoutMs = 20_000)

    fun setGaming(host: String, on: Boolean) = post(host, "/gaming", JSONObject().put("on", on))

    /** PC-to-phone speed test (the direction video travels). */
    fun speedStep(host: String, mbps: Int, ms: Int = 1500): SpeedStep? {
        val socket = DatagramSocket(0)
        return try {
            socket.soTimeout = 500
            socket.receiveBufferSize = 4 * 1024 * 1024
            var error: String? = null
            val sender = Thread {
                error = post(host, "/calibrate", JSONObject().put("port", socket.localPort).put("mbps", mbps).put("ms", ms), timeoutMs = ms + 5000)
            }.apply { start() }

            val seen = HashSet<Int>()
            var total = 0
            val arrivals = ArrayList<Long>()
            val buf = ByteArray(1500)
            val packet = DatagramPacket(buf, buf.size)
            val deadline = SystemClock.elapsedRealtime() + ms + 2500
            var quietSince = 0L
            while (SystemClock.elapsedRealtime() < deadline) {
                try {
                    socket.receive(packet)
                    val seq = readInt(buf, 0)
                    total = readInt(buf, 4)
                    if (seen.add(seq)) arrivals.add(System.nanoTime())
                    quietSince = 0L
                } catch (e: SocketTimeoutException) {
                    if (!sender.isAlive) {
                        if (quietSince == 0L) quietSince = SystemClock.elapsedRealtime()
                        else if (SystemClock.elapsedRealtime() - quietSince > 500) break
                    }
                }
            }
            sender.join(1000)
            if (error != null || total == 0) return null
            val loss = 100.0 * (total - seen.size).coerceAtLeast(0) / total
            // Jitter: average deviation of packet gaps from the expected pacing.
            val expectedNs = 1200.0 * 8 / (mbps * 1_000_000.0) * 1e9
            val jitter = if (arrivals.size > 2) {
                arrivals.zipWithNext { a, b -> abs((b - a) - expectedNs) }.average() / 1e6
            } else {
                0.0
            }
            SpeedStep(mbps, loss, jitter)
        } catch (e: IOException) {
            null
        } finally {
            socket.close()
        }
    }

    private fun readInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun request(host: String, method: String, path: String, body: String?, timeoutMs: Int): Pair<Int, String>? {
        val conn = URL("http://$host:$PORT$path").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 1500
            conn.readTimeout = timeoutMs
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
