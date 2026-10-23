package com.pitchtrace.app.core.pitch

/**
 * 一帧音高检测结果。[frequency] 为 0 表示这一帧没有可信音高（静音或噪声）。
 */
data class PitchResult(
    val frequency: Float,
    val confidence: Float,
) {
    val hasPitch: Boolean get() = frequency > 0f

    companion object {
        val NONE = PitchResult(0f, 0f)
    }
}

/**
 * 落在时间轴上的一次音高观测，是绘制卷帘的原子数据。
 * [timeMs] 与 [midi] 都是连续量，绘制时不需要再量化。
 */
data class PitchSample(
    val timeMs: Long,
    val midi: Float,
    val frequency: Float,
    val confidence: Float,
)
