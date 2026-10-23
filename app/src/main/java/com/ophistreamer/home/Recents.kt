package com.ophistreamer.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.limelight.AppView
import com.limelight.Game
import com.limelight.ShortcutTrampoline
import com.limelight.computers.ComputerDatabaseManager
import org.json.JSONArray
import org.json.JSONObject

data class RecentGame(
    val pcUuid: String,
    val pcName: String,
    val appName: String,
    val appId: Int,
    val appUuid: String?,
    val time: Long,
)

data class SavedPc(val uuid: String, val name: String, val address: String?, val paired: Boolean)

/** Games started from this app, newest first. Filled by ServerHelper.createStartIntent. */
object Recents {
    private const val PREFS = "ophi_home"
    private const val KEY_RECENTS = "recents"
    private const val KEY_CALIBRATED_AT = "calibrated_at"
    private const val KEY_CALIBRATED_NET = "calibrated_net"
    private const val KEY_FAVORITES = "favorites"
    private const val MAX = 12

    @JvmStatic
    fun record(context: Context, pcUuid: String?, pcName: String?, appName: String?, appId: Int, appUuid: String?) {
        if (pcUuid.isNullOrBlank()) return
        val name = appName ?: ""
        val rest = all(context).filterNot {
            it.pcUuid.equals(pcUuid, ignoreCase = true) && (it.appId == appId && it.appName == name)
        }
        val list = listOf(RecentGame(pcUuid, pcName ?: "", name, appId, appUuid, System.currentTimeMillis())) + rest
        prefs(context).edit().putString(KEY_RECENTS, serialize(list.take(MAX))).apply()
    }

    fun all(context: Context): List<RecentGame> = parse(prefs(context).getString(KEY_RECENTS, null))

    private fun serialize(list: List<RecentGame>): String {
        val arr = JSONArray()
        for (g in list) {
            arr.put(
                JSONObject()
                    .put("pc", g.pcUuid).put("pcName", g.pcName)
                    .put("app", g.appName).put("id", g.appId).put("uuid", g.appUuid ?: "")
                    .put("t", g.time),
            )
        }
        return arr.toString()
    }

    private fun parse(raw: String?): List<RecentGame> {
        if (raw == null) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentGame(
                    pcUuid = o.getString("pc"),
                    pcName = o.optString("pcName"),
                    appName = o.optString("app"),
                    appId = o.optInt("id", -1),
                    appUuid = o.optString("uuid").ifBlank { null },
                    time = o.optLong("t"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun markCalibrated(context: Context) {
        prefs(context).edit()
            .putLong(KEY_CALIBRATED_AT, System.currentTimeMillis())
            .putString(KEY_CALIBRATED_NET, NetworkSignature.current(context))
            .apply()
    }

    /** True when the phone is on a network the connection check hasn't seen yet. */
    fun needsCheck(context: Context): Boolean {
        val now = NetworkSignature.current(context) ?: return false
        return prefs(context).getString(KEY_CALIBRATED_NET, null) != now
    }

    // ---- Favourites (kept separately so they never fall off the recent list)

    fun favorites(context: Context): List<RecentGame> = parse(prefs(context).getString(KEY_FAVORITES, null))

    fun isFavorite(context: Context, g: RecentGame) = favorites(context).any { same(it, g) }

    fun toggleFavorite(context: Context, g: RecentGame) {
        val now = favorites(context)
        val list = if (now.any { same(it, g) }) now.filterNot { same(it, g) } else listOf(g) + now
        prefs(context).edit().putString(KEY_FAVORITES, serialize(list)).apply()
    }

    private fun same(a: RecentGame, b: RecentGame) =
        a.pcUuid.equals(b.pcUuid, ignoreCase = true) && a.appId == b.appId && a.appName == b.appName

    fun calibratedAt(context: Context): Long = prefs(context).getLong(KEY_CALIBRATED_AT, 0)

    fun savedPcs(context: Context): List<SavedPc> {
        val db = ComputerDatabaseManager(context)
        return try {
            db.allComputers.map { c ->
                SavedPc(
                    uuid = c.uuid,
                    name = c.name ?: "PC",
                    address = (c.manualAddress ?: c.localAddress)?.address,
                    paired = c.serverCert != null,
                )
            }
        } finally {
            db.close()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Starts streams through Artemis's own ShortcutTrampoline (wakes, polls and pairs the PC like the app does). */
object Launch {
    /** With [game] it starts (or resumes) that game; without, it opens the PC's game list. */
    fun play(context: Context, pcUuid: String, game: RecentGame?) {
        val i = Intent(context, ShortcutTrampoline::class.java).putExtra(AppView.UUID_EXTRA, pcUuid)
        if (game != null) {
            i.putExtra(Game.EXTRA_APP_NAME, game.appName)
            if (!game.appUuid.isNullOrBlank()) {
                i.putExtra(Game.EXTRA_APP_UUID, game.appUuid)
            } else if (game.appId >= 0) {
                i.putExtra(Game.EXTRA_APP_ID, game.appId.toString())
            }
        }
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
