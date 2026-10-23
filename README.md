# 音迹 · PitchTrace

用手机麦克风实时检测音高，把声音画成钢琴卷帘上的“音迹”——横轴是时间，纵轴是音高，
当前时刻固定在偏右的竖线上，轨迹向左滚动，同时支持录下这一段声音并回放。

> 灵感来自 Android 上的“知唱音域调音仪”那类实时音高显示工具，这里是完全独立实现的版本。

## 界面

| 采集中 | 待机 |
| --- | --- |
| ![采集中](docs/screenshots/live.png) | ![待机](docs/screenshots/idle.png) |

截图由 `ScreenScreenshotTest` 用 Robolectric 原生图形模式把真实 Compose 界面渲染出来，
不是设计稿；重新生成：`./gradlew :app:testDebugUnitTest`，产物在 `app/build/screenshots/`。

## 功能

- **实时音高**：YIN 基频检测，约 10ms 输出一次，延迟 ≈ 50ms（含分析窗）
- **钢琴卷帘**：半音网格、左右音名标注、秒线、当前音名与光标
- **音域档位**：低音 E1–C4 / 人声 C2–C6 / 高音 G3–C7 / 全音域 C1–C7
- **A4 校准**：415–466 Hz 可调（默认 440，管弦乐队常用 442）
- **录音回放**：采音同时写 WAV，音高轨迹单独落盘，回放时按时间轴还原，不需要重新分析
- **抗误判**：八度误判纠正、三点中值平滑、置信度与 RMS 双门限（静音不画线）

## 技术要点

| 关注点 | 做法 |
| --- | --- |
| 采集 | `AudioRecord`，48kHz 单声道 PCM16，优先 `UNPROCESSED` 音源关闭 AGC/降噪/回声消除 |
| 检测 | YIN：差分函数 → 累积均值归一化 → 阈值取谷 → 抛物线插值，纯 Kotlin 实现 |
| 降采样 | 整数抽取 2 倍到 24kHz，运算量减半，精度靠插值保住 |
| 平滑 | 置信度门限 → 八度折叠 → 三点中值 |
| 渲染 | 单 Canvas 绘制，轨迹为一条 `Path`，光晕 + 实线两次描边 |
| 状态 | 音频线程只投递样本，30Hz 合并下发 `StateFlow`，避免 93Hz 触发 Compose 重组 |
| 存储 | `filesDir/takes/take.wav` + `take.pitch`（自定义二进制，含时间戳/音高/置信度） |

## 目录结构

```
app/src/main/java/com/pitchtrace/app/
├── core/dsp/        YinPitchDetector.kt   纯算法，无 Android 依赖
│                    PitchAnalyzer.kt      分帧 / 抽取 / RMS 门限
├── core/pitch/      Music.kt             频率 ↔ MIDI ↔ 音名
│                    PitchTracker.kt      八度纠正 + 中值平滑
│                    PitchRange.kt        音域档位
├── core/audio/      AudioCapture.kt      AudioRecord 封装
│                    WavCodec.kt          WAV 读写
│                    PlaybackEngine.kt    AudioTrack 流式回放
├── core/store/      SessionStore.kt      录音会话落盘
├── ui/              PianoRoll.kt         卷帘绘制
│                    PitchTraceScreen.kt  主界面 / 播放条 / 设置面板
├── PitchViewModel.kt                     编排层
└── MainActivity.kt                       权限与入口
```

`core/dsp` 与 `core/pitch` 不依赖 Android，所以音准逻辑可以在 JVM 上直接单测。

## 构建

需要 JDK 17、Android SDK（platform 35、build-tools 35）、Gradle 8.9（仓库自带 wrapper）。

```bash
./gradlew assembleDebug          # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # DSP / 音准单测 + 界面截图
```

单元测试覆盖：440Hz / C2 / C6 的音分误差、静音与白噪声不误报、八度误判折叠、单帧跳点平滑、
采集率 48k→分析率 24k 的端到端流程。

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK，不要提交。

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者把 APK 拷到手机上手动安装（debug 包带 `.debug` 后缀，可以和其它版本共存）。

## 参数与限制

- 低频（C2 以下）受手机麦克风频响限制，偶尔会掉到八度关系上，已加折叠纠错，但仍不如右声道电容麦
- 分析窗 40–50ms，快速滑音会被“抹平”一点，这是精度和延迟的取舍
- 录制只保留最近一条（`take.wav`），再次录音会覆盖
- 暂不支持导出/分享音频、节拍器、音阶训练

## 后续可做

- 录音列表与导出（MediaStore / 分享）
- 频谱瀑布图
- 音准打分、音域统计（唱到的最低/最高音）
- 使用 `Oboe` 或 `AudioRecord` 的 `AAudio` 低延迟路径
