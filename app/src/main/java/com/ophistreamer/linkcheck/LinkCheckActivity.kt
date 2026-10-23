package com.ophistreamer.linkcheck


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SteamOS-inspired palette: deep navy, slate text, bright blue accent.
private val BgTop = Color(0xFF1B2838)
private val BgBottom = Color(0xFF0B0F14)
private val CardBg = Color(0xFF151C25)
private val CardBorder = Color(0xFF26313E)
private val Divider = Color(0xFF212B37)
private val TextMain = Color(0xFFDCDEDF)
private val Muted = Color(0xFF8B98A5)
private val Accent = Color(0xFF1A9FFF)
private val AccentDeep = Color(0xFF0B6FD1)
private val Good = Color(0xFF59BF40)
private val Warn = Color(0xFFE6B450)
private val Bad = Color(0xFFE5534B)
private val HeroTop = Color(0xFF2A475E)

private val CardShape = RoundedCornerShape(6.dp)

enum class Level(val color: Color) { PASS(Good), WARN(Warn), FAIL(Bad), INFO(Muted) }

data class Check(val name: String, val level: Level, val detail: String)

/** Live gamepad input, fed by the activity so it works whatever has focus. */
object GamepadInput {
    var lastButton by mutableStateOf("")
    var leftX by mutableFloatStateOf(0f)
    var leftY by mutableFloatStateOf(0f)
    var rightX by mutableFloatStateOf(0f)
    var rightY by mutableFloatStateOf(0f)
    var events by mutableIntStateOf(0)
}

class LinkCheckActivity : ComponentActivity() {
    companion object {
        /** Start the check right away (first-run setup, new network). */
        const val EXTRA_AUTOSTART = "ophi.autostart"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = BgBottom, surface = CardBg)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
                ) {
                    ConnectionScreen(onKeepScreenOn = ::keepScreenOn, autoStart = intent.getBooleanExtra(EXTRA_AUTOSTART, false))
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) && KeyEvent.isGamepadButton(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                GamepadInput.lastButton = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
                GamepadInput.events++
            }
            // Don't consume: A falls back to "select" and B to "back", so the screen stays navigable.
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            GamepadInput.leftX = event.getAxisValue(MotionEvent.AXIS_X)
            GamepadInput.leftY = event.getAxisValue(MotionEvent.AXIS_Y)
            GamepadInput.rightX = event.getAxisValue(MotionEvent.AXIS_Z)
            GamepadInput.rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
            GamepadInput.events++
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private enum class StepState { Waiting, Running, Done, Failed, Skipped }

private class Fix(val title: String, val detail: String, val action: String? = null, val onAction: (() -> Unit)? = null)

private data class Verdict(val title: String, val summary: String, val color: Color)

private const val CHECK_SECONDS = 30

/**
 * Connection check, made for players rather than testers: one button, four plain steps, a grade,
 * things to fix with one-tap actions, and the best settings applied automatically.
 * Everything technical sits under "Show details".
 */
