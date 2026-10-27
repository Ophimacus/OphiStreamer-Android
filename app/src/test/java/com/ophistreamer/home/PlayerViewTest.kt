package com.ophistreamer.home

import android.app.Application
import android.content.Context
import android.content.Intent
import com.limelight.Game
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerViewTest {
    @Test fun profilesStayOnTheirOwnPcAndAppAndInvalidIdentityDoesNotOverwrite() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("ophi_player_view_v1", 0).edit().clear().commit()
        fun stream(pc: String, app: String) = Intent().putExtra(Game.EXTRA_PC_UUID, pc).putExtra(Game.EXTRA_APP_UUID, app)
        val first = stream("pc-one", "game-a")
        assertEquals(PlayerView.Selection(), PlayerView.load(c, first))
        PlayerView.save(c, first, "p2", "stacked")
        assertEquals(PlayerView.Selection("p2", "stacked"), PlayerView.load(c, first))
        assertEquals(PlayerView.Selection(), PlayerView.load(c, stream("pc-two", "game-a")))
        assertEquals(PlayerView.Selection(), PlayerView.load(c, stream("pc-one", "game-b")))
        PlayerView.save(c, Intent(), "p1", "side")
        assertEquals(PlayerView.Selection(), PlayerView.load(c, Intent()))
        assertEquals("p2", PlayerView.load(c, first).player)
        PlayerView.save(c, first, "full", "stacked")
        assertEquals("full", PlayerView.load(c, first).player)
    }

    @Test fun appIdFallbackWorksAndAmbiguousConcatenationsCannotShareAProfile() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val a = Intent().putExtra(Game.EXTRA_PC_UUID, "a").putExtra(Game.EXTRA_APP_UUID, "bc")
        val b = Intent().putExtra(Game.EXTRA_PC_UUID, "ab").putExtra(Game.EXTRA_APP_UUID, "c")
        PlayerView.save(c, a, "p1", "side")
        assertEquals("full", PlayerView.load(c, b).player)
        val id = Intent().putExtra(Game.EXTRA_PC_UUID, "fallback").putExtra(Game.EXTRA_APP_ID, 77)
        PlayerView.save(c, id, "p2", "side")
        assertEquals("p2", PlayerView.load(c, id).player)
    }
}
