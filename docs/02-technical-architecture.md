# SpaceSlimmer 技术方案

## 1. 目标与约束

### 1.1 工程目标

- **一份决策逻辑**：预设、跳过、体积预估、任务状态机在 Rust 中实现，双端行为一致。
- **平台做擅长的事**：硬件编解码、相册、权限、后台、通知。
- **移动端现实**：发热、内存、进程会被杀、App 体积、审核、编解码专利。
- **可演进**：V1 不把 FFmpeg 做成不可拆卸的巨石；特殊格式作为可选 feature。

### 1.2 硬约束

| 约束 | 处理 |
|------|------|
| 用户要快、要并行 | 视频走 HW；图片/音频多线程；视频默认 1–2 并发 |
| 同一 Rust Core | `ss-core` + UniFFI；平台 codec 以 trait 回调注入 |
| Kotlin + Compose / Swift + SwiftUI | UI 不共享；共享的是领域层 |
| 不上传媒体 | 无媒体网络 API；遥测只有聚合事件 |
| 包体积 | 核心 APK/IPA 控制；AV1 软件、完整 FFmpeg 做成可选 |
| 专利 | 优先系统 HEVC/AAC 编码器，避免自行嵌入收费编码器商用 |

### 1.3 明确的架构选择

**采用：双原生 UI + 共享 Rust 领域核心 + 平台媒体后端。**

不采用：

- Flutter/RN 做 UI（与指定技术栈不符，且媒体管线仍要原生）。
- 纯 KMP 共享（编解码生态与 UniFFI+Rust 的系统库绑定不如 Rust 合适）。
- 全部 FFmpeg 转码（体积、GPL/LGPL、发热、与系统编码器重复）。
- 全部纯 Rust 软件编码视频（移动端 4K 不可接受）。

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│  Android: Application + Compose UI + ViewModels + WorkManager   │
│  iOS:     App + SwiftUI + Observable store + Background tasks   │
├─────────────────────────────────────────────────────────────────┤
│  Platform Gateway（Kotlin / Swift）                              │
│  - Permission / Picker / MediaStore / PhotoKit                   │
│  - HwVideoBackend / HwAudioBackend                               │
│  - Notification / Thermal / Battery / Foreground service         │
│  - File bookmark / security-scoped URL                           │
├─────────────────────────────────────────────────────────────────┤
│  UniFFI 边界  ss_core.*                                          │
│  CoreEngine: 提交任务、订阅事件、读历史、改设置                    │
├─────────────────────────────────────────────────────────────────┤
│  Rust crate graph                                                │
│  ss-core        门面、生命周期                                    │
│  ss-domain      预设、决策、预估、状态机（纯逻辑，易测）           │
│  ss-probe       容器/码流摘要（轻量，可走平台补充）                │
│  ss-image       JPEG/WebP/PNG/AVIF 软件管线                      │
│  ss-audio       解码(symphonia) + Opus；AAC 可回落平台            │
│  ss-jobs        队列、并行配额、取消、持久化                       │
│  ss-platform    trait: VideoTranscoder, AudioAac, Fs, Clock      │
│  ss-sqlite      任务/历史/保险箱索引                              │
└─────────────────────────────────────────────────────────────────┘
```

**依赖方向**：UI → Gateway → UniFFI → ss-core → domain/jobs。  
`ss-image` / `ss-audio` 不反向依赖 UI。  
`ss-platform` 只有 trait；Android/iOS 在启动时注册实现。

视频转码**主路径不在 Rust 内解码每一帧**，而是：

1. Rust 决策出发包（分辨率、码率、codec、容器、是否 copy 音轨）。
2. 平台 `VideoTranscoder` 执行，周期性回传 progress。
3. Rust 校验输出大小、更新保险箱与历史。

图片主路径**可以全在 Rust**（解码-缩放-编码），大图注意分块与 downsampling。

---

## 3. 仓库结构

建议 Monorepo：

```
SpaceSlimmer/
  docs/
  rust/
    Cargo.toml                 # workspace
    crates/
      ss-core/                 # uniffi 入口
      ss-domain/
      ss-probe/
      ss-image/
      ss-audio/
      ss-jobs/
      ss-platform/
      ss-sqlite/
      ss-uniffi/               # UDL 或 proc-macro 导出
    tests/                     # 黄金样张、决策快照
  android/
    app/
    core-android/              # UniFFI 绑定、JNI 加载、Gateway
    video-hw/                  # Media3 Transformer / MediaCodec
    audio-hw/
  ios/
    App/
    CoreIOS/                   # XCFramework 包装
    VideoHW/                   # AVFoundation / VideoToolbox
    AudioHW/
  packaging/
    uniffi-bindgen scripts
    xcframework.sh
    cargo-ndk 构建
  testdata/                    # Git LFS 小样片（注意版权，用自拍照）
