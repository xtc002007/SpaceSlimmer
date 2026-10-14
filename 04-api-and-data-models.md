# UniFFI API 与数据模型

> **文档版本：** 2026-09-11（Wave D2 遥测）— 补充 `record_telemetry` / `telemetry_json`；`flush`、`extend_vault_item`、`set_dual_video_hw`、`purge_expired_vault`、前后台暂停/恢复仍有效。移除 `pause_policy` 枚举，改由 `pause_on_background` / `resume_on_foreground` 显式调用。

本文定义 `ss-core` 对外契约。Kotlin/Swift 名称由 UniFFI 生成，逻辑字段保持一致。实现时以本文为接口评审基线。

---

## 1. 设计原则

- 对外 API 面尽量小：一个 `CoreEngine` + 若干值类型 + 一个观察者。
- 所有路径用 UTF-8 字符串；Android 用 content URI 字符串，iOS 用 file URL 或 `ph://` 包装后再由平台层解析。
- **Rust 不直接读 content://**。平台 Gateway 把可读的绝对路径、或把文件 copy/fd 到可访问路径，再把 `LocalMediaRef` 交给核心。长期可用「平台 Fd 转储」避免复制：V1 允许对 Picker 大视频 **不复制**，由 `VideoTranscoder` 直接吃 URI（见双路径）。

### 1.1 双路径 I/O

| 媒体 | 决策/预估 | 执行 |
|------|-----------|------|
| 视频 | 平台 probe 结果传入 Rust | 平台 transcoder 用 URI |
| 图片 | 可 copy 到 tmp 或平台解码 RGBA | Rust 编码写 tmp |
| 音频 | 文件路径最简单 | Rust 或平台 |

因此 Job 创建时携带：

- `display_uri`（给 UI）
- `probe`（已填好的指纹，或让 core 调平台）
- `backend_hint`（HwVideo / RustImage / RustAudio）

---

## 2. 核心类型

```rust
enum MediaKind { Video, Image, Audio }

enum QualityPreset {
  VideoExtreme, VideoHigh, VideoBalanced, VideoSmall, VideoTiny,
  VideoTargetSize, VideoCustom,
  ImageMax, ImageHigh, ImageSmall,
  AudioSpeech, AudioMusicHigh, AudioMusicSmall,
}

enum Compatibility { Compatible, Efficient, MoreEfficient, KeepContainer }

enum OutputPolicy { SaveAsNew, ReplaceAfterConfirm, ReplaceToVault }

enum JobState {
  Queued, Running, Paused, Cancelling,
  Succeeded, Skipped, Failed, CommittedReplace
}

enum SkipReason {
  AlreadyEfficient, TooSmall, EstimateNotWorth,
  WouldGrow, UnsupportedHdr, UnsupportedFps,
  UnsupportedCodec, UserDisabled, TargetSizeUnreachable
}
```

输出时长校验失败（与源差 > 阈值）走 `FailReason::InvalidOutput`，**不是** SkipReason。没有 `SkipReason::DurationMismatch`。

任务 `message` 本地化 key：

| key | 含义 |
|-----|------|
| `skip.already_efficient` | 已经很瘦 |
| `skip.too_small` | 过小 |
| `skip.not_worth` | 收益不足 |
| `skip.would_grow` | 会变大 |
| `skip.hdr` | HDR |
| `skip.slowmo` | 高帧率/慢动作 |
| `skip.codec` | 不支持的编码 |
| `skip.user_disabled` | 用户关闭跳过以外的禁用 |
| `skip.target_unreachable` | 指定体积达不到 |
| `skip.gif` | GIF 动图（优先于 `skip.codec`） |
| `skip.raw` | RAW/DNG（优先于 `skip.codec`） |

辅助函数：`second_pass_video_bitrate(current_bps, output_bytes, target_bytes) -> Option<u64>`，输出超过目标 15% 时给出第二遍码率。

enum FailReason {
  ProbeFailed, DecodeFailed, EncodeFailed, MuxFailed,
  DiskFull, Permission, Cancelled, InvalidOutput,
  PlatformCrash, Unknown
}

enum TimeClass { Fast, Medium, PlugIn }
```

```rust
struct MediaFingerprint {
  kind: MediaKind,
  mime: String,
  container: String,
  video_codec: Option<String>,  // "h264" "hevc" "av1" "vp9"
  audio_codec: Option<String>,
  width: u32,
  height: u32,
  duration_ms: u64,
  fps: Option<f32>,
  rotation: u16,
  file_bytes: u64,
  video_bitrate_bps: Option<u64>,
  audio_bitrate_bps: Option<u64>,
  has_alpha: bool,
  is_hdr: bool,
  is_slowmo: bool,
  is_live_photo: bool,
  channels: Option<u8>,
  sample_rate: Option<u32>,
}

struct HwCaps {
  hevc_hw_encode: bool,
  av1_hw_encode: bool,
  hevc_max_w: u32,
  hevc_max_h: u32,
  av1_max_w: u32,
  av1_max_h: u32,
  max_image_side: u32,
}

