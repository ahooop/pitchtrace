package com.pitchtrace.app.core.practice

/**
 * 扫频音域练习：从中心音向下逐半音探到最低、再向上逐半音探到最高。
 *
 * 索引约定（[RangeSweep]）：
 * - `0..downCount` 是向下段，索引 0 是中心音；
 * - `downCount+1..downCount+upCount` 是向上段。
 */
data class RangeSweep(
    val exercise: Exercise,
    val centerMidi: Int,
    val downCount: Int,
    val upCount: Int,
) {
    val downRange: IntRange get() = 0..downCount

    val upRange: IntRange get() = (downCount + 1)..(downCount + upCount)

    val totalNotes: Int get() = exercise.notes.size
}

object RangeExercise {

    const val NOTE_MS = 700L
    const val GAP_MS = 100L
    const val CENTER_MIDI = 60
    const val DOWN_SEMITONES = 24
    const val UP_SEMITONES = 24

    /** 默认 C4 起，向下到 C2、向上到 C6，正好覆盖「人声」档。 */
    fun sweep(
        centerMidi: Int = CENTER_MIDI,
        down: Int = DOWN_SEMITONES,
        up: Int = UP_SEMITONES,
    ): RangeSweep {
        val midis = ArrayList<Int>(1 + down + up)
        midis.add(centerMidi)
        for (i in 1..down) midis.add(centerMidi - i)
        for (i in 1..up) midis.add(centerMidi + i)

        var cursor = 0L
        val notes = midis.map { midi ->
            Note(
                midi = midi.toFloat(),
                startMs = cursor,
                durationMs = NOTE_MS,
            ).also { cursor += NOTE_MS + GAP_MS }
        }
        return RangeSweep(
            exercise = Exercise(id = "range-sweep", title = "音域扫描", notes = notes),
            centerMidi = centerMidi,
            downCount = down,
            upCount = up,
        )
    }
}
