package com.ophistreamer.home

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CoopPreferencesTest {
    private fun prefs() = PreferenceConfiguration().apply {
        width = 1920; height = 1080; fps = 120f
        bitrate = 20_000; meteredBitrate = 12_000
        resolutionScaleFactor = 150; framePacingWarpFactor = 4
        useVirtualDisplay = true; enableHdr = true; playHostAudio = true; multiController = false
    }

    @Test fun sharedHostAndGuestCapBothBitratesAndDisableFrameWarp() {
        for (guest in listOf(false, true)) {
            val controller = Robolectric.buildActivity(Activity::class.java).setup()
            val activity = controller.get()
            activity.intent = Intent().putExtra(Coop.EXTRA_SHARED, true).putExtra(Coop.EXTRA_GUEST, guest)
            val p = prefs()
            Coop.applyStreamPreferences(activity, p)
            assertEquals(1280, p.width); assertEquals(720, p.height)
            assertEquals(60f, p.fps, 0f); assertEquals(0, p.framePacingWarpFactor)
            assertEquals(8000, p.bitrate); assertEquals(8000, p.meteredBitrate)
            assertEquals(100, p.resolutionScaleFactor)
            assertFalse(p.enableHdr); assertFalse(p.playHostAudio); assertTrue(p.multiController)
            assertEquals(!guest, p.useVirtualDisplay)
            controller.pause().stop().destroy()
        }
    }

    @Test fun soloPreferencesAndLowerQualityChoicesRemainIntact() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.intent = Intent()
        val solo = prefs()
        Coop.applyStreamPreferences(activity, solo)
        assertEquals(1920, solo.width); assertEquals(120f, solo.fps, 0f)
        assertEquals(20_000, solo.bitrate); assertEquals(4, solo.framePacingWarpFactor)
        assertTrue(solo.useVirtualDisplay); assertTrue(solo.enableHdr)
        activity.intent.putExtra(Coop.EXTRA_SHARED, true)
        val lower = prefs().apply { fps = 30f; bitrate = 4000; meteredBitrate = 2000 }
        Coop.applyStreamPreferences(activity, lower)
        assertEquals(30f, lower.fps, 0f); assertEquals(4000, lower.bitrate); assertEquals(2000, lower.meteredBitrate)
        controller.pause().stop().destroy()
    }
}
