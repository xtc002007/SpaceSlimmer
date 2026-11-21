# 10. 存储全景与磁盘分析设计规范（Storage Analyzer）

> **状态**：已批准（设计合同草案）  
> **设计基准**：净室重写借鉴 WinDirStat（GPL-3.0 隔离，纯算法引用 Squarified Treemap 与 Cushion Shading 论文）  
> **关联文档**：[01-product-design.md](01-product-design.md), [02-technical-architecture.md](02-technical-architecture.md), [04-api-and-data-models.md](04-api-and-data-models.md), [05-roadmap.md](05-roadmap.md)

---

## 1. 目标与产品定位

SpaceSlimmer 从单一“媒体压缩”工具升级为**全流程手机存储瘦身闭环**：
```
[ 存储全景扫描 ] ──> [ 图形化多维展现 ] ──> [ 精准定位大头/重复 ] ──> [ 处置：压缩 / 删除 / 回收 ] ──> [ 效果对账 & 历史记录 ]
```

### 1.1 核心原则
1. **真实与诚实**：受 Android 分区存储限制，扫不到的区域（系统/私有应用数据）通过伪节点 `<系统/不可访问>` 诚实展现差额，不造假。
2. **安全网护航**：删除动作默认移入系统回收站或 SpaceSlimmer 保险箱（Vault），支持宽限期反悔；敏感系统目录与 Android 保护目录设为只读。
3. **Rust 跨端与净室设计**：核心树结构、遍历、分类、Treemap 布局全部在 Rust 实现，零依赖 WinDirStat 源码，避免 GPL 传染风险。

---

## 2. 三级数据源架构与 Android 权限体系

| 级别 | 数据源 | 权限要求 | 覆盖范围 | 预期耗时 | 渠道分发策略 |
|---|---|---|---|---|---|
| **L1 (快速)** | `MediaStore` 查询 | `READ_MEDIA_IMAGES/VIDEO/AUDIO` | 音视频与照片媒体库 | 1~3 秒 | Google Play 默认 |
| **L2 (自定义)** | SAF 目录树 (`ACTION_OPEN_DOCUMENT_TREE`) | 运行时单目录授权 | 用户手动选定文件夹（含 Downloads、文档等） | 视文件数（较慢，~10-20s/万文件） | Google Play 默认 |
| **L3 (全盘)** | Native `std::fs` 直接遍历 | `MANAGE_EXTERNAL_STORAGE` | `/storage/emulated/0` 几乎全量共享存储 | 5~15 秒（10 万文件） | 独立渠道 / 待申诉包 |
| **系统补充** | `StorageStatsManager` | `PACKAGE_USAGE_STATS`（可选引导） | 系统总空间、应用已用、数据、缓存大小 | 瞬间 | 辅助计算 Unknown/FreeSpace |

---

## 3. Rust 模块划分与核心设计

### 3.1 Crate 规划
- `ss-scan`：树型 Arena 结构（`ScanTree` / `Node`）、遍历调度器、多线程 Worker（受温控与电量约束）、过滤器。
- `ss-classify`：基于文件扩展名、MIME、魔数的数据驱动分类器（内置版本号 `classify_v1.0`）。
- `ss-treemap`：Squarified 树图布局计算器、Cushion 阴影参数、小块智能折叠与命中测试。
- `ss-dupe`：三段式（大小初筛 → 采样哈希 → 全量哈希）重复文件检测。
- `ss-sqlite`：升至 Schema v5，支持扫描历史、节点索引与分类统计。

### 3.2 节点压缩与内存保护策略
手机端常见数十万小文件，为防止内存溢出（OOM）：
1. **小文件折叠**：单文件 `< 256KB`（可配置）不生成独立子节点，直接聚合入目录下的 `<其他小文件>` 虚拟节点。
2. **Arena 紧凑存储**：节点使用 `u32` 索引指针代替堆指针，单节点控制在 48~56 字节。
3. **增量动态更新**：删除或压缩处置后，父链执行 `UpwardSubtract` 更新大小与文件计数，无需全局重扫。

---

## 4. 可视化与交互规格

1. **全景健康条**：已用 / 空闲 / 各分类（视频/图片/音频/压缩包/文档/应用/系统）堆叠占比。
2. **Treemap 矩形树图**：
   - 采用 Squarified 算法保证最佳长宽比；
   - 采用 1~2 层逐级下钻方案，单屏渲染控制在 ≤ 2000 块以内，保持 60fps；
   - 颜色按文件主导类别染色，目录按类别微色调渲染底框。
3. **多维视图切换**：
   - 地图视图 (Treemap)
   - 树型列表视图 (File Tree View，带比例柱状图)
   - 分类统计视图 (Category & Extensions)
   - 大文件榜 Top N
   - 重复文件扫描 (Duplicates)

---

## 5. 处置链路与现有系统融合

1. **压缩联动**：选中目录或文件中的音视频/图片，一键生成 `CompressPlan`，直接交给 `ss-jobs` 调度器执行现有的无损/高保真压缩流程。
2. **删除与回收**：
   - MediaStore 项：调用系统 `createTrashRequest` 移入回收站。
   - 物理路径项：移动到应用 Vault，设定宽限期自动清理。
   - 删除成功后通知 Rust 层 `apply_removed(node_ids)` 增量扣减。
3. **保护路径名单**：系统关键目录、应用私有数据目录一律打上 `PROTECTED` 标记，禁止直接删除。

---

## 6. 数据存储与导出规格

1. **SQLite Schema v5**：
   - `scans`：扫描记录（时间、范围、耗时、分类统计快照）
   - `scan_nodes`：持久化大文件与目录节点
   - `scan_category_stats`：分类快照
2. **导出能力**：
   - **CSV 导出**：列格式与 WinDirStat 兼容，便于在 PC 端对比与专业审计。
   - **JSON / PNG**：便携分享卡片与诊断数据。

---

## 7. 里程碑交付计划 (M0 - M6)

- **M0（1周）**：技术 Spike，真机实测 L1/L2/L3 耗时与 Treemap 帧率。
- **M1（2周）**：Rust `ss-scan` / `ss-classify` / `ss-treemap` 核心与 CLI 工具（与 WinDirStat 对账）。
- **M2（2周）**：Android 平台层 Lister（MediaStore / SAF / Native）与前台扫描服务。
- **M3（2.5周）**：Compose 存储全景 UI、实时地图与多维视图。
- **M4（2周）**：处置动作闭环（删除/回收/压缩联动/增量更新）。
- **M5（2周）**：SQLite v5 历史存储、版本对比、CSV 导出、大文件榜与重复文件。
- **M6（1.5周）**：真机 OEM 矩阵加固与政策合规。
