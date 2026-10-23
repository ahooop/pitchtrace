package com.pitchtrace.app.core.dsp

import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchResult
import com.pitchtrace.app.core.pitch.PitchTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.sin

/**
 * 端到端（采集帧 → 分析 → 跟踪）验证。重点是八度纠错：人声跳到高八度时
 * 不能把轨迹甩到屏幕外。
 */
class PitchAnalyzerTest {

    private fun render(frequency: Float, sampleRate: Int, samples: Int): FloatArray {
        val out = FloatArray(samples)
        val step = 2.0 * PI * frequency / sampleRate
        for (i in 0 until samples) out[i] = (0.5 * sin(step * i)).toFloat()
        return out
    }

    @Test
    fun analyzer_reports_expected_pitch_for_48k_input() {
        val rate = 48000
        val analyzer = PitchAnalyzer(rate, minFrequency = 60f, maxFrequency = 1300f)
        val buffer = render(220f, rate, rate) // 1 秒，足够跑出几十帧
        val results = ArrayList<PitchResult>()

        var offset = 0
        while (offset < buffer.size) {
            val count = minOf(512, buffer.size - offset)
            analyzer.process(buffer.copyOfRange(offset, offset + count), count) { results.add(it) }
            offset += count
        }

        val valid = results.filter { it.hasPitch }
        assertTrue("应当产出若干检测结果", valid.size > 3)
        val median = valid.map { it.frequency }.sorted()[valid.size / 2]
        val error = 1200.0 * log2(median.toDouble() / 220.0)
        assertTrue("220Hz 检测误差 ${error} 音分过大", kotlin.math.abs(error) < 8.0)
        assertTrue("分析率应为采集率的一半", analyzer.analysisRate == rate / 2)
    }

    @Test
    fun tracker_folds_octave_jump_back() {
        val tracker = PitchTracker(a4 = 440f)
        val a3 = 220f
        val a4 = 440f

        val first = tracker.track(PitchResult(a3, 0.95f), 0L)
        assertEquals("A3", Music.noteName(first!!.midi))

        // 真实发声没变，但检测跳到了高八度：应当被折回 A3 附近
        val second = tracker.track(PitchResult(a4, 0.95f), 10L)
        assertTrue("八度误判应被纠正", second!!.midi < 60f)
        assertEquals(57f, second.midi, 2f)
    }

    @Test
    fun tracker_drops_low_confidence() {
        val tracker = PitchTracker(a4 = 440f)
        assertNull(tracker.track(PitchResult(440f, 0.2f), 0L))
        assertNull(tracker.track(PitchResult.NONE, 10L))
    }

    @Test
    fun tracker_smooths_single_frame_spike() {
        val tracker = PitchTracker(a4 = 440f)
        tracker.track(PitchResult(440f, 0.95f), 0L)
        tracker.track(PitchResult(440f, 0.95f), 10L)
        val spiked = tracker.track(PitchResult(466.16f, 0.95f), 20L) // A#4，单帧跳点
        assertTrue("单帧跳点应被中值滤掉", kotlin.math.abs(spiked!!.midi - 69f) < 0.6f)
    }
}
