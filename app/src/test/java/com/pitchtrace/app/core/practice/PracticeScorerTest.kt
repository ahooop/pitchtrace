package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评分器的验收标准，对应设计文档第 11 节。
 * 用构造样本代替真实录音，保证判定是确定的。
 */
class PracticeScorerTest {

    private val exercise = ExerciseLibrary.scale

    private fun samples(
        centsOffset: Float = 0f,
        delayMs: Long = 0L,
        skip: Int = -1,
        midiShift: Float = 0f,
    ): List<PitchSample> {
        val out = ArrayList<PitchSample>()
        exercise.notes.forEachIndexed { index, note ->
            if (index == skip) return@forEachIndexed
            var time = note.startMs + 20L
            while (time < note.endMs - 20L) {
                val midi = note.midi + midiShift + centsOffset / 100f
                out.add(
                    PitchSample(
                        timeMs = time + delayMs,
                        midi = midi,
                        frequency = Music.midiToFrequency(midi),
                        confidence = 0.9f,
                    ),
                )
                time += 10L
            }
        }
        return out
    }

    @Test
    fun perfect_performance_hits_every_note() {
        val result = PracticeScorer.score(exercise, samples())
        assertEquals(1f, result.hitRate, 0.001f)
        assertTrue(result.detectedVoice)
        assertEquals(0f, result.avgAbsCents, 0.5f)
        assertTrue(result.noteResults.all { it.verdict == Verdict.HIT })
    }

    @Test
    fun forty_five_cents_off_is_still_hit() {
        val result = PracticeScorer.score(exercise, samples(centsOffset = 45f))
        assertEquals(1f, result.hitRate, 0.001f)
        assertTrue(result.noteResults.all { it.verdict == Verdict.HIT })
    }

    @Test
    fun one_and_a_half_semitone_off_is_miss() {
        val result = PracticeScorer.score(exercise, samples(midiShift = 1.5f))
        assertEquals(0f, result.hitRate, 0.001f)
        assertTrue(result.noteResults.all { it.verdict == Verdict.MISS })
    }

    @Test
    fun delay_is_compensated_by_alignment() {
        val result = PracticeScorer.score(exercise, samples(delayMs = 300L))
        assertEquals(1f, result.hitRate, 0.01f)
        assertTrue(
            "对齐偏移应接近 300ms，实际 ${result.offsetMs}",
            kotlin.math.abs(result.offsetMs - 300L) <= 40L,
        )
    }

    @Test
    fun skipped_note_is_absent() {
        val result = PracticeScorer.score(exercise, samples(skip = 1))
        assertEquals(Verdict.ABSENT, result.noteResults[1].verdict)
        assertEquals(14f / 15f, result.hitRate, 0.001f)
    }

    @Test
    fun empty_input_reports_no_voice() {
        val result = PracticeScorer.score(exercise, emptyList())
        assertFalse(result.detectedVoice)
        assertEquals(0f, result.hitRate, 0.001f)
        assertTrue(result.noteResults.all { it.verdict == Verdict.ABSENT })
    }

    @Test
    fun singing_an_octave_below_is_miss() {
        val result = PracticeScorer.score(exercise, samples(midiShift = -12f))
        assertEquals(0f, result.hitRate, 0.001f)
    }
}
