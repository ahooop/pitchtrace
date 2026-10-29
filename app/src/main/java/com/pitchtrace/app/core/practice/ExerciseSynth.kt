package com.pitchtrace.app.core.practice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.pitchtrace.app.core.pitch.Music
import kotlin.math.PI
import kotlin.math.sin

/**
 * 参考音播放器：把 [Exercise] 的音符序列合成成 PCM，交给 AudioTrack 播放。
 *
 * 用正弦波加一点点二次谐波就够了——音色越干净，越不会被麦克风误当成
 * 「另一个音高」，用户也更容易跟准。进度用 `playbackHeadPosition` 换算成毫秒，
 * 而不是写入量，避免缓冲带来的系统性超前。
 */
class ExerciseSynth {

    @Volatile
    private var playing = false

    private var worker: Thread? = null
    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = playing

    fun play(
        exercise: Exercise,
        onProgress: (Long) -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        stop()
        if (exercise.notes.isEmpty()) {
            onFinished()
            return
        }
        playing = true

        worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var local: AudioTrack? = null
            try {
                val pcm = render(exercise, SAMPLE_RATE)
                val bytes = pcm.size * 2
                local = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minOf(bytes, 8192).coerceAtLeast(4096))
                    .build()
                track = local
                local.play()

                var written = 0
                val chunk = 1024
                while (playing && written < pcm.size) {
                    val n = minOf(chunk, pcm.size - written)
                    val ok = local.write(pcm, written, n)
                    if (ok <= 0) break
                    written += ok
                    onProgress(headMs(local))
                }
                // 写入完成不等于播完，等播放头走到练习结尾。
                while (playing) {
                    val ms = headMs(local)
                    onProgress(ms)
                    if (ms >= exercise.durationMs) break
                    Thread.sleep(20)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "reference playback failed", t)
            } finally {
                try {
                    local?.stop()
                } catch (ignored: Throwable) {
                }
                try {
                    local?.release()
                } catch (ignored: Throwable) {
                }
                track = null
                playing = false
                onFinished()
            }
        }, "pitchtrace-reference").apply {
            priority = Thread.NORM_PRIORITY + 2
            start()
        }
    }

    fun stop() {
        playing = false
        track?.let { audioTrack ->
            try {
                audioTrack.pause()
                audioTrack.flush()
            } catch (ignored: Throwable) {
            }
        }
        worker?.let { thread ->
            try {
                thread.join(400)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        worker = null
    }

    private fun headMs(audioTrack: AudioTrack): Long =
        audioTrack.playbackHeadPosition.toLong() * 1000L / SAMPLE_RATE

    companion object {
        const val SAMPLE_RATE = 44100
        private const val TAG = "ExerciseSynth"
        private const val TAIL_MS = 400L
        private const val AMPLITUDE = 0.28f

        /** 合成整段参考音；纯函数，方便单独测长度。 */
        fun render(exercise: Exercise, sampleRate: Int = SAMPLE_RATE): ShortArray {
            val totalMs = exercise.durationMs + TAIL_MS
            val total = (totalMs * sampleRate / 1000L).toInt()
            val out = ShortArray(total)
            for (note in exercise.notes) {
                val frequency = Music.midiToFrequency(note.midi)
                val start = (note.startMs * sampleRate / 1000L).toInt()
                val length = (note.durationMs * sampleRate / 1000L).toInt()
                val attack = (0.02 * sampleRate).toInt().coerceAtLeast(1)
                val release = (0.08 * sampleRate).toInt().coerceAtLeast(1)
                val omega = 2.0 * PI * frequency / sampleRate
                for (i in 0 until length) {
                    val index = start + i
                    if (index >= total) break
                    val envelope = when {
                        i < attack -> i.toFloat() / attack
                        length - i < release -> (length - i).toFloat() / release
                        else -> 1f
                    }
                    val wave = sin(omega * i) * 0.82 + sin(omega * 2.0 * i) * 0.18
                    out[index] = (wave * envelope * AMPLITUDE * 32767f).toInt().toShort()
                }
            }
            return out
        }
    }
}
