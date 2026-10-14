# SpaceSlimmer 第一阶段签收报告

> **版本：** 0.1.0 / preset_v1.0  
> **平台：** Android（minSdk 26）  
> **日期：** 2026-09-11  
> **状态：** 内测候选，**未签收**（代码与主机门就绪；待真机签字）

## 1. 交付范围（诚实状态）

| 维度 | 完成度 | 说明 |
|------|--------|------|
| Phase 1 工程闭环 | **~93%** | Rust workspace 测试 + Debug APK 可编；**未真机签收** |
| Android V1 §17 产品面 | **~93%** | 雷达/预设/保险箱/历史报告/混合单 group + A11y/Opus 门控/时长校验已落地；待真机验收 |
| 全量 V1（Android+iOS+上架） | **~48%** | iOS 无 Xcode 工程；Play 文案包见 `docs/09-play-listing.md`（未提交商店） |
| 含 P1/P2 文档项 | **~64%** | Android 已落地 EMA/二次编码/自定义全参/混合单 group/历史 JSON/实况探测/片段/报告/小组件/批量重命名归档/AV1 HW/SAF 树导出/AMR+OGG 输入/端侧遥测/花屏启发式；iOS/IAP/AVIF/桌面/libx264/人脸 CV 仍未做 |

| Sprint | 状态 | 说明 |
|--------|------|------|
| Sprint 0 工程骨架 | ✅ | UniFFI + Compose 空壳 |
| Sprint 1 决策引擎 | ✅ | `ss-domain` 纯函数 + 快照测试 |
| Sprint 2 任务系统 | ✅ | 队列、SQLite v4（vault compressed_uri）、崩溃恢复、暂停/取消 |
| Sprint 3 照片闭环 | ✅ | Rust `ss-image` 后台线程 + EXIF/GPS |
| Sprint 4 音频闭环 | ⚠️ | AAC MediaCodec；Opus 通过 `opus-encode` feature。Linux CI 以 `--features opus-encode` 编 `.so`；Windows 交叉编译可能仍需 `CMAKE_POLICY_VERSION_MINIMUM=3.5` 或暂关 feature |
| Sprint 5 视频 HW | ⚠️ | Media3 主路径；Plan B：VBR + Media3 缩放预过 + EGL blit；真机矩阵未全覆盖 |
| Sprint 6 产品表面 | ⚠️ | 雷达 5000 + MediaStore 元数据快路径 + 墙时；确认页单类型复用照片/视频/音频预设；混合走目的卡后进 Tasks |
| Sprint 7 质量门 | ⚠️ | 自动化门 + instrumentation：JPEG `submitGroup`、GIF/DNG skip 文案、WAV→Opus（缺 feature 则 skip）、短 MP4 HW（失败 skip）；**真机 smoke 待签字** |

**不再使用单一「88%」数字**；签收以 §3 检查单 + 真机签字栏为准。

## 2. 已知偏差

| 项 | 计划 | 实际 |
|----|------|------|
| Opus Rust 编码 | Sprint 4 | `opus-encode` feature；Linux CI / **Release** `assembleRelease` 启用；Windows **Debug** 默认 `--no-default-features`（UI 隐藏 Opus）；可 `local.properties` 覆盖 |
| mozjpeg NDK | Sprint 3 | **未实现**；使用 `image` crate + 解码前 `max_side` 缩放。A2 峰值超 400MB 或小体积 JPEG 不瘦时再评估，不为勾选引入 NDK 失败 |
| oxipng | Sprint 3 | **未实现**（同上） |
| MediaCodec Plan B | Sprint 5 | Media3 主路径；失败时 `VideoScalePrePass` + `MediaCodecPlanB`（VBR + EGL 缩放到 recipe 尺寸）+ HEVC 黑名单 |
| ss-probe | Sprint 3/5 | UniFFI `probe_image_dimensions_l2` / `probe_mp4_ftyp_l2` / `probe_mp4_video_dimensions_l2` 主路径；Kotlin `L2Probe` 仅 fallback。`ss-platform` 仍是文档 marker，不为勾选实现 |
| WebP 有损 | Sprint 3 | **有损**（`webp` crate）；quality≥95 仍走无损 WebP 编码 |
| 热电/低电 | Sprint 6 | `SystemMonitor` → `updateThermal`/`updatePower`；低电暂停 + 充电自动 resume |
| AlreadyEfficient（HEVC/AV1） | 2026-09-11 | 已修正 `src_bps ≤ target×1.1` 判定顺序（先于 EstimateNotWorth） |
| 音轨 copy 预估 | 2026-09-11 | 预估计入源码率，不再当 0 |
| 视频时长校验 | 2026-09-11 | `max(0.5s, 2%)` 合同；`fail.invalid_output` |
| WebP 质量档 | Sprint 3 | High=80（与 03 表一致）；JPEG 仍 82 |
| Opus UI | Sprint 4 | `opus_encode_available()` + 缺 feature 时隐藏 Opus 芯片 |
| 真机 smoke | Sprint 7 | 见 `docs/smoke-checklist.md` + `packaging/run-device-smoke.ps1` + `packaging/record-device-signoff.ps1`，尚未签字 |
| testdata 二进制 | Sprint 7 | `packaging/prepare-testdata.ps1` 本地生成；不入库 |