```

版本：Rust 2021/2024 edition、Android minSdk 26（Android 8，覆盖 HW codec 与 64-bit）、targetSdk 当前；iOS 16+（SwiftUI 与 PhotoKit 现代 API）。ABIs：`arm64-v8a` 必须，`x86_64` 仅模拟器。iOS：`ios-arm64` + `ios-sim-arm64`（可加 x64 sim）。

---

## 4. UniFFI 集成

### 4.1 为什么是 UniFFI

- 官方级 Kotlin/Swift 生成，错误类型、回调、异步（via diplomat/uniffi future 或自行 callback）成熟。
- 比手写 JNI/cbindgen 更不容易在双端漂移。

### 4.2 生命周期

```text
App start
  → load libss_core
  → CoreEngine.open(app_dir, cache_dir, platform_callbacks)
  → restore persisted job queue
App background
  → CoreEngine.pause_policy(OnBackground)
App terminate
  → CoreEngine.flush()
```

`CoreEngine` 为长寿命单例。UI 不要直接持有内部 crate 类型。

### 4.3 线程模型

- UniFFI 调用默认不要阻塞主线程。
- Kotlin：`Dispatchers.IO` / 专用 `codec` 线程池包装挂起函数。
- Swift：`Task.detached` + `@MainActor` 回写 UI。
- Rust 内部：`ss-jobs` 用 `async`（tokio **不要**在移动端默认多线程全局爆炸）。推荐：
  - **`async-executor` / `futures-lite` + 自建线程池**，或
  - `tokio` rt-multi-thread 但 `worker_threads` 固定为 2，图片 CPU 用 `rayon` 线程池且 `num_threads` 受配额限制。

推荐组合：

- 任务编排：单线程 async executor（清晰的状态机）。
- 图片并行：`rayon` pool，size = `image_parallelism`。
- 视频：0 条 Rust 重计算线程，等待平台回调。

### 4.4 回调接口（平台注入）

```text
trait VideoTranscoder
  start(job_id, VideoRecipe, src, dst) -> Result<()>
  cancel(job_id)
  # 进度通过 CoreObserver 反向事件，不一定放这个 trait

trait ImageIo (可选)
  # 若 HEIF 解码 Rust 侧困难，平台解码成 RGBA 文件/共享内存再交给 ss-image 编码

trait AacEncoder
  encode_pcm_to_m4a(...)

trait SystemInfo
  thermal_state, battery, is_power_save, hw_caps (hevc, av1, max_size)

trait Notifications
  update_progress(...)
