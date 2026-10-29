# 音迹 PitchTrace · 练习模式设计说明

日期：2026-10-29
状态：设计已确认，准备实施

## 1. 背景

现有 PitchTrace 只有「自由聆听」：麦克风收音、实时画音高轨迹、可录音回放。
它回答「我现在唱的是什么音」，但不回答「我唱得准不准」。

要判断准不准，必须有**标准答案**。而单麦克风收到的混合信号无法区分音源——
如果把伴奏和人声一起收进来，两个源音高相同时混成一个峰，测不出各自偏差。
所以练习模式的前提是**信号天然分离**：

- 参考音由 App 播放，走耳机，不进麦克风；
- 麦克风只收人声。

参考音高来自练习定义本身（音符序列），不需要从音频里解析，因此是精确的。

## 2. 目标与非目标

目标：

- 内置合成参考音，耳机播放，麦克风收人声；
- 实时双轨显示（参考轨 + 人声轨）与宽松状态判定；
- 练习结束后做精确评分：音分偏差、命中率、逐音回顾；
- 保存历史成绩，看多次练习的趋势。

非目标（本期不做）：

- 导入真实歌曲（伴奏、和弦、人声混在一起，参考轨不可靠）；
- 音源分离 / 多音高归属；
- 多人对比、社交分享；
- 麦克风外的其他输入设备。

## 3. 用户流程

1. 进入练习模式，选择练习（音阶 / 琶音 / 旋律）；
2. 看到「请戴耳机」提示，确认后开始；
3. 参考音按节拍播放，卷帘上出现灰色虚线的目标轨迹；
4. 用户跟唱，人声轨迹以现有着色叠加；顶部显示当前判定（绿/黄/红/灰）与偏差条；
5. 练习结束自动停止，弹出结果面板：命中率、平均音分、逐音对错；
6. 结果写入历史，历史页可看每次的命中率趋势。

## 4. 数据模型

```kotlin
data class Note(val midi: Float, val startMs: Long, val durationMs: Long) {
    val endMs: Long get() = startMs + durationMs
}

data class Exercise(val id: String, val title: String, val notes: List<Note>) {
    val durationMs: Long get() = notes.lastOrNull()?.endMs ?: 0L
}
```

内置练习（`ExerciseLibrary`）：

| id | 标题 | 内容 | 音符时长 |
|---|---|---|---|
| `scale-c4` | C 大调音阶 | C4–C5 上行再下行，8 个音 ×2 | 520ms，间隙 120ms |
| `arpeggio-c4` | C 大调琶音 | C4 E4 G4 C5 E5 C5 G4 E4 | 同上 |
| `twinkle` | 小星星（两句） | C C G G A A G，F F E E D D C | 同上 |

音符序列在代码里用 MIDI 数字写死，`Note.startMs` 由累积时长生成。

## 5. 参考轨

`ReferenceTrack` 把 `Exercise` 投影成固定时间栅格（步长 10ms）的期望音高数组：

- 栅格落在某个音符的 `[startMs, endMs)` 内 → 该音符的 MIDI；
- 落在间隙 → `NaN`（无目标）；
- 提供 `expectedMidiAt(t)`、`hasTargetAt(t)`、`targetsNear(t, toleranceMs)`。

参考轨**不经过音频解析**，所以不存在识别误差。

## 6. 实时判定（宽松）

每帧（约 10ms 一次）执行：

1. 取当前时间 ±200ms 内的目标音符集合；
2. 人声 MIDI 与集合中最近的目标比较，得到偏差 `Δ = voiceMidi - targetMidi`；
3. 判定：
   - `|Δ| ≤ 0.5`（±50 音分）→ HIT（绿）
   - `0.5 < |Δ| ≤ 1.0` → CLOSE（黄）
   - `|Δ| > 1.0` → MISS（红）
   - 无人声或集合为空 → NONE（灰）

用 200ms 时间窗而不是帧对帧，是为了让实时反馈对播放/采集延迟不敏感。

界面元素：

- 卷帘上叠一层灰色虚线表示参考轨；
- 人声沿用现有彩色轨迹；
- 顶部一个状态胶囊 + 当前偏差条（−100…+100 音分）。

## 7. 事后评分（精确）

`PracticeScorer` 是纯函数，输入 `Exercise` + 人声 `List<PitchSample>`，输出 `PracticeResult`。

### 7.1 时间对齐

1. 参考序列：`ReferenceTrack` 的栅格数组（10ms）；
2. 人声序列：样本按同一栅格最近邻填充，无样本为 `NaN`；
3. 对偏移 `offset ∈ [-600ms, +600ms]`，步长 10ms：
   - 取两边都非 `NaN` 的格子，计算 `|ref[i] - voice[i+offset]|` 的中位数；
   - 中位数最小者即为最佳偏移。
4. 有效重叠格数少于练习时长的 20% 时，判定为「未检测到有效人声」，跳过对齐。

