package com.pitchtrace.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Ink {
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF14171C)
    val SurfaceHigh = Color(0xFF1C2026)
    val Grid = Color(0xFF23272E)
    val GridStrong = Color(0xFF31363F)
    val Trace = Color(0xFFE3A94E)
    val TraceGlow = Color(0x40E3A94E)
    val Accent = Color(0xFF4DBE8E)
    val Text = Color(0xFFEDEFF2)
    val TextDim = Color(0xFF868D97)
    val Danger = Color(0xFFE0765F)
}

private val scheme = darkColorScheme(
    primary = Ink.Trace,
    onPrimary = Color(0xFF261A06),
    secondary = Ink.Accent,
    onSecondary = Color(0xFF04160E),
    background = Ink.Background,
    onBackground = Ink.Text,
    surface = Ink.Surface,
    onSurface = Ink.Text,
    surfaceVariant = Ink.SurfaceHigh,
    onSurfaceVariant = Ink.TextDim,
    outline = Ink.GridStrong,
    error = Ink.Danger,
)

val MonoNumbers = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
)

@Composable
fun PitchTraceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
