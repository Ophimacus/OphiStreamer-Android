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
            // Same as tapping the app icon: an open task (setup, a check, pairing) comes forward unchanged.
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
        }
        finish()
    }
}

/** Identifies the network the phone streams over, so a new one can prompt a fresh connection check. */
object NetworkSignature {
    fun current(context: Context): String? {
        // Android can give the hotspot a new subnet each time it turns on; it's still the same network.
        if (NetProbe.phoneHotspot(context) != null) return "hotspot"
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

    /** The layout as it is now, to hand back to [restoreBackup] if an edit is cancelled. */
    fun backup(context: Context): String = snapshot(context)

    fun restoreBackup(context: Context, json: String) {
        restore(context, json)
        onEdited(context)
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

/**
 * When the on-screen controls show. "auto" (default): on when no controller is connected, off the
 * moment one connects, back on if it disconnects, live during a stream.
 */
object OscMode {
    const val PREF = "ophi_osc_mode"
    val ids = listOf("auto", "on", "off")

    fun label(id: String) = when (id) {
        "auto" -> "Auto"
        "on" -> "Always"
        else -> "Off"
    }

    @JvmStatic
    fun current(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, "auto") ?: "auto"

    @JvmStatic
    fun isAuto(context: Context) = current(context) == "auto"

    /** Keeps Artemis's own switches in step, so its settings screen and the stream agree with ours. */
    fun set(context: Context, id: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF, id)
            .putBoolean("checkbox_show_onscreen_controls", id != "off")
            .putBoolean("checkbox_hide_osc_when_has_gamepad", id == "auto")
            .apply()
    }

    /** A real controller (USB or Bluetooth), not the phone's own buttons. */
    @JvmStatic
    fun hasPhysicalGamepad(context: Context): Boolean {
        if (com.ophistreamer.linkcheck.DeviceProbe.controllers().any { it.external }) return true
        val usb = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager ?: return false
        return usb.deviceList.values.any { it.vendorId in PAD_VENDORS }
    }

    // GameSir, Microsoft, Sony, Nintendo, 8BitDo, PDP, PowerA, Hori, Logitech, Razer
    private val PAD_VENDORS = setOf(0x3537, 0x045E, 0x054C, 0x057E, 0x2DC8, 0x0E6F, 0x24C6, 0x0F0D, 0x046D, 0x1532)
}
