package com.ophistreamer.linkcheck

import android.content.Context
import androidx.preference.PreferenceManager
import com.limelight.profiles.ProfilesManager

/**
 * Writes Link Check's recommendation straight into the stream settings, using the same keys
 * as the Settings screen (com.limelight.preferences.PreferenceConfiguration).
 */
object Calibration {
    fun apply(context: Context, advice: MoonlightAdvice, fps: Int, bitrateMbps: Int) {
        val hevc = advice.codec.startsWith("H.265")
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("list_resolution", "${advice.width}x${advice.height}")
            .putString("list_fps", fps.toString())
            .putInt("seekbar_bitrate_kbps", bitrateMbps * 1000)
            .putString("video_format", if (hevc) "forceh265" else "neverh265")
            .putString("frame_pacing", "latency")
            // Apollo builds a virtual display at exactly this size, so no scaling or black bars.
            .putBoolean("checkbox_use_virtual_display", true)
            .putBoolean("checkbox_enable_perf_overlay", true)
            .apply()
        com.ophistreamer.home.Recents.markCalibrated(context)
    }

    /** Name of the active settings profile, which overrides these values while streaming; empty if none. */
    fun activeProfileName(): String = ProfilesManager.getInstance().activeName
}
