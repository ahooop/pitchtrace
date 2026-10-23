package com.pitchtrace.app.core.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 音高与频率之间的换算工具。全部使用 MIDI 音高（浮点）作为中间表示：
 * 60 = C4，69 = A4。
 */
object Music {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToFrequency(midi: Float, a4: Float = 440f): Float =
        (a4 * 2.0.pow(((midi - 69f) / 12f).toDouble())).toFloat()

    fun frequencyToMidi(frequency: Float, a4: Float = 440f): Float {
        if (frequency <= 0f) return 0f
        return (69f + 12f * ln(frequency / a4) / ln(2f)).toFloat()
    }

    fun noteIndex(midi: Float): Int = midi.roundToInt()

    fun noteName(midi: Float): String {
        val m = midi.roundToInt()
        val octave = Math.floorDiv(m, 12) - 1
        return NOTE_NAMES[Math.floorMod(m, 12)] + octave
    }

    /** 相对最近半音的音分偏差，范围 [-50, 50]。 */
    fun centsOff(frequency: Float, a4: Float = 440f): Int =
        ((frequencyToMidi(frequency, a4) - frequencyToMidi(frequency, a4).roundToInt()) * 100f)
            .toInt()

    fun isBlackKey(midi: Int): Boolean = NOTE_NAMES[Math.floorMod(midi, 12)].length > 1

    fun isNatural(midi: Int): Boolean = !isBlackKey(midi)

    fun pitchClassName(midi: Int): String = NOTE_NAMES[Math.floorMod(midi, 12)]

    fun octaveOf(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** 两个频率是否相差约一个八度（用于八度误判纠正）。 */
    fun isOctaveApart(midiA: Float, midiB: Float): Boolean = abs(abs(midiA - midiB) - 12f) < 1.2f

    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 10)
        val centis = total % 100
        val seconds = (total / 100) % 60
        val minutes = total / 6000
        return java.util.Locale.US.let { locale -> String.format(locale, "%02d:%02d.%02d", minutes, seconds, centis) }
    }
}