### 7.2 逐音打分

对齐后，对每个音符：

1. 窗口 `[startMs + offset, endMs + offset]`；
2. 取窗内人声样本的 MIDI 中位数 `m`；
3. 样本数少于窗口栅格数的 30% → `ABSENT`（漏唱）；
4. 否则 `cents = 100 * (m - note.midi)`，判定：
   - `|cents| ≤ 50` → `HIT`
   - `50 < |cents| ≤ 100` → `CLOSE`
   - `> 100` → `MISS`

### 7.3 汇总

```kotlin
data class PracticeResult(
    val exerciseId: String,
    val offsetMs: Long,
    val detectedVoice: Boolean,
    val noteResults: List<NoteResult>,
    val hitRate: Float,        // HIT 数 / 总音符数
    val avgAbsCents: Float,    // 仅统计非 ABSENT 音符
)

data class NoteResult(
    val note: Note,
    val sungMidi: Float?,      // null = 漏唱
    val centsOff: Float?,
    val verdict: Verdict,      // HIT / CLOSE / MISS / ABSENT
)
```

## 8. 界面

- `PitchTraceScreen` 顶部分段控件切换「自由 / 练习」；
- 练习模式：
  - 练习选择下拉 + 开始/停止按钮；
  - 实时状态胶囊与偏差条；
  - 卷帘叠加参考轨；
- `PracticeResultSheet`：命中率、平均音分、逐音列表（音名 + 唱的音名 + 偏差 + 对错色块）；
- `PracticeHistoryScreen`：按时间倒序列出每次成绩，顶部一个简单趋势条（最近 10 次命中率）。

## 9. 持久化

`PracticeHistoryStore` 沿用 `SessionStore` 的二进制风格（`DataOutputStream`），
存到 `filesDir/practice/history.dat`：

```
magic: Int = 0x50545243 ("PTRC")
version: Int = 1
count: Int
repeat(count) {
    exerciseId: UTF
    timestamp: Long
    offsetMs: Long
    hitRate: Float
    avgAbsCents: Float
    noteCount: Int
    verdicts: ByteArray(noteCount)  // 0=HIT 1=CLOSE 2=MISS 3=ABSENT
}
```

读取时校验 magic/version，坏文件返回空列表（不抛异常）。只保留最近 200 条。

## 10. 错误处理

| 情况 | 处理 |
|---|---|
| 未戴耳机，麦克风收到参考音 | 开始前给一次性提示；运行中若目标音能量持续异常强，状态栏提示「疑似漏音」 |
| 未授权麦克风 | 沿用现有权限流程，练习不可开始 |
| 全程未检测到人声 | 结果页显示「未检测到人声」，不写历史 |
| AudioTrack 初始化失败 | 提示并退回自由模式 |
| 历史文件损坏 | 返回空列表，不影响当前结果展示 |
| 练习中途 Activity 销毁 | 停止播放与采集，丢弃未完成的评分 |

## 11. 测试

单元测试（JVM，无 Android 依赖）：

- `PracticeScorerTest`
  - 所有音准 → 命中率 1.0，平均偏差 ≈ 0
  - 整体偏 +50 音分 → 仍全部 HIT
  - 整体偏 +120 音分 → 全部 MISS
  - 整体延迟 300ms → 对齐后仍 HIT
  - 第二个音无样本 → 该音 ABSENT，其余正常
  - 唱错音（+3 半音）→ 对应音 MISS
  - 空样本 → `detectedVoice = false`
- `ReferenceTrackTest`
  - 音符内查询返回正确 MIDI
  - 间隙查询返回 NaN
  - 边界时间恰好等于 endMs 时不算命中
- `PracticeHistoryStoreTest`：写入后读回一致；损坏文件返回空列表

截图测试：练习界面（含参考轨虚线）、结果面板。

## 12. 里程碑

1. **M1 核心算法**：`Note` / `Exercise` / `ExerciseLibrary` / `ReferenceTrack` / `PracticeScorer` + 单测；
2. **M2 参考音播放**：`ExerciseSynth`，AudioTrack 播放与时间基准；
3. **M3 实时界面**：模式切换、双轨绘制、状态胶囊、偏差条；
4. **M4 结果与历史**：结果面板、`PracticeHistoryStore`、历史页。

## 13. 风险与取舍

| 风险 | 说明 | 对策 |
|---|---|---|
| 延迟估计失败 | 用户起唱过晚、漏唱多 | 重叠格数不足时直接判「未检测到人声」，不给错误分数 |
| 耳机漏音 | 外放或耳塞漏音导致麦克风收到参考音 | 开始前提示；运行中能量异常提示 |
| 人声与参考同音高 | 混在一起无法分辨 | 靠耳机物理隔离，产品层面明确要求 |
| 评分对身体差异敏感 | 音域不同的人唱不到 C4–C5 | 练习固定音域，后续可加「低八度版本」 |
