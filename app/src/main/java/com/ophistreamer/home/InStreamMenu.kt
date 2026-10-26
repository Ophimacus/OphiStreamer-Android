package com.ophistreamer.home

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.limelight.Game
import com.ophistreamer.ui.OphiBad
import com.ophistreamer.ui.OphiBgBottom
import com.ophistreamer.ui.OphiBorder
import com.ophistreamer.ui.OphiCaption
import com.ophistreamer.ui.OphiCard
import com.ophistreamer.ui.OphiCardShape
import com.ophistreamer.ui.OphiChoiceRow
import com.ophistreamer.ui.OphiControlRow
import com.ophistreamer.ui.OphiDividerLine
import com.ophistreamer.ui.OphiLinkRow
import com.ophistreamer.ui.OphiMuted
import com.ophistreamer.ui.OphiPrimaryButton
import com.ophistreamer.ui.OphiSection
import com.ophistreamer.ui.OphiSegmented
import com.ophistreamer.ui.OphiSlider
import com.ophistreamer.ui.OphiTabs
import com.ophistreamer.ui.OphiText
import com.ophistreamer.ui.OphiToggleRow
import com.ophistreamer.ui.focusRing

/**
 * The menu during a stream (Start/Select+Start, the floating button, or back). Same SteamOS look as Home's
 * Quick menu; everything here applies to the running game straight away. Artemis's full menu stays one tap away.
 */
object InStreamMenu {
    private var dialog: ComponentDialog? = null

    @JvmStatic
    fun isOpen() = dialog?.isShowing == true

    @JvmStatic
    fun hide() {
        dialog?.dismiss()
        dialog = null
    }

