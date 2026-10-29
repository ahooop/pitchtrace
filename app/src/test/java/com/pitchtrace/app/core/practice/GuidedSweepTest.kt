package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSweepTest {

    private val sweep = RangeExercise.sweep(centerMidi = 60, down = 3, up = 3)

    /** 只唱前 [sungNotes] 个音，模拟「唱到某个音就跟不上了」。 */
    private fun samples(sungNotes: Int = Int.MAX_VALUE): List<PitchSample> {
        val out = ArrayList<PitchSample>()
        sweep.exercise.notes.forEachIndexed { index, note ->
            if (index >= sungNotes) return@forEachIndexed
            var time = note.startMs + 20L
            while (time < note.endMs - 20L) {
                out.add(
                    PitchSample(
                        timeMs = time,
                        midi = note.midi,
                        frequency = Music.midiToFrequency(note.midi),
                        confidence = 0.9f,
                    ),
                )
                time += 10L
            }
        }
        return out
    }

    @Test
    fun full_sweep_reaches_both_ends() {
        val result = GuidedSweep.evaluate(sweep, samples())
        assertEquals(57f, result.lowMidi, 0.01f) // C4 往下 3 个半音 = A3
        assertEquals(63f, result.highMidi, 0.01f) // C4 往上 3 个半音 = D#4
        assertTrue(result.hasRange)
        assertEquals(sweep.totalNotes, result.hitCount)
    }

    @Test
    fun stops_after_consecutive_misses() {
        // 向下段索引 0..3，只唱到索引 1（B3），后面都不唱
        val result = GuidedSweep.evaluate(sweep, samples(sungNotes = 2))
        assertEquals(59f, result.lowMidi, 0.01f)
        assertTrue("向上段完全没唱，应为空", result.highMidi.isNaN())
    }

    @Test
    fun empty_input_has_no_range() {
        val result = GuidedSweep.evaluate(sweep, emptyList())
        assertFalse(result.hasRange)
        assertEquals(0, result.hitCount)
    }

    @Test
    fun wrong_pitch_does_not_count_as_hit() {
        // 全部唱高一个八度
        val shifted = samples().map { it.copy(midi = it.midi + 12f) }
        val result = GuidedSweep.evaluate(sweep, shifted)
        assertFalse(result.hasRange)
    }
}
