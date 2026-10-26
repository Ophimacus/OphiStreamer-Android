package com.ophistreamer.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionLearningStorageTest {
    private lateinit var context: Context
    private val scope = SessionLearning.Scope("pc", "network-hash", SessionLearning.Mode.SOLO, false)
    private val preset = SessionLearning.Preset(1280, 720, 60f, 8000)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ophi_session_learning_v1", 0).edit().clear().commit()
    }

    private fun session(): SessionLearning.Session {
        // setEnabled increments the initial epoch exactly once.
        SessionLearning.setEnabled(context, true)
        return SessionLearning.Session(context, scope, 1, preset)
    }

    @Test fun collectsDistinctFrameNumbersAndFreezesDurationBeforeTeardown() {
        val s = session()
        s.frame(5) // Before connected: excluded.
        s.start()
        s.frame(10); s.frame(10); s.frame(13); s.frame(13)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
        s.end(-123)
        s.frame(20)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        s.end()
        s.finish(0, 1); s.finish(10, 1)
        val report = SessionLearning.history(context, scope).single()
        assertEquals(120_000L, report.durationMs)
        assertEquals(2L, report.deliveredFrames)
        assertEquals(2L, report.missingFrames)
        assertEquals(1L, report.lossEvents)
        assertEquals(-123, report.terminationCode)
        assertNull(report.decoderAverageMs)
    }

    @Test fun failedConnectIsNotAStreamAndCounterResetIsNotNetworkLoss() {
        val failed = session()
        failed.end(); failed.finish(10, 1)
        assertTrue(SessionLearning.history(context, scope).isEmpty())
        val s = session()
        s.start(); s.frame(100); s.frame(1); s.end(); s.finish(10, 1)
        val report = SessionLearning.history(context, scope).single()
        assertTrue(report.counterDiscontinuity)
        assertEquals(0L, report.missingFrames)
        assertFalse(report.qualifies())
    }

    @Test fun disabledOrResetSessionsCannotRepopulateHistory() {
        assertFalse(SessionLearning.isEnabled(context))
        val s = session()
        s.start(); s.frame(1)
        SessionLearning.setEnabled(context, false)
        SessionLearning.setEnabled(context, true)
        s.finish(10, 1)
        assertTrue(SessionLearning.history(context, scope).isEmpty())
        val beforeReset = SessionLearning.Session(context, scope, 3, preset)
        beforeReset.start()
        SessionLearning.reset(context)
        SessionLearning.setEnabled(context, true)
        beforeReset.finish(10, 1)
        assertTrue(SessionLearning.history(context, scope).isEmpty())
    }

    @Test fun boundsReportsAndSeparatesPcNetworkRoleAndMetering() {
        repeat(12) { val s = session(); s.start(); s.frame(it); s.finish(10, 1) }
        assertEquals(SessionLearning.MAX_SESSIONS, SessionLearning.history(context, scope).size)
        listOf(scope.copy(pcUuid = "other"), scope.copy(networkId = "other"),
            scope.copy(mode = SessionLearning.Mode.SHARED_HOST), scope.copy(mode = SessionLearning.Mode.GUEST),
            scope.copy(metered = true)).forEach { assertTrue(SessionLearning.history(context, it).isEmpty()) }
        repeat(40) { SessionLearning.setManualOverride(context, scope.copy(pcUuid = "pc-$it"), true) }
        assertFalse(SessionLearning.isManualOverride(context, scope.copy(pcUuid = "pc-0")))
        assertTrue(SessionLearning.isManualOverride(context, scope.copy(pcUuid = "pc-39")))
    }

    @Test fun malformedStorageFailsClosedAndResetDisables() {
        context.getSharedPreferences("ophi_session_learning_v1", 0).edit().putString("state", "{broken").commit()
        assertFalse(SessionLearning.isEnabled(context))
        assertTrue(SessionLearning.history(context, scope).isEmpty())
        SessionLearning.setEnabled(context, true)
        SessionLearning.setManualOverride(context, scope, true)
        assertTrue(SessionLearning.isManualOverride(context, scope))
        SessionLearning.reset(context)
        assertFalse(SessionLearning.isEnabled(context))
        assertFalse(SessionLearning.isManualOverride(context, scope))
    }
}