    /** [more] opens Artemis's original menu (special keys, mouse mode, server commands...). */
    @JvmStatic
    fun show(game: Game, more: Runnable) {
        hide()
        val d = ComponentDialog(game, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val close = { d.dismiss() }
        // Run an action on the game once the menu is gone and the game has focus back.
        val after = { action: () -> Unit ->
            d.dismiss()
            game.window.decorView.postDelayed({ if (!game.isFinishing) action() }, 150)
            Unit
        }
        d.setContentView(
            ComposeView(game).apply {
                setContent { Panel(game, close, after, more) }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        d.setOnDismissListener { if (dialog === d) dialog = null }
        d.show()
        d.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowInsetsControllerCompat(w, w.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        dialog = d
    }
}

@Composable
private fun Panel(game: Game, close: () -> Unit, after: (() -> Unit) -> Unit, more: Runnable) {
    var tab by remember { mutableIntStateOf(0) }
    val firstTab = remember { FocusRequester() }
    LaunchedEffect(tab) { runCatching { firstTab.requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        // The game stays visible on the left, slightly dimmed; tapping it resumes.
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0x66000000)).clickable(onClick = close))
        Column(
            Modifier
                .fillMaxHeight()
                .width(380.dp)
                .background(Color(0xF20E141B))
                .border(1.dp, OphiBorder)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> { tab = (tab + 2) % 3; true }
                        KeyEvent.KEYCODE_BUTTON_R1 -> { tab = (tab + 1) % 3; true }
                        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE -> { close(); true }
                        else -> false
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick menu", color = OphiText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(game.ophiStreamSummary(), color = OphiMuted, fontSize = 11.sp)
                }
                Box(Modifier.size(40.dp).focusRing(CircleShape).clickable(onClick = close), contentAlignment = Alignment.Center) {
                    Text("✕", color = OphiMuted, fontSize = 16.sp)
                }
            }
            OphiTabs(listOf("Game", "Controls", "Display"), tab, Modifier.padding(horizontal = 8.dp).focusRequester(firstTab)) { tab = it }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (tab) {
                    0 -> GameTab(game, close, after)
                    1 -> ControlsTab(game, after)
                    else -> DisplayTab(game, after, more)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(OphiBgBottom).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("L1 / R1  Tabs", color = OphiMuted, fontSize = 12.sp)
                Text("B  Back to game", color = OphiMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GameTab(game: Game, close: () -> Unit, after: (() -> Unit) -> Unit) {
    OphiPrimaryButton("Resume", height = 48.dp, onClick = close)
    OphiSection("Session") {
        if (!Coop.isGuestStream(game.intent)) OphiLinkRow("Show keyboard") { after { game.toggleKeyboard() } }
        OphiDividerLine()
        if (!Coop.isGuestStream(game.intent)) OphiLinkRow("Send clipboard to PC") { after { game.sendClipboard(true) } }
    }
    OphiSection("Leave") {
        OphiLinkRow("Disconnect", "Game keeps running") { after { game.disconnect() } }
        OphiDividerLine()
        if (!Coop.isGuestStream(game.intent)) Row(
            Modifier.fillMaxWidth().height(52.dp).focusRing(OphiCardShape).clickable { after { game.quit() } }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Quit game", color = OphiBad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Closes it on the PC", color = OphiMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControlsTab(game: Game, after: (() -> Unit) -> Unit) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(game) }
    var mode by remember { mutableStateOf(OscMode.current(game)) }
    var theme by remember { mutableStateOf(OscTheme.current(game)) }
    var opacity by remember { mutableIntStateOf(prefs.getInt("seekbar_osc_opacity", 90).coerceIn(20, 100)) }
    var layouts by remember { mutableStateOf(OscLayouts.names(game)) }
    var layout by remember { mutableStateOf(OscLayouts.active(game)) }

    OphiSection("Touch controls") {
        OphiControlRow(
            "Show touch controls",
            when (mode) {
                "auto" -> if (OscMode.hasPhysicalGamepad(game)) "Hidden: controller connected" else "On: no controller"
                "on" -> "Always on"
                else -> "Off"
            },
        ) {
            OphiSegmented(OscMode.ids, mode, { OscMode.label(it) }, Modifier.fillMaxWidth()) {
                mode = it
                OscMode.set(game, it)
                game.ophiApplyOscMode()
            }
        }
        OphiDividerLine()
        OphiControlRow("Theme") {
            OphiSegmented(OscTheme.ids, theme, { OscTheme.label(it) }, Modifier.fillMaxWidth()) {
                theme = it
                prefs.edit().putString(OscTheme.PREF, it).apply()
                game.ophiRefreshOsc()
            }
        }
        OphiDividerLine()
        OphiControlRow("Opacity", "$opacity%") {
            OphiSlider(opacity.toFloat(), 20f..100f, 5f) {
                opacity = it.toInt()
                prefs.edit().putInt("seekbar_osc_opacity", opacity).apply()
                game.ophiSetOscOpacity(opacity)
            }
        }
    }

    OphiPrimaryButton("Edit layout over the game", height = 48.dp) { after { game.ophiStartLayoutEdit() } }

    OphiSection("Layouts") {
        layouts.forEachIndexed { i, name ->
            if (i > 0) OphiDividerLine()
            OphiChoiceRow(name, name == layout, if (name == layout) "In use" else null) {
                OscLayouts.select(game, name)
                layouts = OscLayouts.names(game)
                layout = OscLayouts.active(game)
                game.ophiRefreshOsc()
            }
        }
    }
}

@Composable
private fun DisplayTab(game: Game, after: (() -> Unit) -> Unit, more: Runnable) {
    var overlay by remember { mutableStateOf(game.ophiPerfOverlayOn()) }
    var zoom by remember { mutableStateOf(game.isZoomModeEnabled) }

    OphiSection("Screen") {
        OphiToggleRow("Performance overlay", overlay, "Frame time, delay and bitrate") {
            overlay = it
            game.toggleHUD()
        }
        OphiDividerLine()
        OphiToggleRow("Zoom mode", zoom, "Pinch and pan the picture") {
            zoom = it
            game.toggleZoomMode()
        }
        OphiDividerLine()
        OphiLinkRow("Rotate screen") { after { game.rotateScreen() } }
    }
    if (!Coop.isGuestStream(game.intent)) OphiSection("More") {
        OphiLinkRow("All options", "Keys, mouse, PC commands") { after { more.run() } }
    }
    Text(
        "Resolution, frame rate and bitrate change on the next stream. Set them in the Quick menu on Home.",
        color = OphiMuted, fontSize = 12.sp,
    )
}