## 3. 验收检查单

- [x] 三种媒体 Picker → 预设 → 批量压缩 → 结果 → 另存/替换（代码路径）
- [x] `decide` 使用设置中的跳过阈值
- [x] 取消/暂停先停平台编码器；完成回调校验 Running 状态
- [x] 保险箱 `vault_path` + `original_uri`；恢复写回原 URI
- [x] 替换失败不再静默另存；不可写 URI 禁用替换策略
- [x] 雷达混合队列单次 `submit_group`（每项 `request_override`）
- [x] Release `packaging/check-so-size.ps1` + `packaging/run-quality-gate.ps1`
- [x] 失败注入自动化（磁盘满停队列、崩溃清 tmp、跳过设置、图片取消）— 见 `docs/failure-injection.md`
- [ ] **真机 smoke 全绿**（`docs/smoke-checklist.md` + `packaging/run-device-smoke.ps1`）
- [ ] OEM HEVC/磁盘满/权限边界矩阵（手动项见 `docs/failure-injection.md` § Manual）
- [ ] **内存 profiling**（50 JPEG 批次峰值，见 `packaging/memory-profiling-guide.md`）
- [x] **签收前自动化门（主机）** — `packaging/validate-signoff-readiness.ps1`（不替代真机 smoke / 内存 / OEM 矩阵）

### 真机签字栏（勾完再改本文状态为已签收）

| 类别 | 签字人 | 日期 | 备注 |
|------|--------|------|------|
| Photo | | | |
| Video | | | |
| Audio | | | |
| Product | | | |
| Quality gate | | | |
| Failure injection (manual) | | | |

## 4. 内存 profiling（待填写）

按 [packaging/memory-profiling-guide.md](../packaging/memory-profiling-guide.md) 在真机上跑 50 张 JPEG 批量压缩，用 Android Studio Profiler 记录峰值 native heap：

| Metric | Value | Device | Date |
|--------|-------|--------|------|
| Peak native heap (50 JPEG batch) | _待测_ | | |

不要用估算代替签字。若 OOM：再降 `DEFAULT_IMAGE_PARALLELISM` 或收紧 `max_side`。

## 4.1 雷达扫描墙时（待填写）

合同：5000 项全量扫描 **< 8s**。UI 展示 `elapsedMs`。代码路径：MediaStore WIDTH/HEIGHT/DURATION 快路径 + `decideBatch`，缺尺寸才 L2/Rust probe。

| Metric | Value | Device | Date |
|--------|-------|--------|------|
| Radar 5000-item wall clock (ms) | _待测_ | | |

## 5. 构建验证

```powershell
cd rust; cargo test --workspace
$env:ANDROID_NDK_HOME=[IO.Path]::Combine($env:ANDROID_HOME,"ndk","28.0.13004108")
cd android; .\gradlew :app:assembleRelease
cd ..\packaging; .\check-so-size.ps1
```

## 6. 第二阶段入口

- iOS SwiftUI + AVFoundation 对等实现（需 macOS）
- 热/电降级与 OEM HEVC 黑名单加固（Android 代码已有，待真机矩阵）
- Play / App Store 上架：文案包见 [09-play-listing.md](09-play-listing.md)，尚未提交商店
