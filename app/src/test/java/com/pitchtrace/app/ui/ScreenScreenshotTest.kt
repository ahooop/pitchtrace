package com.pitchtrace.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import com.pitchtrace.app.Mode
import com.pitchtrace.app.UiState
import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchRange
import com.pitchtrace.app.core.pitch.PitchSample
import com.pitchtrace.app.core.practice.ExerciseLibrary
import com.pitchtrace.app.core.practice.NoteResult
import com.pitchtrace.app.core.practice.PracticeResult
import com.pitchtrace.app.core.practice.RangeResult
import com.pitchtrace.app.core.practice.RangeSource
import com.pitchtrace.app.core.practice.Verdict
import com.pitchtrace.app.ui.theme.LocalMotionEnabled
import com.pitchtrace.app.ui.theme.PitchTraceTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/**
 * 用 Robolectric 的原生图形模式把界面真正画出来并截图。
 *
 * 这不是设计稿，而是 Compose 按真实布局渲染的像素，用来在没有真机时检查排版、
 * 配色和卷帘几何。产物在 app/build/screenshots/ 下。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenScreenshotTest {

    private fun melody(): List<PitchSample> {
        val notes = listOf(57f, 59f, 60f, 62f, 64f, 65f, 67f, 69f, 67f, 65f, 64f, 62f)
        val samples = ArrayList<PitchSample>(400)
        for (i in 0 until 380) {
            val midi = notes[(i / 32) % notes.size] + (sin(i / 3.0) * 0.18).toFloat()
            samples.add(
                PitchSample(
                    timeMs = i * 11L,
                    midi = midi,
                    frequency = Music.midiToFrequency(midi, 440f),
                    confidence = 0.9f,
                ),
            )
        }
        return samples
    }

    private fun render(name: String, state: UiState) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val composeView = ComposeView(activity).apply {
            setContent {
                PitchTraceTheme {
                    CompositionLocalProvider(LocalMotionEnabled provides false) {
                        PitchTraceScreen(
                            state = state,
                            onMicClick = {},
                            onPlayClick = {},
                            onClearClick = {},
                            onA4Change = {},
                            onRangeChange = {},
                            onDismissError = {},
                            onPracticeStart = {},
                            onPracticeStop = {},
                            onPracticeDismiss = {},
                            onRangeStart = {},
                            onRangeFinish = {},
                            onRangeDismiss = {},
                            onApplyRange = {},
                        )
                    }
                }
            }
        }
        activity.setContentView(composeView)
        controller.start().resume().visible()
        shadowOf(activity.mainLooper).idle()

        val decor = activity.window.decorView
        val widthPx = 411 * activity.resources.displayMetrics.density.toInt()
        val heightPx = 914 * activity.resources.displayMetrics.density.toInt()
        decor.measure(
            MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, widthPx, heightPx)
        shadowOf(activity.mainLooper).idle()
        composeView.measure(
            MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, widthPx, heightPx)
        shadowOf(activity.mainLooper).idle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decor.draw(canvas)

        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun render_idle_screen() {
        render("idle", UiState(mode = Mode.IDLE, range = PitchRange.VOICE))
    }

    @Test
    fun render_live_screen() {
        val samples = melody()
        val clock = samples.last().timeMs
        render(
            name = "live",
            state = UiState(
                mode = Mode.LIVE,
                samples = samples,
                clockMs = clock,
                currentMidi = samples.last().midi,
                currentNote = "A4",
                cents = 4,
                elapsedMs = clock,
                takeMs = clock,
                range = PitchRange.VOICE,
                sampleRate = 48000,
            ),
        )
    }

    @Test
    fun render_practice_screen() {
        val samples = melody()
        val clock = samples.last().timeMs
        val exercise = ExerciseLibrary.scale
        render(
            name = "practice",
            state = UiState(
                mode = Mode.PRACTICE,
                samples = samples,
                clockMs = clock,
                currentMidi = samples.last().midi,
                currentNote = "A4",
                elapsedMs = clock,
                range = PitchRange.VOICE,
                sampleRate = 48000,
                practiceId = exercise.id,
                practiceTitle = exercise.title,
                practiceNotes = exercise.notes,
                practiceDurationMs = exercise.durationMs,
                practiceVerdict = Verdict.HIT,
                practiceCents = 12f,
            ),
        )
    }

    @Test
    fun render_practice_result() {
        val exercise = ExerciseLibrary.scale
        val samples = melody()
        val clock = samples.last().timeMs
        val result = PracticeResult(
            exerciseId = exercise.id,
            offsetMs = 0L,
            detectedVoice = true,
            noteResults = exercise.notes.mapIndexed { index, note ->
                NoteResult(
                    note = note,
                    sungMidi = note.midi + 0.2f,
                    centsOff = 20f,
                    verdict = when {
                        index % 5 == 0 -> Verdict.MISS
                        index % 3 == 0 -> Verdict.CLOSE
                        else -> Verdict.HIT
                    },
                )
            },
            hitRate = 0.67f,
            avgAbsCents = 28f,
        )
        render(
            name = "practice-result",
            state = UiState(
                mode = Mode.IDLE,
                samples = samples,
                clockMs = clock,
                elapsedMs = clock,
                range = PitchRange.VOICE,
                sampleRate = 48000,
                practiceId = exercise.id,
                practiceTitle = exercise.title,
                practiceNotes = exercise.notes,
                practiceDurationMs = exercise.durationMs,
                practiceResult = result,
            ),
        )
    }

    @Test
    fun render_range_screen() {
        val samples = melody()
        val clock = samples.last().timeMs
        render(
            name = "range",
            state = UiState(
                mode = Mode.RANGE,
                samples = samples,
                clockMs = clock,
                currentMidi = samples.last().midi,
                elapsedMs = clock,
                range = PitchRange.VOICE,
                sampleRate = 48000,
                rangeLowMidi = 40f,
                rangeHighMidi = 67f,
                rangeTargetNote = "G4",
                rangeHitFlags = List(49) { index ->
                    when {
                        index < 8 -> index % 4 != 0
                        index < 10 -> false
                        else -> null
                    }
                },
            ),
        )
    }

    @Test
    fun render_range_result() {
        val samples = melody()
        render(
            name = "range-result",
            state = UiState(
                mode = Mode.IDLE,
                samples = samples,
                clockMs = samples.last().timeMs,
                elapsedMs = samples.last().timeMs,
                range = PitchRange.VOICE,
                sampleRate = 48000,
                rangeLowMidi = 41f,
                rangeHighMidi = 67f,
                rangeResult = RangeResult(
                    lowMidi = 41f,
                    highMidi = 67f,
                    lowStableMs = 820L,
                    highStableMs = 640L,
                    source = RangeSource.MIXED,
                ),
            ),
        )
    }
}
