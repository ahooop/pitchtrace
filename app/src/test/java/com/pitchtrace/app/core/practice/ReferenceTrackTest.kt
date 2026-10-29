package com.pitchtrace.app.core.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 参考轨的关键性质：音符内有目标、间隙无目标、越界无目标。
 * 参考轨是评分的地基，它错一格，后面的音分就全错。
 */
class ReferenceTrackTest {

    private val track = ReferenceTrack(ExerciseLibrary.scale)

    @Test
    fun track_reports_note_pitch_inside_window() {
        assertEquals(60f, track.expectedMidiAt(0L), 0.001f)
        assertEquals(60f, track.expectedMidiAt(519L), 0.001f)
        assertEquals(62f, track.expectedMidiAt(640L), 0.001f)
    }

    @Test
    fun track_has_no_target_in_gap() {
        assertTrue("520ms 是第一个音符的结束点，应落在间隙", track.expectedMidiAt(520L).isNaN())
        assertTrue(track.expectedMidiAt(600L).isNaN())
        assertFalse(track.hasTargetAt(560L))
    }

    @Test
    fun track_returns_nan_outside_duration() {
        assertTrue(track.expectedMidiAt(track.durationMs + 500L).isNaN())
        assertTrue(track.expectedMidiAt(-10L).isNaN())
    }

    @Test
    fun nearest_target_picks_closest_note() {
        assertEquals(60f, track.nearestTargetMidi(0L, 200L), 0.001f)
        assertEquals(62f, track.nearestTargetMidi(700L, 200L), 0.001f)
        assertTrue("容差 20ms 时 560ms 处两边都够不着", track.nearestTargetMidi(560L, 20L).isNaN())
    }

    @Test
    fun grid_covers_whole_exercise() {
        val grid = track.gridSnapshot()
        assertEquals(15, track.exercise.notes.size)
        assertTrue("栅格应覆盖整个练习时长", grid.size >= (track.durationMs / 10L).toInt())
    }
}
