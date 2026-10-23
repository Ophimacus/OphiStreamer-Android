package com.ophistreamer.home

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.limelight.PcView
import com.limelight.R
import com.limelight.preferences.StreamSettings
import com.ophistreamer.linkcheck.DeviceProbe
import com.ophistreamer.linkcheck.LinkCheckActivity
import com.ophistreamer.linkcheck.NetProbe
import com.ophistreamer.ui.OphiAccent
import com.ophistreamer.ui.OphiBackground
import com.ophistreamer.ui.OphiBad
import com.ophistreamer.ui.OphiBgBottom
import com.ophistreamer.ui.OphiBorder
import com.ophistreamer.ui.OphiButtonShape
import com.ophistreamer.ui.OphiCaption
import com.ophistreamer.ui.OphiCard
import com.ophistreamer.ui.OphiCardRaised
import com.ophistreamer.ui.OphiCardShape
import com.ophistreamer.ui.OphiGood
import com.ophistreamer.ui.OphiHeroTop
import com.ophistreamer.ui.OphiMuted
import com.ophistreamer.ui.OphiPill
import com.ophistreamer.ui.OphiPrimaryButton
import com.ophistreamer.ui.OphiRow
import com.ophistreamer.ui.OphiSecondaryButton
import com.ophistreamer.ui.OphiSegmented
import com.ophistreamer.ui.OphiStat
import com.ophistreamer.ui.OphiText
import com.ophistreamer.ui.OphiWarn
import com.ophistreamer.ui.focusRing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SteamOS-style home: one card for the PC with a big PLAY, a recent-games shelf, link status,
 * button hints and a slide-in Quick menu. Artemis's PC list (PcView) is still one button away.
 */
class HomeActivity : ComponentActivity() {
    companion object {
        /** Set by the Quick Settings tile: start the last game as soon as Home opens. */
        const val EXTRA_AUTOPLAY = "ophi.autoplay"
    }

    private var quickOpen by mutableStateOf(false)
    private var refreshKey by mutableIntStateOf(0)
    private var startStatus by mutableStateOf<StartStatus?>(null)
    private val starter by lazy { StreamStarter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same first-run defaults PcView sets, so stream settings exist before the first PLAY.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        setContent {
            OphiBackground {
                HomeScreen(
                    refreshKey = refreshKey,
                    quickOpen = quickOpen,
                    startStatus = startStatus,
                    onToggleQuick = { quickOpen = !quickOpen },
                    onPlay = { pcUuid, game -> play(pcUuid, game) },
                )
            }
        }
        if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
            Recents.all(this).firstOrNull()?.let { play(it.pcUuid, it) }
        }
    }

    private fun play(pcUuid: String, game: RecentGame?) {
        starter.start(pcUuid, game) { startStatus = it }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    override fun onStop() {
        super.onStop()
        starter.cancel()
        if (startStatus == StartStatus.Connecting) startStatus = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> { quickOpen = !quickOpen; return true }
                KeyEvent.KEYCODE_BUTTON_Y -> { startActivity(Intent(this, LinkCheckActivity::class.java)); return true }
                KeyEvent.KEYCODE_BUTTON_X -> { startActivity(Intent(this, PcView::class.java)); return true }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (quickOpen) { quickOpen = false; return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
@Composable
private fun HomeScreen(
    refreshKey: Int,
    quickOpen: Boolean,
    startStatus: StartStatus?,
    onToggleQuick: () -> Unit,
    onPlay: (String, RecentGame?) -> Unit,
) {
    val context = LocalContext.current
    val pcs = remember(refreshKey) { Recents.savedPcs(context) }
    val recents = remember(refreshKey) { Recents.all(context) }
    val pc = remember(pcs, recents) {
        recents.firstNotNullOfOrNull { r -> pcs.firstOrNull { it.uuid.equals(r.pcUuid, ignoreCase = true) } }
            ?: pcs.firstOrNull { it.paired } ?: pcs.firstOrNull()
    }
    val pcRecents = recents.filter { pc != null && it.pcUuid.equals(pc.uuid, ignoreCase = true) }
    var latency by remember { mutableStateOf<Double?>(null) }
    var probed by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    // Check the PC quietly; while it doesn't answer, keep checking every few seconds.
    LaunchedEffect(pc?.address, refreshKey) {
        probed = false
        while (true) {
            latency = pc?.address?.let { addr -> withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, addr, 47989) } }
            probed = true
            if (latency != null || pc?.address == null) break
            kotlinx.coroutines.delay(4000)
        }
    }
    LaunchedEffect(pc?.uuid) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(context, refreshKey)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                val wide = maxWidth > 600.dp
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, startStatus, playFocus, onPlay, Modifier.weight(1.3f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Shelf(pc, pcRecents, onPlay)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, startStatus, playFocus, onPlay, Modifier.fillMaxWidth().height(280.dp))
                        Shelf(pc, pcRecents, onPlay)
                    }
                }
            }
            HintBar(onToggleQuick)
        }

        AnimatedVisibility(
            visible = quickOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QuickMenu(onClose = onToggleQuick)
        }
    }
}

