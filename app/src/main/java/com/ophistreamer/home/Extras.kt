package com.ophistreamer.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.ophistreamer.linkcheck.NetProbe

/** On-screen control colour themes, applied to every button at once (Artemis's editor still moves/resizes/hides). */
object OscTheme {
    const val PREF = "ophi_osc_theme"
    val ids = listOf("steamos", "glass", "bold", "classic")

    fun label(id: String) = when (id) {
        "steamos" -> "SteamOS"
        "glass" -> "Glass"
        "bold" -> "Bold"
        else -> "Classic"
    }

    fun current(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, "steamos") ?: "steamos"

    /** {normal, pressed} ARGB, or null for Artemis's own colours. Opacity is applied afterwards. */
    @JvmStatic
    fun colors(context: Context): IntArray? = when (current(context)) {
        "steamos" -> intArrayOf(0xFF8B98A5.toInt(), 0xFF1A9FFF.toInt())
        "glass" -> intArrayOf(0xFFFFFFFF.toInt(), 0xFF59BF40.toInt())
        "bold" -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6B450.toInt())
        else -> null
    }
}

/** Opens OphiStreamer on Home when the GameSir (or an Xbox-mode pad) is plugged in. */
class ControllerAttachActivity : Activity() {
    companion object {
        const val PREF_ENABLED = "ophi_open_on_controller"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_ENABLED, true)
        // Never interrupt a running stream.
        if (enabled && com.limelight.Game.instance == null) {
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}

/** Identifies the network the phone streams over, so a new one can prompt a fresh connection check. */
object NetworkSignature {
    fun current(context: Context): String? {
        NetProbe.phoneHotspot(context)?.let { return "hotspot:" + it.ip.substringBeforeLast('.') }
        val wifi = NetProbe.wifiStatus(context)
        if (!wifi.connected) return null
        return "wifi:" + (wifi.ssid ?: wifi.gateway ?: "unknown")
    }
}