struct VideoCustomParams {
  max_long_edge: Option<u32>,
  fps_cap: Option<f32>,
  video_bitrate_bps: Option<u64>,
  audio_bitrate_bps: Option<u64>,
  codec: Option<String>,
  audio_copy: Option<bool>,      // None=自动, Some(true)=复制, Some(false)=重编码
  trim_start_ms: Option<u64>,    // P2 片段
  trim_end_ms: Option<u64>,
  strict_target: Option<bool>,   // 超目标 15% 再压一遍
}

struct CompressRequest {
  preset: QualityPreset,
  compatibility: Compatibility,
  output_policy: OutputPolicy,
  target_bytes: Option<u64>,
  custom: Option<VideoCustomParams>,
  keep_gps: bool,
  keep_hdr: bool,          // false => skip hdr or tone-map if enabled
  skip_enabled: bool,
  image_format: Option<String>,  // "jpeg" "webp" "avif" "png"
  audio_format: Option<String>,  // "aac" "opus" "mp3"
}

struct Recipe {
  kind: MediaKind,
  // 视频
  out_w: u32,
  out_h: u32,
  fps: Option<f32>,
  video_codec: String,
  video_bitrate_bps: u64,
  audio_copy: bool,
  audio_codec: String,
  audio_bitrate_bps: u64,
  container: String,
  // 图片
  image_out_format: Option<String>,
  image_quality: Option<u8>,
  max_side: Option<u32>,
  // 音频
  audio_out_format: Option<String>,
  audio_channels: Option<u8>,  # 1 = mono speech
  // 片段 / 指定体积二次编码
  trim_start_ms: Option<u64>,
  trim_end_ms: Option<u64>,
  target_bytes: Option<u64>,
}

struct Decision {
  skip: bool,
  skip_reason: Option<SkipReason>,
  recipe: Option<Recipe>,
  estimate_bytes_low: u64,
  estimate_bytes_high: u64,
  time_class: TimeClass,
}

struct JobSpec {
  id: String,                 // uuid
  display_name: String,
  source_uri: String,
  local_path: Option<String>, // 平台可提供
  fingerprint: MediaFingerprint,
  request_override: Option<CompressRequest>, // 混合队列每项独立预设；None 用 group 请求
}

struct JobView {
  spec: JobSpec,
  state: JobState,
  progress: f32,
  decision: Option<Decision>,
  output_uri: Option<String>,
  output_bytes: Option<u64>,
  saved_bytes: Option<i64>,
  fail: Option<FailReason>,
  skip: Option<SkipReason>,
  message: String,            // 本地化 key 或已本地化短句
}

struct GroupView {
  id: String,
  jobs: Vec<JobView>,
  running: u32,
  done: u32,
  failed: u32,
  skipped: u32,
  saved_bytes: i64,
}
```

`saved_bytes` 在保险箱未清理时是「相册变瘦」而不是「磁盘变瘦」。UI 要同时展示 `vault_bytes`。

---

## 3. CoreEngine API

```text
CoreEngine.open(config: EngineConfig) -> Result<CoreEngine, SsError>   # UniFFI 构造；文档旧称 create

engine.opus_encode_available() -> bool   # 本机 .so 是否含 opus-encode

EngineConfig {
  data_dir: String,
  tmp_dir: String,
  vault_dir: String,
  parallel_profile: Auto | Eco | Fast
}

engine.set_observer(observer: CoreObserver)
engine.set_hw_caps(caps: HwCaps)
engine.update_thermal(level: 0-3)
engine.update_power(battery_pct, charging: bool)
engine.set_dual_video_hw(enabled: bool)   # 双路视频 HW 并行（默认关）

# 生命周期 / 保险箱维护
engine.flush() -> ()                      # 若 auto_delete_vault 则清理过期 vault
engine.purge_expired_vault() -> u32       # 无视 auto_delete，强制清理过期项，返回删除数
engine.pause_on_background()              # 暂停所有含 Running 任务的 group
engine.resume_on_foreground()             # 恢复 pause_on_background 暂停的 group（低电仍 true 时不恢复）

# 诊断（无 PII：禁止 URI / 文件名 / 路径）
engine.record_telemetry(event_type, payload_json) -> ()
engine.telemetry_json(limit) -> String    # JSON 数组 [{id,ts,type,payload}, ...]

# 纯计算，可同步（必须快）
engine.decide(fp: MediaFingerprint, req: CompressRequest, caps: HwCaps) -> Decision
engine.decide_batch(items, req, caps) -> Vec<Decision>

# 任务
engine.submit_group(jobs: Vec<JobSpec>, req: CompressRequest) -> String  # group_id
engine.pause_group(id)
engine.resume_group(id)
engine.cancel_group(id)
engine.cancel_job(id)
engine.retry_job(id)

engine.list_groups() -> Vec<GroupView>
engine.group(id) -> GroupView
engine.vault_usage() -> u64
engine.delete_vault_item(id)
engine.extend_vault_item(vault_id, days) -> VaultItemView
engine.list_vault_items() -> Vec<VaultItemView>
engine.register_vault_backup(job_id, vault_path, original_uri, original_bytes, expires_at) -> vault_id
engine.restore_vault_item(id) -> VaultRestoreView  # vault_path + original_uri
engine.mark_job_committed_replace(job_id)

