package com.pitchtrace.app.core.practice

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** 一次练习的成绩摘要，只存判断「有没有进步」需要的字段。 */
data class PracticeRecord(
    val exerciseId: String,
    val exerciseTitle: String,
    val timestamp: Long,
    val hitRate: Float,
    val avgAbsCents: Float,
)

/**
 * 练习成绩的本地存档：只保留最近 [MAX_RECORDS] 条，最新的在最前。
 *
 * 沿用 SessionStore 的二进制风格，不引入序列化库；
 * 文件损坏、版本不符或读取异常时一律返回空列表，绝不能影响本次结果展示。
 */
class PracticeHistoryStore(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, "practice"))

    private val file: File get() = File(dir, "history.dat")

    fun load(): List<PracticeRecord> {
        if (!file.exists()) return emptyList()
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != MAGIC) return emptyList()
                if (input.readInt() != VERSION) return emptyList()
                val count = input.readInt()
                if (count <= 0 || count > LIMIT_GUARD) return emptyList()
                val list = ArrayList<PracticeRecord>(count)
                repeat(count) {
                    list.add(
                        PracticeRecord(
                            exerciseId = input.readUTF(),
                            exerciseTitle = input.readUTF(),
                            timestamp = input.readLong(),
                            hitRate = input.readFloat(),
                            avgAbsCents = input.readFloat(),
                        ),
                    )
                }
                list
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** 追加一条并裁剪到上限；写盘失败返回 false，但不抛异常。 */
    fun append(record: PracticeRecord): Boolean {
        val list = ArrayList(load())
        list.add(0, record)
        while (list.size > MAX_RECORDS) list.removeAt(list.size - 1)
        return save(list)
    }

    /** 同一练习的上一条成绩，用来在结果页做「上次 vs 这次」对比。 */
    fun lastFor(exerciseId: String): PracticeRecord? =
        load().firstOrNull { it.exerciseId == exerciseId }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun save(records: List<PracticeRecord>): Boolean = try {
        dir.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(records.size)
            for (record in records) {
                out.writeUTF(record.exerciseId)
                out.writeUTF(record.exerciseTitle)
                out.writeLong(record.timestamp)
                out.writeFloat(record.hitRate)
                out.writeFloat(record.avgAbsCents)
            }
        }
        true
    } catch (t: Throwable) {
        false
    }

    companion object {
        const val MAX_RECORDS = 20
        private const val MAGIC = 0x50545248 // "PTRH"
        private const val VERSION = 1
        private const val LIMIT_GUARD = 1000
    }
}