```

HEIF：Android `ImageDecoder` / iOS `ImageIO` 解码后把像素或无损中间格式交给 Rust。不要在 V1 强绑 libheif（体积与构建地狱）。

---

## 5. 平台媒体后端

### 5.1 Android 视频

**首选：Media3 Transformer（Jetpack Media3）**

- 官方维护，支持 MediaCodec 硬件编解码、分辨率缩放、音频混流。
- 比手写 MediaExtractor+Codec+Muxer 少大量边角（旋转、GOP、EOS）。
- 通过 `EditedMediaItem` + `TransformationRequest` 设置 MIME（H.264/HEVC）、分辨率。

**码率控制：** Transformer 对精细 CRF 有限，需要时降到自研 `MediaCodec` 管线：

- `MediaFormat.KEY_BIT_RATE`
- `KEY_BITRATE_MODE`：VBR 优先，不行 CBR
- 部分设备 `KEY_QUALITY`（CQ）—— 能力探测后才用
- I-frame interval 2–4s
- 颜色：BT.709 SDR 默认；HDR 走能力探测

**音频：** 可 `setRemoveAudio(false)` copy；需要降码率时 AAC `MediaCodec` 或平台 `AacEncoder`。

**封装：** `MediaMuxer` MPEG-4。

**能力探测：** `MediaCodecList` 枚举 `video/hevc`、`video/av01` encoder，记录 max size、bitrate、是否软件（避免把 libgav1 软件编码器当 HW）。

**后台：** `foregroundServiceType="dataSync"` 或更合适的 mediaProcessing（Android 14+ `mediaProcessing`/`specialUse` 需声明）。通知栏进度。`WorkManager` 仅作重启补偿，不把 4K 转码完全交给 deferrable worker（会被限）。

**分区存储：**

- Picker 返回的 URI 持久化 `takePersistableUriPermission`（若可用）。
- 写入：`MediaStore.Video.Media` `IS_PENDING` 再发布。
- 替换：先写新文件，校验，再删旧（用户确认后）；不要原地 truncate。

### 5.2 iOS 视频

**首选：AVAssetReader + AVAssetWriter**（可控码率/分辨率）

- `AVAssetExportSession` 太粗，适合“低/中/高质量”三档原型，不适合指定体积。
- VideoToolbox 由 Writer 内部调用。
- `HEVC`：`AVVideoCodecType.hevc`；兼容：`.h264`。
- 码率：`AVVideoAverageBitRateKey`、`AVVideoMaxKeyFrameIntervalKey`、`AVVideoProfileLevelKey`。
- Quality：`AVVideoQualityKey`（0...1）在部分 codec 可用，需实测。
- 音频：`AVEncoderBitRateKey` AAC。
- 旋转：写入 `transform`，避免像素旋转。
- HDR：`HDR10/Dolby` 需要源 track 特性；V1 若 `hasHDR` 且不能安全透传则 domain 层 skip 或 SDR downconvert（产品已定义要提示）。

**相册：**

- 输入：`PHPicker` / `PHAsset` + `requestAVAsset`。
- 输出：`PHAssetChangeRequest` 创建视频；替换需删除原 asset（用户确认）并保存新的；原片复制进 App Group/Documents 保险箱。
- Security-scoped URL：文件 App 选中的要 `startAccessingSecurityScopedResource`。

**后台：** `beginBackgroundTask` 只能再撑几十秒。产品必须：长时间转码保持应用打开；可选 `BGProcessingTaskRequest`（不保证）。插电+空闲可尝试继续，但不作为承诺。

### 5.3 图片

| 格式 | 解码 | 编码 |
|------|------|------|
| JPEG | zune-jpeg / image / mozjpeg decode | mozjpeg |
| PNG | png crate | oxipng（无损）或量化后 PNG；有损建议转 JPEG/WebP |
| WebP | libwebp-sys 或 image | libwebp |
| AVIF | dav1d/rav1e 路径较重 | ravif（注意速度，可限最大边） |
| HEIF | **平台解码** | 平台 HEIF 编码或转 JPEG/AVIF |

大图策略：

- 先读尺寸，超过 8192 边长则下采样解码（Android Bitmap 采样、iOS downsampling、JPEG 可按 MCU）。
- 输出最大边由预设决定（例如均衡档照片最大边 4096 或保持）。
- EXIF orientation 在编码前应用或写入 EXIF。

并行：图片任务在 rayon 跑，注意峰值内存 = 并发数 × 解码缓冲。4K RGBA ≈ 50MB+，并发 8 会炸。**按内存预算算并发**：`min(n, RAM_budget / worst_frame)`，默认 3–6。

### 5.4 音频

- 解码：`symphonia`（WAV/FLAC/MP3/AAC/OGG）。
- 重采样：`rubato` 或 `dasp`，统一到 44.1/48kHz。
- Opus：`opus` crate，语音 VBR 32–64k，音乐 96–128k。
- AAC：优先平台（Android MediaCodec `audio/mp4a-latm`，iOS AudioConverter），保证 M4A 兼容。
- 声道：语音可 downmix stereo→mono（产品在语音档开启）。

---

## 6. 任务系统与并行

### 6.1 队列模型

```text
JobGroup (一次“开始压缩”)
  Job (单个媒体文件)
    Stage: Probe → Decide → Transcode → Validate → Commit → Index
