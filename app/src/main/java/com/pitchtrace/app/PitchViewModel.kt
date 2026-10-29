package com.pitchtrace.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pitchtrace.app.core.audio.AudioCapture
import com.pitchtrace.app.core.audio.PlaybackEngine
import com.pitchtrace.app.core.audio.WavData
import com.pitchtrace.app.core.audio.WavReader
import com.pitchtrace.app.core.audio.WavWriter
import com.pitchtrace.app.core.dsp.PitchAnalyzer
import com.pitchtrace.app.core.pitch.Music
import com.pitchtrace.app.core.pitch.PitchRange
import com.pitchtrace.app.core.pitch.PitchSample
import com.pitchtrace.app.core.pitch.PitchTracker
import com.pitchtrace.app.core.practice.Exercise
import com.pitchtrace.app.core.practice.ExerciseSynth
import com.pitchtrace.app.core.practice.GuidedSweep
import com.pitchtrace.app.core.practice.Note
import com.pitchtrace.app.core.practice.PracticeResult
import com.pitchtrace.app.core.practice.PracticeScorer
import com.pitchtrace.app.core.practice.RangeExercise
import com.pitchtrace.app.core.practice.RangeProbe
import com.pitchtrace.app.core.practice.RangeResult
import com.pitchtrace.app.core.practice.RangeSource
import com.pitchtrace.app.core.practice.RangeSweep
import com.pitchtrace.app.core.practice.ReferenceTrack
import com.pitchtrace.app.core.practice.Verdict
import com.pitchtrace.app.core.store.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class Mode { IDLE, LIVE, PLAYBACK, PRACTICE, RANGE }

data class UiState(
    val mode: Mode = Mode.IDLE,
    val samples: List<PitchSample> = emptyList(),
    val clockMs: Long = 0L,
    val currentMidi: Float? = null,
    val currentNote: String = "",
    val cents: Int = 0,
    val elapsedMs: Long = 0L,
    val takeMs: Long = 0L,
    val hasTake: Boolean = false,
    val playMs: Long = 0L,
    val a4: Float = 440f,
    val range: PitchRange = PitchRange.VOICE,
    val sampleRate: Int = 0,
    val windowMs: Long = 4500L,
    val error: String? = null,
    // ---- 练习模式 ----
    val practiceId: String? = null,
    val practiceTitle: String = "",
    val practiceNotes: List<Note> = emptyList(),
    val practiceDurationMs: Long = 0L,
    val practiceVerdict: Verdict? = null,
    val practiceCents: Float = 0f,
    val practiceResult: PracticeResult? = null,
    // ---- 音域测试 ----
    val rangeLowMidi: Float = Float.NaN,
    val rangeHighMidi: Float = Float.NaN,
    val rangeTargetNote: String = "",
    val rangeGuided: Boolean = false,
    val rangeResult: RangeResult? = null,
    /** 扫频逐音标记：true 唱对、false 没唱对、null 还没轮到。只用于展示，不打断播放。 */
    val rangeHitFlags: List<Boolean?> = emptyList(),
)

/**
 * 唯一的编排层：
 * 音频线程 → PitchAnalyzer → PitchTracker → Channel → 收集协程 → StateFlow。
 *
 * 关键点是“音频线程绝不做 UI 相关的事”，以及“状态以 30Hz 合并下发”，
 * 否则 93Hz 的检测结果会直接把 Compose 的重组打爆。
 */
class PitchViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var capture: AudioCapture? = null

    @Volatile
    private var analyzer: PitchAnalyzer? = null

    @Volatile
    private var writer: WavWriter? = null

    @Volatile
    private var tracker = PitchTracker()

    private val playback = PlaybackEngine()
    private val synth = ExerciseSynth()

    private var practiceExercise: Exercise? = null
    private var practiceTrack: ReferenceTrack? = null

    private val rangeProbe = RangeProbe()
    private var rangeSweep: RangeSweep? = null
    private var rangeTrack: ReferenceTrack? = null

    private val liveTailMs = 250L
    private var rangeHits: BooleanArray = BooleanArray(0)
    private var rangeScoredUpTo = 0
    private var rangeFlagsCache: List<Boolean?> = emptyList()
    private var rangeFlagsScored = -1

    private val lock = Any()
    private val live = ArrayList<PitchSample>(8192)
    private var lastSampleTime = 0L
    private var startTime = 0L

    private var takeSamples: List<PitchSample> = emptyList()
    private var takeMs = 0L
    private var hasTake = false
    private var playMs = 0L

    private var lastEmit = 0L
    private var collector: Job? = null
    private val frames = Channel<PitchSample>(capacity = 256)

    init {
        restoreLastTake()
        startCollector()
    }

    // ---------------------------------------------------------------- 采集

    fun startListening() {
        beginCapture(Mode.LIVE, recordToTake = true)
    }

    private fun beginCapture(mode: Mode, recordToTake: Boolean) {
        if (capture != null) return
        playback.stop()
        playMs = 0

        val newCapture = AudioCapture(
            onFrame = { buffer, count -> handleFrame(buffer, count) },
            onError = { message ->
                viewModelScope.launch(Dispatchers.Main) {
                    _ui.update { it.copy(error = message, mode = Mode.IDLE) }
                }
            },
        )
        if (!newCapture.start()) {
            newCapture.stop()
            return
        }

        val range = _ui.value.range
        val a4 = _ui.value.a4
        val rate = newCapture.sampleRate
        val newAnalyzer = PitchAnalyzer(rate, range.minFrequency(a4), range.maxFrequency(a4))

        capture = newCapture
        analyzer = newAnalyzer
        tracker = PitchTracker(a4)
        synchronized(lock) {
            live.clear()
            lastSampleTime = 0L
        }
        startTime = SystemClock.uptimeMillis()
        writer = if (recordToTake) {
            runCatching { WavWriter(store.wavFile, rate) }.getOrNull()
        } else {
            null
        }
        takeSamples = emptyList()
        takeMs = 0
        hasTake = false
        playMs = 0

        _ui.update {
            it.copy(
                mode = mode,
                samples = emptyList(),
                clockMs = 0,
                currentMidi = null,
                currentNote = "",
                cents = 0,
                elapsedMs = 0,
                takeMs = 0,
                hasTake = false,
                playMs = 0,
                sampleRate = rate,
            )
        }
    }

    fun stopListening() {
        val active = capture ?: return
        capture = null
        active.stop()

        writer?.let { runCatching { it.close() } }
        writer = null
        analyzer = null

        val snapshot: List<PitchSample>
        val last: Long
        synchronized(lock) {
            snapshot = live.toList()
            last = lastSampleTime
        }

        if (snapshot.size >= 4 && last > startTime + 150) {
            takeMs = last - startTime
            takeSamples = snapshot.map { it.copy(timeMs = max(0L, it.timeMs - startTime)) }
            hasTake = true
            viewModelScope.launch(Dispatchers.IO) { store.saveTrack(takeSamples) }
        } else {
            takeSamples = emptyList()
            takeMs = 0
            hasTake = false
        }
        playMs = takeMs

        _ui.update {
            it.copy(
                mode = Mode.IDLE,
                samples = windowOf(takeSamples, takeMs),
                clockMs = takeMs,
                currentMidi = null,
                currentNote = "",
                cents = 0,
                elapsedMs = takeMs,
                takeMs = takeMs,
                hasTake = hasTake,
                playMs = playMs,
            )
        }
    }

    fun toggleListening() {
        if (capture != null) stopListening() else startListening()
    }

    // ---------------------------------------------------------------- 练习

    fun startPractice(exercise: Exercise) {
        if (capture != null) return
        synth.stop()
        practiceExercise = exercise
        practiceTrack = ReferenceTrack(exercise)
        _ui.update {
            it.copy(
                practiceId = exercise.id,
                practiceTitle = exercise.title,
                practiceNotes = exercise.notes,
                practiceDurationMs = exercise.durationMs,
                practiceVerdict = null,
                practiceCents = 0f,
                practiceResult = null,
            )
        }
        beginCapture(Mode.PRACTICE, recordToTake = false)
        if (capture == null) return
        synth.play(exercise)
    }

    fun stopPractice() {
        val active = capture ?: return
        capture = null
        active.stop()
        synth.stop()
        analyzer = null
        writer?.let { runCatching { it.close() } }
        writer = null

        val exercise = practiceExercise
        if (exercise == null) {
            _ui.update { it.copy(mode = Mode.IDLE, practiceVerdict = null) }
            return
        }

        val snapshot: List<PitchSample>
        synchronized(lock) { snapshot = live.toList() }
        val relative = snapshot.map { it.copy(timeMs = max(0L, it.timeMs - startTime)) }
        val result = PracticeScorer.score(exercise, relative)

        _ui.update {
            it.copy(
                mode = Mode.IDLE,
                samples = windowOf(relative, exercise.durationMs),
                clockMs = exercise.durationMs,
                currentMidi = null,
                currentNote = "",
                cents = 0,
                elapsedMs = exercise.durationMs,
                practiceVerdict = null,
                practiceResult = result,
            )
        }
    }

    // ---------------------------------------------------------------- 音域测试

    /**
     * @param guided true = 播放半音阶扫频，跟唱测极限；false = 自由唱，实时记录稳定极值。
     * 自由测之后紧接着点引导测时，保留自由测的极值，最后合并成更宽的结果。
     */
    fun startRangeTest(guided: Boolean) {
        if (capture != null) return
        playback.stop()
        synth.stop()
        if (!guided) rangeProbe.reset()
        rangeSweep = if (guided) RangeExercise.sweep() else null
        rangeTrack = rangeSweep?.let { ReferenceTrack(it.exercise) }
        rangeHits = BooleanArray(rangeSweep?.exercise?.notes?.size ?: 0)
        rangeScoredUpTo = 0
        rangeFlagsCache = emptyList()
        rangeFlagsScored = -1
        _ui.update {
            it.copy(
                rangeResult = null,
                rangeLowMidi = Float.NaN,
                rangeHighMidi = Float.NaN,
                rangeTargetNote = "",
                rangeGuided = guided,
                rangeHitFlags = emptyList(),
            )
        }
        beginCapture(Mode.RANGE, recordToTake = false)
        if (capture == null) return
        rangeSweep?.let { synth.play(it.exercise) }
    }

    fun finishRangeTest() {
        val active = capture ?: return
        capture = null
        active.stop()
        synth.stop()
        analyzer = null
        writer?.let { runCatching { it.close() } }
        writer = null

        val snapshot: List<PitchSample>
        synchronized(lock) { snapshot = live.toList() }
        val relative = snapshot.map { it.copy(timeMs = max(0L, it.timeMs - startTime)) }

        rangeProbe.finish()
        val free = rangeProbe.snapshot()
        val sweep = rangeSweep
        val result = if (sweep != null) {
            val guided = GuidedSweep.evaluate(sweep, relative)
            RangeResult.merge(free, guided).copy(
                source = when {
                    free.hasRange && guided.hasRange -> RangeSource.MIXED
                    guided.hasRange -> RangeSource.GUIDED
                    else -> RangeSource.FREE
                },
            )
        } else {
            free
        }

        val endMs = relative.lastOrNull()?.timeMs ?: 0L
        _ui.update {
            it.copy(
                mode = Mode.IDLE,
                samples = windowOf(relative, endMs),
                clockMs = endMs,
                currentMidi = null,
                currentNote = "",
                cents = 0,
                elapsedMs = endMs,
                rangeResult = result,
                rangeLowMidi = result.lowMidi,
                rangeHighMidi = result.highMidi,
                rangeTargetNote = "",
            )
        }
    }

    fun clearRangeResult() {
        _ui.update { it.copy(rangeResult = null) }
    }

    private fun handleFrame(buffer: FloatArray, count: Int) {
        writer?.let { w -> runCatching { w.write(buffer, count) } }
        val active = analyzer ?: return
        active.process(buffer, count) { result ->
            val now = SystemClock.uptimeMillis()
            // 分析窗口有半个窗长的固有延迟，往回调一半让轨迹贴着真实发声时刻
            val sampleTime = now - active.windowMs / 2
            val sample = tracker.track(result, sampleTime) ?: return@process
            frames.trySend(sample)
        }
    }

    // ---------------------------------------------------------------- 回放

    fun togglePlayback() {
        if (capture != null) return
        if (playback.isPlaying) {
            playback.stop()
            return
        }
        if (!hasTake) return
        val data: WavData = WavReader.read(store.wavFile) ?: return
        playMs = 0
        _ui.update {
            it.copy(
                mode = Mode.PLAYBACK,
                playMs = 0,
                clockMs = 0,
                samples = windowOf(takeSamples, 0),
                currentMidi = null,
                currentNote = "",
            )
        }
        playback.play(
            data = data,
            onProgress = { position -> emitPlayback(position) },
            onFinished = {
                viewModelScope.launch(Dispatchers.Main) {
                    // stop() 之后可能紧接着 startListening()，此时不能把 LIVE 状态覆盖回 IDLE
                    if (_ui.value.mode != Mode.PLAYBACK) return@launch
                    playMs = takeMs
                    _ui.update {
                        it.copy(
                            mode = Mode.IDLE,
                            playMs = takeMs,
                            clockMs = takeMs,
                            samples = windowOf(takeSamples, takeMs),
                        )
                    }
                }
            },
        )
    }

    private fun emitPlayback(position: Long) {
        if (_ui.value.mode != Mode.PLAYBACK) return
        val clamped = position.coerceIn(0, max(takeMs, 0))
        playMs = clamped
        _ui.update {
            it.copy(
                mode = Mode.PLAYBACK,
                playMs = clamped,
                clockMs = clamped,
                samples = windowOf(takeSamples, clamped),
            )
        }
    }

    // ---------------------------------------------------------------- 设置与清理

    fun setA4(value: Float) {
        val v = value.coerceIn(415f, 466f)
        tracker.a4 = v
        _ui.update { it.copy(a4 = v) }
    }

    fun setRange(range: PitchRange) {
        val active = capture
        if (active != null) {
            analyzer = PitchAnalyzer(
                active.sampleRate,
                range.minFrequency(_ui.value.a4),
                range.maxFrequency(_ui.value.a4),
            )
            tracker.reset()
        }
        _ui.update { it.copy(range = range) }
    }

    fun clearTake() {
        if (capture != null) return
        playback.stop()
        store.clear()
        synchronized(lock) { live.clear() }
        takeSamples = emptyList()
        takeMs = 0
        hasTake = false
        playMs = 0
        _ui.update {
            it.copy(
                mode = Mode.IDLE,
                samples = emptyList(),
                clockMs = 0,
                elapsedMs = 0,
                takeMs = 0,
                hasTake = false,
                playMs = 0,
                currentMidi = null,
                currentNote = "",
            )
        }
    }

    fun consumeError() {
        _ui.update { it.copy(error = null) }
    }

    fun clearPracticeResult() {
        _ui.update { it.copy(practiceResult = null, practiceVerdict = null) }
    }

    fun reportError(message: String) {
        _ui.update { it.copy(error = message) }
    }

    private fun restoreLastTake() {
        if (!store.hasTake()) return
        val samples = store.loadTrack()
        if (samples.isEmpty()) return
        takeSamples = samples
        takeMs = samples.last().timeMs
        hasTake = true
        playMs = takeMs
        _ui.update {
            it.copy(
                samples = windowOf(samples, takeMs),
                clockMs = takeMs,
                takeMs = takeMs,
                hasTake = true,
                playMs = takeMs,
                elapsedMs = takeMs,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        capture?.stop()
        capture = null
        playback.stop()
        synth.stop()
        writer?.let { runCatching { it.close() } }
        writer = null
        frames.close()
        collector?.cancel()
    }

    // ---------------------------------------------------------------- 内部工具

    private fun startCollector() {
        collector = viewModelScope.launch(Dispatchers.Default) {
            for (sample in frames) {
                synchronized(lock) {
                    live.add(sample)
                    lastSampleTime = sample.timeMs
                }
                if (_ui.value.mode == Mode.RANGE) rangeProbe.accept(sample)
                val now = SystemClock.uptimeMillis()
                if (now - lastEmit >= 33L) {
                    lastEmit = now
                    emitLive()
                }
            }
        }
    }

    private fun emitLive() {
        val snapshot: List<PitchSample>
        val clock: Long
        val elapsed: Long
        synchronized(lock) {
            clock = if (lastSampleTime == 0L) startTime else lastSampleTime
            snapshot = windowOf(live, clock)
            elapsed = max(0L, clock - startTime)
        }
        if (capture == null) return
        val current = snapshot.lastOrNull()

        if (_ui.value.mode == Mode.RANGE) {
            emitRange(snapshot, elapsed)
            return
        }

        if (_ui.value.mode == Mode.PRACTICE) {
            emitPractice(snapshot, current, elapsed)
            return
        }

        _ui.update {
            it.copy(
                mode = Mode.LIVE,
                samples = snapshot,
                clockMs = clock,
                currentMidi = current?.midi,
                currentNote = current?.let { s -> Music.noteName(s.midi) } ?: "",
                cents = current?.let { s -> Music.centsOff(s.frequency, it.a4) } ?: 0,
                elapsedMs = elapsed,
            )
        }
    }

    /**
     * 练习模式的实时反馈：只在 ±200ms 时间窗里找目标音，
     * 不做帧对帧比较，所以播放/采集延迟不会把判定带偏。
     */
    private fun emitPractice(
        snapshot: List<PitchSample>,
        current: PitchSample?,
        elapsed: Long,
    ) {
        val exercise = practiceExercise ?: return
        if (elapsed > exercise.durationMs + 800L) {
            stopPractice()
            return
        }

        val target = practiceTrack?.nearestTargetMidi(elapsed)
        val cents = if (current != null && target != null && !target.isNaN()) {
            100f * (current.midi - target)
        } else {
            Float.NaN
        }
        val verdict = when {
            cents.isNaN() -> null
            abs(cents) <= PracticeScorer.MAX_CENTS_HIT -> Verdict.HIT
            abs(cents) <= PracticeScorer.MAX_CENTS_CLOSE -> Verdict.CLOSE
            else -> Verdict.MISS
        }
        val relative = snapshot.map { it.copy(timeMs = max(0L, it.timeMs - startTime)) }

        _ui.update {
            it.copy(
                mode = Mode.PRACTICE,
                samples = relative,
                clockMs = elapsed,
                currentMidi = current?.midi,
                currentNote = current?.let { s -> Music.noteName(s.midi) } ?: "",
                cents = if (cents.isNaN()) 0 else cents.toInt(),
                elapsedMs = elapsed,
                practiceVerdict = verdict,
                practiceCents = if (cents.isNaN()) 0f else cents,
            )
        }
    }

    private fun emitRange(snapshot: List<PitchSample>, elapsed: Long) {
        val sweep = rangeSweep
        if (sweep != null && elapsed > sweep.exercise.durationMs + 500L) {
            finishRangeTest()
            return
        }

        val relative = snapshot.map { it.copy(timeMs = max(0L, it.timeMs - startTime)) }
        val latest = snapshot.lastOrNull()
        val target = rangeTrack?.nearestTargetMidi(elapsed)
        if (sweep != null) advanceRangeHits(sweep, relative, elapsed)

        _ui.update {
            it.copy(
                mode = Mode.RANGE,
                samples = relative,
                clockMs = elapsed,
                elapsedMs = elapsed,
                currentMidi = latest?.midi,
                currentNote = latest?.let { s -> Music.noteName(s.midi) } ?: "",
                cents = 0,
                rangeLowMidi = rangeProbe.lowMidi,
                rangeHighMidi = rangeProbe.highMidi,
                rangeTargetNote = if (target != null && !target.isNaN()) Music.noteName(target) else "",
                rangeHitFlags = if (sweep != null) refreshRangeFlags() else emptyList(),
            )
        }
    }

    /**
     * 每播完一个音就判定一次并推进指针，只做标记，不打断播放。
     * 判定窗口尾部多留 [liveTailMs]，因为人跟唱总比参考音慢一点。
     */
    private fun advanceRangeHits(sweep: RangeSweep, relative: List<PitchSample>, elapsed: Long) {
        val notes = sweep.exercise.notes
        while (rangeScoredUpTo < notes.size) {
            val note = notes[rangeScoredUpTo]
            if (elapsed < note.endMs + liveTailMs) break
            rangeHits[rangeScoredUpTo] = GuidedSweep.hitAt(
                note = note,
                samples = relative,
                offsetMs = 0L,
                tailMs = liveTailMs,
            )
            rangeScoredUpTo++
        }
    }

    /** 标记条只在推进时重建，避免每帧生成新列表触发重组。 */
    private fun refreshRangeFlags(): List<Boolean?> {
        if (rangeFlagsScored == rangeScoredUpTo) return rangeFlagsCache
        rangeFlagsScored = rangeScoredUpTo
        rangeFlagsCache = List(rangeHits.size) { index ->
            if (index < rangeScoredUpTo) rangeHits[index] else null
        }
        return rangeFlagsCache
    }

    /** 取 [clock] 之前一段时间窗内的样本，用二分定位，避免每次全表扫描。 */
    private fun windowOf(source: List<PitchSample>, clock: Long, span: Long = 4500L): List<PitchSample> {
        if (source.isEmpty()) return emptyList()
        val end = firstIndexAfter(source, clock + 60L)
        if (end <= 0) return emptyList()
        val begin = firstIndexAfter(source, clock - span - 120L)
        if (begin >= end) return emptyList()
        return source.subList(begin, end).toList()
    }

    private fun firstIndexAfter(source: List<PitchSample>, time: Long): Int {
        var low = 0
        var high = source.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (source[mid].timeMs <= time) low = mid + 1 else high = mid
        }
        return low
    }
}