@Composable
private fun TopBar(context: Context, refreshKey: Int) {
    val hotspot = remember(refreshKey) { NetProbe.phoneHotspot(context) }
    val wifi = remember(refreshKey) { NetProbe.wifiStatus(context) }
    val pads = remember(refreshKey) { DeviceProbe.controllers() }
    val battery = remember(refreshKey) {
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    val time = remember(refreshKey) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("OPHISTREAMER", color = OphiAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        Text(
            when {
                hotspot != null -> "Hotspot on"
                wifi.connected -> "Wi-Fi ${wifi.band}"
                else -> "Offline"
            },
            color = if (hotspot != null || wifi.connected) OphiText else OphiBad, fontSize = 12.sp,
        )
        Text(if (pads.isNotEmpty()) "Controller" else "No controller", color = if (pads.isNotEmpty()) OphiGood else OphiMuted, fontSize = 12.sp)
        val heat = remember(refreshKey) { HeatGuard.statusText(context) }
        if (heat != null && heat.second > 0) Text(heat.first, color = if (heat.second == 2) OphiBad else OphiWarn, fontSize = 12.sp)
        Text("$battery%", color = if (battery in 0..15) OphiWarn else OphiText, fontSize = 12.sp)
        Text(time, color = OphiText, fontSize = 12.sp)
    }
}

@Composable
private fun Hero(
    pc: SavedPc?,
    lastGame: RecentGame?,
    latency: Double?,
    probed: Boolean,
    startStatus: StartStatus?,
    playFocus: FocusRequester,
    onPlay: (String, RecentGame?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier
            .border(1.dp, OphiBorder, OphiCardShape)
            .background(Brush.verticalGradient(listOf(OphiHeroTop, com.ophistreamer.ui.OphiCard)), OphiCardShape)
            .padding(18.dp),
    ) {
        if (pc == null) {
            Text("Let's find your PC", color = OphiText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Pair it once. After that, Play starts streaming in one tap.", color = OphiMuted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            OphiPrimaryButton("Find my PC", focusRequester = playFocus) { context.startActivity(Intent(context, PcView::class.java)) }
            return@Column
        }

        // One status line, plain words.
        val connecting = startStatus == StartStatus.Connecting
        val (statusText, statusColor) = when {
            connecting -> "Connecting..." to OphiAccent
            startStatus == StartStatus.NotPaired || !pc.paired -> "Needs pairing once" to OphiWarn
            startStatus == StartStatus.Offline -> "Not connected" to OphiBad
            startStatus is StartStatus.Failed -> startStatus.message to OphiBad
            !probed -> "Checking..." to OphiMuted
            latency != null -> "Ready · ${"%.0f".format(latency)} ms" to OphiGood
            else -> "Not connected" to OphiBad
        }
        val reachable = latency != null && startStatus != StartStatus.Offline

        Text(pc.name, color = OphiText, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).background(statusColor, CircleShape))
            Text(statusText, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        if (!reachable && !connecting && probed && pc.paired) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn on your phone hotspot and connect the laptop to it. This updates by itself.",
                color = OphiMuted, fontSize = 13.sp,
            )
        }
        if (connecting) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.LinearProgressIndicator(color = OphiAccent, trackColor = OphiBorder, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.weight(1f))
        when {
            !pc.paired || startStatus == StartStatus.NotPaired ->
                OphiPrimaryButton("Pair once", focusRequester = playFocus) { context.startActivity(Intent(context, PcView::class.java)) }
            else -> OphiPrimaryButton(
                text = when {
                    connecting -> "Connecting..."
                    lastGame != null && lastGame.appName.isNotBlank() -> "Play ${lastGame.appName}"
                    else -> "Play"
                },
                enabled = !connecting,
                height = 60.dp,
                focusRequester = playFocus,
            ) { onPlay(pc.uuid, lastGame) }
        }
        Spacer(Modifier.height(8.dp))
        OphiSecondaryButton("All games", enabled = !connecting) { onPlay(pc.uuid, null) }
    }
}
@Composable
private fun Shelf(pc: SavedPc?, games: List<RecentGame>, onPlay: (String, RecentGame?) -> Unit) {
    val context = LocalContext.current
    OphiCaption("Recent")
    if (pc == null || games.isEmpty()) {
        Text("Games you play show up here.", color = OphiMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games.take(8)) { g -> Capsule(context, g) { onPlay(g.pcUuid, g) } }
    }
}

