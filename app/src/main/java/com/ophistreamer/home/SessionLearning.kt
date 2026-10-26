package com.ophistreamer.home

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.Keep
import com.google.gson.Gson
import com.limelight.Game
import com.limelight.preferences.PreferenceConfiguration
import com.ophistreamer.linkcheck.NetProbe
import java.security.MessageDigest

/** Opt-in, local-only learning. No settings writes and no live-stream quality changes. */
object SessionLearning {
    const val MIN_DURATION_MS = 120_000L
    const val MIN_FRAMES = 300L
    const val MAX_SCOPES = 24
    const val MAX_SESSIONS = 8
    private const val MAX_AGE_MS = 14 * 24 * 60 * 60 * 1000L
    private const val STORE = "ophi_session_learning_v1"
    private val gson = Gson()

    @Keep enum class Mode { SOLO, SHARED_HOST, GUEST }
    @Keep data class Scope(val pcUuid: String, val networkId: String, val mode: Mode, val metered: Boolean)
    @Keep data class Preset(
        val width: Int, val height: Int, val fps: Float, val bitrateKbps: Int,
        val codec: String = "AUTO", val hdr: Boolean = false, val scale: Int = 100,
    ) {
        fun valid() = width > 0 && height > 0 && fps.isFinite() && fps > 0 && bitrateKbps > 0
    }

    /**
     * durationMs: monotonic connected lifetime, excluding connect/teardown time.
     * deliveredFrames: distinct increasing frame numbers reaching submitDecodeUnit, not rendered FPS.
     * missingFrames / (missingFrames + deliveredFrames): gaps in those numbers, matching the
     * renderer's network-frame-drop concept. NOT packet loss, FEC loss or decoder/render drops.
     * decoderAverageMs: renderer.getAverageDecoderLatency(), after stop. It sums accepted
     * enqueue-to-output timings (<1000ms) / received frames in completed ~1s stats windows,
     * integer truncated; missing timings/dropped output still affect its denominator. The final
     * partial window is excluded. Null means unavailable/zero, never a claim of decoder capacity.
     * terminationCode: native connection termination code; null means local stop, not a network drop.
     */
    @Keep data class Report(
        val endedAtMs: Long, val durationMs: Long, val baseline: Preset, val preset: Preset,
        val deliveredFrames: Long, val missingFrames: Long, val lossEvents: Long,
        val decoderAverageMs: Int?, val activeVideoFormat: Int,
        val terminationCode: Int?, val networkChanged: Boolean, val counterDiscontinuity: Boolean,
    ) {
        val missingFrameFraction: Double? get() = if (deliveredFrames + missingFrames > 0)
            missingFrames.toDouble() / (deliveredFrames + missingFrames) else null
        fun qualifies() = durationMs >= MIN_DURATION_MS && deliveredFrames >= MIN_FRAMES &&
            !networkChanged && !counterDiscontinuity && baseline.valid() && preset.valid() &&
            missingFrames >= 0 && lossEvents >= 0
    }
    @Keep data class Recommendation(val baseline: Preset, val target: Preset, val reason: String, val evidenceAtMs: Long)
    @Keep private data class Bucket(
        val scope: Scope, val reports: List<Report> = emptyList(), val manual: Boolean = false,
        val pending: Recommendation? = null, val touchedAt: Long = 0,
    )
    @Keep private data class State(val enabled: Boolean = false, val epoch: Long = 0, val buckets: List<Bucket> = emptyList())

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private fun read(context: Context): State = runCatching {
        val raw = prefs(context).getString("state", null) ?: return@runCatching State()
        require(raw.length <= 512_000)
        val state = gson.fromJson(raw, State::class.java)
        // Force validation inside this boundary, including malformed/null Gson fields.
        require(state.buckets.size <= MAX_SCOPES)
        state.buckets.forEach { b ->
            require(b.scope.pcUuid.isNotBlank() && b.scope.networkId.isNotBlank())
            require(b.reports.size <= MAX_SESSIONS)
            b.reports.forEach { require(it.baseline.valid() && it.preset.valid()) }
        }
        state
    }.getOrElse { State() }
    private fun write(context: Context, state: State) {
        prefs(context).edit().putString("state", gson.toJson(state)).apply()
    }
    private fun put(state: State, bucket: Bucket) = state.copy(buckets =
        (listOf(bucket) + state.buckets.filterNot { it.scope == bucket.scope })
            .sortedByDescending { it.touchedAt }.take(MAX_SCOPES))

