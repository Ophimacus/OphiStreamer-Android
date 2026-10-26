package com.ophistreamer.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.limelight.preferences.PreferenceConfiguration

/** Local role is explicit; it does not confer host permissions or claim a controller slot. */
object Coop {
    const val EXTRA_GUEST = "ophi.coop.guest"
    const val EXTRA_SHARED = "ophi.coop.shared"
    const val PREF_GUEST = "ophi_coop_guest"

    @JvmStatic fun isGuest(context: Context) = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_GUEST, false)
    fun setGuest(context: Context, guest: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_GUEST, guest).apply()
    }

    fun rememberHostMode(context: Context, pcUuid: String, enabled: Boolean) {
        context.getSharedPreferences("ophi_coop_hosts", Context.MODE_PRIVATE).edit()
            .putBoolean(pcUuid, enabled).putLong("$pcUuid:seen", System.currentTimeMillis()).apply()
    }

    @JvmStatic fun isSharedPc(context: Context, pcUuid: String): Boolean {
        val p = context.getSharedPreferences("ophi_coop_hosts", Context.MODE_PRIVATE)
        return p.getBoolean(pcUuid, false) && System.currentTimeMillis() - p.getLong("$pcUuid:seen", 0) in 0..600_000L
    }

    @JvmStatic fun isGuestStream(intent: Intent) = intent.getBooleanExtra(EXTRA_GUEST, false)

    /** Per-stream ceiling: leaves saved solo preferences untouched. Joiner must not create a second display. */
    @JvmStatic fun applyStreamPreferences(context: Context, prefs: PreferenceConfiguration) {
        val intent = (context as? Activity)?.intent ?: return
        if (!intent.getBooleanExtra(EXTRA_SHARED, false)) return
        prefs.width = 1280
        prefs.height = 720
        prefs.fps = minOf(prefs.fps, 60f)
        prefs.bitrate = minOf(prefs.bitrate, 8000)
        prefs.meteredBitrate = minOf(prefs.meteredBitrate, 8000)
        prefs.resolutionScaleFactor = 100
        prefs.multiController = true
        prefs.playHostAudio = false
        prefs.enableHdr = false
        if (isGuestStream(intent)) prefs.useVirtualDisplay = false
    }
}
