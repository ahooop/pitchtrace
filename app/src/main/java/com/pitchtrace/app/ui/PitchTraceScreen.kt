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
import com.pitchtrace.app.ui.theme.Ink
import com.pitchtrace.app.ui.theme.LocalMotionEnabled
import com.pitchtrace.app.ui.theme.MonoNumbers
import java.util.Locale

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
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.samples.isEmpty()) {
                    Text(
                        text = if (state.mode == Mode.LIVE) "正在聆听…" else "按下麦克风，唱一个音",
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
                listening = state.mode == Mode.LIVE,
                canClear = state.hasTake && state.mode == Mode.IDLE,
                onMicClick = onMicClick,
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
