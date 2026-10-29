package com.pitchtrace.app.core.practice

import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchRange

enum class RangeSource { FREE, GUIDED, MIXED }

/**
 * 一次音域测试的结果。两个极值都是「稳定唱到」的音，不是瞬时最大值。
 */
data class RangeResult(
    val lowMidi: Float,
    val highMidi: Float,
    val lowStableMs: Long = 0L,
    val highStableMs: Long = 0L,
    val source: RangeSource = RangeSource.FREE,
) {

    val hasRange: Boolean
        get() = !lowMidi.isNaN() && !highMidi.isNaN() && highMidi >= lowMidi

    val spanSemitones: Float
        get() = if (hasRange) highMidi - lowMidi else 0f

    val lowNote: String get() = if (lowMidi.isNaN()) "—" else Music.noteName(lowMidi)

    val highNote: String get() = if (highMidi.isNaN()) "—" else Music.noteName(highMidi)

    /** C2（MIDI 36）以下手机麦克风就明显衰减了，测出来可能是设备的底。 */
    val lowLimitedByMic: Boolean get() = !lowMidi.isNaN() && lowMidi <= 36.5f

    /**
     * 选能容纳当前音域的最小档位。
     *
     * 先按上下各留 1 个半音余量找；找不到就按原值再找一次——否则音域恰好顶到
     * 档位边界（比如最低音就是 E1）时会被误判成「放不下」。
     */
    fun recommendedRange(): PitchRange {
        if (!hasRange) return PitchRange.VOICE

        // 每个 margin 各找一个「能装下」的档位，再在这些候选里挑跨度最小的。
        // 不能找到第一个就返回：留余量时可能先命中 FULL，而原值其实能装进 BASS。
        val candidates = ArrayList<PitchRange>(2)
        for (margin in floatArrayOf(1f, 0f)) {
            val low = lowMidi - margin
            val high = highMidi + margin
            val fit = PitchRange.entries.firstOrNull { low >= it.minMidi && high <= it.maxMidi }
            if (fit != null) candidates.add(fit)
        }
        return candidates.minByOrNull { it.maxMidi - it.minMidi } ?: PitchRange.FULL
    }

    companion object {
        val EMPTY = RangeResult(Float.NaN, Float.NaN)

        /** 混合模式：自由测与引导测各取更宽的那一侧。 */
        fun merge(free: RangeResult, guided: GuidedSweepResult): RangeResult = RangeResult(
            lowMidi = nanMin(free.lowMidi, guided.lowMidi),
            highMidi = nanMax(free.highMidi, guided.highMidi),
            lowStableMs = free.lowStableMs,
            highStableMs = free.highStableMs,
            source = RangeSource.MIXED,
        )

        private fun nanMin(a: Float, b: Float): Float = when {
            a.isNaN() -> b
            b.isNaN() -> a
            else -> minOf(a, b)
        }

        private fun nanMax(a: Float, b: Float): Float = when {
            a.isNaN() -> b
            b.isNaN() -> a
            else -> maxOf(a, b)
        }
    }
}
