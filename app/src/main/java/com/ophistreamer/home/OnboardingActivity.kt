package com.ophistreamer.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.PcView
import com.ophistreamer.linkcheck.LinkCheckActivity
import com.ophistreamer.linkcheck.NetProbe
import com.ophistreamer.ui.OphiAccent
import com.ophistreamer.ui.OphiBackground
import com.ophistreamer.ui.OphiBad
import com.ophistreamer.ui.OphiBorder
import com.ophistreamer.ui.OphiCard
import com.ophistreamer.ui.OphiGood
import com.ophistreamer.ui.OphiMuted
import com.ophistreamer.ui.OphiPill
import com.ophistreamer.ui.OphiPrimaryButton
import com.ophistreamer.ui.OphiSecondaryButton
import com.ophistreamer.ui.OphiText
import com.ophistreamer.ui.OphiWarn
import com.ophistreamer.ui.focusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** First-run setup: phone hotspot on 5 GHz, the PC app and pairing, then a connection check. */
class OnboardingActivity : ComponentActivity() {
    companion object {
        private const val PREFS = "ophi_home"
        private const val KEY_DONE = "onboarded"
        const val PC_APP_URL = "https://github.com/Ophimacus/OphiStreamer/releases/download/windows-latest/OphiStreamerSetup.exe"

        fun isDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
        fun markDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OphiBackground {
                Onboarding(
                    onFinish = { runCheck ->
                        markDone(this)
                        if (runCheck) {
                            startActivity(Intent(this, LinkCheckActivity::class.java).putExtra(LinkCheckActivity.EXTRA_AUTOSTART, true))
                        }
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun Onboarding(onFinish: (runCheck: Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val next = remember { FocusRequester() }

    // Live status, refreshed every few seconds while the guide is open.
    var hotspotOn by remember { mutableStateOf(false) }
    var pcAddress by remember { mutableStateOf<String?>(null) }
    var pcAppOk by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }
    var guest by remember { mutableStateOf(Coop.isGuest(context)) }
    LaunchedEffect(step) {
        while (true) {
            val hs = NetProbe.phoneHotspot(context)
            hotspotOn = hs != null
            if (step == 1) {
                val saved = Recents.savedPcs(context)
                paired = saved.any { it.paired }
                val addr = saved.firstOrNull()?.address
                    ?: pcAddress
                    ?: hs?.let { NetProbe.findSunshineHosts(it).firstOrNull() }
                pcAddress = addr
                pcAppOk = addr != null && withContext(Dispatchers.IO) { PhoneLinkClient.status(addr) } != null
            }
            tick++
            delay(3000)
        }
    }
    LaunchedEffect(step) { runCatching { next.requestFocus() } }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("OPHISTREAMER", color = OphiAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(if (i <= step) OphiAccent else OphiBorder))
                }
            }
            OphiCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (step) {
                        0 -> {
                            Text(if (guest) "Join the host phone's hotspot" else "Turn on your phone hotspot", color = OphiText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            com.ophistreamer.ui.OphiToggleRow("Joining another player?", guest) {
                                guest = it
                                Coop.setGuest(context, it)
                            }
                            Text(
                                if (guest) "Keep your own hotspot off. Join the first phone's 5 GHz Wi-Fi network, then pair with the same PC."
                                else "Your phone becomes the network, so it works anywhere. Set the band to 5 GHz for the smoothest stream: Personal hotspot > More > AP band > 5 GHz.",
                                color = OphiMuted, fontSize = 14.sp,
                            )
                            if (!guest) OphiPill(if (hotspotOn) "Hotspot is on" else "Hotspot is off", if (hotspotOn) OphiGood else OphiWarn)
                            OphiSecondaryButton(if (guest) "Open Wi-Fi settings" else "Open hotspot settings") {
                                if (guest) context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) else openHotspotSettings(context)
                            }
                        }
                        1 -> {
                            Text("Get your laptop ready", color = OphiText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "On the laptop: join this phone's hotspot and install OphiStreamer. It sets up Apollo and pairs with this phone for you.",
                                color = OphiMuted, fontSize = 14.sp,
                            )
                            Text(OnboardingActivity.PC_APP_URL, color = OphiAccent, fontSize = 12.sp)
                            OphiSecondaryButton("Copy download link") { clipboard.setText(AnnotatedString(OnboardingActivity.PC_APP_URL)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OphiPill(if (pcAddress != null) "Laptop found" else "Looking for laptop", if (pcAddress != null) OphiGood else OphiMuted)
                                OphiPill(if (pcAppOk) "OphiStreamer running" else "App not seen", if (pcAppOk) OphiGood else OphiMuted)
                                OphiPill(if (paired) "Paired" else "Not paired", if (paired) OphiGood else OphiWarn)
                            }
                            if (!paired) {
                                OphiSecondaryButton("Pair my laptop") { context.startActivity(Intent(context, PcView::class.java)) }
                                Text(if (guest) "Pair this phone using the PIN in Apollo's web panel, then grant viewing and controller input. The host phone starts the game first."
                                    else "Tap your laptop in the list. The PIN is entered for you when OphiStreamer runs on it.", color = OphiMuted, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Text("Check your connection", color = OphiText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "A 30-second check measures delay and speed, then picks the best settings for this phone. You can run it again any time from Connection.",
                                color = OphiMuted, fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            OphiPrimaryButton(
                text = when (step) {
                    0 -> "Next"
                    1 -> if (paired) "Next" else "Next (pair later)"
                    else -> "Run the check"
                },
                focusRequester = next,
            ) {
                if (step < 2) step++ else onFinish(true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (step > 0) {
                    Text("Back", color = OphiMuted, fontSize = 13.sp, modifier = Modifier.focusRing().clickable { step-- }.padding(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Skip setup", color = OphiMuted, fontSize = 13.sp, modifier = Modifier.focusRing().clickable { onFinish(false) }.padding(6.dp))
            }
        }
    }
}

/** Same as Link Check's: the tether action isn't public everywhere, so fall back to wireless settings. */
internal fun openHotspotSettings(context: Context) {
    for (action in listOf("android.settings.TETHER_SETTINGS", android.provider.Settings.ACTION_WIRELESS_SETTINGS)) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {
        }
    }
}
