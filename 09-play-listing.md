# Play / 商店上架材料包（Wave D3 文案）

> **状态：** 2026-09-11 文案包。未提交 Google Play / App Store，无签名上架包，无实拍截图。  
> **IAP：** Phase 1 免费；`FEATURE_IAP=false`。本文不要写成「含内购」。  
> **iOS：** 需 macOS + Xcode 后再补 App Store 截图与审核备注。

对外名：**SpaceSlimmer（空间瘦身）**。副标题沿用产品合同：压缩视频、照片、音频，不删回忆。

---

## 1. Google Play 列表

### 应用名称（≤50）

- 中文：`空间瘦身 SpaceSlimmer`
- 英文：`SpaceSlimmer`

### 简短介绍（≤80）

- 中文：`压缩视频、照片和音频，原片进保险箱，处理全程在本机。`
- 英文：`Compress video, photos, and audio on-device. Originals stay in a vault.`

### 完整介绍

中文：

```
空间瘦身把占空间的视频、照片和录音变瘦，但不删你的回忆。

• 空间雷达按「预计可省」排序，批量压缩
• 开始前给出体积区间；已经很瘦的文件会明确跳过
• 默认另存为新文件；替换原片会先备份到保险箱，到期前可恢复
• 视频走设备硬件编码（H.264 / HEVC，支持时 AV1），失败自动回退
• 照片与音频在本机处理，不上传服务器
• 中英界面；任务可暂停、取消，过热或低电会降速或询问

适合相册满了、不敢删原片、又想给手机腾出空间的人。
不是清理大师，也不是剪辑软件。
```

英文：

```
SpaceSlimmer slims video, photos, and recordings without deleting memories.

• Space radar ranks files by estimated savings, then batch-compresses
• Size range before you start; already-efficient files are skipped
• Save as new by default; replace originals only after a vault backup
• Hardware video encode (H.264 / HEVC, AV1 when the device supports it) with automatic fallback
• Photos and audio stay on-device — nothing is uploaded
• Chinese and English; pause, cancel, thermal and low-battery throttling

This is not a junk cleaner or a video editor.
```

### 分类与标签

- 类别：工具（Tools）
- 标签建议：storage、video compressor、photo compressor、gallery、offline

---

## 2. 权限审核说明（Play 声明用）

| 权限 | 用户可见理由（中） | 用户可见理由（英） |
|------|-------------------|-------------------|
| 照片 / 视频读取 | 扫描相册找出可压缩的视频和照片，并在你确认后读取所选文件。 | Scan the library for compressible video and photos, and read files you pick. |
| 音频读取 | 读取你选择的录音或歌曲以便压缩。 | Read audio files you choose so they can be compressed. |
| 通知 | 长时间压缩时显示进度；保险箱到期提醒。 | Show progress during long jobs and vault-expiry reminders. |
| 前台服务（数据同步） | 压缩可能数分钟，需要在通知栏保持任务存活，媒体不上传。 | Compression can take minutes; a foreground notification keeps the job alive. Media is not uploaded. |
| 开机完成 | 仅用于提示未完成的压缩队列，不自动开扫全盘。 | Remind you of unfinished jobs after reboot. Does not auto-scan the whole disk. |

SAF「导出到文件夹」在运行时由系统文档树授权，不预声明 `MANAGE_EXTERNAL_STORAGE`。

---

## 3. Data safety 表（按当前实现勾）

| 问题 | 答案 |
|------|------|
| 是否收集个人数据 | **否**（无账号、无云同步） |
| 是否分享给第三方 | **否** |
| 是否加密传输 | 不适用（无上传） |
| 用户能否请求删除 | 用户可删保险箱与历史；无服务端副本 |
| 媒体文件 | **不收集**。仅本机读写用户选出的文件 |
| 诊断事件 | 仅本机 SQLite：`probe_done` / `job_finished` / `fallback` 等，含类型/体积档/耗时档，**不含路径、文件名、缩略图**。历史页可导出 JSON，仍不含媒体 |
| 大致位置 / 精确位置 | 不收集。关闭「保留 GPS」时从输出中剥离 GPS 与设备型号 |
| 财务 / 购买 | 当前免费，无结算 |

若日后打开 IAP，必须另开表：购买记录走 Play Billing，仍不上传媒体。

---

## 4. 隐私政策要点（可粘贴到托管页）

1. 媒体始终在设备本地处理，开发者不接收原片或压缩结果。
2. 崩溃日志不得包含媒体内容或文件路径。
3. 可选诊断事件只含文件类型、编解码家族、体积/时长档位、结果与是否硬件编码。
4. 保险箱与任务历史存在应用私有目录，卸载即删除。
5. 联系方式：上架前补运营邮箱（本文不编造）。

---

## 5. 截图说明（待实拍，禁止用估算图冒充真机）

建议顺序（手机竖屏）：

1. 首页空间雷达：大字「预计可省」+ Top 列表  
2. 视频预设目的卡（均衡 / 指定体积）  
3. 任务中心进度  
4. 照片前后滑杆对比  
5. 结果页已省卡片 + 导出到文件夹  
6. 保险箱列表  

每张配文只讲可见功能，不承诺「最高压缩 90%」。

---

## 6. App Store（占位，等 Mac）

- 名称：SpaceSlimmer
- 副标题：Compress video, photos, audio
- 关键词草案：compress, video, photo, storage, vault, offline
- 审核备注：PhotoKit 仅用于用户选中的资源；替换前写入保险箱；无账户。
- 隐私清单：相册目的字符串必须写「压缩用户选择的照片和视频」。

Windows 主机**不能**产出可提交的 iOS 包。

---

## 7. 明确不要写入商店页的话

- 不要写已支持 iOS / IAP / AVIF / FFmpeg / 桌面版。
- 不要写「AI 花屏检测」——实现是抽帧亮度方差启发式。
- 不要用未签字的 07 真机数据当宣传数字。
