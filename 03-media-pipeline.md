# 媒体管线、预设算法与格式矩阵

本文把产品档位翻译成可实现的算法。所有数字均为 **V1 默认值**，必须可在 `ss-domain` 配置，便于实验室调参而不改 UI。

---

## 1. 管线总览

```
Source URI
    │
    ▼
 Probe  →  MediaFingerprint
    │
    ▼
 Decide →  Skip | Recipe
    │
    ▼
 Estimate →  size_range, time_class   （给 UI，不阻塞开始）
    │
    ▼
 Execute
    ├─ video: Platform HW (primary) / software (optional)
    ├─ image: ss-image
    └─ audio: ss-audio / platform AAC
    │
    ▼
 Validate  →  Commit  →  Index
```

Decide 是纯函数：`(fingerprint, user_preset, hw_caps, settings) -> Decision`。必须单测快照。

---

## 2. 视频

### 2.1 分辨率对齐

输出宽高：

1. 按用户档得到 `max_long_edge`：
   - 保持原样：源 long edge
   - 4K：3840
   - 1080p：1920
   - 720p：1280
   - 540p：960
2. `out_long = min(src_long, max_long_edge)`
3. 短边按源宽高比计算，**都对齐到 16**（有的编码器要对齐 2 或 8，以设备 caps 为准，失败则再对齐 16）。
4. 竖屏：long edge 是高。

质量档默认 max_long_edge：

| 档 | max_long_edge |
|----|----------------|
| 极致画质 | 源（不降） |
| 高清 | min(源, 1920) 若源≥2160；若源≤1920 则保持 |
| 均衡 | min(源, 1920) |
| 小体积 | min(源, 1280) |
| 极简 | min(源, 960) |

说明：iPhone 4K 的最大杠杆是 **4K→1080p**，不是再堆 HEVC。产品「均衡」在双端都降到 1080p 封顶，避免 iOS 上“压了没变小”。若用户选极致画质，则保持 4K 只降码率。

### 2.2 帧率

- 源 ≤ 30：保持。
- 源 48–60 且档位为小体积/极简：降到 30。
- 均衡：60 可保持（流畅是卖点），但码率按 60 的表走。
- 120/240 慢动作：V1 **跳过**（产品定义），避免破坏时间元数据。

### 2.3 编码器选择

```
if user.compatibility == Compatible:
    video_codec = H264
elif user.compatibility == Efficient:
    if caps.hevc_hw: HEVC
    else: H264
elif user.compatibility == MoreEfficient:
    if caps.av1_hw: AV1
    elif allow_software_av1 and not thermal: AV1_SW  # P1
    elif caps.hevc_hw: HEVC
    else: H264
```

容器 V1 固定 MP4。

### 2.4 码率模型（核心）

硬件编码器没有可靠 CRF，V1 用 **约束 VBR 目标码率**。

**步骤 A：源有效码率**

```
src_v_bitrate = max(0, (file_size * 8 / duration_s) - src_audio_bitrate)
```

若缺 duration，则不估计码率，走分辨率档的 floor 表。

**步骤 B：编码效率系数**（相对 H.264）

| 输出编码 | coeff |
|----------|-------|
| H.264 | 1.00 |
| HEVC | 0.60 |
| AV1 | 0.45 |
| VP9 | 0.55 |

**步骤 C：质量系数 q**

| 档 | q |
|----|---|
| 极致 | 0.85 |
| 高清 | 0.70 |
| 均衡 | 0.55 |
| 小体积 | 0.40 |
| 极简 | 0.28 |

**步骤 D：分辨率缩放**

```
pixels_ratio = (out_w * out_h) / (src_w * src_h)   # ≤ 1
fps_ratio    = out_fps / src_fps                   # 通常 1
```

像素不是线性质量，用开方缓和：

```
pixel_factor = sqrt(pixels_ratio)
```

**步骤 E：目标视频码率**

```
target = src_v_bitrate * q * coeff_out / coeff_in * pixel_factor * fps_ratio
```

`coeff_in`：源是 HEVC 用 0.60，H.264 用 1.00，AV1 用 0.45，未知当 1.00。

**步骤 F：夹紧到分辨率地板与天花板**

1080p30 SDR 建议地板/天花板（kbps）：

| 档 | H.264 | HEVC |
|----|-------|------|
| 极致 | 10–16 Mbps | 6–10 Mbps |
| 高清 | 6–10 | 4–7 |
| 均衡 | 4–7 | 2.5–4.5 |
| 小体积（720p） | 2.5–4 | 1.5–2.5 |
| 极简（540p） | 1.2–2 | 0.8–1.5 |