```

状态：`Queued | Running | Paused | Cancelling | Succeeded | Skipped | Failed | Replaced`。

持久化：SQLite（`ss-sqlite`）。杀进程后：

- 运行中的视频：临时文件删除，Job 回到 Queued 或标记 Interrupted。
- 已 Commit 的不重做。

### 6.2 调度器

三类 worker 配额：

| 类型 | 省电 | 自动（默认） | 最快 |
|------|------|--------------|------|
| Video | 1 | 1 | 2（需 HW 双实例探测成功且温度正常） |
| Image | 2 | min(4, mem) | min(8, mem) |
| Audio | 2 | 4 | 6 |

规则：

- 全局 CPU 目标：不要 100% 占满所有核；留 1 核给 UI。
- `thermal = serious/critical`：视频强制 1，图片降到 2，且降低编码器 preset 速度（HW 无 preset 则降分辨率任务优先插队？不，只降并发）。
- 用户暂停：停止取新任务；当前视频尽量 cancel 并保留 checkpoint——V1 **不做视频 GOP checkpoint**，取消即丢临时文件。
- 优先级：用户当前可见的 JobGroup > 旧队列。

### 6.3 进度

统一事件：

```text
JobProgress { job_id, ratio: 0.0-1.0, eta_ms, stage }
GroupProgress { done, total, saved_bytes, running }
```

视频进度来自平台时间戳 `pts / duration`。  
图片：线性 0.2 解码 0.8 编码或直接完成跳动（要有 indeterminate 再完成）。  
UI 节流：Rust 侧 100–200ms 合并一次事件，避免 Compose/SwiftUI 刷新风暴。

---

## 7. 文件系统、原子提交、保险箱

### 7.1 目录

```
app_support/
  db/ss.sqlite
  tmp/{job_id}.*
  vault/{asset_id}/original.bin
  vault/{asset_id}/meta.json
  exports/   # 可选
