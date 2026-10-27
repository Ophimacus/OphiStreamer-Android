package com.ophistreamer.home

import android.app.Application
import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.virtual_controller.VirtualController
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TouchControllerReleaseTest {
    @Test fun hidingTouchControlsReleasesHeldButtonsSticksAndTriggers() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val handler = mock(ControllerHandler::class.java)
        val controller = VirtualController(handler, FrameLayout(c), c)
        controller.controllerInputContext.apply {
            inputMap = 0xffff
            leftStickX = 30_000; leftStickY = -30_000
            rightStickX = 10_000; rightStickY = -10_000
            leftTrigger = 100; rightTrigger = 100
        }
        controller.hide()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        verify(handler, atLeastOnce()).reportOscState(0, 0, 0, 0, 0, 0, 0)
        verifyNoMoreInteractions(handler)
    }
}
