package com.pitchtrace.app.core.pitch

import kotlin.math.abs

/**
 * 把逐帧的 YIN 结果变成可绘制的连续音高：
 * 1. 置信度门限过滤（噪声、气声、混响尾巴）；
 * 2. 八度误判纠正（人声最常见的错误就是跑到高八度或低八度）；
 * 3. 三点中值平滑，去掉单帧跳点，同时保留颤音。
 */
class PitchTracker(
    a4: Float = 440f,
    var confidenceGate: Float = 0.62f,
) {

    var a4: Float = a4
        set(value) {
            field = value
            reset()
        }

    private val window = FloatArray(3)
    private var windowCount = 0
    private var lastMidi = Float.NaN

    fun reset() {
        windowCount = 0
        lastMidi = Float.NaN
    }

    fun track(result: PitchResult, timeMs: Long): PitchSample? {
        if (!result.hasPitch || result.confidence < confidenceGate) return null

        var midi = Music.frequencyToMidi(result.frequency, a4)
        if (midi.isNaN() || midi <= 0f) return null

        if (!lastMidi.isNaN()) {
            val delta = midi - lastMidi
            if (delta > 9.5f) {
                val candidate = midi - 12f
                if (abs(candidate - lastMidi) < 2.5f) midi = candidate
            } else if (delta < -9.5f) {
                val candidate = midi + 12f
                if (abs(candidate - lastMidi) < 2.5f) midi = candidate
            }
        }

        if (windowCount < window.size) {
            window[windowCount++] = midi
        } else {
            window[0] = window[1]
            window[1] = window[2]
            window[2] = midi
        }
        val smoothed = median(window, windowCount)
        lastMidi = smoothed

        return PitchSample(
            timeMs = timeMs,
            midi = smoothed,
            frequency = result.frequency,
            confidence = result.confidence,
        )
    }

    private fun median(values: FloatArray, count: Int): Float = when (count) {
        0 -> 0f
        1 -> values[0]
        2 -> (values[0] + values[1]) / 2f
        else -> {
            val a = values[0]
            val b = values[1]
            val c = values[2]
            when {
                a in b..c || a in c..b -> a
                b in a..c || b in c..a -> b
                else -> c
            }
        }
    }
}