@Composable
private fun Capsule(context: Context, game: RecentGame, onClick: () -> Unit) {
    // Artemis caches box art as cache/boxart/<pcUuid>/<appId>.png
    val art = remember(game.pcUuid, game.appId) {
        val f = File(context.cacheDir, "boxart/${game.pcUuid}/${game.appId}.png")
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() else null
    }
    Box(
        Modifier
            .width(84.dp)
            .height(112.dp)
            .focusRing(OphiCardShape)
            .border(1.dp, OphiBorder, OphiCardShape)
            .background(com.ophistreamer.ui.OphiCard, OphiCardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (art != null) {
            Image(art, contentDescription = game.appName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(game.appName.ifBlank { "Game" }, color = OphiMuted, fontSize = 11.sp, modifier = Modifier.padding(6.dp), maxLines = 3)
        }
    }
}

@Composable
private fun HintBar(onToggleQuick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(OphiBgBottom)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("A", "Play") {}
        Hint("Y", "Link Check") { context.startActivity(Intent(context, LinkCheckActivity::class.java)) }
        Hint("X", "All PCs") { context.startActivity(Intent(context, PcView::class.java)) }
        Spacer(Modifier.weight(1f))
        Hint("☰", "Quick menu", onClick = onToggleQuick)
    }
}

@Composable
private fun Hint(button: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(20.dp).background(OphiCardRaised, CircleShape), contentAlignment = Alignment.Center) {
            Text(button, color = OphiText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = OphiMuted, fontSize = 12.sp)
    }
}

@Composable
private fun QuickMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val screen = remember { DeviceProbe.screen(context) }
    val fpsOptions = remember { listOf(60, 90, 120).filter { it <= maxOf(60, screen.maxRefreshHz) } }
    var fps by remember { mutableIntStateOf(prefs.getString("list_fps", "60")?.toFloatOrNull()?.toInt() ?: 60) }
    var bitrate by remember { mutableFloatStateOf((prefs.getInt("seekbar_bitrate_kbps", 12000) / 1000f).coerceIn(3f, 40f)) }
    val native = "${screen.widthPx}x${screen.heightPx}"
    val resOptions = remember { listOf(native, "1280x720", "1920x1080").distinct() }
    var res by remember { mutableStateOf(prefs.getString("list_resolution", native) ?: native) }
    var overlay by remember { mutableStateOf(prefs.getBoolean("checkbox_enable_perf_overlay", false)) }
    var autoResume by remember { mutableStateOf(AutoResume.isEnabled(context)) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(330.dp)
            .background(Color(0xFF10161E))
            .border(1.dp, OphiBorder)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quick menu", color = OphiText, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        QuickLabel("Frame rate")
        OphiSegmented(fpsOptions, fps, { "$it" }, Modifier.fillMaxWidth()) {
            fps = it
            prefs.edit().putString("list_fps", it.toString()).apply()
        }

        QuickLabel("Bitrate: ${bitrate.toInt()} Mbps")
        Slider(
            value = bitrate,
            onValueChange = { bitrate = it },
            onValueChangeFinished = { prefs.edit().putInt("seekbar_bitrate_kbps", bitrate.toInt() * 1000).apply() },
            valueRange = 3f..40f,
            steps = 36,
            colors = SliderDefaults.colors(thumbColor = OphiAccent, activeTrackColor = OphiAccent, inactiveTrackColor = OphiBorder),
        )

        QuickLabel("Resolution")
        OphiSegmented(resOptions, res, { if (it == native) "Native" else it.substringAfter('x') + "p" }, Modifier.fillMaxWidth()) {
            res = it
            prefs.edit().putString("list_resolution", it).apply()
        }

        QuickSwitch("Performance overlay", overlay) {
            overlay = it
            prefs.edit().putBoolean("checkbox_enable_perf_overlay", it).apply()
        }
        QuickSwitch("Auto-resume after drops", autoResume) {
            autoResume = it
            prefs.edit().putBoolean(AutoResume.PREF_ENABLED, it).apply()
        }

        OphiSecondaryButton("Calibrate now") { context.startActivity(Intent(context, LinkCheckActivity::class.java)) }
        OphiSecondaryButton("Advanced settings") { context.startActivity(Intent(context, StreamSettings::class.java)) }
        OphiSecondaryButton("Close") { onClose() }
        Text("Changes apply to the next stream.", color = OphiMuted, fontSize = 11.sp)
    }
}

@Composable
private fun QuickLabel(text: String) {
    Text(text, color = OphiText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun QuickSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(OphiButtonShape)
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OphiText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = OphiAccent, checkedThumbColor = Color.White),
        )
    }
}