4K 地板约是 1080p 的 2.2–3 倍，不要按像素 4 倍堆（那是源码率虚高的原因）。

```
target = clamp(target, floor(out_res, codec, fps), min(ceil, src_v_bitrate * 0.95))
```

**永远不要比源视频码率更高**（除非源码率异常低到低于地板——此时若节省为负则 Skip）。

音频默认：

- copy 若源是 AAC 且码率 ≤ 目标音频档。
- 否则 AAC 160kbps（均衡/高清）、128（小）、96（极简）、96 单声道（语音视频会议类，可选）。

### 2.5 指定体积模式

```
target_total_bps = target_file_bytes * 8 / duration_s
audio_bps = decided_audio_bps
video_bps = target_total_bps - audio_bps - mux_overhead(~2%)
```

若 `video_bps` 低于该分辨率地板：

1. 自动降一档分辨率，重算；
2. 仍不够再降档；
3. 仍不够：告诉 UI「无法在保持可看的前提下达到该体积」，给出能达到的最小体积，让用户确认。

两遍编码（first pass）在移动 HW 上不现实。V1 单遍 VBR + 完成后若超过目标 15% 且用户要求严格，可选 **P1 再压一遍**。默认：尽力，结果可能 ±20%。UI 必须写「约」。

### 2.6 智能跳过（视频）

Skip if any：

1. 源时长 < 1s 且大小 < 2MB（往往是动态照片碎片，另案）。
2. `estimated_out > source * 0.92`（节省 < 8%）且绝对节省 < 5MB。
3. 源已是 HEVC/AV1 **且** 分辨率已 ≤ 目标档 **且** 源码率已 ≤ 目标码率 * 1.1。
4. 不支持的：VR、多视频轨、DRM、加密、HDR 无法处理、慢动作 120fps+。
5. 输出 codec 与源相同、分辨率相同、目标码率 ≥ 源码率。

Skip 不是失败，UI 聚合展示。

### 2.7 校验

- duration 差 > max(0.5s, 2%)：失败，不替换。
- 无视频轨：失败。
- 花屏检测 V1 不做 CV；依赖编码器正常 EOS。P2 可选警告：Android `FlatFrameDetector` 抽 3 帧算 luma 标准差，过低则 `quality.flat_frame` SnackBar + 遥测（不是机器学习、不拦替换）。

### 2.8 复制与重封装

若只需修容器（罕见）：`remux only`。V1 可不做，避免坏文件。

音轨 copy：PTS 与视频对齐失败时回退为转码音频。

---

## 3. 照片

### 3.1 决策树

```
if GIF 动画: V1 skip 或只处理静态 GIF
if RAW: skip
if PNG 且有 alpha:
    高清/均衡 → WebP/AVIF 保透明；若用户要 JPEG → 白底合成并提示
if PNG 截图（无 alpha，颜色少）:
    转 JPEG q=0.82 或 WebP
if HEIC:
    极致：可能 skip（已高效）或再压质量 0.8
    均衡：转 JPEG/AVIF（用户选格式）
if JPEG:
    mozjpeg 质量映射，保持或缩小边长
```

### 3.2 质量映射

| 档 | JPEG q | WebP q | AVIF q（rav1e speed 6） |
|----|--------|--------|-------------------------|
| 最高 | 90 | 90 | 80 |
| 高清 | 82 | 80 | 60 |
| 小体积 | 70 | 70 | 40 |

Mozjpeg：progressive + trellis。  
缩小：仅当 long_edge > 用户上限。默认均衡 **不强制缩小**，只再压缩；小体积档上限 1920。

### 3.3 跳过

- 输出预估 ≥ 源的 90% 且差 < 200KB。
- 已经是很小的 JPEG（< 150KB）且非 PNG。
- 解码失败。

### 3.4 EXIF

- 先解码方向，像素转正或写入 Orientation=1。
- 复制 DateTimeOriginal。
- GPS：设置开关。

---

## 4. 音频

| 档 | 采样 | 声道 | 编码 | 码率 |
|----|------|------|------|------|
| 语音 | 32–48k | mono | AAC/Opus | 64 kbps |
| 音乐高品质 | 44.1/48k | stereo | AAC | 192 kbps |
| 音乐小体积 | 44.1/48k | stereo | AAC/Opus | 96 kbps |

探测：

- 单声道语音 WAV 自动选语音档（UI 可预勾）。
- 源码率 ≤ 目标 * 1.1 且已是有损：skip。
- FLAC/WAV 永不 skip（除非用户选保持无损）。

