package com.pitchtrace.app.core.practice

import kotlin.math.abs

/**
 * 把 [Exercise] 投影成固定时间栅格的「期望音高」数组。
 *
 * 参考轨是算出来的，不是从音频里解析的——所以它没有识别误差，
 * 这也是「内置合成参考音」相对「导入歌曲」的核心优势。
 *
 * 落在音符之间的间隙里时返回 `NaN`，表示这一刻没有目标。
 */
class ReferenceTrack(
    val exercise: Exercise,
    val stepMs: Long = DEFAULT_STEP_MS,
) {

    private val grid: FloatArray = buildGrid(exercise, stepMs)

    val durationMs: Long get() = exercise.durationMs

    val gridSize: Int get() = grid.size

    fun expectedMidiAt(timeMs: Long): Float {
        val index = indexOf(timeMs)
        return if (index < 0) Float.NaN else grid[index]
    }

    fun hasTargetAt(timeMs: Long): Boolean = !expectedMidiAt(timeMs).isNaN()

    /** 时间窗内的目标音，用于实时判定的宽松匹配。 */
    fun targetsNear(
        timeMs: Long,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): List<Note> = exercise.notes.filter {
        timeMs >= it.startMs - toleranceMs && timeMs <= it.endMs + toleranceMs
    }

    /** 时间窗内离 [timeMs] 最近（按音符中点距离）的目标音高，没有目标时为 `NaN`。 */
    fun nearestTargetMidi(
        timeMs: Long,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): Float {
        val candidates = targetsNear(timeMs, toleranceMs)
        if (candidates.isEmpty()) return Float.NaN
        val middle = { note: Note -> (note.startMs + note.endMs) / 2 }
        return candidates.minByOrNull { abs(middle(it) - timeMs) }?.midi ?: Float.NaN
    }

    /** 供实时绘制和评分使用的只读快照。 */
    fun gridSnapshot(): FloatArray = grid.copyOf()

    /** 返回时间对应的栅格下标，越界返回 -1。 */
    fun indexOf(timeMs: Long): Int {
        if (timeMs < 0) return -1
        val index = (timeMs / stepMs).toInt()
        return if (index in grid.indices) index else -1
    }

    companion object {
        const val DEFAULT_STEP_MS = 10L
        const val DEFAULT_TOLERANCE_MS = 200L

        private fun buildGrid(exercise: Exercise, stepMs: Long): FloatArray {
            val size = ((exercise.durationMs + stepMs - 1) / stepMs).toInt().coerceAtLeast(1)
            val grid = FloatArray(size) { Float.NaN }
            for (note in exercise.notes) {
                val from = ((note.startMs + stepMs - 1) / stepMs).toInt()
                val to = ((note.endMs + stepMs - 1) / stepMs).toInt()
                val safeTo = to.coerceAtMost(size)
                for (i in from until safeTo) grid[i] = note.midi
            }
            return grid
        }
    }
}
