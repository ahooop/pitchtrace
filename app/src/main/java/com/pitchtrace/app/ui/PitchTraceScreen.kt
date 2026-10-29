package com.pitchtrace.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchtrace.app.Mode
import com.pitchtrace.app.R
import com.pitchtrace.app.UiState
import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchRange
import com.pitchtrace.app.core.practice.Exercise
import com.pitchtrace.app.core.practice.ExerciseLibrary
import com.pitchtrace.app.core.practice.PracticeResult
import com.pitchtrace.app.core.practice.RangeResult
import com.pitchtrace.app.core.practice.RangeSource
import com.pitchtrace.app.core.practice.Verdict
import com.pitchtrace.app.ui.theme.Ink
import com.pitchtrace.app.ui.theme.LocalMotionEnabled
import com.pitchtrace.app.ui.theme.MonoNumbers
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitchTraceScreen(
    state: UiState,
    onMicClick: () -> Unit,
    onPlayClick: () -> Unit,
    onClearClick: () -> Unit,
    onA4Change: (Float) -> Unit,
    onRangeChange: (PitchRange) -> Unit,
    onDismissError: () -> Unit,
    onPracticeStart: (Exercise) -> Unit,
    onPracticeStop: () -> Unit,
    onPracticeDismiss: () -> Unit,
    onRangeStart: (Boolean) -> Unit,
    onRangeFinish: () -> Unit,
    onRangeDismiss: () -> Unit,
    onApplyRange: (PitchRange) -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var practiceOpen by rememberSaveable { mutableStateOf(false) }
    var rangeOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val practicing = state.mode == Mode.PRACTICE
    val ranging = state.mode == Mode.RANGE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(
                a4 = state.a4,
                onA4Click = { settingsOpen = true },
                onSettingsClick = { settingsOpen = true },
            )

            ModeSwitch(
                practice = practiceOpen || practicing,
                range = rangeOpen || ranging,
                onFree = {
                    if (practicing) onPracticeStop()
                    if (ranging) onRangeFinish()
                    practiceOpen = false
                    rangeOpen = false
                },
                onPractice = {
                    practiceOpen = true
                    rangeOpen = false
                },
                onRange = {
                    practiceOpen = false
                    rangeOpen = true
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                PianoRoll(
                    samples = state.samples,
                    range = state.range,
                    clockMs = state.clockMs,
                    windowMs = state.windowMs,
                    currentMidi = state.currentMidi,
                    currentCents = if (state.mode == Mode.LIVE) state.cents else null,
                    referenceNotes = if (practiceOpen || state.mode == Mode.PRACTICE) state.practiceNotes else emptyList(),
                    rangeMarks = if (rangeOpen || state.mode == Mode.RANGE) {
                        listOf(state.rangeLowMidi, state.rangeHighMidi)
                    } else {
                        emptyList()
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.samples.isEmpty() && state.practiceResult == null) {
                    Text(
                        text = when {
                            state.mode == Mode.LIVE -> "正在聆听…"
                            state.mode == Mode.RANGE -> "唱到你能到的最低和最高音"
                            practiceOpen -> "选择一项练习，戴上耳机"
                            rangeOpen -> "先自由唱，或跟着半音阶测极限"
                            else -> "按下麦克风，唱一个音"
                        },
                        color = Ink.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Ink.Surface.copy(alpha = 0.85f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                if (state.mode == Mode.LIVE) {
                    LiveBadge(
                        elapsedMs = state.elapsedMs,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 10.dp, start = 56.dp),
                    )
                }

                if (practicing) {
                    PracticeHud(
                        verdict = state.practiceVerdict,
                        cents = state.practiceCents,
                        elapsedMs = state.elapsedMs,
                        durationMs = state.practiceDurationMs,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 56.dp),
                    )
                }

                if (practiceOpen && !practicing && state.practiceResult == null) {
                    PracticePicker(
                        onStart = onPracticeStart,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                    )
                }

                state.practiceResult?.let { result ->
                    PracticeResultCard(
                        result = result,
                        onDismiss = onPracticeDismiss,
                        onRetry = {
                            state.practiceId?.let { ExerciseLibrary.byId(it) }?.let(onPracticeStart)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                if (rangeOpen && !ranging && state.rangeResult == null) {
                    RangePanel(
                        lowMidi = state.rangeLowMidi,
                        highMidi = state.rangeHighMidi,
                        onFree = { onRangeStart(false) },
                        onGuided = { onRangeStart(true) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 22.dp),
                    )
                }

                if (ranging) {
                    RangeHud(
                        lowMidi = state.rangeLowMidi,
                        highMidi = state.rangeHighMidi,
                        elapsedMs = state.elapsedMs,
                        targetNote = state.rangeTargetNote,
                        onFinish = onRangeFinish,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 56.dp),
                    )
                }

                if (ranging && state.rangeHitFlags.isNotEmpty()) {
                    RangeHitStrip(
                        flags = state.rangeHitFlags,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    )
                }

                state.rangeResult?.let { result ->
                    RangeResultCard(
                        result = result,
                        onDismiss = onRangeDismiss,
                        onApply = onApplyRange,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                if (state.error != null) {
                    ErrorBanner(
                        message = state.error,
                        onDismiss = onDismissError,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    )
                }
            }

            TransportBar(
                state = state,
                onPlayClick = onPlayClick,
            )

            BottomBar(
                listening = state.mode == Mode.LIVE || practicing || ranging,
                canClear = state.hasTake && state.mode == Mode.IDLE,
                onMicClick = {
                    when {
                        ranging -> onRangeFinish()
                        practicing -> onPracticeStop()
                        else -> onMicClick()
                    }
                },
                onClearClick = onClearClick,
                onSettingsClick = { settingsOpen = true },
            )
        }

        if (settingsOpen) {
            ModalBottomSheet(
                onDismissRequest = { settingsOpen = false },
                sheetState = sheetState,
                containerColor = Ink.Surface,
                contentColor = Ink.Text,
            ) {
                SettingsSheet(
                    a4 = state.a4,
                    range = state.range,
                    onA4Change = onA4Change,
                    onRangeChange = onRangeChange,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    a4: Float,
    onA4Click: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark()
        Spacer(Modifier.width(10.dp))
        Column {
            Text("音迹", color = Ink.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("实时音高轨迹", color = Ink.TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onA4Click() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("A4", color = Ink.TextDim, fontSize = 9.sp)
            Text(
                text = String.format(Locale.US, "%.0f", a4),
                color = Ink.Trace,
                style = MonoNumbers.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "设置",
            tint = Ink.TextDim,
            modifier = Modifier
                .size(22.dp)
                .clickable { onSettingsClick() },
        )
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val cx = size.width / 2f
            drawLine(Ink.GridStrong, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.55f), 1f)
            drawLine(Ink.Trace, Offset(cx, size.height * 0.15f), Offset(cx, size.height * 0.9f), 2f, StrokeCap.Round)
            drawCircle(Ink.Trace, radius = size.width * 0.11f, center = Offset(cx, size.height * 0.18f))
        }
    }
}

@Composable
private fun LiveBadge(elapsedMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.SurfaceHigh.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Ink.Danger),
        )
        Spacer(Modifier.width(5.dp))
        Text(Music.formatTime(elapsedMs), color = Ink.TextDim, style = MonoNumbers.copy(fontSize = 10.sp))
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Danger.copy(alpha = 0.16f))
            .clickable { onDismiss() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Ink.Danger, fontSize = 11.sp)
    }
}

@Composable
private fun TransportBar(state: UiState, onPlayClick: () -> Unit) {
    val positionMs = if (state.mode == Mode.LIVE) state.elapsedMs else state.playMs
    val totalMs = if (state.mode == Mode.LIVE) state.elapsedMs else state.takeMs
    val progress = when {
        state.mode == Mode.LIVE -> 0f
        totalMs > 0 -> (positionMs.toFloat() / totalMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayButton(
                enabled = state.hasTake && state.mode != Mode.LIVE,
                playing = state.mode == Mode.PLAYBACK,
                onClick = onPlayClick,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${Music.formatTime(positionMs)} / ${Music.formatTime(totalMs)}",
                color = if (state.hasTake || state.mode == Mode.LIVE) Ink.TextDim else Ink.TextDim.copy(alpha = 0.45f),
                style = MonoNumbers,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.sampleRate > 0) "${state.sampleRate / 1000} kHz" else "—",
                color = Ink.TextDim.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Ink.Grid),
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(Ink.Trace.copy(alpha = 0.65f)),
                )
            }
        }
    }
}

@Composable
private fun PlayButton(enabled: Boolean, playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(1.dp, if (enabled) Ink.GridStrong else Ink.Grid, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (playing) "暂停" else "回放",
            tint = if (enabled) Ink.Text else Ink.TextDim.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun BottomBar(
    listening: Boolean,
    canClear: Boolean,
    onMicClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = R.drawable.ic_eq,
            description = "清空轨迹",
            enabled = canClear,
            onClick = onClearClick,
        )
        MicButton(listening = listening, onClick = onMicClick)
        IconAction(
            icon = R.drawable.ic_tune,
            description = "设置",
            enabled = true,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun IconAction(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) Ink.TextDim else Ink.TextDim.copy(alpha = 0.25f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        // 只有采集中才需要呼吸圈：空闲时不起动画，省电也避免无谓的帧
        if (listening) MicPulseRing()
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (listening) Ink.Accent.copy(alpha = 0.12f) else Ink.SurfaceHigh)
                .border(1.5.dp, if (listening) Ink.Accent else Ink.GridStrong, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = if (listening) "停止采集" else "开始采集",
                tint = if (listening) Ink.Accent else Ink.Text,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun MicPulseRing() {
    if (!LocalMotionEnabled.current) return
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "mic-pulse-value",
    )
    Canvas(Modifier.size(88.dp)) {
        val radius = size.minDimension / 2f * (0.52f + 0.46f * pulse)
        drawCircle(color = Ink.Accent.copy(alpha = 0.20f * (1f - pulse)), radius = radius)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(
    a4: Float,
    range: PitchRange,
    onA4Change: (Float) -> Unit,
    onRangeChange: (PitchRange) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp),
    ) {
        Text("校准", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("A4 基准音高决定所有音名的换算。", color = Ink.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A4", color = Ink.TextDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%.0f Hz", a4),
                color = Ink.Trace,
                style = MonoNumbers.copy(fontSize = 13.sp),
            )
        }
        Slider(
            value = a4,
            onValueChange = onA4Change,
            valueRange = 415f..466f,
            steps = 50,
        )

        Spacer(Modifier.height(14.dp))
        Text("显示音域", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PitchRange.entries.forEach { item ->
                FilterChip(
                    selected = item == range,
                    onClick = { onRangeChange(item) },
                    label = { Text(item.label, fontSize = 12.sp) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "检测算法为 YIN，分析窗约 40ms，10ms 输出一次。人声建议用「人声」档，C2–C6。",
            color = Ink.TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ModeSwitch(
    practice: Boolean,
    range: Boolean,
    onFree: () -> Unit,
    onPractice: () -> Unit,
    onRange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeTab(label = "自由聆听", selected = !practice && !range, onClick = onFree)
        Spacer(Modifier.width(6.dp))
        ModeTab(label = "练习", selected = practice, onClick = onPractice)
        Spacer(Modifier.width(6.dp))
        ModeTab(label = "音域", selected = range, onClick = onRange)
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Ink.Text else Ink.TextDim,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Ink.SurfaceHigh else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun RangePanel(
    lowMidi: Float,
    highMidi: Float,
    onFree: () -> Unit,
    onGuided: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface.copy(alpha = 0.96f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("音域测试", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text("稳定保持 0.3 秒以上才算数", color = Ink.TextDim, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RangeReadout(title = "最低", midi = lowMidi)
            Spacer(Modifier.width(20.dp))
            RangeReadout(title = "最高", midi = highMidi)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            RangeButton(text = "自由测", onClick = onFree)
            Spacer(Modifier.width(8.dp))
            RangeButton(text = "引导测", onClick = onGuided)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "引导测会逐半音播放参考音，跟着唱到极限",
            color = Ink.TextDim.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun RangeReadout(title: String, midi: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Ink.TextDim, fontSize = 9.sp)
        Text(
            text = if (midi.isNaN()) "—" else Music.noteName(midi),
            color = if (midi.isNaN()) Ink.TextDim else Ink.Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            style = MonoNumbers,
        )
    }
}

@Composable
private fun RangeButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Ink.Text,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Ink.SurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun RangeHud(
    lowMidi: Float,
    highMidi: Float,
    elapsedMs: Long,
    targetNote: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Surface.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "低 ${if (lowMidi.isNaN()) "—" else Music.noteName(lowMidi)} · " +
                "高 ${if (highMidi.isNaN()) "—" else Music.noteName(highMidi)}",
            color = Ink.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (targetNote.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("跟唱 $targetNote", color = Ink.TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Music.formatTime(elapsedMs),
                color = Ink.TextDim.copy(alpha = 0.8f),
                style = MonoNumbers.copy(fontSize = 9.sp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "结束",
                color = Ink.Trace,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onFinish() },
            )
        }
    }
}

@Composable
private fun RangeResultCard(
    result: RangeResult,
    onDismiss: () -> Unit,
    onApply: (PitchRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface.copy(alpha = 0.97f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("你的音域", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = "关闭",
                color = Ink.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onDismiss() },
            )
        }

        if (!result.hasRange) {
            Spacer(Modifier.height(6.dp))
            Text("没有检测到稳定的音，再试一次", color = Ink.Danger, fontSize = 11.sp)
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    text = result.lowNote,
                    color = Ink.Accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = freqText(result.lowMidi),
                    color = Ink.TextDim,
                    style = MonoNumbers.copy(fontSize = 9.sp),
                )
            }
            Text(" — ", color = Ink.TextDim, fontSize = 14.sp)
            Column {
                Text(
                    text = result.highNote,
                    color = Ink.Accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = freqText(result.highMidi),
                    color = Ink.TextDim,
                    style = MonoNumbers.copy(fontSize = 9.sp),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.spanSemitones.roundToInt()} 个半音",
                    color = Ink.Text,
                    fontSize = 12.sp,
                )
                Text(sourceLabel(result.source), color = Ink.TextDim, fontSize = 9.sp)
            }
        }

        if (result.lowLimitedByMic) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "最低音可能受手机麦克风限制，实际音域可能更低",
                color = Ink.Danger,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "推荐档位：${result.recommendedRange().label}",
                color = Ink.TextDim,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "用这个档位显示",
                color = Ink.Trace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.Trace.copy(alpha = 0.12f))
                    .clickable { onApply(result.recommendedRange()) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RangeHitStrip(flags: List<Boolean?>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.Surface.copy(alpha = 0.88f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        flags.forEach { flag ->
            val color = when (flag) {
                true -> Ink.Accent
                false -> Ink.Danger
                null -> Ink.GridStrong
            }
            Box(
                Modifier
                    .width(3.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

private fun freqText(midi: Float): String =
    if (midi.isNaN()) "—" else String.format(Locale.US, "%.1f Hz", Music.midiToFrequency(midi))

private fun sourceLabel(source: RangeSource): String = when (source) {
    RangeSource.FREE -> "自由测"
    RangeSource.GUIDED -> "引导测"
    RangeSource.MIXED -> "自由 + 引导"
}

@Composable
private fun PracticeHud(
    verdict: Verdict?,
    cents: Float,
    elapsedMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val tint = verdictColor(verdict)
    val label = when (verdict) {
        Verdict.HIT -> "准"
        Verdict.CLOSE -> "微偏"
        Verdict.MISS -> "跑调"
        else -> "跟唱"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Surface.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(Modifier.width(5.dp))
            Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (verdict != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = String.format(Locale.US, "%+.0f¢", cents),
                    color = Ink.TextDim,
                    style = MonoNumbers.copy(fontSize = 11.sp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${Music.formatTime(elapsedMs)} / ${Music.formatTime(durationMs)}",
            color = Ink.TextDim.copy(alpha = 0.8f),
            style = MonoNumbers.copy(fontSize = 9.sp),
        )
    }
}

@Composable
private fun PracticePicker(onStart: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface.copy(alpha = 0.96f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("练习模式", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text("参考音走耳机，麦克风只收你的声音", color = Ink.TextDim, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
        ExerciseLibrary.all.forEach { exercise ->
            Text(
                text = "${exercise.title} · ${Music.noteName(exercise.notes.first().midi)}–" +
                    Music.noteName(exercise.notes.maxOf { it.midi }),
                color = Ink.Text,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Ink.SurfaceHigh)
                    .clickable { onStart(exercise) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeResultCard(
    result: PracticeResult,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface.copy(alpha = 0.97f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "准确率 ${(result.hitRate * 100).roundToInt()}%",
                color = Ink.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "平均偏差 ${result.avgAbsCents.roundToInt()}¢",
                color = Ink.TextDim,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "关闭",
                color = Ink.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onDismiss() },
            )
        }

        if (!result.detectedVoice) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "没有检测到人声，检查耳机和麦克风后再试一次",
                color = Ink.Danger,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            result.noteResults.forEach { note ->
                val tint = verdictColor(note.verdict)
                Text(
                    text = Music.noteName(note.note.midi),
                    color = tint,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "再练一次",
            color = Ink.Trace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Ink.Trace.copy(alpha = 0.12f))
                .clickable { onRetry() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

private fun verdictColor(verdict: Verdict?): Color = when (verdict) {
    Verdict.HIT -> Ink.Accent
    Verdict.CLOSE -> Ink.Trace
    Verdict.MISS -> Ink.Danger
    else -> Ink.TextDim
}
