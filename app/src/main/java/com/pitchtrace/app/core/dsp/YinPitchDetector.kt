package com.pitchtrace.app.core.dsp

import com.pitchtrace.app.core.pitch.PitchResult
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * YIN 基频检测（de Cheveigné & Kawahara, 2002）。
 *
 * 实现要点：
 * 1. 先减去直流分量，否则差分函数会被偏置压平；
 * 2. 差分函数 d(τ) 只在 [tauMin, tauMax] 内计算，省掉一半以上的乘法；
 * 3. 累积均值归一化差分 d'(τ) 消除“τ 越大 d 越小”的偏置；
 * 4. 取第一个低于阈值的 τ，再局部下探到极小值，最后抛物线插值求亚采样精度。
 *
 * 这里刻意不使用 FFT：在 24kHz、单声道、10ms 步进下直接算差分已经足够快
 * （约每帧 25 万次乘加），换来的是零依赖、可被 JVM 单测直接验证。
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Float = 60f,
    private val maxFrequency: Float = 1300f,
    private val threshold: Float = 0.15f,
) {

    private var difference = FloatArray(0)
    private var cmnd = FloatArray(0)
    private var centered = FloatArray(0)

    fun detect(buffer: FloatArray, length: Int = buffer.size): PitchResult {
        if (length < 128) return PitchResult.NONE

        val tauMin = max(2, ceil(sampleRate / maxFrequency).toInt())
        val tauMax = min(length / 2, (sampleRate / minFrequency).toInt())
        if (tauMax - tauMin < 4) return PitchResult.NONE

        val overlap = length - tauMax
        if (difference.size <= tauMax + 1) {
            difference = FloatArray(tauMax + 2)
            cmnd = FloatArray(tauMax + 2)
        }
        if (centered.size < length) centered = FloatArray(length)

        var mean = 0f
        for (i in 0 until length) mean += buffer[i]
        mean /= length
        for (i in 0 until length) centered[i] = buffer[i] - mean

        for (tau in tauMin..tauMax) {
            var sum = 0f
            var j = 0
            while (j < overlap) {
                val delta = centered[j] - centered[j + tau]
                sum += delta * delta
                j++
            }
            difference[tau] = sum
        }

        cmnd[tauMin - 1] = 1f
        var running = 0f
        for (tau in tauMin..tauMax) {
            running += difference[tau]
            cmnd[tau] = if (running <= 1e-12f) 1f else difference[tau] * tau / running
        }

        var best = -1
        for (tau in tauMin..tauMax) {
            if (cmnd[tau] < threshold) {
                best = tau
                break
            }
        }
        if (best == -1) {
            // 全程没有低于阈值：取全局最小。如果连谷底都不够低，说明这段信号没有周期性
            // （静音、白噪声、气声），直接判为无音高，不要硬凑一个频率出来。
            var lowest = Float.MAX_VALUE
            for (tau in tauMin..tauMax) {
                if (cmnd[tau] < lowest) {
                    lowest = cmnd[tau]
                    best = tau
                }
            }
            if (best == -1 || lowest >= 0.35f) return PitchResult.NONE
        } else {
            while (best + 1 <= tauMax && cmnd[best + 1] < cmnd[best]) best++
        }
        if (best <= 0) return PitchResult.NONE

        var refined = best.toFloat()
        if (best > tauMin && best + 1 <= tauMax) {
            val prev = cmnd[best - 1]
            val cur = cmnd[best]
            val next = cmnd[best + 1]
            val denominator = prev - 2f * cur + next
            if (abs(denominator) > 1e-9f) {
                refined = best + 0.5f * (prev - next) / denominator
            }
        }

        val frequency = sampleRate / refined
        if (frequency < minFrequency || frequency > maxFrequency) return PitchResult.NONE

        return PitchResult(frequency, (1f - cmnd[best]).coerceIn(0f, 1f))
    }
}
