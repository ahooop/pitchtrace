package com.pitchtrace.app.core.practice

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PracticeHistoryStoreTest {

    private lateinit var dir: File
    private lateinit var store: PracticeHistoryStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("pitchtrace-history").toFile()
        store = PracticeHistoryStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun record(
        id: String = "scale-c4",
        hitRate: Float = 0.8f,
        time: Long = 1_000L,
    ) = PracticeRecord(
        exerciseId = id,
        exerciseTitle = "C 大调音阶",
        timestamp = time,
        hitRate = hitRate,
        avgAbsCents = 22f,
    )

    @Test
    fun append_then_load_round_trips() {
        assertTrue(store.append(record(hitRate = 0.7f, time = 100L)))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(0.7f, loaded[0].hitRate, 0.001f)
        assertEquals("C 大调音阶", loaded[0].exerciseTitle)
    }

    @Test
    fun newest_record_comes_first() {
        store.append(record(hitRate = 0.5f, time = 100L))
        store.append(record(hitRate = 0.9f, time = 200L))
        assertEquals(0.9f, store.load()[0].hitRate, 0.001f)
    }

    @Test
    fun last_for_returns_previous_run_of_same_exercise() {
        store.append(record(id = "scale-c4", hitRate = 0.6f))
        store.append(record(id = "twinkle", hitRate = 0.95f))
        assertEquals(0.6f, store.lastFor("scale-c4")!!.hitRate, 0.001f)
        assertNull(store.lastFor("arpeggio-c4"))
    }

    @Test
    fun keeps_only_max_records() {
        repeat(PracticeHistoryStore.MAX_RECORDS + 5) { index ->
            store.append(record(hitRate = index / 100f, time = index.toLong()))
        }
        assertEquals(PracticeHistoryStore.MAX_RECORDS, store.load().size)
    }

    @Test
    fun corrupt_file_returns_empty_list() {
        dir.mkdirs()
        File(dir, "history.dat").writeText("not a history file")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun clear_removes_everything() {
        store.append(record())
        store.clear()
        assertTrue(store.load().isEmpty())
    }
}
