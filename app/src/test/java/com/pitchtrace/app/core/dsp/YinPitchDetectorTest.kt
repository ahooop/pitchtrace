package com.pitchtrace.app.core.dsp

import com.pitchtrace.app.core.pitch.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DSP 层是纯 Kotlin，可以直接在 JVM 上验证。这里用合成正弦波当输入，
 * 断言检测到的音分误差，避免“看起来能跑但音准飘”的问题。
 */
class YinPitchDetectorTest {

    private fun sine(frequency: Float, sampleRate: Int, samples: Int, phase: Double = 0.0): FloatArray {
        val out = FloatArray(samples)
        val step = 2.0 * PI * frequency / sampleRate
        for (i in 0 until samples) {
            out[i] = (0.6 * sin(step * i + phase)).toFloat()
        }
        return out
    }

    private fun centsError(expectedHz: Float, actualHz: Float): Float =
        (1200.0 * kotlin.math.log2(actualHz.toDouble() / expectedHz)).toFloat()

    @Test
    fun detects_a440_within_five_cents() {
        val rate = 24000
        val detector = YinPitchDetector(rate, minFrequency = 60f, maxFrequency = 1300f)
        val buffer = sine(440f, rate, 1104)
        val result = detector.detect(buffer, buffer.size)

        assertTrue("应当检测到音高", result.hasPitch)
        val error = kotlin.math.abs(centsError(440f, result.frequency))
        assertTrue("440Hz 误差 $error 音分过大", error < 5f)
        assertTrue("置信度应较高", result.confidence > 0.8f)
    }

    @Test
    fun detects_low_c2() {
        val rate = 24000
        val detector = YinPitchDetector(rate, minFrequency = 60f, maxFrequency = 1300f)
        val target = 65.41f // C2
        val buffer = sine(target, rate, 1104)
        val result = detector.detect(buffer, buffer.size)

        assertTrue("C2 应当被检测到", result.hasPitch)
        val error = kotlin.math.abs(centsError(target, result.frequency))
        assertTrue("C2 误差 $error 音分过大", error < 12f)
    }

    @Test
    fun detects_high_c6() {
        val rate = 24000
        val detector = YinPitchDetector(rate, minFrequency = 60f, maxFrequency = 1300f)
        val target = 1046.5f // C6
        val buffer = sine(target, rate, 1104)
        val result = detector.detect(buffer, buffer.size)

        assertTrue("C6 应当被检测到", result.hasPitch)
        val error = kotlin.math.abs(centsError(target, result.frequency))
        assertTrue("C6 误差 $error 音分过大", error < 12f)
    }

    @Test
    fun silence_returns_no_pitch() {
        val rate = 24000
        val detector = YinPitchDetector(rate, minFrequency = 60f, maxFrequency = 1300f)
        val buffer = FloatArray(1104)
        val result = detector.detect(buffer, buffer.size)
        assertTrue("静音不应产生音高", !result.hasPitch)
    }

    @Test
    fun noise_returns_no_pitch() {
        val rate = 24000
        val detector = YinPitchDetector(rate, minFrequency = 60f, maxFrequency = 1300f)
        val random = java.util.Random(7)
        val buffer = FloatArray(1104) { (random.nextFloat() * 2f - 1f) * 0.5f }
        val result = detector.detect(buffer, buffer.size)
        val rejected = !result.hasPitch || result.confidence < 0.62f
        assertTrue("白噪声应被判为无音高或低置信度", rejected)
    }

    @Test
    fun midi_conversion_matches_standard_tuning() {
        assertEquals(440f, Music.midiToFrequency(69f, 440f), 0.01f)
        assertEquals(69f, Music.frequencyToMidi(440f, 440f), 0.001f)
        assertEquals(60f, Music.noteIndex(Music.frequencyToMidi(261.63f, 440f)).toFloat(), 0f)
        assertEquals("C4", Music.noteName(60f))
        assertEquals("A4", Music.noteName(69f))
        assertEquals("C#4", Music.noteName(61f))
        // A4 基准改成 442 之后，440Hz 应该偏低约 8 音分
        val cents = Music.frequencyToMidi(440f, 442f)
        val offset = ((cents - 69f) * 100f).roundToInt()
        assertEquals(-8f, offset.toFloat(), 2f)
    }
}
