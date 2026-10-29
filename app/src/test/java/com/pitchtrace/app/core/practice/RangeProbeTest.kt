package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音域提取的关键性质：只认稳定段，不认瞬时极值。
 */
class RangeProbeTest {

    private fun sample(timeMs: Long, midi: Float) = PitchSample(
        timeMs = timeMs,
        midi = midi,
        frequency = Music.midiToFrequency(midi),
        confidence = 0.9f,
    )

    private fun feed(probe: RangeProbe, from: Long, to: Long, midi: Float): Long {
        var time = from
        while (time <= to) {
            probe.accept(sample(time, midi))
            time += 10L
        }
        return time
    }

    @Test
    fun stable_tone_is_committed() {
        val probe = RangeProbe()
        feed(probe, 0L, 400L, 60f)
        probe.finish()
        assertEquals(60f, probe.lowMidi, 0.01f)
        assertEquals(60f, probe.highMidi, 0.01f)
        assertEquals(1, probe.stableCount)
    }

    @Test
    fun short_tone_is_ignored() {
        val probe = RangeProbe()
        feed(probe, 0L, 200L, 40f)
        probe.finish()
        assertTrue("200ms 不足以算稳定", probe.lowMidi.isNaN())
        assertEquals(0, probe.stableCount)
    }

    @Test
    fun jitter_does_not_commit() {
        val probe = RangeProbe()
        var time = 0L
        var index = 0
        while (time <= 1000L) {
            probe.accept(sample(time, if (index % 2 == 0) 60f else 62f))
            time += 10L
            index++
        }
        probe.finish()
        assertTrue("来回抖动不应产生稳定段", probe.lowMidi.isNaN())
        assertEquals(0, probe.stableCount)
    }

    @Test
    fun low_and_high_segments_are_tracked() {
        val probe = RangeProbe()
        var time = feed(probe, 0L, 400L, 48f)
        time += 300L // 留出间隙，强制分段
        feed(probe, time, time + 400L, 72f)
        probe.finish()
        assertEquals(48f, probe.lowMidi, 0.01f)
        assertEquals(72f, probe.highMidi, 0.01f)
        assertEquals(2, probe.stableCount)
    }

    @Test
    fun reset_clears_everything() {
        val probe = RangeProbe()
        feed(probe, 0L, 400L, 60f)
        probe.finish()
        probe.reset()
        assertTrue(probe.lowMidi.isNaN())
        assertTrue(probe.highMidi.isNaN())
        assertEquals(0, probe.stableCount)
    }
}