@Composable
fun ConnectionScreen(onKeepScreenOn: (Boolean) -> Unit, autoStart: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var wifi by remember { mutableStateOf(NetProbe.wifiStatus(context)) }
    var hotspot by remember { mutableStateOf(NetProbe.phoneHotspot(context)) }
    var host by remember { mutableStateOf(if (hotspot != null) "" else wifi.gateway ?: "") }
    var controllers by remember { mutableStateOf(DeviceProbe.controllers()) }
    val screen = remember { DeviceProbe.screen(context) }
    val decoders = remember { DeviceProbe.decoders() }
    val advice = remember { DeviceProbe.advice(screen, decoders) }

    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val stepNames = listOf("Finding your PC", "Measuring delay", "Testing speed", "Choosing the best settings")
    val steps = remember { mutableStateListOf(StepState.Waiting, StepState.Waiting, StepState.Waiting, StepState.Waiting) }
    val samples = remember { mutableStateListOf<Double?>() }
    var checks by remember { mutableStateOf(listOf<Check>()) }
    var capacityMbps by remember { mutableStateOf<Int?>(null) }
    var pcApp by remember { mutableStateOf<com.ophistreamer.home.PcStatus?>(null) }
    var portsOpen by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var fixNote by remember { mutableStateOf<String?>(null) }
    val checkFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { checkFocus.requestFocus() }
        // Quick look before the check: find the PC on this phone's hotspot.
        hotspot?.let { hs -> if (host.isBlank()) host = NetProbe.findSunshineHosts(hs).firstOrNull() ?: "" }
    }

    fun runCheck() = scope.launch {
        running = true
        done = false
        copied = false
        applied = null
        fixNote = null
        checks = emptyList()
        samples.clear()
        capacityMbps = null
        pcApp = null
        portsOpen = false
        progress = 0f
        for (i in steps.indices) steps[i] = StepState.Waiting
        onKeepScreenOn(true)
        val locks = DeviceProbe.acquireStreamingWifiLocks(context)
        try {
            val results = mutableListOf<Check>()

            // 1. Find the PC
            steps[0] = StepState.Running
            wifi = NetProbe.wifiStatus(context)
            hotspot = NetProbe.phoneHotspot(context)
            controllers = DeviceProbe.controllers()
            val hs = hotspot
            if (hs != null) {
                results += Check("Phone hotspot", Level.PASS, "On (${hs.iface}, ${hs.ip})")
                val reachable = host.isNotBlank() && withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, host, 47989) } != null
                if (!reachable) host = NetProbe.findSunshineHosts(hs).firstOrNull() ?: ""
            } else {
                results += wifiChecks(wifi)
                if (host.isBlank()) host = wifi.gateway ?: ""
            }
            results += controllerCheck(controllers)
            val net = if (hs != null) null else NetProbe.wifiNetwork(context)
            if (host.isBlank()) {
                results += Check("PC found", Level.FAIL, "No PC with Apollo found")
                steps[0] = StepState.Failed
                for (i in 1..3) steps[i] = StepState.Skipped
                checks = results
                return@launch
            }
            val open = withContext(Dispatchers.IO) {
                NetProbe.SUNSHINE_TCP_PORTS.associateWith { NetProbe.tcpConnectMs(net, host, it) != null }
            }
            results += portCheck(open)
            withContext(Dispatchers.IO) { NetProbe.sunshineInfo(net, host) }?.let { results += Check("Streaming host", Level.PASS, it) }
            pcApp = withContext(Dispatchers.IO) { com.ophistreamer.home.PhoneLinkClient.status(host) }
            results += if (pcApp != null) Check("OphiStreamer on PC", Level.PASS, if (pcApp?.gaming == true) "Gaming mode on" else "Gaming mode off")
            else Check("OphiStreamer on PC", Level.INFO, "Not running or not installed")
            val port = NetProbe.SUNSHINE_TCP_PORTS.firstOrNull { open[it] == true }
            portsOpen = port != null
            steps[0] = if (port != null) StepState.Done else StepState.Failed
            checks = results.toList()
            if (port == null) {
                for (i in 1..3) steps[i] = StepState.Skipped
                return@launch
            }

            // 2. Delay
            steps[1] = StepState.Running
            val totalMs = CHECK_SECONDS * 1000L
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < totalMs) {
                samples += withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(net, host, port) }
                progress = 0.6f * ((SystemClock.elapsedRealtime() - start).toFloat() / totalMs).coerceAtMost(1f)
                delay(NetProbe.SAMPLE_INTERVAL_MS)
            }
            results += latencyCheck(LatencyStats(samples.toList()))
            steps[1] = StepState.Done

            // 3. Speed (needs OphiStreamer on the PC)
            if (pcApp != null) {
                steps[2] = StepState.Running
                val speed = mutableListOf<com.ophistreamer.home.SpeedStep>()
                val rates = listOf(5, 10, 15, 20, 30, 40)
                for ((i, mbps) in rates.withIndex()) {
                    val s = withContext(Dispatchers.IO) { com.ophistreamer.home.PhoneLinkClient.speedStep(host, mbps) } ?: break
                    speed += s
                    progress = 0.6f + 0.35f * (i + 1) / rates.size
                    if (s.lossPct > 2.0) break
                }
                val clean = speed.filter { it.clean }.maxOfOrNull { it.mbps }
                capacityMbps = clean
                val detail = speed.joinToString { "${it.mbps}: %.1f%%".format(it.lossPct) }
                results += when {
                    clean == null -> Check("Link speed", Level.FAIL, "Loss even at 5 Mbps ($detail)")
                    clean >= 20 -> Check("Link speed", Level.PASS, "Clean up to $clean Mbps · loss $detail")
                    else -> Check("Link speed", Level.WARN, "Clean only up to $clean Mbps · loss $detail")
                }
                steps[2] = if (clean != null) StepState.Done else StepState.Failed
            } else {
                results += Check("Link speed", Level.INFO, "Install OphiStreamer on the PC for a real speed test")
                steps[2] = StepState.Skipped
            }

            // 4. Apply the best settings for this phone and link
            steps[3] = StepState.Running
            val mbps = bitrateFor(advice.bitrateMbps, capacityMbps)
            Calibration.apply(context, advice, advice.fps, mbps)
            applied = "${advice.resolution} · ${advice.fps} fps · $mbps Mbps"
            steps[3] = StepState.Done
            progress = 1f
            checks = results
        } finally {
            DeviceProbe.release(locks)
            running = false
            done = true
            onKeepScreenOn(false)
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart) {
            delay(1500) // let the quick PC lookup finish first
            runCheck()
        }
    }

    val stats = if (samples.isEmpty()) null else LatencyStats(samples.toList())
    val verdict = if (done && !running) verdictOf(host, portsOpen, stats, capacityMbps) else null
    val app = pcApp
    val fixes = if (!done || running) emptyList() else buildList {
        if (host.isBlank()) {
            add(Fix("Connect the laptop", "Turn on this phone's hotspot and join it from the laptop, then check again.", "Hotspot settings") { openHotspotSettings(context) })
        } else if (!portsOpen) {
            if (app != null) {
                add(Fix("Restart Apollo", "The laptop is connected but Apollo isn't answering. OphiStreamer on the PC can restart it.", "Restart") {
                    scope.launch { fixNote = withContext(Dispatchers.IO) { com.ophistreamer.home.PhoneLinkClient.repair(host) } ?: "Apollo restarted. Check again in a few seconds." }
                })
            } else {
                add(Fix("Start Apollo on the laptop", "The laptop is connected but Apollo isn't answering."))
            }
        }
        if (hotspot == null && wifi.connected && wifi.band == "2.4 GHz") {
            add(Fix("Use 5 GHz", "This Wi-Fi is on 2.4 GHz, which is slower and busier."))
        }
        val cap = capacityMbps
        if (hotspot != null && cap != null && cap < 20) {
            add(Fix("Set the hotspot to 5 GHz", "Speed is low. Personal hotspot > More > AP band > 5 GHz.", "Hotspot settings") { openHotspotSettings(context) })
        }
        if (stats != null && stats.spikes > 0) {
            if (app != null && !app.gaming) {
                add(Fix("Turn on Gaming mode", "It stops the laptop's Wi-Fi scans that cause hiccups.", "Turn on") {
                    scope.launch { fixNote = withContext(Dispatchers.IO) { com.ophistreamer.home.PhoneLinkClient.setGaming(host, true) } ?: "Gaming mode is on. Check again to compare." }
                })
            } else if (app == null) {
                add(Fix("Install OphiStreamer on the PC", "It removes the laptop's Wi-Fi hiccups and adds the speed test."))
            }
        }
        if (hotspot == null && wifi.connected && wifi.rssiDbm < -67) {
            add(Fix("Move closer to the laptop", "The Wi-Fi signal is weak (${wifi.rssiDbm} dBm)."))
        }
        if (controllers.isEmpty()) {
            add(Fix("Plug in your controller", "Connect the GameSir and turn on OTG in Settings."))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text("OPHISTREAMER", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("Connection", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // ---- Hero: before, during, after
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, CardShape)
                .background(Brush.verticalGradient(listOf(HeroTop, CardBg)), CardShape)
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    running -> {
                        Text("Checking your connection...", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stepNames.forEachIndexed { i, name -> StepLine(name, steps[i]) }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Accent,
                            trackColor = CardBorder,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                    verdict != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(12.dp).background(verdict.color, CircleShape))
                            Text(verdict.title, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(verdict.summary, color = Muted, fontSize = 14.sp)
                        if (stats != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile("%.0f ms".format(stats.avg), "DELAY", if (stats.avg < 15) Good else Warn)
                                StatTile("${stats.spikes}", "HICCUPS", if (stats.spikes == 0) Good else Warn)
                                StatTile(capacityMbps?.let { "$it" } ?: "-", "SPEED Mbps", when {
                                    capacityMbps == null -> Muted
                                    capacityMbps!! >= 20 -> Good
                                    else -> Warn
                                })
                            }
                        }
                    }
                    else -> {
                        Text("Check your connection", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("About $CHECK_SECONDS seconds. Keep the phone near the laptop.", color = Muted, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(
                                when {
                                    hotspot != null -> "Hotspot on"
                                    wifi.connected -> "Wi-Fi ${wifi.band}"
                                    else -> "No Wi-Fi"
                                },
                                if (hotspot != null || wifi.connected) Good else Bad,
                            )
                            Pill(if (host.isNotBlank()) "PC found" else "No PC yet", if (host.isNotBlank()) Good else Warn)
                            Pill(if (controllers.isNotEmpty()) "Controller" else "No controller", if (controllers.isNotEmpty()) Good else Muted)
                        }
                    }
                }
                com.ophistreamer.ui.OphiPrimaryButton(
                    text = when {
                        running -> "Checking..."
                        done -> "Check again"
                        else -> "Check now"
                    },
                    enabled = !running,
                    focusRequester = checkFocus,
                ) { runCheck() }
            }
        }

        // ---- Things to fix
        if (fixes.isNotEmpty()) {
            SectionHeader("Things to fix")
            SteamCard {
                fixes.forEachIndexed { i, fix ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fix.title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(fix.detail, color = Muted, fontSize = 12.sp)
                        }
                        if (fix.action != null && fix.onAction != null) {
                            SecondaryButton(fix.action, modifier = Modifier.width(140.dp)) { fix.onAction.invoke() }
                        }
                    }
                    if (i < fixes.lastIndex) HorizontalDivider(color = Divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            fixNote?.let { Text(it, color = Accent, fontSize = 12.sp) }
        }

        // ---- Settings applied automatically
        applied?.let { now ->
            SectionHeader("Your stream settings")
            SteamCard {
                SettingRow(
                    "Applied",
                    now,
                    Good,
                    sub = "Used for your next stream. Change them any time in the Quick menu.",
                    last = advice.higherFps == null,
                )
                advice.higherFps?.let { fast ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Your phone can also do $fast fps", color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        SecondaryButton("Use $fast fps", modifier = Modifier.width(140.dp)) {
                            val mbps = bitrateFor(advice.higherBitrateMbps, capacityMbps)
                            Calibration.apply(context, advice, fast, mbps)
                            applied = "${advice.resolution} · $fast fps · $mbps Mbps"
                        }
                    }
                }
            }
        }

        // ---- Details for the curious
        Text(
            if (showDetails) "Hide details" else "Show details",
            color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .focusRing(CardShape)
                .clickable { showDetails = !showDetails }
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
        if (showDetails) {
            if (samples.isNotEmpty() && stats != null) {
                SteamCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delay over $CHECK_SECONDS seconds", color = TextMain, fontSize = 14.sp)
                        LatencyChart(samples, (CHECK_SECONDS * 1000 / NetProbe.SAMPLE_INTERVAL_MS).toInt(), Modifier.fillMaxWidth().height(100.dp))
                        Text(
                            "avg %.1f ms · p99 %.1f ms · max %.1f ms · lost %d/%d".format(stats.avg, stats.p99, stats.max, stats.lost, stats.sent),
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                }
            }
            if (checks.isNotEmpty()) {
                SectionHeader("All checks")
                SteamCard { checks.forEachIndexed { i, c -> ResultRow(c, last = i == checks.lastIndex) } }
            }
            SectionHeader("This phone")
            SteamCard {
                SettingRow("PC address", host.ifBlank { "Not found" })
                SettingRow("Screen", "${screen.widthPx}×${screen.heightPx} · ${screen.maxRefreshHz} Hz")
                decoders.forEach { d ->
                    SettingRow(
                        d.label,
                        if (d.codecName == null) "Not supported" else "720p ${fpsText(d.maxFps720)} · 1080p ${fpsText(d.maxFps1080)}",
                        if (d.codecName == null) Muted else if (d.hardware) Good else Warn,
                    )
                }
                SettingRow(
                    "Controller",
                    controllers.firstOrNull()?.name ?: "None",
                    if (controllers.isNotEmpty()) Good else Muted,
                    sub = controllers.firstOrNull()?.let { "USB ID %04x:%04x · ".format(it.vendorId, it.productId) }.orEmpty() +
                        "Last input: " + GamepadInput.lastButton.ifBlank { "press a button" },
                    last = true,
                )
            }
            SecondaryButton(if (copied) "Copied" else "Copy report") {
                clipboard.setText(
                    AnnotatedString(buildReport(context, wifi, hotspot, host, checks, stats, screen, decoders, advice, controllers)),
                )
                copied = true
            }
        }
    }
}

