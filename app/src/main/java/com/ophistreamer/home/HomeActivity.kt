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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import com.ophistreamer.ui.OphiChoiceRow
import com.ophistreamer.ui.OphiControlRow
import com.ophistreamer.ui.OphiDividerLine
import com.ophistreamer.ui.OphiLinkRow
import com.ophistreamer.ui.OphiSection
import com.ophistreamer.ui.OphiSlider
import com.ophistreamer.ui.OphiTabs
import com.ophistreamer.ui.OphiTextButton
import com.ophistreamer.ui.OphiToggleRow
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.launch
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
        } else if (!OnboardingActivity.isDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        registerReceiver(screenOnReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    // The button press that wakes the phone shouldn't also press Play.
    private var screenOnAt = 0L
    private val screenOnReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            screenOnAt = android.os.SystemClock.elapsedRealtime()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOnReceiver) }
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
        val fromPad = event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD)
        if (fromPad && android.os.SystemClock.elapsedRealtime() - screenOnAt < 1500) return true
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
    var pcApp by remember { mutableStateOf<PcStatus?>(null) }
    var repairMsg by remember { mutableStateOf<String?>(null) }
    var repairing by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    var liveAddress by remember(pc?.uuid) { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Repair through the OphiStreamer PC app: restarts Apollo, then checks again.
    val onRepair: () -> Unit = {
        val addr = liveAddress ?: pc?.address
        if (addr != null && !repairing) {
            repairing = true
            repairMsg = "Repairing: restarting Apollo..."
            scope.launch {
                val err = withContext(Dispatchers.IO) { PhoneLinkClient.repair(addr) }
                if (err == null) {
                    repairMsg = "Apollo restarted. Checking again..."
                    kotlinx.coroutines.delay(6000)
                    latency = withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, addr, 47989) }
                    repairMsg = if (latency != null) "Repaired. Press Play." else "Apollo restarted but isn't answering yet."
                } else {
                    repairMsg = err
                }
                repairing = false
            }
        }
    }

    // Check the PC quietly; while it doesn't answer, keep checking every few seconds.
    LaunchedEffect(pc?.address, refreshKey) {
        probed = false
        while (true) {
            var addr = liveAddress ?: pc?.address
            latency = addr?.let { a -> withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, a, 47989) } }
            if (latency == null) {
                // The hotspot may have a new subnet since pairing: find the PC on it.
                NetProbe.phoneHotspot(context)?.let { hs -> NetProbe.findSunshineHosts(hs).firstOrNull() }?.let { found ->
                    liveAddress = found
                    addr = found
                    latency = withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, found, 47989) }
                }
            }
            pcApp = addr?.let { a -> withContext(Dispatchers.IO) { PhoneLinkClient.status(a) } }
            probed = true
            if (latency != null || addr == null) break
            kotlinx.coroutines.delay(4000)
        }
    }
    LaunchedEffect(pc?.uuid) { runCatching { playFocus.requestFocus() } }
    // Closing the Quick menu removes the focused item; put the controller back on Play.
    LaunchedEffect(quickOpen) { if (!quickOpen) runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(context, refreshKey)
            val newNetwork = remember(refreshKey) { pc != null && OnboardingActivity.isDone(context) && Recents.needsCheck(context) }
            if (newNetwork) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 8.dp)
                        .border(1.dp, OphiAccent.copy(alpha = 0.5f), OphiCardShape)
                        .background(com.ophistreamer.ui.OphiCard, OphiCardShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("New network. Run a quick check so the stream uses the best settings.", color = OphiText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    OphiSecondaryButton("Check", Modifier.width(110.dp), height = 36.dp) {
                        context.startActivity(Intent(context, LinkCheckActivity::class.java).putExtra(LinkCheckActivity.EXTRA_AUTOSTART, true))
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                val wide = maxWidth > 600.dp
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, startStatus, pcApp, repairMsg, repairing, onRepair, playFocus, onPlay, Modifier.weight(1.3f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Shelf(pc, pcRecents, onPlay)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(pc, pcRecents.firstOrNull(), latency, probed, startStatus, pcApp, repairMsg, repairing, onRepair, playFocus, onPlay, Modifier.fillMaxWidth().height(280.dp))
                        Shelf(pc, pcRecents, onPlay)
                    }
                }
            }
            HintBar(onToggleQuick)
        }

        AnimatedVisibility(visible = quickOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onToggleQuick))
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
    pcApp: PcStatus?,
    repairMsg: String?,
    repairing: Boolean,
    onRepair: () -> Unit,
    playFocus: FocusRequester,
    onPlay: (String, RecentGame?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val art = remember(lastGame?.pcUuid, lastGame?.appId) { lastGame?.let { boxArt(context, it) } }
    Box(
        modifier
            .border(1.dp, OphiBorder, OphiCardShape)
            .clip(OphiCardShape)
            .background(com.ophistreamer.ui.OphiCard),
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.45f))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(OphiHeroTop.copy(alpha = if (art != null) 0.55f else 1f), com.ophistreamer.ui.OphiCard.copy(alpha = 0.97f))),
            ),
        )
    Column(Modifier.matchParentSize().padding(18.dp)) {
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
        if (pcApp != null && pcApp.gaming) {
            Spacer(Modifier.height(4.dp))
            Text("Gaming mode on the PC", color = OphiAccent, fontSize = 12.sp)
        }
        if (repairMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(repairMsg, color = if (repairing) OphiAccent else OphiMuted, fontSize = 13.sp)
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
        val showRepair = pcApp != null && (startStatus is StartStatus.Failed || (probed && latency == null))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OphiSecondaryButton("All games", Modifier.weight(1f), enabled = !connecting) { onPlay(pc.uuid, null) }
            if (showRepair) OphiSecondaryButton(if (repairing) "Repairing..." else "Repair", Modifier.weight(1f), enabled = !repairing) { onRepair() }
        }
    }
    }
}

