package com.pitchtrace.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchRange
import com.pitchtrace.app.core.pitch.PitchSample
import com.pitchtrace.app.core.practice.Note
import com.pitchtrace.app.ui.theme.Ink

/**
 * 钢琴卷帘：横轴时间、纵轴 MIDI 音高，当前时刻固定在偏右的竖线上，轨迹向左滚动。
 *
 * 绘制顺序从底到顶：黑键行底色 → 半音网格 → 音名标注 → 秒线 → 轨迹光晕 → 轨迹 → 光标。
 * 音名标注在进入绘制前就排版好并缓存，避免每帧重排几十个文字。
 */
@Composable
fun PianoRoll(
    samples: List<PitchSample>,
    range: PitchRange,
    clockMs: Long,
    windowMs: Long,
    currentMidi: Float?,
    modifier: Modifier = Modifier,
    currentCents: Int? = null,
    nowFraction: Float = 0.58f,
    referenceNotes: List<Note> = emptyList(),
    rangeMarks: List<Float> = emptyList(),
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    val gutter = with(density) { 40.dp.toPx() }
    val labelStyle = remember {
        TextStyle(color = Ink.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
    val noteStyle = remember {
        TextStyle(color = Ink.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    val labelLayouts = remember(range, textMeasurer, labelStyle) {
        (range.minMidi..range.maxMidi)
            .filter { Music.isNatural(it) }
            .associateWith { midi ->
                textMeasurer.measure(AnnotatedString(Music.noteName(midi.toFloat())), labelStyle)
            }
    }

    Canvas(modifier = modifier) {
        if (size.width <= gutter * 2f || size.height <= 0f) return@Canvas

        val left = gutter
        val right = size.width - gutter
        val bottom = size.height
        val midiSpan = (range.maxMidi - range.minMidi).toFloat()
        val semitone = bottom / midiSpan
        val nowX = left + (right - left) * nowFraction
        val pxPerMs = (right - left) / windowMs.toFloat()

        fun yOf(midi: Float): Float = (range.maxMidi - midi) * semitone
        fun xOf(time: Long): Float = nowX - (clockMs - time) * pxPerMs

        // 黑键行底色，让半音结构一眼可辨（保持很轻，避免变成斑马纹）
        for (m in range.minMidi until range.maxMidi) {
            if (Music.isBlackKey(m + 1)) {
                drawRect(
                    color = Color(0x0BFFFFFF),
                    topLeft = Offset(left, yOf((m + 1).toFloat())),
                    size = Size(right - left, semitone),
                )
            }
        }

        // 半音网格
        for (m in range.minMidi..range.maxMidi) {
            val y = yOf(m.toFloat())
            drawLine(
                color = if (Music.isNatural(m)) Ink.GridStrong else Ink.Grid,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f,
            )
        }

        // 音名标注（左右两侧对称）
        val labelGap = 6.dp.toPx()
        for ((midi, layout) in labelLayouts) {
            val y = yOf(midi.toFloat())
            val top = y - layout.size.height / 2f
            if (top + layout.size.height < 0f || top > bottom) continue
            drawText(layout, topLeft = Offset(labelGap, top))
            drawText(layout, topLeft = Offset(right + labelGap, top))
        }

        // 秒线
        var second = (clockMs / 1000L) * 1000L
        while (second > clockMs - windowMs - 1000L) {
            val x = xOf(second)
            if (x in left..right) drawDashedVertical(x, 0f, bottom, Ink.Grid)
            second -= 1000L
        }

        // 音域标记：测到的最高/最低音各画一条水平线
        for (mark in rangeMarks) {
            if (mark.isNaN()) continue
            val y = yOf(mark).coerceIn(0f, bottom)
            drawLine(
                color = Ink.Accent.copy(alpha = 0.65f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.2f,
            )
            drawLine(
                color = Ink.Accent.copy(alpha = 0.18f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 5f,
            )
        }

        // 参考轨：练习模式下把目标音画成半透明灰条，人声轨迹叠在上面
        for (note in referenceNotes) {
            val x0 = xOf(note.startMs)
            val x1 = xOf(note.endMs)
            if (x1 < left || x0 > right) continue
            val from = x0.coerceAtLeast(left)
            val to = x1.coerceAtMost(right)
            if (to <= from) continue
            val barTop = yOf(note.midi) - semitone * 0.34f
            val barHeight = semitone * 0.68f
            drawRoundRect(
                color = Ink.TextDim.copy(alpha = 0.16f),
                topLeft = Offset(from, barTop),
                size = Size(to - from, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = Ink.TextDim.copy(alpha = 0.45f),
                topLeft = Offset(from, barTop),
                size = Size(to - from, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1f),
            )
        }

        // 轨迹：时间间隔过大就断开，噪声段自然成缺口
        val path = Path()
        var started = false
        var lastTime = 0L
        for (sample in samples) {
            if (sample.timeMs > clockMs + 30L) break
            val x = xOf(sample.timeMs)
            if (x < left - 8f) {
                started = false
                continue
            }
            val y = yOf(sample.midi).coerceIn(0f, bottom)
            if (!started || sample.timeMs - lastTime > 130L) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
            lastTime = sample.timeMs
        }
        if (started) {
            drawPath(
                path = path,
                color = Ink.TraceGlow,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = path,
                color = Ink.Trace,
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // 当前时刻竖线
        drawLine(
            color = Ink.Trace.copy(alpha = 0.35f),
            start = Offset(nowX, 0f),
            end = Offset(nowX, bottom),
            strokeWidth = 1.2f,
        )

        // 实时音高光标
        if (currentMidi != null && currentMidi.isFinite()) {
            val y = yOf(currentMidi).coerceIn(0f, bottom)
            drawCircle(Ink.Trace.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = Offset(nowX, y))
            drawCircle(Ink.Trace, radius = 4.5.dp.toPx(), center = Offset(nowX, y))
            drawNoteBadge(
                measurer = textMeasurer,
                style = noteStyle,
                text = buildString {
                    append(Music.noteName(currentMidi))
                    if (currentCents != null) {
                        append(' ')
                        if (currentCents > 0) append('+')
                        append(currentCents)
                        append("¢")
                    }
                },
                anchorX = nowX + 14.dp.toPx(),
                anchorY = y,
                rightLimit = right,
            )
        }

        // 两侧留白分隔线
        drawLine(Ink.GridStrong, Offset(left, 0f), Offset(left, bottom), 1f)
        drawLine(Ink.GridStrong, Offset(right, 0f), Offset(right, bottom), 1f)
    }
}

private fun DrawScope.drawNoteBadge(
    measurer: TextMeasurer,
    style: TextStyle,
    text: String,
    anchorX: Float,
    anchorY: Float,
    rightLimit: Float,
) {
    val layout = measurer.measure(AnnotatedString(text), style)
    val pad = 4.dp.toPx()
    val x = anchorX.coerceAtMost(rightLimit - layout.size.width - pad)
    val y = anchorY - layout.size.height / 2f
    drawRoundRect(
        color = Ink.SurfaceHigh,
        topLeft = Offset(x - pad, y - pad / 2f),
        size = Size(layout.size.width + pad * 2f, layout.size.height + pad),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(layout, topLeft = Offset(x, y))
}

private fun DrawScope.drawDashedVertical(
    x: Float,
    top: Float,
    bottom: Float,
    color: Color,
) {
    val dash = 5.dp.toPx()
    var y = top
    while (y < bottom) {
        drawLine(color, Offset(x, y), Offset(x, (y + dash).coerceAtMost(bottom)), 1f)
        y += dash * 2f
    }
}
