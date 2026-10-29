package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.PitchSample
import kotlin.math.abs

/**
 * 自由唱时的音域提取：只认「稳定保持」的音，不认瞬时极值。
 *
 * 连续同一音高（相对段内中位数偏差 ≤ [toleranceSemitones]）保持 ≥ [minStableMs]，
 * 才提交为一次有效音高并更新最低/最高。咳嗽、椅子响、换气声都撑不过这个门槛。
 */
class RangeProbe(
    private val minStableMs: Long = 300L,
    private val toleranceSemitones: Float = 1f,
    private val maxGapMs: Long = 250L,
    private val minSamples: Int = 3,
) {

    private val segment = ArrayList<Float>(256)
    private var segmentStart = 0L
    private var segmentEnd = 0L
    private var anchor = Float.NaN

    var lowMidi: Float = Float.NaN
        private set

    var highMidi: Float = Float.NaN
        private set

    var lowStableMs: Long = 0L
        private set

    var highStableMs: Long = 0L
        private set

    /** 已提交的稳定段数量，用来判断这次测试到底有没有唱出东西。 */
    var stableCount: Int = 0
        private set

    fun reset() {
        segment.clear()
        segmentStart = 0L
        segmentEnd = 0L
        anchor = Float.NaN
        lowMidi = Float.NaN
        highMidi = Float.NaN
        lowStableMs = 0L
        highStableMs = 0L
        stableCount = 0
    }

    fun accept(sample: PitchSample) {
        if (segment.isEmpty()) {
            startSegment(sample)
            return
        }
        val tooLate = sample.timeMs - segmentEnd > maxGapMs
        val offPitch = abs(sample.midi - anchor) > toleranceSemitones
        if (tooLate || offPitch) {
            commitSegment()
            startSegment(sample)
            return
        }
        segment.add(sample.midi)
        segmentEnd = sample.timeMs
        anchor = median(segment)
    }

    /** 用户手动结束或超时必须调用，否则最后一段不计入。 */
    fun finish() {
        commitSegment()
    }

    fun snapshot(source: RangeSource = RangeSource.FREE): RangeResult =
        RangeResult(lowMidi, highMidi, lowStableMs, highStableMs, source)

    private fun startSegment(sample: PitchSample) {
        segment.clear()
        segment.add(sample.midi)
        segmentStart = sample.timeMs
        segmentEnd = sample.timeMs
        anchor = sample.midi
    }

    private fun commitSegment() {
        if (segment.size >= minSamples) {
            val duration = segmentEnd - segmentStart
            if (duration >= minStableMs) {
                val midi = median(segment)
                if (lowMidi.isNaN() || midi < lowMidi) {
                    lowMidi = midi
                    lowStableMs = duration
                }
                if (highMidi.isNaN() || midi > highMidi) {
                    highMidi = midi
                    highStableMs = duration
                }
                stableCount++
            }
        }
        segment.clear()
        anchor = Float.NaN
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
