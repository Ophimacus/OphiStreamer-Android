package com.ophistreamer.home

import android.content.Context
import android.content.Intent
import com.limelight.Game
import java.security.MessageDigest

/** Local, per-PC/app picture selection. Never changes host display topology or controller IDs. */
object PlayerView {
    val players = listOf("full", "p1", "p2")
    val splits = listOf("side", "stacked")
    fun playerLabel(id: String) = when (id) { "p1" -> "Player 1"; "p2" -> "Player 2"; else -> "Full picture" }
    fun splitLabel(id: String) = if (id == "stacked") "Top / bottom" else "Left / right"
    data class Selection(val player: String = "full", val split: String = "side")

    private fun prefs(context: Context) = context.getSharedPreferences("ophi_player_view_v1", Context.MODE_PRIVATE)
    private fun key(intent: Intent): String? {
        val pc = intent.getStringExtra(Game.EXTRA_PC_UUID)?.takeIf { it.isNotBlank() } ?: return null
        val app = intent.getStringExtra(Game.EXTRA_APP_UUID)?.takeIf { it.isNotBlank() }
            ?: intent.getIntExtra(Game.EXTRA_APP_ID, 0).takeIf { it > 0 }?.toString() ?: return null
        return MessageDigest.getInstance("SHA-256").digest("${pc.length}:$pc$app".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @JvmStatic fun load(context: Context, intent: Intent): Selection {
        val key = key(intent) ?: return Selection()
        val p = prefs(context)
        return Selection(p.getString("$key:player", "full").takeIf { it in players } ?: "full",
            p.getString("$key:split", "side").takeIf { it in splits } ?: "side")
    }

    @JvmStatic fun save(context: Context, intent: Intent, player: String, split: String) {
        require(player in players && split in splits)
        val key = key(intent) ?: return
        prefs(context).edit().putString("$key:player", player).putString("$key:split", split).apply()
    }
}
