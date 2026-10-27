package com.ophistreamer.home

import android.app.Application
import android.content.Context
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
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
        view.setDesiredAspectRatio(16.0 / 9)
        val root = FrameLayout(c)
        root.addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val touchOverlay = FrameLayout(c)
        root.addView(touchOverlay, FrameLayout.LayoutParams(-1, -1))
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1610, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
        view.setPlayerView("p2", "side"); layout()
        val surface = view.surfaceView
        assertEquals(640, view.width); assertEquals(720, view.height)
        assertEquals(-640, surface.left); assertEquals(1280, surface.width)
        assertEquals(640, surface.clipBounds!!.left)
        assertEquals(1280, surface.clipBounds!!.right)
        assertTrue(view.isPlayerViewActive)
        assertEquals(1610, touchOverlay.width); assertEquals(720, touchOverlay.height)
        view.setPlayerView("full", "side"); layout()
        assertFalse(view.isPlayerViewActive)
        assertNull(surface.clipBounds)
        assertEquals(0, surface.left)
        assertEquals(view.width, surface.width)
        assertEquals(1280, view.width)
        assertEquals(1610, touchOverlay.width)
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
