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
    private var quickOpen by mutableStateOf(false)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same first-run defaults PcView sets, so stream settings exist before the first PLAY.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        setContent {
            OphiBackground {
                HomeScreen(
                    refreshKey = refreshKey,
                    quickOpen = quickOpen,
                    onToggleQuick = { quickOpen = !quickOpen },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
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
private fun HomeScreen(refreshKey: Int, quickOpen: Boolean, onToggleQuick: () -> Unit) {
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

    LaunchedEffect(pc?.address, refreshKey) {
        probed = false
        latency = pc?.address?.let { addr -> withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, addr, 47989) } }
        probed = true
    }
    LaunchedEffect(pc?.uuid) { runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(context, refreshKey)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                val wide = maxWidth > 600.dp
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, playFocus, Modifier.weight(1.3f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                            Shelf(pc, pcRecents)
                            Spacer(Modifier.height(10.dp))
                            LinkPanel(context, refreshKey)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, playFocus, Modifier.fillMaxWidth().height(300.dp))
                        Shelf(pc, pcRecents)
                        LinkPanel(context, refreshKey)
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
    playFocus: FocusRequester,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    Column(
        modifier
            .border(1.dp, OphiBorder, OphiCardShape)
            .background(Brush.verticalGradient(listOf(OphiHeroTop, com.ophistreamer.ui.OphiCard)), OphiCardShape)
            .padding(16.dp),
    ) {
        if (pc == null) {
            Text("No PC yet", color = OphiText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Find your PC and pair it once. After that, PLAY starts streaming in one tap.", color = OphiMuted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            OphiPrimaryButton("Find PCs", focusRequester = playFocus) { context.startActivity(Intent(context, PcView::class.java)) }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pc.name, color = OphiText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (pc.address ?: "address unknown") + if (lastGame != null && lastGame.appName.isNotBlank()) " · last: ${lastGame.appName}" else "",
                    color = OphiMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                !pc.paired -> OphiPill("Not paired", OphiWarn)
                !probed -> OphiPill("Checking", OphiMuted)
                latency != null -> OphiPill("Online", OphiGood)
                else -> OphiPill("Offline", OphiBad)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OphiStat(latency?.let { "%.0f ms".format(it) } ?: "-", "LATENCY", if (latency != null && latency < 20) OphiGood else OphiText)
            OphiStat(prefs.getString("list_fps", "60") ?: "60", "FPS")
            OphiStat("${prefs.getInt("seekbar_bitrate_kbps", 0) / 1000}".let { if (it == "0") "auto" else it }, "MBPS")
        }
        Spacer(Modifier.weight(1f))
        OphiPrimaryButton(
            text = if (lastGame != null) "Play ${lastGame.appName.ifBlank { "" }}".trim() else "Play",
            focusRequester = playFocus,
        ) { Launch.play(context, pc.uuid, lastGame) }
        Spacer(Modifier.height(8.dp))
        OphiSecondaryButton("All games on ${pc.name}") { Launch.play(context, pc.uuid, null) }
    }
}

@Composable
private fun Shelf(pc: SavedPc?, games: List<RecentGame>) {
    val context = LocalContext.current
    OphiCaption("Recent")
    if (pc == null || games.isEmpty()) {
        Text("Games you play show up here.", color = OphiMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games.take(8)) { g -> Capsule(context, g) { Launch.play(context, g.pcUuid, g) } }
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
private fun LinkPanel(context: Context, refreshKey: Int) {
    val hotspot = remember(refreshKey) { NetProbe.phoneHotspot(context) }
    val wifi = remember(refreshKey) { NetProbe.wifiStatus(context) }
    val pads = remember(refreshKey) { DeviceProbe.controllers() }
    val heat = remember(refreshKey) { HeatGuard.statusText(context) }
    val calibrated = remember(refreshKey) { Recents.calibratedAt(context) }
    OphiCaption("Link")
    OphiCard(Modifier.fillMaxWidth()) {
        OphiRow(
            "Connection",
            when {
                hotspot != null -> "Phone hotspot"
                wifi.connected -> "${wifi.band} · ${wifi.rssiDbm} dBm"
                else -> "Not connected"
            },
            if (hotspot != null || wifi.connected) OphiText else OphiBad,
        )
        OphiRow("Controller", pads.firstOrNull()?.name ?: "None", if (pads.isNotEmpty()) OphiGood else OphiMuted)
        if (heat != null) OphiRow("Phone temperature", heat.first, when (heat.second) { 2 -> OphiBad; 1 -> OphiWarn; else -> OphiGood })
        OphiRow(
            "Calibration",
            if (calibrated == 0L) "Not yet" else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(calibrated)),
            if (calibrated == 0L) OphiWarn else OphiGood,
            last = true,
        )
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
