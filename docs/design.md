# 音迹 PitchTrace · 设计说明

日期：2026-10-22
状态：v0.1 已实现

## 目标

做一个安卓端的实时音高轨迹工具：对着手机唱或弹，屏幕上立刻出现音高随时间的轨迹，
可以录音并回放，回放时轨迹与声音同轴。

## 关键决策

### 1. 原生 Kotlin + Compose，而不是网页壳

实时性要求高：麦克风回调 → 基频检测 → 逐帧绘制，中间不能有 WebView/JS 桥的开销。
`AudioRecord` + `AudioTrack` 直连，检测走纯 Kotlin 的 YIN，绘制走单个 Compose Canvas。

### 2. YIN 而不是 FFT 峰值

FFT 找峰值在基频缺失（人声、拨弦起音）时容易锁到谐波上，且需要加窗、插值、谐波判断，
代码量更大。YIN 的累积均值归一化差分函数对周期信号很稳，人声场景更可靠，几百行就能写完，
且不引入依赖。代价是 O(N·τ) 的运算量，因此做了两件事：

- 只计算 `[tauMin, tauMax]` 区间（由音域档位决定）
- 采集 48k、分析 24k，运算量再减半

### 3. 采集用 24k 分析率

最高音 C7 ≈ 2093Hz，24k 采样率的奈奎斯特是 12kHz，远高于需求；低频 C1 ≈ 32.7Hz 需要
约 735 点周期，窗口取 3 倍周期（约 2200 点）即可。整数抽取 2 倍实现简单，不需要多相滤波。

### 4. 状态以 30Hz 下发

检测 93 次/秒，但屏幕 60Hz。每个样本都触发 Compose 重组会直接把主线程打满。
所以音频线程只 `trySend` 到 Channel，收集协程按 33ms 节流合并成一次 `StateFlow` 更新，
可见窗口（4.5s，约 420 个点）随状态一起拷贝，列表本身不跨帧增长。

### 5. 音高轨迹和音频分开存

回放时若重新跑检测，会有两遍算力和不一致的风险。录制时同步把 `(timeMs, midi, freq, conf)`
写进 `take.pitch`，回放只读轨迹，声音和轨迹天然同轴。

## 数据流

```
AudioRecord ──帧──▶ PitchAnalyzer ──PitchResult──▶ PitchTracker ──PitchSample──▶ Channel
                                                                       │
                                        WavWriter ◀──帧(旁路)──────────┤
                                                                       ▼
                                        收集协程（33ms 节流）──▶ StateFlow<UiState>
                                                                       │
                                                                       ▼
                                                    PianoRoll Canvas（单帧绘制）
```

## 模块边界

| 模块 | 职责 | 依赖 | 可测性 |
| --- | --- | --- | --- |
| `YinPitchDetector` | 单窗基频 + 置信度 | 无 | JVM 单测 |
| `PitchAnalyzer` | 抽取、分帧、静音门限 | 无 | JVM 单测 |
| `PitchTracker` | 八度纠正、平滑、MIDI 映射 | `Music` | JVM 单测 |
| `AudioCapture` | 采集线程与设备兼容 | Android | 真机 |
| `WavWriter/WavReader` | WAV 编解码 | IO | 可单测（未覆盖） |
| `PlaybackEngine` | 流式回放与进度 | Android | 真机 |
| `PianoRoll` | 卷帘几何与绘制 | Compose | 目测 |
| `PitchViewModel` | 编排与状态合并 | 全部 | 目测 |

## 错误处理

- 麦克风打不开 / 被占用：`AudioCapture` 依次尝试 48k、44.1k、UNPROCESSED、MIC，全失败时回调错误文案
- 静音：RMS < 0.0018 直接返回无音高，不画线，避免满屏噪声轨迹
- 低置信度：YIN 置信度 < 0.3 视为无音高，< 0.62 由 tracker 丢弃
- 权限被拒：界面提示，不重复弹窗
- 录音文件损坏 / 不存在：`WavReader` 返回 null，回放按钮置灰

## 已知取舍

- 录音只保留最近一条，没有列表管理
- 回放不能从任意位置拖动（进度条只展示）
- 全音域档位运算量约为「人声」档的 4 倍，老机型可能掉帧