    @JvmStatic @Synchronized fun isEnabled(context: Context) = read(context).enabled
    /** Disable cancels pending applications and invalidates in-flight collection. Keeps history. */
    @JvmStatic @Synchronized fun setEnabled(context: Context, enabled: Boolean) {
        val s = read(context)
        if (s.enabled != enabled) write(context, s.copy(enabled = enabled, epoch = s.epoch + 1,
            buckets = s.buckets.map { it.copy(pending = null) }))
    }
    /** Clears all PCs/networks, opts out, and prevents an old session from repopulating history. */
    @JvmStatic @Synchronized fun reset(context: Context) {
        write(context, State(epoch = read(context).epoch + 1))
    }
    @JvmStatic @Synchronized fun history(context: Context, scope: Scope): List<Report> =
        read(context).buckets.find { it.scope == scope }?.reports?.toList() ?: emptyList()
    @JvmStatic @Synchronized fun isManualOverride(context: Context, scope: Scope) =
        read(context).buckets.find { it.scope == scope }?.manual ?: false
    /** Blocks learned application for this scope; normal saved-setting changes also invalidate a queue. */
    @JvmStatic @Synchronized fun setManualOverride(context: Context, scope: Scope, manual: Boolean) {
        val s = read(context)
        val b = s.buckets.find { it.scope == scope } ?: Bucket(scope)
        write(context, put(s, b.copy(manual = manual, pending = null, touchedAt = System.currentTimeMillis())))
    }

    /** Stable network label is hashed locally; unknown Wi-Fi and unsupported transports are excluded.
     * "hotspot" means this phone's hotspot, not a remote hotspot identity. We deliberately reject
     * gateway-only identities: unrelated routers commonly share the same gateway address.
     */
    @JvmStatic @JvmOverloads fun currentScope(context: Context, pcUuid: String, mode: Mode = Mode.SOLO): Scope? {
        if (pcUuid.isBlank()) return null
        val network = runCatching {
            if (NetProbe.phoneHotspot(context) != null) "hotspot"
            else NetProbe.wifiStatus(context).let { wifi ->
                wifi.ssid?.takeIf { wifi.connected && it.isNotBlank() && !it.equals("<unknown ssid>", true) }
                    ?.let { "wifi:$it" }
            }
        }.getOrNull() ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(network.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return Scope(pcUuid.lowercase(java.util.Locale.ROOT), hash, mode, cm.isActiveNetworkMetered)
    }
    private fun scope(context: Context, intent: Intent): Scope? = currentScope(context,
        intent.getStringExtra(Game.EXTRA_PC_UUID) ?: "", when {
            Coop.isGuestStream(intent) -> Mode.GUEST
            intent.getBooleanExtra(Coop.EXTRA_SHARED, false) -> Mode.SHARED_HOST
            else -> Mode.SOLO
        })
    @JvmStatic fun currentPreset(context: Context, scope: Scope): Preset =
        preset(PreferenceConfiguration.readPreferences(context), scope.metered)
    private fun preset(p: PreferenceConfiguration, metered: Boolean) = Preset(
        p.width, p.height, p.fps, if (metered) p.meteredBitrate else p.bitrate,
        p.videoFormat.name, p.enableHdr, p.resolutionScaleFactor)

    /** Pure policy: two most recent sessions must qualify at the same settings within 14 days.
     * Only reductions; never infer spare capacity from a good run or a decoder capability query.
     */
    fun evaluate(reports: List<Report>, current: Preset, nowMs: Long): Recommendation? {
        if (!current.valid()) return null
        val recent = reports.takeLast(2)
        if (recent.size < 2 || recent.any { !it.qualifies() || it.baseline != current ||
                nowMs - it.endedAtMs !in 0..MAX_AGE_MS } || recent[0].preset != recent[1].preset) return null
        val measured = recent.last().preset
        val networkPressure = recent.all { (it.missingFrameFraction ?: 0.0) >= 0.02 }
        val decoderPressure = recent.map { it.activeVideoFormat }.distinct().size == 1 &&
            recent.all { it.activeVideoFormat != 0 && it.decoderAverageMs != null &&
                it.decoderAverageMs > 1.25 * 1000.0 / it.preset.fps }
        var target = current
        val reasons = mutableListOf<String>()
        if (networkPressure && measured.bitrateKbps > 2000) {
            target = target.copy(bitrateKbps = minOf(current.bitrateKbps,
                maxOf(2000, (measured.bitrateKbps * 0.8).toInt())))
            reasons += "At least 2% missing video frames in both recent sessions"
        }
        if (decoderPressure && measured.fps > 30f) {
            target = target.copy(fps = minOf(current.fps, if (measured.fps > 60f) 60f else 30f))
            reasons += "Observed decoder average exceeded the frame budget in both recent sessions"
        }
        if (target == current) return null
        return Recommendation(current, target, reasons.joinToString("; "), recent.last().endedAtMs)
    }
    /** Revalidate accepted evidence at launch: manual edits, opt-out and stale evidence win. */
    internal fun acceptedTarget(reports: List<Report>, current: Preset, pending: Recommendation?,
                                enabled: Boolean, manual: Boolean, nowMs: Long): Preset? {
        if (!enabled || manual || pending == null) return null
        return pending.target.takeIf { pending == evaluate(reports, current, nowMs) }
    }
    @JvmStatic @Synchronized fun recommendation(context: Context, scope: Scope): Recommendation? {
        val s = read(context)
        val b = s.buckets.find { it.scope == scope } ?: return null
        if (!s.enabled || b.manual) return null
        return evaluate(b.reports, currentPreset(context, scope), System.currentTimeMillis())
    }
    /** Explicit acceptance; one shot, exact scope/baseline, expires with evidence. Safe during a stream:
     * only prepareStreamPreferences on a subsequent Game instance can consume it.
     */
    @JvmStatic @Synchronized fun queueForNextStream(context: Context, scope: Scope, rec: Recommendation): Boolean {
        if (recommendation(context, scope) != rec) return false
        val s = read(context)
        val b = s.buckets.first { it.scope == scope }
        write(context, put(s, b.copy(pending = rec, touchedAt = System.currentTimeMillis())))
        return true
    }
    @JvmStatic @Synchronized fun hasPendingApplication(context: Context, scope: Scope) =
        read(context).buckets.find { it.scope == scope }?.pending != null
    @JvmStatic @Synchronized fun cancelPendingApplication(context: Context, scope: Scope) {
        val s = read(context)
        val b = s.buckets.find { it.scope == scope } ?: return
        write(context, put(s, b.copy(pending = null)))
    }

    /** Game-only launch hook. Mutates this launch's in-memory prefs; never SharedPreferences.
     * Call Coop.applyStreamPreferences immediately after this to enforce co-op invariants.
     */
    @JvmStatic @Synchronized fun prepareStreamPreferences(context: Context, intent: Intent,
                                                         p: PreferenceConfiguration): Session? {
        var s = read(context)
        if (!s.enabled) return null
        val key = scope(context, intent) ?: return null
        val baseline = preset(p, key.metered)
        val b = s.buckets.find { it.scope == key }
        if (b?.pending != null) {
            val pending = b.pending
            val target = acceptedTarget(b.reports, baseline, pending, s.enabled, b.manual, System.currentTimeMillis())
            s = put(s, b.copy(pending = null))
            write(context, s) // Consume even a stale queue, before constructing a stream.
            if (target != null) {
                p.fps = minOf(p.fps, target.fps)
                if (key.metered) p.meteredBitrate = minOf(p.meteredBitrate, target.bitrateKbps)
                else p.bitrate = minOf(p.bitrate, target.bitrateKbps)
            }
        }
        return Session(context.applicationContext, key, s.epoch, baseline)
    }
    private fun networkToken(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) cm.activeNetwork?.toString() else null
    }.getOrNull()

