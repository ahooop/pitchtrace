package com.pitchtrace.app.core.pitch

import com.pitchtrace.app.core.pitch.Music

/**
 * 显示（也是检测）音域档位。minMidi / maxMidi 同时决定卷帘纵向范围和 YIN 的搜索边界。
 */
enum class PitchRange(
    val label: String,
    val minMidi: Int,
    val maxMidi: Int,
) {
    BASS("低音", 28, 60),      // E1 – C4
    VOICE("人声", 36, 84),     // C2 – C6
    TREBLE("高音", 55, 96),    // G3 – C7
    FULL("全音域", 24, 96);    // C1 – C7

    /** 检测下限略微放宽，避免处于音域边缘的音被判死。 */
    fun minFrequency(a4: Float): Float = Music.midiToFrequency(minMidi.toFloat() - 0.8f, a4)

    /** 检测上限略微放宽，并留出颤音余量。 */
    fun maxFrequency(a4: Float): Float = Music.midiToFrequency(maxMidi.toFloat() + 0.8f, a4)
}
