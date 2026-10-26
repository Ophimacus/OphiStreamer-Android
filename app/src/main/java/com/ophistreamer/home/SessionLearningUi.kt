package com.ophistreamer.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophistreamer.ui.OphiSection
import com.ophistreamer.ui.OphiToggleRow
import com.ophistreamer.ui.OphiLinkRow
import com.ophistreamer.ui.OphiSegmented
import com.ophistreamer.ui.OphiTextButton
import com.ophistreamer.ui.OphiMuted
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Standalone card: caller owns placement; no navigation or global preference changes. */
@Composable
fun SessionLearningSettings(context: Context, pcUuid: String) {
    var revision by remember { mutableIntStateOf(0) }
    var mode by remember(pcUuid) { mutableStateOf(SessionLearning.Mode.SOLO) }
    // Refresh on completion/disable/reset from another surface, without a per-frame UI timer.
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("ophi_session_learning_v1", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = remember(context, revision) { SessionLearning.isEnabled(context) }
    val scope = SessionLearning.currentScope(context, pcUuid, mode)
    val reports = scope?.let { SessionLearning.history(context, it) }.orEmpty()
    val recommendation = scope?.let { SessionLearning.recommendation(context, it) }
    val manual = scope?.let { SessionLearning.isManualOverride(context, it) } ?: false
    val pending = scope?.let { SessionLearning.hasPendingApplication(context, it) } ?: false
    var expanded by remember(scope) { mutableStateOf(false) }
    var latestExpanded by remember(scope) { mutableStateOf(false) }
    OphiSection("Session learning") {
        OphiToggleRow("Learn from my streams", enabled,
            "Local reports · changes only when you choose Apply next stream") {
            SessionLearning.setEnabled(context, it); revision++
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OphiSegmented(SessionLearning.Mode.entries.toList(), mode, { option ->
                when (option) {
                    SessionLearning.Mode.SOLO -> "Solo"
                    SessionLearning.Mode.SHARED_HOST -> "Co-op host"
                    SessionLearning.Mode.GUEST -> "Guest"
                }
            }, Modifier.fillMaxWidth()) { mode = it }
            Text(if (scope == null) "Choose a PC on an identifiable Wi-Fi or phone hotspot network."
                else "This PC · current network${if (scope.metered) " · metered" else ""}", color = OphiMuted, fontSize = 11.sp)
        }
        if (scope != null) {
            OphiToggleRow("Keep my manual settings", manual, "Block learned changes for this PC, network and role") {
                SessionLearning.setManualOverride(context, scope, it); revision++
            }
            if (reports.isNotEmpty()) {
                OphiLinkRow(if (latestExpanded) "Hide latest report" else "Latest report", "${reports.last().durationMs / 1000}s") {
                    latestExpanded = !latestExpanded
                }
                if (latestExpanded) SessionReportSummary(reports.last())
                if (reports.size > 1) {
                    OphiLinkRow(if (expanded) "Hide history" else "History", "${reports.size - 1} earlier") { expanded = !expanded }
                    if (expanded) reports.dropLast(1).asReversed().forEach { SessionReportSummary(it) }
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending) {
                    Text("Accepted for the next stream here.", color = OphiMuted, fontSize = 12.sp)
                    OphiTextButton("Cancel next-stream change") {
                        SessionLearning.cancelPendingApplication(context, scope); revision++
                    }
                } else if (recommendation != null) {
                    Text(recommendation.reason, color = OphiMuted, fontSize = 12.sp)
                    Text("Next stream: ${recommendation.target.fps.toInt()} FPS · ${recommendation.target.bitrateKbps} kbps",
                        color = OphiMuted, fontSize = 12.sp)
                    OphiTextButton("Apply next stream") {
                        SessionLearning.queueForNextStream(context, scope, recommendation); revision++
                    }
                } else Text(when {
                    reports.isEmpty() -> "No sessions saved here yet."
                    !enabled -> "Learning is off. Saved reports stay on this phone."
                    manual -> "Your manual settings are protected."
                    else -> "No reduction suggested. Needs two recent sessions of 2+ minutes and 300+ frames."
                }, color = OphiMuted, fontSize = 12.sp)
            }
        }
        OphiLinkRow("Reset all learning and disable") { SessionLearning.reset(context); revision++ }
    }
}

@Composable
private fun SessionReportSummary(report: SessionLearning.Report) {
    val missing = report.missingFrameFraction?.let { String.format(Locale.getDefault(), "%.2f%%", it * 100) } ?: "unavailable"
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(report.endedAtMs))
    Text("$date · ${report.durationMs / 1000}s connected\n" +
        "${report.preset.width}×${report.preset.height} · ${report.preset.fps.toInt()} FPS target · ${report.preset.bitrateKbps} kbps\n" +
        "${report.deliveredFrames} delivered video frames · ${report.missingFrames} missing ($missing) · ${report.lossEvents} gap events\n" +
        "Decoder average: ${report.decoderAverageMs?.let { "$it ms" } ?: "unavailable"} (completed stats windows)\n" +
        (report.terminationCode?.let { "Connection ended with code $it. " } ?: "Stopped locally. ") +
        when {
            report.networkChanged -> "Network changed; excluded from recommendations."
            report.counterDiscontinuity -> "Frame counter restarted; excluded from recommendations."
            !report.qualifies() -> "Too short or too few frames for recommendations."
            else -> "Eligible for learning."
        }, color = OphiMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
}
