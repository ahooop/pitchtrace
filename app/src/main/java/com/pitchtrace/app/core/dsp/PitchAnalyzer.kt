package com.pitchtrace.app.core.dsp

import com.pitchtrace.app.core.pitch.PitchResult
import kotlin.math.sqrt

/**
 * 采集侧的分帧器：
 * - 把采集率（48k / 44.1k）整数抽取到分析率（约 24k），运算量减半；
 * - 维护一个环形窗口，每 [hopSize] 个分析点跑一次 YIN；
 * - 顺手做 RMS 静音门限，静音时直接给出 [PitchResult.NONE]，省掉一次 YIN。
 *
 * 这里只做信号处理，不持有任何 Android 对象，所以可以完整跑 JVM 单测。
 */
class PitchAnalyzer(
    private val captureRate: Int,
    minFrequency: Float,
    maxFrequency: Float,
    private val hopSize: Int = 256,
    private val silenceRms: Float = 0.0018f,
) {

    /** 分析采样率：采集率的一半（48k → 24k），至少 16k。 */
    val analysisRate: Int = if (captureRate >= 32000) captureRate / 2 else captureRate

    private val decimation: Int = (captureRate / analysisRate).coerceAtLeast(1)
    private val detector = YinPitchDetector(analysisRate, minFrequency, maxFrequency)
    private val tauMax: Int = (analysisRate / minFrequency).toInt().coerceAtMost(512)

    /** 窗口长度取 3 个最长周期，保证低频也有足够的重叠样本。 */
    val frameSize: Int = (tauMax * 3).coerceIn(1024, 3072)

    /** 一次分析覆盖的时长（ms），用于把延迟对齐到时间轴。 */
    val windowMs: Long = (frameSize * 1000L / analysisRate)

    private val ring = FloatArray(frameSize)
    private val frame = FloatArray(frameSize)

    private var writePos = 0
    private var filled = 0
    private var sinceHop = 0
    private var accumulator = 0
    private var sum = 0.0

    fun reset() {
        writePos = 0
        filled = 0
        sinceHop = 0
        accumulator = 0
        sum = 0.0
        java.util.Arrays.fill(ring, 0f)
    }

    /**
     * 喂入一段采集数据（[-1, 1] 的浮点单声道），每凑够一个 hop 回调一次结果。
     * 回调在调用线程上同步执行，调用方需要自己保证线程亲和性。
     */
    fun process(buffer: FloatArray, count: Int, onResult: (PitchResult) -> Unit) {
        var i = 0
        while (i < count) {
            sum += buffer[i]
            accumulator++
            if (accumulator == decimation) {
                ring[writePos] = (sum / decimation).toFloat()
                sum = 0.0
                accumulator = 0
                writePos++
                if (writePos == frameSize) writePos = 0
                if (filled < frameSize) filled++

                if (filled == frameSize) {
                    sinceHop++
                    if (sinceHop >= hopSize) {
                        sinceHop = 0
                        analyze(onResult)
                    }
                }
            }
            i++
        }
    }

    private fun analyze(onResult: (PitchResult) -> Unit) {
        var index = writePos
        var energy = 0.0
        for (k in 0 until frameSize) {
            val v = ring[index]
            frame[k] = v
            energy += v.toDouble() * v
            index++
            if (index == frameSize) index = 0
        }
        val rms = sqrt(energy / frameSize).toFloat()
        if (rms < silenceRms) {
            onResult(PitchResult.NONE)
            return
        }
        val result = detector.detect(frame, frameSize)
        onResult(if (result.hasPitch && result.confidence < 0.3f) PitchResult.NONE else result)
    }
}
