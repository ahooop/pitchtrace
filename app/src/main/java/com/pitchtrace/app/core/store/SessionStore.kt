package com.pitchtrace.app.core.store

import android.content.Context
import com.pitchtrace.app.core.pitch.PitchSample
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 录音会话的落盘：一条 WAV + 一条音高轨迹。
 * 音高轨迹单独存盘，回放时就不用重新跑分析，省电且结果和录制时完全一致。
 */
class SessionStore(context: Context) {

    private val dir: File = File(context.filesDir, "takes").apply { mkdirs() }

    val wavFile: File get() = File(dir, "take.wav")
    private val trackFile: File get() = File(dir, "take.pitch")

    fun saveTrack(samples: List<PitchSample>): Boolean = try {
        DataOutputStream(BufferedOutputStream(FileOutputStream(trackFile))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(samples.size)
            for (sample in samples) {
                out.writeLong(sample.timeMs)
                out.writeFloat(sample.midi)
                out.writeFloat(sample.frequency)
                out.writeFloat(sample.confidence)
            }
        }
        true
    } catch (t: Throwable) {
        false
    }

    fun loadTrack(): List<PitchSample> {
        if (!trackFile.exists()) return emptyList()
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(trackFile))).use { input ->
                if (input.readInt() != MAGIC) return emptyList()
                if (input.readInt() != VERSION) return emptyList()
                val count = input.readInt()
                if (count <= 0 || count > 5_000_000) return emptyList()
                val list = ArrayList<PitchSample>(count)
                repeat(count) {
                    list.add(
                        PitchSample(
                            timeMs = input.readLong(),
                            midi = input.readFloat(),
                            frequency = input.readFloat(),
                            confidence = input.readFloat(),
                        ),
                    )
                }
                list
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun hasTake(): Boolean = wavFile.exists() && wavFile.length() > 44

    fun clear() {
        runCatching { trackFile.delete() }
        runCatching { wavFile.delete() }
    }

    private companion object {
        const val MAGIC = 0x50544348 // "PTCH"
        const val VERSION = 1
    }
}