/** Artemis caches box art as cache/boxart/<pcUuid>/<appId>.png */
private fun boxArt(context: Context, game: RecentGame): androidx.compose.ui.graphics.ImageBitmap? {
    val f = File(context.cacheDir, "boxart/${game.pcUuid}/${game.appId}.png")
    return if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() else null
}
@Composable
private fun Shelf(pc: SavedPc?, games: List<RecentGame>, onPlay: (String, RecentGame?) -> Unit) {
    val context = LocalContext.current
    var favKey by remember { mutableIntStateOf(0) }
    val favorites = remember(favKey, pc?.uuid) { Recents.favorites(context).filter { pc != null && it.pcUuid.equals(pc.uuid, ignoreCase = true) } }
    val toggle: (RecentGame) -> Unit = { g -> Recents.toggleFavorite(context, g); favKey++ }
    if (favorites.isNotEmpty()) {
        OphiCaption("Favourites")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites) { g -> Capsule(context, g, true, { onPlay(g.pcUuid, g) }, { toggle(g) }) }
        }
        Spacer(Modifier.height(10.dp))
    }
    OphiCaption("Recent")
    if (pc == null || games.isEmpty()) {
        Text("Games you play show up here.", color = OphiMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        return
    }
    if (favorites.isEmpty()) Text("Long-press a game to add it to Favourites.", color = OphiMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games.take(8)) { g -> Capsule(context, g, favorites.any { it.appId == g.appId && it.appName == g.appName }, { onPlay(g.pcUuid, g) }, { toggle(g) }) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Capsule(context: Context, game: RecentGame, favorite: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val art = remember(game.pcUuid, game.appId) { boxArt(context, game) }
    Box(
        Modifier
            .width(84.dp)
            .height(112.dp)
            .focusRing(OphiCardShape)
            .border(1.dp, OphiBorder, OphiCardShape)
            .background(com.ophistreamer.ui.OphiCard, OphiCardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (favorite) {
            Text("\u2605", color = OphiWarn, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
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
        Hint("Y", "Connection") { context.startActivity(Intent(context, LinkCheckActivity::class.java)) }
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
    var tab by remember { mutableIntStateOf(0) }
    val firstTab = remember { FocusRequester() }
    LaunchedEffect(tab) { runCatching { firstTab.requestFocus() } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    Column(
        Modifier
            .fillMaxHeight()
            .then(if (wide) Modifier.width(380.dp) else Modifier.fillMaxWidth())
            .background(Color(0xFF0E141B))
            .border(1.dp, OphiBorder)
            .safeDrawingPadding()
            .onPreviewKeyEvent {
                // L1 / R1 switch tabs, like the Steam Deck's Quick Access menu.
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_L1 -> { tab = (tab + 2) % 3; true }
                    KeyEvent.KEYCODE_BUTTON_R1 -> { tab = (tab + 1) % 3; true }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick menu", color = OphiText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).focusRing(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = OphiMuted, fontSize = 16.sp) }
        }
        OphiTabs(listOf("Stream", "Controls", "App"), tab, Modifier.padding(horizontal = 8.dp).focusRequester(firstTab)) { tab = it }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (tab) {
                0 -> StreamTab(prefs, screen)
                1 -> ControlsTab(prefs)
                else -> AppTab(prefs)
            }
        }

        Row(
            Modifier.fillMaxWidth().background(OphiBgBottom).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Hint("⇆", "L1 / R1 Tabs", onClick = { tab = (tab + 1) % 3 })
            Hint("B", "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Text("Applies to the next stream", color = OphiMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StreamTab(prefs: android.content.SharedPreferences, screen: com.ophistreamer.linkcheck.ScreenInfo) {
    val fpsOptions = remember { listOf(60, 90, 120).filter { it <= maxOf(60, screen.maxRefreshHz) } }
    var fps by remember { mutableIntStateOf(prefs.getString("list_fps", "60")?.toFloatOrNull()?.toInt() ?: 60) }
    var bitrate by remember { mutableFloatStateOf((prefs.getInt("seekbar_bitrate_kbps", 12000) / 1000f).coerceIn(3f, 40f)) }
    val native = "${screen.widthPx}x${screen.heightPx}"
    val resOptions = remember { listOf(native, "1280x720", "1920x1080").distinct() }
    var res by remember { mutableStateOf(matchResolution(prefs.getString("list_resolution", native) ?: native, resOptions, native)) }
    var overlay by remember { mutableStateOf(prefs.getBoolean("checkbox_enable_perf_overlay", false)) }

    OphiSection("Video") {
        OphiControlRow("Resolution") {
            OphiSegmented(resOptions, res, { if (it == native) "Native" else it.substringAfter('x') + "p" }, Modifier.fillMaxWidth()) {
                res = it
                prefs.edit().putString("list_resolution", it).apply()
            }
        }
        OphiDividerLine()
        OphiControlRow("Frame rate", "$fps FPS") {
            OphiSegmented(fpsOptions, fps, { "$it" }, Modifier.fillMaxWidth()) {
                fps = it
                prefs.edit().putString("list_fps", it.toString()).apply()
            }
        }
        OphiDividerLine()
        OphiControlRow("Bitrate", "${bitrate.toInt()} Mbps") {
            OphiSlider(bitrate, 3f..40f, 1f) {
                bitrate = it
                prefs.edit().putInt("seekbar_bitrate_kbps", it.toInt() * 1000).apply()
            }
        }
    }
    OphiSection("While streaming") {
        OphiToggleRow("Performance overlay", overlay, "Frame time, delay and bitrate in a corner") {
            overlay = it
            prefs.edit().putBoolean("checkbox_enable_perf_overlay", it).apply()
        }
    }
}

/** Calibration or Artemis may store a size that isn't one of the buttons (e.g. a few pixels off native). */
private fun matchResolution(stored: String, options: List<String>, native: String): String {
    if (stored in options) return stored
    val h = stored.substringAfter('x').toIntOrNull() ?: return native
    return options.minByOrNull { kotlin.math.abs((it.substringAfter('x').toIntOrNull() ?: 0) - h) } ?: native
}

@Composable
private fun ControlsTab(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var oscMode by remember { mutableStateOf(OscMode.current(context)) }
    var oscTheme by remember { mutableStateOf(OscTheme.current(context)) }
    var oscOpacity by remember { mutableIntStateOf(prefs.getInt("seekbar_osc_opacity", 90).coerceIn(20, 100)) }
    var layouts by remember { mutableStateOf(OscLayouts.names(context)) }
    var layout by remember { mutableStateOf(OscLayouts.active(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    fun reload() { layouts = OscLayouts.names(context); layout = OscLayouts.active(context) }

    OphiSection("On-screen controls") {
        OphiControlRow(
            "Show touch controls",
            when (oscMode) {
                "auto" -> if (OscMode.hasPhysicalGamepad(context)) "Hidden: controller connected" else "On: no controller"
                "on" -> "Always on"
                else -> "Off"
            },
        ) {
            OphiSegmented(OscMode.ids, oscMode, { OscMode.label(it) }, Modifier.fillMaxWidth()) {
                oscMode = it
                OscMode.set(context, it)
            }
        }
        OphiDividerLine()
        OphiControlRow("Theme") {
            OphiSegmented(OscTheme.ids, oscTheme, { OscTheme.label(it) }, Modifier.fillMaxWidth()) {
                oscTheme = it
                prefs.edit().putString(OscTheme.PREF, it).apply()
            }
        }
        OphiDividerLine()
        OphiControlRow("Opacity", "$oscOpacity%") {
            OphiSlider(oscOpacity.toFloat(), 20f..100f, 5f) {
                oscOpacity = it.toInt()
                prefs.edit().putInt("seekbar_osc_opacity", oscOpacity).apply()
            }
        }
    }

    OphiPrimaryButton("Edit layout \u00B7 $layout", height = 48.dp) {
        context.startActivity(Intent(context, OscEditorActivity::class.java))
    }

    OphiSection("Layouts") {
        layouts.forEachIndexed { i, name ->
            if (i > 0) OphiDividerLine()
            OphiChoiceRow(name, name == layout, if (name == layout) "In use" else null) {
                OscLayouts.select(context, name)
                reload()
                note = null
            }
        }
        OphiDividerLine()
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OphiTextButton("+ New", Modifier.weight(1f)) {
                val from = layout
                OscLayouts.create(context)
                reload()
                note = "New layout copied from \"$from\". Edit it in a game."
            }
            OphiTextButton("Rename", Modifier.weight(1f)) { renameLayoutDialog(context, layout) { reload() } }
            OphiTextButton("Reset", Modifier.weight(1f)) {
                OscLayouts.resetActive(context)
                note = "\"$layout\" is back to the default positions."
            }
            OphiTextButton("Delete", Modifier.weight(1f), enabled = layouts.size > 1) {
                OscLayouts.delete(context, layout)
                reload()
                note = null
            }
        }
    }
    Text(
        note ?: "Auto hides the touch controls while your GameSir is connected and brings them back if it disconnects, even mid-game.",
        color = OphiMuted, fontSize = 12.sp,
    )
}

@Composable
private fun AppTab(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var autoResume by remember { mutableStateOf(AutoResume.isEnabled(context)) }
    var openOnPad by remember { mutableStateOf(prefs.getBoolean(ControllerAttachActivity.PREF_ENABLED, true)) }

    OphiSection("Behaviour") {
        OphiToggleRow("Auto-resume after drops", autoResume, "Reconnects to the same game by itself") {
            autoResume = it
            prefs.edit().putBoolean(AutoResume.PREF_ENABLED, it).apply()
        }
        OphiDividerLine()
        OphiToggleRow("Open when controller is plugged in", openOnPad, "Goes straight to Home with Play ready") {
            openOnPad = it
            prefs.edit().putBoolean(ControllerAttachActivity.PREF_ENABLED, it).apply()
        }
    }
    OphiSection("More") {
        OphiLinkRow("Check connection") { context.startActivity(Intent(context, LinkCheckActivity::class.java)) }
        OphiDividerLine()
        OphiLinkRow("Advanced settings", "Artemis options") { context.startActivity(Intent(context, StreamSettings::class.java)) }
    }
}

/** Small system dialog: typing a name is the one place a keyboard is unavoidable. */
private fun renameLayoutDialog(context: android.content.Context, current: String, onDone: () -> Unit) {
    val input = android.widget.EditText(context).apply {
        setText(current)
        setSelectAllOnFocus(true)
        isSingleLine = true
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("Rename layout")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            OscLayouts.rename(context, current, input.text.toString())
            onDone()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
