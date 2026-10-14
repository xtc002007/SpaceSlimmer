# SpaceSlimmer

跨平台手机媒体压缩应用：在尽量保住清晰度与流畅度的前提下，压缩视频、音频、照片，把被占用的存储空间还给用户。

- **Android**：Kotlin + Jetpack Compose + Rust Core
- **iOS**：Swift + SwiftUI + 同一套 Rust Core（第二阶段）
- **共享引擎**：Rust（UniFFI 导出 Kotlin / Swift 绑定）

本仓库是 SpaceSlimmer 的 Monorepo：**Rust 共享核心 + Android 产品实现**（第一阶段），附带完整设计文档与签收清单。实现争议以 `docs/` 为合同；签收状态以 [07-phase1-signoff.md](docs/07-phase1-signoff.md) 为准。

## 开发阶段

| 阶段 | 目标 | 周期 | 文档 |
|------|------|------|------|
| **第一阶段** | Android 产品级：三种媒体可真实使用，Rust API 冻结 | 约 14–16 周 | [06-phase1-development-plan.md](docs/06-phase1-development-plan.md) |
| **第二阶段** | iOS 对等 + 双端加固 + Play / App Store 上架 | 约 10–12 周 | [05-roadmap.md](docs/05-roadmap.md) §1.2 |

**当前状态：** 第一阶段 **内测候选**（三种媒体闭环 + 雷达/保险箱/设置已接线）。真机 smoke 与 OEM 矩阵见 [docs/smoke-checklist.md](docs/smoke-checklist.md)、[docs/07-phase1-signoff.md](docs/07-phase1-signoff.md)。iOS 仍为 Phase 2 占位。

## 文档索引

| 文档 | 内容 |
|------|------|
| [docs/00-overview.md](docs/00-overview.md) | 一页总览：问题、原则、架构结论、里程碑 |
| [docs/01-product-design.md](docs/01-product-design.md) | 产品定位、用户、功能、交互、预设、商业化、指标 |
| [docs/02-technical-architecture.md](docs/02-technical-architecture.md) | 分层架构、Rust Core、平台层、并行、存储、安全 |
| [docs/03-media-pipeline.md](docs/03-media-pipeline.md) | 探测、决策、编解码、画质/体积算法、格式矩阵 |
| [docs/04-api-and-data-models.md](docs/04-api-and-data-models.md) | UniFFI API、数据模型、错误码、任务状态机 |
| [docs/05-roadmap.md](docs/05-roadmap.md) | 两阶段交付、工程规范、风险、ADR、默认拍板 |
| [docs/06-phase1-development-plan.md](docs/06-phase1-development-plan.md) | **第一阶段 Sprint 0–7 详细计划、验收、出口检查单** |
| [docs/07-phase1-signoff.md](docs/07-phase1-signoff.md) | 第一阶段签收报告（真机签字栏） |
| [docs/08-wave-e-f-backlog.md](docs/08-wave-e-f-backlog.md) | Wave E/F backlog 与 Phase 1 边界 |
| [docs/09-play-listing.md](docs/09-play-listing.md) | Play / 商店文案包（未提交） |
| [docs/smoke-checklist.md](docs/smoke-checklist.md) | 真机 smoke 清单 |
| [docs/failure-injection.md](docs/failure-injection.md) | 失败注入（自动 + 手工） |

## 第一阶段默认拍板

| 项 | 取值 |
|----|------|
| minSdk | 26 |
| IAP / 付费墙 | 无 |
| AVIF / MP3 | 不做 |
| 替换保险箱 | 强制 |
| iOS 可发布包 | 第一阶段不做 |

## 一句话原则

**平台负责“快”（硬件编解码 + 相册/权限），Rust 负责“准”（统一决策、预设、任务编排、图片/音频软件编解码、体积预估）。**

## 本地构建与验收

```powershell
# Rust
cd rust
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings

# Android（需 NDK 28.0.13004108、cargo-ndk、ANDROID_HOME）
cd ..\android
.\gradlew :app:assembleDebug
```

安装后打开「关于」页，应显示核心版本 `0.1.0` 与预设表 `preset_v1.0`。

详见 [packaging/README.md](packaging/README.md) 与 [docs/06-phase1-development-plan.md](docs/06-phase1-development-plan.md)。