峰值归一化 V1 不做，避免改变听感责任。

---

## 5. 体积预估

给 UI 的是 **区间**，不是单点。

```
out_hat = f(...)   # 上面的模型
low  = out_hat * 0.80
high = out_hat * 1.15
```

视频 HW 波动大，区间更宽：0.75–1.25。  
批量总和用各文件 mid 求和，再显示「约 x–y GB」。

预估必须 **< 2ms/文件**（纯算术）。禁止为预估做一次真实编码。

校准：每台设备完成后用 `actual/predicted` 的 EMA 存在本地，修正该设备系数（P1）。V1 可用固定表。

---

## 6. 耗时预估档

```
if all images/audio: Fast
elif video_hw and long_edge <= 1920 and duration_sum < 10min: Medium
else: PlugIn
```

Android 可参考历史 `ms per output_MB`。

---

## 7. 格式矩阵（实现优先级）

### 7.1 视频

| 输入容器 | 输入编码 | V1 | 备注 |
|----------|----------|----|------|
| MP4/MOV | H.264 | 必须 | |
| MP4/MOV | HEVC | 必须 | |
| MP4 | VP9 | 尽力 | 解码 HW 不一定有 |
| WEBM | VP9/AV1 | P1 | |
| MKV | H.264/HEVC | P1 | 需 FFmpeg 或 demux |
| AVI/FLV | | P2 | |
| MP4 | AV1 | 尽力 | 解码可能慢 |
| 任何 | Dolby Vision profile 5 | skip | 直到 HDR 策略完成 |

输出：MP4 + H.264 Baseline/Main/High（High 默认）或 HEVC Main。音频 AAC-LC。

### 7.2 图片

| 入 | 出 JPEG | WebP | AVIF | PNG |
|----|---------|------|------|-----|
| JPEG | 必须 | 必须 | P1 | 否 |
| PNG | 必须 | 必须 | P1 | 必须（oxipng） |
| WEBP | 必须 | 必须 | P1 | 否 |
| HEIC | 必须 | P1 | P1 | 否 |
| GIF 静 | 必须 | P1 | | |
| GIF 动 | skip/P1 | | | |

### 7.3 音频

| 入 | AAC | Opus | MP3 |
|----|-----|------|-----|
| WAV | 必须 | 必须 | P1 |
| FLAC | 必须 | 必须 | P1 |
| MP3 | 必须 | P1 | 必须（再压） |
| M4A | 必须 | P1 | P1 |
| OGG | P1 | 必须 | P1 |

---

## 8. 硬件能力探测伪代码

```
caps.hevc_enc = exists encoder mime=hevc, max>=1920x1080, hardware=true
caps.av1_enc  = same for av01
caps.hevc_4k  = max>=3840x2160
caps.hdr10    = encoder color transfer PQ supported
```

Android 判断硬件：`!name.startsWith("OMX.google") && !isSoftwareOnly`。  
某次 HEVC 失败（error/code）→ 本 session 回退 H.264，并记入机型计数；达阈值写本地 blacklist。

---

## 9. 颜色、HDR、旋转

- SDR：BT.709，encoder color range 与源一致（TV/full），不一致时优先 TV。
- HDR V1：skip 或 tone-map SDR（若平台 Transformer 支持，可作为「转 SDR」显式选项，默认 skip 更安全）。
- 旋转：优先 metadata；若编码器忽略 rotation 则 bake transform。
- 镜像前置摄像头视频：保留源 transform。

---

## 10. 内存与 4K

4K RGBA 一帧 ~33MB，YUV 更小但仍高。视频在平台管线应 **surface/yuv 直通**，不要把每帧拉进 Rust。

Rust 禁止为视频实现 naive RGB frame loop，除非软件回退路径且强制降到 1080p。

图片：decode 到 max 边后再 encode；Android 可 `inSampleSize`。

---

## 11. 实验室调参流程

1. 固定 20 条样片：安卓 H.264 1080p、iPhone HEVC 4K、夜景、运动、人脸特写、屏幕录制。
2. 出包后人工 1–5 分 + 体积比。
3. 只改 `ss-domain` 常量，不动 UI。
4. 冻结一组 `preset_v1.0` 版本号写入输出 ffprobe comment 或 sidecar（可选 `me=ss/preset_v1.0`）便于售后。

输出 MP4 metadata：`encoder=SpaceSlimmer`、`preset=`、`src_hash 短`（隐私：默认只写 preset，不写路径）。
