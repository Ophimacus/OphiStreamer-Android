package com.ophistreamer.home

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** Quick Settings tile: "Play on <PC>" resumes the last game from anywhere. */
@RequiresApi(Build.VERSION_CODES.N)
class PlayTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val last = Recents.all(this).firstOrNull()
        qsTile?.apply {
            state = if (last != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            label = if (last != null) "Play on ${last.pcName.ifBlank { "PC" }}" else "OphiStreamer"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = last?.appName?.ifBlank { null } ?: "Play once to set up"
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val last = Recents.all(this).firstOrNull()
        val intent = if (last != null) {
            Intent(this, PlayFromTileActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Invisible hop so the tile can start the stream through the normal launch path. */
class PlayFromTileActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // Home starts the last game and shows any problem on its card (no popups).
        startActivity(Intent(this, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_AUTOPLAY, true))
        finish()
    }
}
