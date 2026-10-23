package com.pitchtrace.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * 回放引擎：流式喂 AudioTrack，进度由播放头位置换算，和音高轨迹共用同一条时间轴。
 */
class PlaybackEngine {

    @Volatile
    private var playing = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = playing

    fun play(
        data: WavData,
        onProgress: (Long) -> Unit,
        onFinished: () -> Unit,
    ) {
        stop()
        if (data.samples.isEmpty()) {
            onFinished()
            return
        }
        playing = true

        worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var local: AudioTrack? = null
            try {
                val bytes = data.samples.size * 2
                local = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(data.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minOf(bytes, 16384).coerceAtLeast(4096))
                    .build()
                track = local
                local.play()

                val chunk = ShortArray(2048)
                var index = 0
                val total = data.samples.size
                var lastReport = 0L
                while (playing && index < total) {
                    val n = minOf(chunk.size, total - index)
                    for (i in 0 until n) {
                        chunk[i] = (data.samples[index + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    val written = local.write(chunk, 0, n)
                    if (written <= 0) break
                    index += written
                    val positionMs = index * 1000L / data.sampleRate
                    val now = System.currentTimeMillis()
                    if (positionMs - lastReport >= 60 || now - lastReport >= 60) {
                        lastReport = positionMs
                        onProgress(positionMs)
                    }
                }
                onProgress(index * 1000L / data.sampleRate)
            } catch (t: Throwable) {
                Log.e(TAG, "playback failed", t)
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
        }, "pitchtrace-playback").apply {
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

    private companion object {
        const val TAG = "PlaybackEngine"
    }
}
