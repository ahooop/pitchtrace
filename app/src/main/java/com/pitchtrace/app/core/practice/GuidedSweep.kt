package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.PitchSample
import kotlin.math.abs

data class GuidedSweepResult(
    val lowMidi: Float,
    val highMidi: Float,
    val offsetMs: Long,
    val hitCount: Int,
    val noteCount: Int,
) {
    val hasRange: Boolean get() = !lowMidi.isNaN() && !highMidi.isNaN()
}

/**
 * 引导扫频的判定：逐个音看「那一格里有没有唱对的样本」。
 *
 * 播放和采集之间有延迟，所以先在 ±400ms 内搜一个让命中数最多的整体偏移，
 * 再按方向从中心往外走：命中就继续，连续 [maxConsecutiveMisses] 个音没唱对就停。
 */
object GuidedSweep {

    private const val MAX_OFFSET_MS = 400L
    private const val OFFSET_STEP_MS = 40L

    /**
     * 必须小于 0.5 个半音。
     *
     * 扫频是逐半音进行的，如果容差给到 1 个半音，每个音都会匹配到自己的邻居，
     * 于是「唱到某处就跟不上」永远不会触发——测试里就是这么暴露出来的。
     */
    private const val HIT_TOLERANCE_SEMITONES = 0.5f
    private const val MIN_WINDOW_RATIO = 0.25f
    private const val GRID_MS = 10L

    fun evaluate(
        sweep: RangeSweep,
        samples: List<PitchSample>,
        maxConsecutiveMisses: Int = 2,
    ): GuidedSweepResult {
        val notes = sweep.exercise.notes
        if (samples.isEmpty() || notes.isEmpty()) {
            return GuidedSweepResult(Float.NaN, Float.NaN, 0L, 0, notes.size)
        }

        var bestOffset = 0L
        var bestHits = -1
        var bestFlags: List<Boolean> = emptyList()

        var offset = -MAX_OFFSET_MS
        while (offset <= MAX_OFFSET_MS) {
            val flags = notes.map { hitAt(it, samples, offset) }
            val hits = flags.count { it }
            if (hits > bestHits) {
                bestHits = hits
                bestOffset = offset
                bestFlags = flags
            }
            offset += OFFSET_STEP_MS
        }

        val low = walk(sweep.downRange, notes, bestFlags, maxConsecutiveMisses)
        val high = walk(sweep.upRange, notes, bestFlags, maxConsecutiveMisses)
        return GuidedSweepResult(
            lowMidi = low,
            highMidi = high,
            offsetMs = bestOffset,
            hitCount = bestHits,
            noteCount = notes.size,
        )
    }

    /** 一个方向内的行走：命中就推进，连续失败即止。 */
    private fun walk(
        range: IntRange,
        notes: List<Note>,
        flags: List<Boolean>,
        maxMisses: Int,
    ): Float {
        var reached = Float.NaN
        var misses = 0
        for (index in range) {
            val hit = flags.getOrElse(index) { false }
            if (hit) {
                val midi = notes[index].midi
                reached = when {
                    reached.isNaN() -> midi
                    // 向下段索引递增、音高递减，取更低的；向上段相反
                    notes[range.first].midi > notes[range.last].midi -> minOf(reached, midi)
                    else -> maxOf(reached, midi)
                }
                misses = 0
            } else {
                misses++
                if (misses >= maxMisses) break
            }
        }
        return reached
    }

    /**
     * 单个音是否唱对。
     *
     * @param offsetMs 整体时间偏移（抵消播放/采集延迟）
     * @param tailMs 判定窗口向后延长的时间；实时跟唱时用户总比参考音慢一点，
     *   用这个尾窗兜住，免得「刚唱到就被判没唱」。
     */
    fun hitAt(
        note: Note,
        samples: List<PitchSample>,
        offsetMs: Long = 0L,
        tailMs: Long = 0L,
    ): Boolean {
        val from = note.startMs + offsetMs
        val to = note.endMs + offsetMs + tailMs
        if (to <= from) return false

        val window = samples.filter { it.timeMs >= from && it.timeMs < to }
        val expected = (note.durationMs / GRID_MS).toInt().coerceAtLeast(1)
        val minCount = (expected * MIN_WINDOW_RATIO).toInt().coerceAtLeast(2)
        if (window.size < minCount) return false

        val midis = window.map { it.midi }.sorted()
        val median = midis[midis.size / 2]
        return abs(median - note.midi) <= HIT_TOLERANCE_SEMITONES
    }
}
