package com.pitchtrace.app.core.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * 极简 WAV 写入器：先占位 44 字节头，边录边写 PCM16，关闭时回填长度。
 * 之所以不一次性攒在内存里，是因为一段几分钟的人声录音很容易上百兆。
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int = 1,
) : Closeable {

    private val out = RandomAccessFile(file, "rw")
    private val scratch = ByteArray(4096)
    private var dataBytes = 0L
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        out.setLength(0)
        out.write(ByteArray(HEADER_SIZE))
    }

    @Synchronized
    fun write(buffer: FloatArray, count: Int) {
        if (closed) return
        var i = 0
        while (i < count) {
            var k = 0
            while (k < scratch.size && i < count) {
                val value = (buffer[i].coerceIn(-1f, 1f) * 32767f).toInt()
                scratch[k++] = (value and 0xFF).toByte()
                scratch[k++] = ((value shr 8) and 0xFF).toByte()
                i++
            }
            out.write(scratch, 0, k)
            dataBytes += k
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        out.seek(0)
        out.write(buildHeader(sampleRate, channels, dataBytes))
        out.close()
    }

    private fun buildHeader(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(HEADER_SIZE)
        fun putAscii(offset: Int, text: String) {
            for (i in text.indices) header[offset + i] = text[i].code.toByte()
        }

        fun putInt(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun putShort(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        putAscii(0, "RIFF")
        putInt(4, (36 + dataBytes).toInt())
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt(16, 16)
        putShort(20, 1) // PCM
        putShort(22, channels)
        putInt(24, sampleRate)
        putInt(28, byteRate)
        putShort(32, channels * 2)
        putShort(34, 16)
        putAscii(36, "data")
        putInt(40, dataBytes.toInt())
        return header
    }

    private companion object {
        const val HEADER_SIZE = 44
    }
}

data class WavData(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

object WavReader {

    fun read(file: File): WavData? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)
                var sampleRate = 0
                var channelCount = 1
                var bits = 16
                var dataSize = 0
                var offset = 0
                while (offset + 8 <= header.size) {
                    val id = String(header, offset, 4, Charsets.US_ASCII)
                    val size = leInt(header, offset + 4)
                    when (id) {
                        "fmt " -> {
                            channelCount = leShort(header, offset + 10)
                            sampleRate = leInt(header, offset + 12)
                            bits = leShort(header, offset + 22)
                        }
                        "data" -> dataSize = size
                    }
                    offset += 8 + size
                }
                if (sampleRate <= 0) return null
                if (dataSize <= 0) dataSize = (raf.length() - 44).toInt()

                val total = dataSize / 2
                val floats = FloatArray(total)
                val buffer = ByteArray(8192)
                var written = 0
                var remaining = dataSize
                while (remaining > 0) {
                    val n = minOf(buffer.size, remaining)
                    val read = raf.read(buffer, 0, n)
                    if (read <= 0) break
                    var k = 0
                    while (k + 1 < read && written < total) {
                        val lo = buffer[k].toInt() and 0xFF
                        val hi = buffer[k + 1].toInt()
                        val value = ((hi shl 8) or lo).toShort()
                        floats[written++] = value / 32768f
                        k += 2
                    }
                    remaining -= read
                }
                if (written == 0) return null
                val trimmed = if (written == floats.size) floats else floats.copyOf(written)
                if (channelCount > 1) {
                    // 只保留左声道，本应用只录单声道，这里只是兜底
                    val mono = FloatArray(trimmed.size / channelCount)
                    for (i in mono.indices) mono[i] = trimmed[i * channelCount]
                    WavData(mono, sampleRate)
                } else {
                    WavData(trimmed, sampleRate)
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
