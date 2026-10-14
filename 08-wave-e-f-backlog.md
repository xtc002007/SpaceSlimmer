# Wave E / Wave F 后置 backlog（Phase 1 边界仍有效）

> **状态：** 2026-09-11 — Android 已落地可在 Windows 完成的 E/F 产品项。Phase 1 仍免费、无 AVIF 默认、无 IAP 付费墙、无 iOS 可构建包。下列「不可做」项不要为凑完成度半套上线。

## Wave E — P1

| 项 | 说明 | 当前仓库状态 |
|----|------|--------------|
| 历史 Tab 深度 | 筛选 + JSON 诊断导出（不含媒体 URI） | **已做** `HistoryScreen` |
| EMA 校准 | `actual/predicted` 本地 EMA | **已做** Settings `estimate_scale_*`，成功任务回写 |
| 二次编码 | 超目标 15% 再压一遍 | **已做** `Recipe.target_bytes` + `second_pass_video_bitrate` + VideoTranscoder |
| 自定义全参数 UI | 分辨率/帧率/码率/编码器 | **已做** `VideoCustomPanel` |
| 仅音频 / 音轨降码 | `audio_copy` 开关 | **已做** 自动 / 复制 / 重编码 |
| Live / Motion Photo | 静图压，指纹 `is_live_photo` | **已做** MediaStore/XMP 探测 + 雷达标记 |
| 相册级一键 | 扫描结果一键入队 | **已做**「压缩全部扫描结果」；非无限全库 |
| 充电且闲时 | 提交后暂停，WorkManager 约束恢复 | **已做** `idle_charge_only` + `IdleChargeWorker` |
| BMP/TIFF 输入 | 解码进 `ss-image` | **已做** image crate `bmp`/`tiff` feature |
| AMR / OGG 输入 | 音频矩阵 P1 | **已做** probe 归一化 `amr`/`vorbis`/`opus` + Picker MIME；解码仍走系统 MediaCodec |
| SAF 树导出 | 照片/视频/音频结果页 | **已做** `ExportHelper` + `ExportToFolderButton`（另存相册仍在） |
| 端侧遥测 | `probe_done` / `job_finished` / `fallback` | **已做** SQLite `events` + `record_telemetry` / `telemetry_json`；历史 JSON 附带 |
| 观看报告 | 按类型节省 | **已做** `WatchReportScreen`（原 Wave F） |
| AV1 HW 输出 | caps 探测后 Media3/Plan B `video/av01` | **已做** 自定义面板芯片 + 失败回退 HEVC/H.264 |
| IAP / 付费墙 | BillingManager + StoreKit 2 | Android `FEATURE_IAP=false`；**不做半套** |
| AVIF 输出 | `avif` feature | 声明未默认启用；**不做半套 NDK** |
| MKV/WEBM | `ffmpeg` feature | **不做** |

## Wave F — P2

| 项 | 说明 | 当前仓库状态 |
|----|------|--------------|
| 片段压缩 | `trim_start_ms` / `trim_end_ms` + Media3 Clipping | **已做** 自定义面板秒级裁剪 |
| 批量重命名、按日期归档 | 另存命名 + 日期目录 | **已做** `ExportNaming` + 设置 |
| Shortcuts | 雷达 / 历史 / 压缩 / **近一周视频** | **已接线** `shortcuts.xml` extra |
| 小组件 | 本周已省 | **已做** `WeeklySavingsWidget` |
| 花屏警告 | 抽帧 luma 方差，非 CV 模型 | **已做** `FlatFrameDetector` → `quality.flat_frame` |
| 软件视频回退 | 纯 CPU libx264 | Plan B 仍是 MediaCodec，**非** libx264 |
| 桌面同源 | Windows/macOS 客户端 | 未开始 |
| 人脸区域剥离 | GPS 以外的区域隐私 | **不做半套 CV**；关闭 GPS 时已剥 Make/Model |
| iOS 对等 | XCFramework + Xcode App | 需 macOS；Windows 禁止空工程 |

## 与 Phase 1 的边界

- **可做（已做）：** 上表标「已做」的 Android 产品路径。
- **不可做：** 半套 IAP、默认打开 AVIF、假装 iOS 可构建、用估算填写 07 真机签字栏。
- **签收：** 07 仍是内测候选；A1/A2/A4 必须真机。
