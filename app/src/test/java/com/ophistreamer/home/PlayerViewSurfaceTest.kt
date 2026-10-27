package com.ophistreamer.home

import android.app.Application
import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.limelight.Game
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.StreamContainer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerViewSurfaceTest {
    @Test fun selectingSecondPlayerClipsVideoOnlyAndFullPictureClearsClip() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val view = StreamContainer(c, null)
        view.init(mock(Game::class.java), PreferenceConfiguration().apply { width = 1280; height = 720; renderMode = 0 })
        fun layout() {
            view.measure(View.MeasureSpec.makeMeasureSpec(1610, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        view.setPlayerView("p2", "side"); layout()
        val surface = view.surfaceView
        assertEquals(640, view.width); assertEquals(720, view.height)
        assertEquals(-640, surface.left); assertEquals(1280, surface.width)
        assertEquals(640, surface.clipBounds!!.left)
        assertEquals(1280, surface.clipBounds!!.right)
        assertTrue(view.isPlayerViewActive)
        view.setPlayerView("full", "side"); layout()
        assertFalse(view.isPlayerViewActive)
        assertNull(surface.clipBounds)
        assertEquals(0, surface.left)
        assertEquals(view.width, surface.width)
    }

    @Test @Config(sdk = [23]) fun olderSurfaceImplementationStaysOnFullPicture() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val view = StreamContainer(c, null)
        view.init(mock(Game::class.java), PreferenceConfiguration().apply { width = 1280; height = 720; renderMode = 0 })
        assertFalse(view.supportsPlayerView())
        view.setPlayerView("p2", "side")
        assertFalse(view.isPlayerViewActive)
    }
}
