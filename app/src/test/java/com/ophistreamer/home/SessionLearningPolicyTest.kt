package com.ophistreamer.home

import org.junit.Assert.*
import org.junit.Test

class SessionLearningPolicyTest {
    private val now = 2_000_000_000L
    private val preset = SessionLearning.Preset(1920, 1080, 120f, 20_000)
    private fun report(loss: Long = 0, decodeMs: Int? = null) = SessionLearning.Report(
        now - 1000, 120_000, preset, preset, 6000, loss, if (loss > 0) 1 else 0,
        decodeMs, 1, null, false, false)

    @Test fun requiresTwoRecentQualifiedSessionsAtMatchingSettings() {
        val bad = report(loss = 200)
        assertNull(SessionLearning.evaluate(listOf(bad), preset, now))
        listOf(
            bad.copy(durationMs = 119_999), bad.copy(deliveredFrames = 299),
            bad.copy(networkChanged = true), bad.copy(counterDiscontinuity = true),
            bad.copy(endedAtMs = now - 15L * 86400_000), bad.copy(endedAtMs = now + 1),
            bad.copy(baseline = preset.copy(bitrateKbps = 19_000)),
            bad.copy(preset = preset.copy(fps = 60f)),
        ).forEach { invalid ->
            assertNull(SessionLearning.evaluate(listOf(invalid, bad), preset, now))
        }
        assertNotNull(SessionLearning.evaluate(listOf(bad, bad), preset, now))
    }

    @Test fun missingVideoFramesUseMissingPlusDeliveredDenominator() {
        assertEquals(0.02, report(loss = 0).copy(deliveredFrames = 980, missingFrames = 20).missingFrameFraction!!, 0.000001)
        assertNull(report().copy(deliveredFrames = 0).missingFrameFraction)
        val atThreshold = report().copy(deliveredFrames = 980, missingFrames = 20)
        val rec = SessionLearning.evaluate(listOf(atThreshold, atThreshold), preset, now)!!
        assertEquals(16_000, rec.target.bitrateKbps)
        assertEquals(preset.fps, rec.target.fps, 0f)
        val belowThreshold = atThreshold.copy(missingFrames = 19)
        assertNull(SessionLearning.evaluate(listOf(belowThreshold, belowThreshold), preset, now))
    }

    @Test fun goodOrMixedSessionsNeverRaiseQualityOrRecommend() {
        assertNull(SessionLearning.evaluate(List(8) { report(decodeMs = 1) }, preset, now))
        assertNull(SessionLearning.evaluate(listOf(report(loss = 200), report()), preset, now))
        // Do not cherry-pick older bad sessions across a recent good or short session.
        assertNull(SessionLearning.evaluate(listOf(report(loss = 200), report(loss = 200), report()), preset, now))
        assertNull(SessionLearning.evaluate(listOf(report(loss = 200), report(loss = 200), report(loss = 200).copy(durationMs = 1)), preset, now))
    }

    @Test fun decoderNeedsMeasuredLatencyAboveBudgetAtSameActiveCodec() {
        val high = report(decodeMs = 12)
        val rec = SessionLearning.evaluate(listOf(high, high), preset, now)!!
        assertEquals(60f, rec.target.fps, 0f)
        assertEquals(preset.bitrateKbps, rec.target.bitrateKbps)
        assertNull(SessionLearning.evaluate(listOf(report(), report()), preset, now))
        assertNull(SessionLearning.evaluate(listOf(high, high.copy(activeVideoFormat = 2)), preset, now))
        assertNull(SessionLearning.evaluate(listOf(high.copy(activeVideoFormat = 0), high.copy(activeVideoFormat = 0)), preset, now))
        assertNull(SessionLearning.evaluate(listOf(report(decodeMs = 10), report(decodeMs = 10)), preset, now))
    }

    @Test fun neverRaisesLowBitrateOrReducesBelowFloors() {
        val floor = preset.copy(fps = 30f, bitrateKbps = 1500)
        val bad = report(loss = 200, decodeMs = 100).copy(baseline = floor, preset = floor)
        assertNull(SessionLearning.evaluate(listOf(bad, bad), floor, now))
        val almostFloor = floor.copy(bitrateKbps = 2100)
        val networkBad = bad.copy(baseline = almostFloor, preset = almostFloor)
        assertEquals(2000, SessionLearning.evaluate(listOf(networkBad, networkBad), almostFloor, now)!!.target.bitrateKbps)
    }

    @Test fun coOpCeilingIsUsedForReductionWhileSavedPreferencesRemainBaseline() {
        val shared = preset.copy(width = 1280, height = 720, fps = 60f, bitrateKbps = 8000)
        val bad = report(loss = 200, decodeMs = 30).copy(preset = shared)
        val rec = SessionLearning.evaluate(listOf(bad, bad), preset, now)!!
        assertEquals(6400, rec.target.bitrateKbps)
        assertEquals(30f, rec.target.fps, 0f)
        assertEquals(preset.width, rec.target.width)
        assertEquals(preset.codec, rec.target.codec)
        assertEquals(preset.hdr, rec.target.hdr)
    }

    @Test fun acceptanceIsOptInAndRevalidatedAfterManualChangesAndNewEvidence() {
        val reports = listOf(report(loss = 200), report(loss = 200))
        val rec = SessionLearning.evaluate(reports, preset, now)!!
        assertEquals(rec.target, SessionLearning.acceptedTarget(reports, preset, rec, true, false, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset, null, true, false, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset, rec, false, false, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset, rec, true, true, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset.copy(bitrateKbps = 12_000), rec, true, false, now))
        assertNull(SessionLearning.acceptedTarget(reports + report(), preset, rec, true, false, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset, rec.copy(target = preset.copy(bitrateKbps = 99_000)), true, false, now))
        assertNull(SessionLearning.acceptedTarget(reports, preset, rec, true, false, now + 15L * 86400_000))
    }
}
