package com.pitchtrace.app.core.practice

/**
 * 练习里的一个目标音。时间以练习开始为 0，单位毫秒。
 */
data class Note(
    val midi: Float,
    val startMs: Long,
    val durationMs: Long,
) {
    val endMs: Long get() = startMs + durationMs

    fun contains(timeMs: Long): Boolean = timeMs >= startMs && timeMs < endMs
}

/**
 * 一个练习：一组按时间排好的目标音。
 * [durationMs] 是最后一个音的结束时间（不含尾部间隙）。
 */
data class Exercise(
    val id: String,
    val title: String,
    val notes: List<Note>,
) {
    val durationMs: Long get() = notes.lastOrNull()?.endMs ?: 0L
}