engine.set_settings(Settings)
engine.settings() -> Settings
```

平台视频完成时必须回调核心（反向）：

```text
# 由 Kotlin/Swift 在 HW 完成/失败时调用
engine.on_platform_progress(job_id, ratio)
engine.on_platform_succeeded(job_id, output_path, output_bytes)
engine.on_platform_failed(job_id, FailReason, detail)
```

核心在 Decide 后若 recipe.kind==Video，通过 observer 请求平台执行：

```text
observer.request_start_video(job_id, recipe, source_uri, dest_path)
observer.request_start_audio(job_id, recipe, source_uri, dest_path)
observer.request_cancel_video(job_id)
```

这样 Rust 是编排者，Kotlin/Swift 是执行者。

---

## 4. CoreObserver（推到 UI 与平台）

```text
trait CoreObserver {
  fn on_group_changed(group: GroupView)
  fn on_job_changed(job: JobView)
  fn request_start_video(job_id, recipe, src, dst)
  fn request_start_audio(job_id, recipe, src, dst)
  fn request_cancel_video(job_id)
  fn request_notification(progress_text, ratio)
}
```

Kotlin 实现里 `request_start_video` 启动 Media3；结束调 `on_platform_*`。

**前后台：** 旧设计 `pause_policy(OnBackground)` 已移除。App `onStop` 调 `pause_on_background()`，`onStart` 调 `resume_on_foreground()`；低电时 `update_power` 置 `low_battery`，`resume_on_foreground` 不恢复直至充电或电量回升。

注意死锁：observer 回调里不要同步再进 engine 锁。实现用队列。

---

## 5. Settings

```text
struct Settings {
  default_preset_video: QualityPreset,
  default_preset_image: QualityPreset,
  default_preset_audio: QualityPreset,
  default_image_format: String,  # jpeg | webp
  default_compat: Compatibility,
  skip_enabled: bool,
  video_skip_ratio: f32,     # default 0.08
  video_skip_abs: u64,       # 5MB
  image_skip_ratio: f32,
  image_skip_abs: u64,
  keep_gps: bool,
  vault_days: u32,           # 7
  auto_delete_vault: bool,   # false
  parallel_profile: enum,
  language: String,
  estimate_scale_image: f32,  # EMA actual/predicted，默认 1.0
  estimate_scale_video: f32,
  estimate_scale_audio: f32,
  idle_charge_only: bool,     # 仅充电且闲置时跑队列
}
```

---

## 6. 错误模型

UniFFI `Result<T, SsError>`：

```text
enum SsError {
  InvalidArgument(String),
  Io(String),
  Db(String),
  NotFound,
  EngineBusy,
  Internal(String),
}
```

业务上的 Skip/Fail 走 JobView，不走异常。只有 API 用错才 `SsError`。

---

## 7. SQLite 表（schema v4）

实现：`rust/crates/ss-sqlite/src/schema.rs`（`SCHEMA_VERSION = 4`）。

```sql
-- v1
groups(id, created_at, request_json, state)
jobs(id, group_id, spec_json, state, progress,
     decision_json, recipe_json, output_uri, output_bytes, saved_bytes,
     skip_reason, fail_reason, message, updated_at)
vault(id, job_id, original_path, original_bytes, expires_at, restored)
settings(key, value_json)

-- v2: vault 可写 URI
ALTER vault ADD vault_path, original_uri

-- v3: 诊断事件（无 URI）
events(id, ts, type, payload_json)

-- v4: 替换流水中压缩件 URI
ALTER vault ADD compressed_uri
```

`jobs` 不再使用布尔 `skip`/`fail` 列；状态在 `state` + `skip_reason` / `fail_reason` / `message`。`decision_json` 保存完整 `Decision` 快照供历史/report。

迁移用版本号表 `schema_migrations`（当前写入 **4**）。

---

## 8. 平台补充 API（不进 Rust，但双端应对齐）

Kotlin/Swift Gateway：

```text
probe(uri) -> MediaFingerprint
pickMedia(...)
saveToGallery(path, kind) -> gallery_uri
deleteGalleryAsset(uri)
copyToVault(uri) -> vault_path
thermal() / battery()
hwCaps() -> HwCaps
startForegroundService()
```

`MediaFingerprint` 字段双端填充规则要写测试：同一文件在 Android/iOS 的 codec 字符串枚举一致（小写、无 profile 前缀）。

---

## 9. 本地化 key（错误与跳过）

Rust 只出稳定 key，UI 翻译。

```
skip.already_efficient
skip.too_small
skip.not_worth
skip.would_grow
skip.hdr
skip.slowmo
skip.codec
fail.encode
fail.disk_full
fail.permission
fail.invalid_output
```

---

## 10. 版本

`engine.version() -> "0.1.0"`  
`engine.preset_table_version() -> "preset_v1.0"`

输出文件 comment 与诊断里带上，便于对照实验室。
