package com.pitchtrace.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

/**
 * AudioRecord 封装：单声道 PCM16，优先 UNPROCESSED 音源。
 *
 * 为什么优先 UNPROCESSED：普通 MIC 音源通常挂着 AGC、降噪、回声消除，这些处理会把
 * 基频“拉平”甚至制造出虚假的 50/60Hz 分量，是音高检测最大的敌人。
 *
 * 但有些厂商机型声明支持 UNPROCESSED，实际却喂回全零数据，所以这里加了一个
 * “连续全零帧”兜底：一旦发现，就地切换回普通 MIC，用户不会看到中断。
 */
class AudioCapture(
    private val onFrame: (FloatArray, Int) -> Unit,
    private val onError: (String) -> Unit,
) {

    @Volatile
    var sampleRate: Int = 0
        private set

    @Volatile
    private var running = false

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var source: Int = MediaRecorder.AudioSource.MIC

    private var thread: Thread? = null
    private var bufferBytes = 0

    private val shortBuffer = ShortArray(2048)
    private val floatBuffer = FloatArray(2048)

    private var zeroStreak = 0
    private var switchedToMic = false

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        val candidates = intArrayOf(48000, 44100)
        for (rate in candidates) {
            val minBytes = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBytes <= 0) {
                Log.w(TAG, "unsupported sample rate $rate (minBuffer=$minBytes)")
                continue
            }
            val bytes = max(minBytes, rate / 5 * 2) // ≈ 200ms，抗抖动
            val preferred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.MIC
            }
            var actualSource = preferred
            var record = openRecord(rate, bytes, preferred)
            if (record == null && preferred != MediaRecorder.AudioSource.MIC) {
                actualSource = MediaRecorder.AudioSource.MIC
                record = openRecord(rate, bytes, MediaRecorder.AudioSource.MIC)
            }
            if (record == null) continue

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            try {
                record.startRecording()
            } catch (t: Throwable) {
                Log.w(TAG, "startRecording failed at $rate", t)
                record.release()
                continue
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                continue
            }

            recorder = record
            sampleRate = rate
            bufferBytes = bytes
            source = actualSource
            switchedToMic = actualSource == MediaRecorder.AudioSource.MIC
            zeroStreak = 0
            running = true
            thread = Thread({ readLoop() }, "pitchtrace-capture").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            Log.i(TAG, "capture started at $rate Hz, source=$actualSource")
            return true
        }

        onError("无法打开麦克风，可能被其他应用占用")
        return false
    }

    fun stop() {
        running = false
        thread?.let { worker ->
            try {
                worker.join(500)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
        recorder?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "stop failed", t)
            }
            record.release()
        }
        recorder = null
        sampleRate = 0
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(rate: Int, bytes: Int, audioSource: Int): AudioRecord? {
        return try {
            val record = AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bytes)
                .build()
            if (record.state == AudioRecord.STATE_INITIALIZED) record else {
                record.release()
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "openRecord source=$audioSource rate=$rate failed", t)
            null
        }
    }

    private fun readLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (running) {
            val record = recorder ?: break
            val read = try {
                record.read(shortBuffer, 0, shortBuffer.size)
            } catch (t: Throwable) {
                Log.e(TAG, "read failed", t)
                -1
            }
            if (read <= 0) {
                if (read < 0) break
                continue
            }

            var peak = 0
            for (i in 0 until read) {
                val value = shortBuffer[i].toInt()
                if (abs(value) > peak) peak = abs(value)
                floatBuffer[i] = value / 32768f
            }

            if (peak == 0) {
                zeroStreak++
                if (zeroStreak == 60 && !switchedToMic) {
                    Log.w(TAG, "UNPROCESSED 音源连续返回全零，切换到 MIC")
                    switchToMic()
                }
            } else {
                zeroStreak = 0
            }

            onFrame(floatBuffer, read)
        }
    }

    @SuppressLint("MissingPermission")
    private fun switchToMic() {
        val current = recorder ?: return
        val fallback = openRecord(sampleRate, bufferBytes, MediaRecorder.AudioSource.MIC) ?: return
        try {
            fallback.startRecording()
            if (fallback.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                fallback.release()
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "switchToMic start failed", t)
            fallback.release()
            return
        }
        recorder = fallback
        source = MediaRecorder.AudioSource.MIC
        switchedToMic = true
        zeroStreak = 0
        try {
            if (current.recordingState == AudioRecord.RECORDSTATE_RECORDING) current.stop()
        } catch (ignored: Throwable) {
        }
        current.release()
    }

    private companion object {
        const val TAG = "AudioCapture"
    }
}
