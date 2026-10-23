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

/**
 * Named on-screen control layouts. Artemis keeps one layout in the "OSC" prefs file (element id -> JSON);
 * we snapshot that file per layout and swap it in when the player switches.
 */
object OscLayouts {
    private const val PREFS = "ophi_osc_layouts"
    private const val OSC = com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE
    private const val KEY_NAMES = "names"
    private const val KEY_ACTIVE = "active"

    fun names(context: Context): List<String> {
        val p = prefs(context)
        val raw = p.getString(KEY_NAMES, null)
        if (raw == null) {
            // First use: whatever Artemis has now becomes the first layout.
            val first = "My layout"
            p.edit().putString(KEY_NAMES, org.json.JSONArray(listOf(first)).toString())
                .putString(KEY_ACTIVE, first).putString(key(first), snapshot(context)).apply()
            return listOf(first)
        }
        val arr = org.json.JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun active(context: Context): String {
        val all = names(context)
        return prefs(context).getString(KEY_ACTIVE, null)?.takeIf { it in all } ?: all.first()
    }

    /** Called after the in-game editor saves, so the edit lands in the active layout. */
    @JvmStatic
    fun onEdited(context: Context) {
        prefs(context).edit().putString(key(active(context)), snapshot(context)).apply()
    }

    fun select(context: Context, name: String) {
        if (name == active(context)) return
        onEdited(context)
        restore(context, prefs(context).getString(key(name), "{}") ?: "{}")
        prefs(context).edit().putString(KEY_ACTIVE, name).apply()
    }

    /** Cycles to the next layout; returns its name. Used by the in-game menu. */
    @JvmStatic
    fun next(context: Context): String {
        val all = names(context)
        val n = all[(all.indexOf(active(context)) + 1) % all.size]
        select(context, n)
        return n
    }

    /** New layout, starting as a copy of the current one so small tweaks are easy. */
    fun create(context: Context): String {
        val all = names(context)
        var i = all.size + 1
        while ("Layout $i" in all) i++
        val name = "Layout $i"
        onEdited(context)
        prefs(context).edit()
            .putString(KEY_NAMES, org.json.JSONArray(all + name).toString())
            .putString(key(name), snapshot(context))
            .putString(KEY_ACTIVE, name)
            .apply()
        return name
    }

    fun rename(context: Context, from: String, to: String): Boolean {
        val all = names(context)
        val clean = to.trim().take(24)
        if (clean.isEmpty() || clean in all || from !in all) return false
        val p = prefs(context)
        p.edit()
            .putString(KEY_NAMES, org.json.JSONArray(all.map { if (it == from) clean else it }).toString())
            .putString(key(clean), p.getString(key(from), "{}"))
            .remove(key(from))
            .apply()
        if (p.getString(KEY_ACTIVE, null) == from) p.edit().putString(KEY_ACTIVE, clean).apply()
        return true
    }

    fun delete(context: Context, name: String) {
        val all = names(context)
        if (all.size <= 1 || name !in all) return
        val rest = all - name
        if (active(context) == name) select(context, rest.first())
        prefs(context).edit().putString(KEY_NAMES, org.json.JSONArray(rest).toString()).remove(key(name)).apply()
    }

    /** Puts the active layout back to Artemis's default positions. */
    fun resetActive(context: Context) {
        restore(context, "{}")
        onEdited(context)
    }

    private fun snapshot(context: Context): String {
        val o = org.json.JSONObject()
        for ((k, v) in context.getSharedPreferences(OSC, Context.MODE_PRIVATE).all) if (v is String) o.put(k, v)
        return o.toString()
    }

    private fun restore(context: Context, json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: org.json.JSONException) { org.json.JSONObject() }
        val e = context.getSharedPreferences(OSC, Context.MODE_PRIVATE).edit().clear()
        for (k in o.keys()) e.putString(k, o.getString(k))
        e.commit()
    }

    private fun key(name: String) = "layout:$name"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
