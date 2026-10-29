package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.PitchSample
import kotlin.math.abs

/** 单个目标音的判定结果。 */
enum class Verdict {
    /** 偏差在 ±50 音分以内。 */
    HIT,

    /** 偏差在 ±50–100 音分之间。 */
    CLOSE,

    /** 偏差超过 ±100 音分，或唱成了别的音。 */
    MISS,

    /** 这一音根本没唱（样本太少）。 */
    ABSENT,
}

data class NoteResult(
    val note: Note,
    val sungMidi: Float?,
    val centsOff: Float?,
    val verdict: Verdict,
)

data class PracticeResult(
    val exerciseId: String,
    val offsetMs: Long,
    val detectedVoice: Boolean,
    val noteResults: List<NoteResult>,
    val hitRate: Float,
    val avgAbsCents: Float,
)

/**
 * 练习评分。纯 Kotlin，不依赖 Android，可以完整跑 JVM 单测。
 *
 * 两步：
 * 1. 用互相关估计人声相对参考轨的整体时间偏移（抵消播放/采集延迟）；
 * 2. 对齐之后逐音取中位数，算音分偏差并判定。
 */
object PracticeScorer {

    const val STEP_MS = 10L
    const val MAX_OFFSET_MS = 600L
    const val MAX_CENTS_HIT = 50f
    const val MAX_CENTS_CLOSE = 100f

    private const val MIN_OVERLAP_RATIO = 0.2f
    private const val MIN_SAMPLE_RATIO = 0.3f
    private const val MAX_OUTLIER_SEMITONES = 12f

    /** 参考轨静音、人声却在唱的惩罚（半音当量）。用来消除「平顶音符」下的对齐多解。 */
    private const val SILENCE_PENALTY = 1.5f
    private const val EPSILON = 0.01f

    fun score(exercise: Exercise, samples: List<PitchSample>): PracticeResult {
        if (samples.isEmpty()) return emptyResult(exercise)

        val track = ReferenceTrack(exercise, STEP_MS)
        val reference = track.gridSnapshot()
        val maxShift = (MAX_OFFSET_MS / STEP_MS).toInt()
        val voice = buildVoiceGrid(samples, reference.size + maxShift * 2 + 1, STEP_MS)

        val offset = align(reference, voice, maxShift)
        val noteResults = exercise.notes.map { note -> scoreNote(note, samples, offset) }
        val scored = noteResults.filter { it.verdict != Verdict.ABSENT }

        val hitRate = if (noteResults.isEmpty()) {
            0f
        } else {
            noteResults.count { it.verdict == Verdict.HIT }.toFloat() / noteResults.size
        }
        val avgAbsCents = if (scored.isEmpty()) {
            0f
        } else {
            scored.mapNotNull { it.centsOff }.map { abs(it) }.average().toFloat()
        }

        return PracticeResult(
            exerciseId = exercise.id,
            offsetMs = offset,
            detectedVoice = scored.isNotEmpty(),
            noteResults = noteResults,
            hitRate = hitRate,
            avgAbsCents = avgAbsCents,
        )
    }

    /**
     * 在 ±[MAX_OFFSET_MS] 范围内找最佳整体偏移。
     * 正偏移表示人声比参考轨晚（唱得慢），单位毫秒。
     */
    private fun align(reference: FloatArray, voice: FloatArray, maxShift: Int): Long {
        var bestOffset = 0L
        var bestCost = Double.MAX_VALUE
        var bestCount = 0
        for (shift in -maxShift..maxShift) {
            val score = costAt(reference, voice, shift) ?: continue
            val cheaper = score.cost < bestCost - 1e-9
            val tiedButWider = abs(score.cost - bestCost) <= 1e-9 && score.count > bestCount
            if (cheaper || tiedButWider) {
                bestCost = score.cost
                bestCount = score.count
                bestOffset = shift * STEP_MS
            }
        }
        return bestOffset
    }

    /**
     * 对齐代价 = 重叠格上的平均绝对差。
     *
     * 两个细节决定它能不能对上：
     * - 参考轨静音、人声却有值时要罚分，否则音符内部是「平顶」，多个偏移都能零代价匹配；
     * - 参考轨有值、人声没值时不罚分，因为漏唱是允许的，罚分会把对齐带跑。
     *
     * 平局时比覆盖格数（[Alignment.count]），偏移越大覆盖越少，从而收敛到真实延迟。
     */
    private fun costAt(reference: FloatArray, voice: FloatArray, shift: Int): Alignment? {
        var sum = 0.0
        var count = 0
        for (i in reference.indices) {
            val j = i + shift
            if (j < 0 || j >= voice.size) continue
            val r = reference[i]
            val v = voice[j]
            if (r.isNaN()) {
                if (!v.isNaN()) {
                    sum += SILENCE_PENALTY
                    count++
                }
                continue
            }
            if (v.isNaN()) continue
            val diff = abs(r - v)
            if (diff > MAX_OUTLIER_SEMITONES) continue
            sum += diff
            count++
        }
        val minOverlap = (reference.size * MIN_OVERLAP_RATIO).toInt().coerceAtLeast(4)
        if (count < minOverlap) return null
        return Alignment(cost = sum / count, count = count)
    }

    private data class Alignment(val cost: Double, val count: Int)

    private fun scoreNote(note: Note, samples: List<PitchSample>, offsetMs: Long): NoteResult {
        val from = note.startMs + offsetMs
        val to = note.endMs + offsetMs
        val window = samples.filter { it.timeMs >= from && it.timeMs < to }

        val expectedCount = (note.durationMs / STEP_MS).toInt().coerceAtLeast(1)
        val minCount = maxOf(2, (expectedCount * MIN_SAMPLE_RATIO).toInt())
        if (window.size < minCount) {
            return NoteResult(note = note, sungMidi = null, centsOff = null, verdict = Verdict.ABSENT)
        }

        val midis = window.map { it.midi }.sorted()
        val median = midis[midis.size / 2]
        val cents = 100f * (median - note.midi)
        val verdict = when {
            abs(cents) <= MAX_CENTS_HIT + EPSILON -> Verdict.HIT
            abs(cents) <= MAX_CENTS_CLOSE + EPSILON -> Verdict.CLOSE
            else -> Verdict.MISS
        }
        return NoteResult(note = note, sungMidi = median, centsOff = cents, verdict = verdict)
    }

    private fun buildVoiceGrid(samples: List<PitchSample>, size: Int, stepMs: Long): FloatArray {
        val grid = FloatArray(size) { Float.NaN }
        for (sample in samples) {
            if (sample.timeMs < 0) continue
            val index = (sample.timeMs / stepMs).toInt()
            if (index in grid.indices) grid[index] = sample.midi
        }
        return grid
    }

    private fun emptyResult(exercise: Exercise): PracticeResult = PracticeResult(
        exerciseId = exercise.id,
        offsetMs = 0L,
        detectedVoice = false,
        noteResults = exercise.notes.map {
            NoteResult(note = it, sungMidi = null, centsOff = null, verdict = Verdict.ABSENT)
        },
        hitRate = 0f,
        avgAbsCents = 0f,
    )
}