    /** Only lifecycle/counter observation, no per-frame disk I/O or quality mutation. */
    class Session internal constructor(private val context: Context, private val scope: Scope,
                                      private val epoch: Long, private val baseline: Preset) {
        private var configured = baseline
        private var started = -1L
        private var ended = -1L
        private var endedWallClock = 0L
        private var lastFrame: Long? = null
        private var delivered = 0L
        private var missing = 0L
        private var events = 0L
        private var discontinuity = false
        private var changed = false
        private var termination: Int? = null
        private var saved = false
        private var token: String? = null
        private var nextNetworkCheck = 0L

        @Synchronized fun configure(p: PreferenceConfiguration, width: Int, height: Int, fps: Float, bitrateKbps: Int) {
            configured = preset(p, scope.metered).copy(width = width, height = height, fps = fps, bitrateKbps = bitrateKbps)
        }
        @Synchronized fun start() {
            if (started >= 0 || ended >= 0) return
            started = SystemClock.elapsedRealtime()
            token = networkToken(context)
        }
        @Synchronized fun frame(frameNumber: Int) {
            if (started < 0 || ended >= 0) return
            val number = frameNumber.toLong() and 0xffffffffL
            val last = lastFrame
            if (number == last) return // IDR parameter sets repeat the frame number.
            if (last != null && number < last) discontinuity = true
            if (last != null && number > last + 1) { missing += number - last - 1; events++ }
            lastFrame = number
            delivered++
            val now = SystemClock.elapsedRealtime()
            if (now >= nextNetworkCheck) {
                changed = changed || token != networkToken(context)
                nextNetworkCheck = now + 5_000
            }
        }
        /** Freeze duration before teardown/connectivity probing. Repeated stop callbacks are harmless. */
        @JvmOverloads @Synchronized fun end(terminationCode: Int? = null) {
            if (ended >= 0) return
            ended = SystemClock.elapsedRealtime()
            endedWallClock = System.currentTimeMillis()
            termination = terminationCode
            changed = changed || token != networkToken(context) ||
                currentScope(context, scope.pcUuid, scope.mode) != scope
        }
        /** Invoke after conn.stop() joins renderer threads, for stable completed-window latency stats. */
        @Synchronized fun finish(decoderAverageMs: Int, activeVideoFormat: Int) {
            if (saved) return
            end()
            saved = true
            if (started < 0) return
            val report = Report(endedWallClock, maxOf(0, ended - started), baseline, configured,
                delivered, missing, events, decoderAverageMs.takeIf { it > 0 }, activeVideoFormat,
                termination, changed, discontinuity)
            synchronized(SessionLearning) {
                val s = read(context)
                if (!s.enabled || s.epoch != epoch) return
                val b = s.buckets.find { it.scope == scope } ?: Bucket(scope)
                write(context, put(s, b.copy(reports = (b.reports + report).takeLast(MAX_SESSIONS),
                    pending = null, touchedAt = report.endedAtMs)))
            }
        }
    }
}
