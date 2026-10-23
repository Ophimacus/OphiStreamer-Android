package com.ophistreamer.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.limelight.Game

/**
 * Turns an unexpected disconnect into "Reconnecting..." instead of an error dialog.
 * Called from Game.connectionTerminated for non-graceful terminations of a stream that was running.
 */
object AutoResume {
    const val PREF_ENABLED = "ophi_auto_resume"
    private const val WINDOW_MS = 2 * 60 * 1000L
    private const val MAX_IN_WINDOW = 3

    private val recent = ArrayDeque<Long>()

    fun isEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, true)

    /** Returns true if a reconnect screen was started; the caller should then finish quietly. */
    @JvmStatic
    fun tryResume(activity: Activity, gameIntent: Intent): Boolean {
        if (!isEnabled(activity)) return false
        val host = gameIntent.getStringExtra(Game.EXTRA_HOST) ?: return false
        val pcUuid = gameIntent.getStringExtra(Game.EXTRA_PC_UUID) ?: return false

        // Give up if the stream keeps dropping right after reconnecting.
        val now = System.currentTimeMillis()
        while (recent.isNotEmpty() && now - recent.first() > WINDOW_MS) recent.removeFirst()
        if (recent.size >= MAX_IN_WINDOW) return false
        recent.addLast(now)

        activity.startActivity(
            Intent(activity, ReconnectActivity::class.java)
                .putExtra(ReconnectActivity.EXTRA_HOST, host)
                .putExtra(ReconnectActivity.EXTRA_PC_UUID, pcUuid)
                .putExtra(ReconnectActivity.EXTRA_PC_NAME, gameIntent.getStringExtra(Game.EXTRA_PC_NAME))
                .putExtra(ReconnectActivity.EXTRA_APP_NAME, gameIntent.getStringExtra(Game.EXTRA_APP_NAME))
                .putExtra(ReconnectActivity.EXTRA_APP_ID, gameIntent.getIntExtra(Game.EXTRA_APP_ID, -1))
                .putExtra(ReconnectActivity.EXTRA_APP_UUID, gameIntent.getStringExtra(Game.EXTRA_APP_UUID)),
        )
        return true
    }
}

/**
 * Warns once per stream when Android reports the phone is getting hot (it hosts the hotspot and
 * decodes video at the same time). Uses the platform thermal status API (Android 10+).
 */
object HeatGuard {
    private var listener: Any? = null

    @JvmStatic
    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || listener != null) return
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        var warned = false
        val l = PowerManager.OnThermalStatusChangedListener { status ->
            if (!warned && status >= PowerManager.THERMAL_STATUS_SEVERE) {
                warned = true
                Toast.makeText(
                    activity,
                    "Phone is getting hot. To avoid slowdowns, use 60 fps or a lower bitrate (Quick menu).",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        pm.addThermalStatusListener(l)
        listener = l
    }

    @JvmStatic
    fun detach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val l = listener as? PowerManager.OnThermalStatusChangedListener ?: return
        (activity.getSystemService(Context.POWER_SERVICE) as PowerManager).removeThermalStatusListener(l)
        listener = null
    }

    /** Short status for the home screen, or null when Android doesn't report it. */
    fun statusText(context: Context): Pair<String, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val s = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        return when {
            s >= PowerManager.THERMAL_STATUS_SEVERE -> "Hot" to 2
            s >= PowerManager.THERMAL_STATUS_MODERATE -> "Warm" to 1
            else -> "Cool" to 0
        }
    }
}