```

临时文件一律写 `tmp`，成功后 copy/move 到相册或导出目录，再删 tmp。

### 7.2 提交协议

1. 输出完整（muxer EOS）。
2. `ss-domain` 校验：能 probe、duration 与源差异 < 阈值（默认 0.3s 或 1%）、size > 0、size < source（否则 SkipKeepSource）。
3. 计算节省字节。
4. 按策略：
   - **SaveAsNew**：写入相册/文件夹，原 URI 不动。
   - **Replace**：原片 copy 到 vault（若尚未有），再写新文件到相册，删除或隐藏原片。
5. 任何一步失败：删除不完整输出，vault 保持，状态 Failed。

iOS 删除 PHAsset 不可逆，**必须先 vault 成功**。Android 删除 MediaStore 项同理。

### 7.3 保险箱

- 原片以文件存储，不依赖相册“隐藏”（OEM 不可靠）。
- 大文件 vault 会占空间——产品必须显示「保险箱占用」，否则用户觉得没省。
- 真正释放空间 = 用户清理保险箱。
- 可做「替换时直接删原片」（危险，设置项，默认关）。V1 建议替换路径强制进保险箱。

---

## 8. 探测（Probe）

完整 FFprobe 太重。分层：

**L1 平台：** Android `MediaMetadataRetriever` / iOS `AVAsset` + `CGImageSource`。快，覆盖 95% 相册文件。

**L2 Rust：** 读 box/atom（mp4）、JPEG SOF、PNG IHDR、ISO BMFF codecs。用于文件选择器里平台元数据缺失时。

**L3 可选 FFmpeg/ffprobe：** 仅 MKV/残缺文件，feature `exotic`。

Probe 输出 `MediaFingerprint`（见 04 文档），供决策与预估使用。必须包含：是否可能已高效、是否 HDR、是否可变帧率、音轨数量、旋转。

---

## 9. 设置、能力、远程开关

本地 settings：默认预设、跳过阈值、保留 GPS、并行档、保险箱天数。

启动时 `SystemInfo.hw_caps` 写入：

- `hevc_hw_encode`
- `av1_hw_encode`
- `max_input_size`
- `hdr_encode`

UI 根据 caps 隐藏 AV1。  
远程配置（可选，不含媒体）：动态关闭某 OEM 的 HW 路径（黑名单机型），走软件或降级 H.264。用 Firebase Remote Config 或自建，**默认无网络也能工作**。

---

## 10. UI 技术要点

### 10.1 Android

- Compose + Navigation + ViewModel + Kotlin Flow 订阅 UniFFI 回调（桥到 `callbackFlow`）。
- 列表：雷达结果可能上万，必须分页/窗口。扫描在 Rust 或 Kotlin 增量回调。
- 对比预览：`Modifier` + 两个 `AsyncImage` 或 `ExoPlayer` 双实例要小心内存；V1 视频用单个 player 切换 dataSource。
- 主题：Material 3 dynamic color 可选，但节省数字保持高对比。

### 10.2 iOS

- SwiftUI + `@Observable`（iOS 17+）或 `ObservableObject` 若最低 iOS 16。
- 视频对比：`AVPlayer`；照片：`Image` + `CoreGraphics` clip。
- 大列表：`LazyVGrid`，PHCachingImageManager。

双端视觉对齐用设计 token（颜色、间距、文案 key），不共享 UI 代码。

---

## 11. 安全与隐私实现

- 无媒体上传；崩溃用 SDK 时 **关闭附件**。
- 日志：默认 info 不含路径，debug 才有 URI。
- SQLCipher 非必须；保险箱文件可考虑 iOS `FileProtectionComplete`、Android `EncryptedFile` 仅对 meta，原片太大不适合逐 byte 加密（会再占空间与 CPU）。依赖系统应用沙箱。
- 备份：Android Auto Backup 排除 `tmp/` 与 `vault/`（否则云备份用户原片，体积与隐私双问题）。iOS 对 vault 标 `NSURLIsExcludedFromBackupKey`。

---

## 12. 构建与 CI

### 12.1 Rust → Android

- `cargo-ndk` 产出 `arm64-v8a` `.so`
- `uniffi-bindgen` 生成 Kotlin
- Gradle 任务依赖 rust 构建；Debug 可只编当前 ABI
- 注意 16KB page size（Android 15+）：NDK r28+ / 正确链接
- R8 保留 UniFFI JNA/JNI 类

### 12.2 Rust → iOS

- `cargo build --target aarch64-apple-ios` 与 sim
- 打 `SsCore.xcframework`
- Swift Package 或 Xcode 直接链
- bitcode 已淘汰；注意隐私清单

### 12.3 CI

- 每次 PR：`cargo test`（domain 决策快照）+ clippy + fmt
- 夜构建：Android assemble + iOS simulator build
- 真机编码测试不进普通 PR（硬件相关），用样片矩阵手工/实验室

### 12.4 依赖许可证

- UniFFI / Rust Apache-2.0 MIT 优先
- mozjpeg 等需审
- 若引入 FFmpeg：LGPL 动态链接 + 用户可替换 .so 的合规流程；**不要 GPL 组件进商店包**
- HEVC：使用系统编码器，法务仍需评估发行地区；软件 x265 不进 V1

---

## 13. 测试策略

| 层 | 内容 |
|----|------|
| ss-domain 单测 | 预设映射、跳过、目标体积反推、分辨率向下对齐 |
| 黄金文件 | 小 JPEG/WAV/H.264 短视频（自产） |
| 平台 instrumentation | 一条短视频 HW 转码成功、相册写入 |
| 失败注入 | 磁盘满、取消、无权限、损坏文件 |
| 性能 | 50 张照片内存峰值、1080p 1min 耗时 |
| 兼容性实验室 | 高通/联发科/Exynos/麒麟；iPhone 12–最新 |

OEM 坑：部分安卓 HEVC 编码器码率忽高忽低、B 帧 mux 失败、旋转绿边。**机型黑名单 + 自动回退 H.264** 是稳定发布的必要条件。

---

## 14. 可观测性

端侧事件（无 PII）：

- `probe_done` {type, codec_family, w, h, duration_bucket}
- `job_finished` {result, preset, in_bucket, out_bucket, ratio, ms, hw:bool}
- `fallback` {from: hevc, to: avc, reason}

聚合后用于改进默认预设。开发者选项：导出一份不含媒体的 JSON 诊断。

---

## 15. 性能预算（旗舰机参考）

| 任务 | 预算 |
|------|------|
| 首页雷达 5000 张元数据 | 首次 < 8s 增量展示，不堵 UI |
| JPEG 4000px 均衡 | 单张 < 300ms |
| 50 张并行 | < 25s，内存峰值 < 400MB |
| 1080p30 H.264→HEVC 1min | ≤ 壁钟 60–90s（HW） |
| 4K60 | 不保证实时；UI 显示建议插电 |
| 库体积 | Android .so < 15MB（无 FFmpeg）；有 FFmpeg feature 另算 |

超预算先降：AVIF 级、图片并发、探针深度。
