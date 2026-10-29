package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.PitchRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeResultTest {

    @Test
    fun recommended_range_picks_smallest_fitting_bucket() {
        // C3–C5 落在人声档（C2–C6）
        assertEquals(PitchRange.VOICE, RangeResult(48f, 72f).recommendedRange())
        // E1–C4 落在低音档
        assertEquals(PitchRange.BASS, RangeResult(28f, 50f).recommendedRange())
        // 跨度超过任何单档时退回全音域
        assertEquals(PitchRange.FULL, RangeResult(24f, 96f).recommendedRange())
    }

    @Test
    fun low_c2_is_flagged_as_microphone_limited() {
        assertTrue(RangeResult(36f, 72f).lowLimitedByMic)
        assertFalse(RangeResult(48f, 72f).lowLimitedByMic)
    }

    @Test
    fun span_and_notes_are_derived() {
        val result = RangeResult(48f, 72f)
        assertTrue(result.hasRange)
        assertEquals(24f, result.spanSemitones, 0.01f)
        assertEquals("C3", result.lowNote)
        assertEquals("C5", result.highNote)
    }

    @Test
    fun empty_result_has_no_range() {
        assertFalse(RangeResult.EMPTY.hasRange)
        assertEquals("—", RangeResult.EMPTY.lowNote)
    }

    @Test
    fun merge_keeps_the_wider_side() {
        val free = RangeResult(46f, 70f, source = RangeSource.FREE)
        val guided = GuidedSweepResult(
            lowMidi = 41f,
            highMidi = 74f,
            offsetMs = 0L,
            hitCount = 20,
            noteCount = 49,
        )
        val merged = RangeResult.merge(free, guided)
        assertEquals(41f, merged.lowMidi, 0.01f)
        assertEquals(74f, merged.highMidi, 0.01f)
        assertEquals(RangeSource.MIXED, merged.source)
    }

    @Test
    fun merge_handles_missing_side() {
        val free = RangeResult(Float.NaN, Float.NaN)
        val guided = GuidedSweepResult(45f, 70f, 0L, 10, 49)
        val merged = RangeResult.merge(free, guided)
        assertEquals(45f, merged.lowMidi, 0.01f)
        assertEquals(70f, merged.highMidi, 0.01f)
    }
}