@Composable
private fun StepLine(name: String, state: StepState) {
    val (glyph, color) = when (state) {
        StepState.Waiting -> "○" to Muted
        StepState.Running -> "●" to Accent
        StepState.Done -> "✓" to Good
        StepState.Failed -> "✕" to Bad
        StepState.Skipped -> "–" to Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(glyph, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Text(
            name,
            color = if (state == StepState.Waiting || state == StepState.Skipped) Muted else TextMain,
            fontSize = 14.sp,
        )
    }
}

private fun verdictOf(host: String, portsOpen: Boolean, stats: LatencyStats?, cap: Int?): Verdict = when {
    host.isBlank() -> Verdict("Can't find your PC", "Connect the laptop to this phone's hotspot and make sure Apollo is running.", Bad)
    !portsOpen -> Verdict("Your PC isn't answering", "The laptop is connected, but Apollo isn't responding.", Bad)
    stats == null -> Verdict("Check didn't finish", "Try again.", Warn)
    else -> {
        val lossPct = if (stats.sent == 0) 100.0 else stats.lost * 100.0 / stats.sent
        val speed = cap?.let { " Speed up to $it Mbps." } ?: ""
        val facts = "Delay %.0f ms, %d hiccup%s in %d seconds.".format(stats.avg, stats.spikes, if (stats.spikes == 1) "" else "s", CHECK_SECONDS) + speed
        when {
            stats.spikes == 0 && stats.lost == 0 && stats.p99 < 20 && (cap == null || cap >= 20) ->
                Verdict("Excellent", "Ready for smooth streaming. $facts", Good)
            stats.spikes <= 2 && lossPct <= 1.0 && (cap == null || cap >= 10) ->
                Verdict("Good", "Playable, with the occasional hitch. $facts", Good)
            else -> Verdict("Needs attention", "You'll likely notice stutter. $facts", Warn)
        }
    }
}

// ---------------------------------------------------------------- components

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun SteamCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, CardShape)
            .background(CardBg, CardShape),
    ) { content() }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    valueColor: Color = TextMain,
    sub: String? = null,
    last: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextMain, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
        }
        if (value.isNotEmpty()) {
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
    if (!last) HorizontalDivider(color = Divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ResultRow(c: Check, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.name, color = TextMain, fontSize = 15.sp)
            if (c.detail.isNotBlank()) Text(c.detail, color = Muted, fontSize = 12.sp)
        }
        Pill(c.level.name, c.level.color)
    }
    if (!last) HorizontalDivider(color = Divider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Pill(text: String, color: Color) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String, color: Color) {
    Column(
        Modifier
            .weight(1f)
            .background(Color.Black.copy(alpha = 0.25f), CardShape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

/** White ring around whatever a gamepad or keyboard has focused (SteamOS-style focus). */
@Composable
private fun Modifier.focusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .focusRing(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Accent, AccentDeep)), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .focusRing(RoundedCornerShape(4.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(4.dp))
            .background(Color(0xFF1F2833), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, CardShape)
            .background(CardBg, CardShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (o in options) {
            val isSel = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .focusRing(RoundedCornerShape(4.dp))
                    .background(if (isSel) Accent else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable(enabled = enabled) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    color = if (isSel) Color.White else Muted,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LatencyChart(samples: List<Double?>, expected: Int, modifier: Modifier) {
    Canvas(modifier) {
        val maxMs = 100.0
        val step = size.width / maxOf(samples.size, expected, 1)
        val barWidth = step.coerceAtLeast(1f)
        val spikeY = size.height * (1 - (LatencyStats.SPIKE_MS / maxMs)).toFloat()
        drawLine(Warn.copy(alpha = 0.35f), Offset(0f, spikeY), Offset(size.width, spikeY), 2f)
        samples.forEachIndexed { i, v ->
            val x = i * step + barWidth / 2
            if (v == null) {
                drawLine(Bad, Offset(x, 0f), Offset(x, size.height), barWidth)
            } else {
                val h = (v.coerceAtMost(maxMs) / maxMs * size.height).toFloat().coerceAtLeast(2f)
                val color = if (v >= LatencyStats.SPIKE_MS) Warn else Accent
                drawLine(color, Offset(x, size.height), Offset(x, size.height - h), barWidth)
            }
        }
    }
}

// ---------------------------------------------------------------- checks

/** Opens the hotspot screen; the tether action is not public on every build, so fall back to wireless settings. */
private fun openHotspotSettings(context: Context) {
    for (action in listOf("android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Keeps 30% headroom below the measured clean link speed, and never exceeds the phone's own advice. */
private fun bitrateFor(advised: Int, capacityMbps: Int?): Int =
    if (capacityMbps == null) advised else minOf(advised, maxOf(3, (capacityMbps * 0.7).toInt()))

private fun fpsText(fps: Int) = if (fps == 0) "no" else "$fps fps"

private fun bandLevel(w: WifiStatus) = when (w.band) {
    "5 GHz", "6 GHz" -> Level.PASS
    "2.4 GHz" -> Level.WARN
    else -> Level.INFO
}

private fun signalLevel(rssi: Int) = when {
    rssi >= -60 -> Level.PASS
    rssi >= -70 -> Level.WARN
    else -> Level.FAIL
}

private fun linkLevel(mbps: Int) = when {
    mbps >= 200 -> Level.PASS
    mbps >= 72 -> Level.WARN
    else -> Level.FAIL
}

private fun wifiChecks(w: WifiStatus): List<Check> {
    if (!w.connected) return listOf(Check("On Wi-Fi", Level.FAIL, "Connect the phone to the laptop's hotspot"))
    return listOf(
        Check("On Wi-Fi", Level.PASS, w.ssid ?: "network name hidden"),
        Check(
            "Band 5 GHz", bandLevel(w),
            when (bandLevel(w)) {
                Level.PASS -> "${w.band}, ${w.frequencyMhz} MHz"
                Level.WARN -> "Hotspot is on 2.4 GHz. On Intel cards, connect the laptop to 5 GHz Wi-Fi first."
                else -> "Unknown frequency"
            },
        ),
        Check("Signal", signalLevel(w.rssiDbm), "${w.rssiDbm} dBm (good is -60 or higher)"),
        Check("Link speed", linkLevel(w.linkMbps), "${w.linkMbps} Mbps (good is 200 or more)"),
        Check(
            "Internet on hotspot",
            if (w.hasInternet) Level.PASS else Level.WARN,
            if (w.hasInternet) "Android can use its low-latency Wi-Fi mode"
            else "Without it Android won't enable low-latency Wi-Fi mode. Keep the laptop sharing its internet.",
        ),
    )
}

private fun controllerCheck(list: List<ControllerInfo>): Check =
    if (list.isEmpty()) {
        Check("Controller", Level.WARN, "None found. Plug in the GameSir and turn on OTG.")
    } else {
        Check("Controller", Level.PASS, list.joinToString { it.name })
    }

private fun portCheck(open: Map<Int, Boolean>): Check {
    val list = open.entries.joinToString { "${it.key} ${if (it.value) "open" else "closed"}" }
    return when {
        open.values.all { it } -> Check("Host ports", Level.PASS, list)
        open.values.any { it } -> Check("Host ports", Level.WARN, list)
        else -> Check(
            "Host ports", Level.FAIL,
            "$list. Is Apollo running? Run the Test Kit on the PC (it sets up the firewall).",
        )
    }
}

private fun latencyCheck(s: LatencyStats): Check {
    val detail = "avg %.1f ms, p99 %.1f ms, max %.1f ms, spikes %d, lost %d/%d".format(
        s.avg, s.p99, s.max, s.spikes, s.lost, s.sent,
    )
    val lossPct = if (s.sent == 0) 100.0 else s.lost * 100.0 / s.sent
    val level = when {
        s.spikes == 0 && s.lost == 0 && s.p99 < 20 -> Level.PASS
        s.spikes <= 2 && lossPct <= 1.0 -> Level.WARN
        else -> Level.FAIL
    }
    return Check("Latency", level, detail)
}

@Suppress("DEPRECATION")
private fun buildReport(
    context: Context,
    w: WifiStatus,
    hotspot: HotspotInfo?,
    host: String,
    checks: List<Check>,
    stats: LatencyStats?,
    screen: ScreenInfo,
    decoders: List<DecoderInfo>,
    advice: MoonlightAdvice,
    controllers: List<ControllerInfo>,
): String {
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    return buildString {
        appendLine("OphiStreamer Link Check report")
        appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, app $version")
        appendLine("Screen: ${screen.widthPx}x${screen.heightPx} @ ${screen.maxRefreshHz} Hz")
        for (d in decoders) {
            appendLine(
                "Decoder ${d.label}: ${d.codecName ?: "none"} (${if (d.hardware) "hw" else "sw"}), " +
                    "720p ${d.maxFps720} fps, 1080p ${d.maxFps1080} fps",
            )
        }
        if (hotspot != null) {
            appendLine("Mode: phone is the hotspot (${hotspot.iface}, ${hotspot.ip}) | PC: ${host.ifBlank { "not found" }}")
        } else {
            appendLine("Mode: phone on Wi-Fi")
            appendLine("Wi-Fi: ${w.ssid ?: "?"} | ${w.band} ${w.frequencyMhz} MHz | ${w.rssiDbm} dBm | ${w.linkMbps} Mbps | internet ${w.hasInternet}")
            appendLine("Phone IP: ${w.phoneIp ?: "?"} | PC: $host")
        }
        for (c in controllers) {
            appendLine("Controller: ${c.name} (%04x:%04x, external ${c.external}, rumble ${c.vibrates})".format(c.vendorId, c.productId))
        }
        if (controllers.isEmpty()) appendLine("Controller: none")
        appendLine("Advice: ${advice.resolution} @ ${advice.fps} fps, ${advice.codec}, ${advice.bitrateMbps} Mbps; ${advice.tryLater ?: "-"}")
        if (stats != null) {
            appendLine(
                "Latency: avg %.1f ms, p99 %.1f ms, max %.1f ms, spikes>=50ms %d, lost %d/%d".format(
                    stats.avg, stats.p99, stats.max, stats.spikes, stats.lost, stats.sent,
                ),
            )
        }
        appendLine()
        for (c in checks) appendLine("[${c.level}] ${c.name} - ${c.detail}")
    }
}
