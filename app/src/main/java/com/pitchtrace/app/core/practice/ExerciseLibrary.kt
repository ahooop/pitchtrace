package com.pitchtrace.app.core.practice

/**
 * 内置练习。音域统一落在 C4–C5，绝大多数人声都够得着。
 *
 * 音符用 MIDI 编号写死：60 = C4，69 = A4，72 = C5。
 * 每个音 520ms，音与音之间留 120ms 的换气间隙——间隙里参考轨没有目标，
 * 评分时也不计入，避免把呼吸声算成跑调。
 */
object ExerciseLibrary {

    private const val NOTE_MS = 520L
    private const val GAP_MS = 120L

    val scale: Exercise = build(
        id = "scale-c4",
        title = "C 大调音阶",
        midis = listOf(60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60),
    )

    val arpeggio: Exercise = build(
        id = "arpeggio-c4",
        title = "C 大调琶音",
        midis = listOf(60, 64, 67, 72, 67, 64, 60),
    )

    val twinkle: Exercise = build(
        id = "twinkle",
        title = "小星星（两句）",
        midis = listOf(60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60),
    )

    val all: List<Exercise> = listOf(scale, arpeggio, twinkle)

    fun byId(id: String): Exercise? = all.firstOrNull { it.id == id }

    private fun build(id: String, title: String, midis: List<Int>): Exercise {
        var cursor = 0L
        val notes = midis.map { midi ->
            Note(
                midi = midi.toFloat(),
                startMs = cursor,
                durationMs = NOTE_MS,
            ).also { cursor += NOTE_MS + GAP_MS }
        }
        return Exercise(id = id, title = title, notes = notes)
    }
}
