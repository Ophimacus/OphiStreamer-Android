package com.ophistreamer.home

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophistreamer.linkcheck.NetProbe
import com.ophistreamer.ui.OphiAccent
import com.ophistreamer.ui.OphiBackground
import com.ophistreamer.ui.OphiBorder
import com.ophistreamer.ui.OphiCard
import com.ophistreamer.ui.OphiMuted
import com.ophistreamer.ui.OphiPrimaryButton
import com.ophistreamer.ui.OphiSecondaryButton
import com.ophistreamer.ui.OphiText
import com.ophistreamer.ui.OphiWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shown after an unexpected disconnect: waits for the PC to answer again, then resumes the same game. */
class ReconnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_HOST = "ophi.host"
        const val EXTRA_PC_UUID = "ophi.pcUuid"
        const val EXTRA_PC_NAME = "ophi.pcName"
        const val EXTRA_APP_NAME = "ophi.appName"
        const val EXTRA_APP_ID = "ophi.appId"
        const val EXTRA_APP_UUID = "ophi.appUuid"
        private const val TIMEOUT_MS = 90_000L
    }

    private val starter by lazy { StreamStarter(this) }

    override fun onStop() {
        super.onStop()
        starter.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val host = intent.getStringExtra(EXTRA_HOST) ?: run { finish(); return }
        val pcUuid = intent.getStringExtra(EXTRA_PC_UUID) ?: run { finish(); return }
        val pcName = intent.getStringExtra(EXTRA_PC_NAME) ?: "your PC"
        val game = RecentGame(
            pcUuid = pcUuid,
            pcName = pcName,
            appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "",
            appId = intent.getIntExtra(EXTRA_APP_ID, -1),
            appUuid = intent.getStringExtra(EXTRA_APP_UUID),
            time = 0,
        )
        setContent {
            OphiBackground {
                ReconnectScreen(
                    host = host,
                    pcName = pcName,
                    gameName = game.appName,
                    onReachable = {
                        // Same quiet start as Home; if it can't start, Home explains why on its card.
                        starter.start(pcUuid, game) { status ->
                            when (status) {
                                StartStatus.Connecting -> Unit
                                StartStatus.Started -> finish()
                                else -> {
                                    startActivity(android.content.Intent(this, HomeActivity::class.java))
                                    finish()
                                }
                            }
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    @Composable
    private fun ReconnectScreen(
        host: String,
        pcName: String,
        gameName: String,
        onReachable: () -> Unit,
        onCancel: () -> Unit,
    ) {
        var attempt by remember { mutableIntStateOf(0) }
        var failed by remember { mutableStateOf(false) }
        var run by remember { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }

        LaunchedEffect(run) {
            failed = false
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < TIMEOUT_MS) {
                attempt++
                val ok = withContext(Dispatchers.IO) { NetProbe.tcpConnectMs(null, host, 47989) } != null
                if (ok) {
                    onReachable()
                    return@LaunchedEffect
                }
                delay(1500)
            }
            failed = true
        }
        LaunchedEffect(failed) { runCatching { focus.requestFocus() } }

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            OphiCard(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OPHISTREAMER", color = OphiAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(
                        if (failed) "Couldn't reach $pcName" else "Reconnecting to $pcName...",
                        color = OphiText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (failed) "Check the phone hotspot is on and the laptop is connected to it."
                        else "The connection dropped. ${if (gameName.isNotBlank()) "$gameName will resume" else "The stream resumes"} as soon as the PC answers. Try $attempt",
                        color = OphiMuted, fontSize = 13.sp,
                    )
                    if (!failed) {
                        LinearProgressIndicator(color = OphiAccent, trackColor = OphiBorder, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (failed) {
                            OphiPrimaryButton("Try again", Modifier.weight(1f), focusRequester = focus) { attempt = 0; run++ }
                        }
                        OphiSecondaryButton("Cancel", Modifier.weight(1f), height = 52.dp) { onCancel() }
                    }
                    if (failed) Text("Tip: open Link Check from the home screen (Y) to see what's wrong.", color = OphiWarn, fontSize = 11.sp)
                }
            }
        }
    }
}
