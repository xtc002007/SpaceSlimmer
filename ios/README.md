# SpaceSlimmer iOS（Wave D 脚手架）

iOS 客户端在 **第二阶段** 实现完整产品能力。Wave D 交付最小 Swift 源文件骨架与 XCFramework 打包脚本；**当前 Windows 环境不包含可编译的 Xcode 工程**。

## 目录结构

```text
ios/
  App/
    SpaceSlimmerApp.swift      # SwiftUI @main 入口
    ContentView.swift          # TabView：Home / Tasks / Vault / Settings 占位
  CoreIOS/
    CoreEngineBridge.swift     # UniFFI CoreEngine 包装（需链 SsCore.xcframework）
    SsCore.xcframework/        # macOS 上运行 packaging/xcframework.sh 后生成
    Generated/                 # UniFFI Swift 绑定（脚本生成，勿手改）
  VideoHW/
    VideoTranscoder.swift      # AVAssetReader + AVAssetWriter 转码 stub
  AudioHW/
    AacEncoder.swift           # AAC 平台编码 stub
  StoreKit/
    IAPManager.swift           # StoreKit 2 骨架（`FeatureFlags.iap` 默认关闭）
  Telemetry/
    Telemetry.swift            # probe_done / job_finished 事件 stub
```

## 构建 Rust Core（macOS）

前置：Rust stable、`xcodebuild`、targets `aarch64-apple-ios` 与 `aarch64-apple-ios-sim`。

```bash
./packaging/xcframework.sh
```

产物：

- `ios/CoreIOS/SsCore.xcframework`
- `ios/CoreIOS/Generated/`（Swift UniFFI 绑定）

在非 macOS 上脚本会 **exit 1** 并提示需要在 Mac 上构建。

## Xcode 接入（预览）

1. 新建 iOS 16+ SwiftUI App，将 `ios/App`、`CoreIOS`、`VideoHW`、`AudioHW`、`StoreKit`、`Telemetry` 源目录加入 target。
2. 链接 `SsCore.xcframework`，并 import UniFFI 模块（`ss_core`）。
3. 在 `SpaceSlimmerApp` 启动时调用 `CoreEngineBridge.shared.bootstrap(...)`.
4. 实现 Platform Gateway：
   - `PHPicker` / `PhotoKit` 选择与写入
   - `VideoTranscoder` 硬件转码
   - `AacEncoder` 平台回调
   - 热/电状态、`beginBackgroundTask` 话术
5. UI 与 Android 行为对齐，验收 [01-product-design.md](../docs/01-product-design.md) §17 V1 冻结清单 iOS 列。

## 依赖的冻结契约

- API 定义：[docs/04-api-and-data-models.md](../docs/04-api-and-data-models.md)
- 决策算法：[docs/03-media-pipeline.md](../docs/03-media-pipeline.md)（在 `ss-domain`，iOS 不得复制公式）
- 可观测性事件：[docs/02-technical-architecture.md](../docs/02-technical-architecture.md) §14
- 最低版本：iOS **16+**

## 相关文档

- [06-phase1-development-plan.md](../docs/06-phase1-development-plan.md) — 第一阶段计划
- [05-roadmap.md](../docs/05-roadmap.md) — 第二阶段 Sprint 规划
- [08-wave-e-f-backlog.md](../docs/08-wave-e-f-backlog.md) — P1/P2 后置项（不进 Phase 1 签收）
- [packaging/README.md](../packaging/README.md) — 构建脚本说明

## Windows 开发机说明

当前主机为 **Windows** 时：

- Android Phase 1 为活跃交付线（`android/` + `rust/` UniFFI）。
- **不要** 在仓库中提交空的 `.xcodeproj` 或宣称 iOS 可构建。
- iOS 完整构建必须在 **macOS** 执行 `packaging/xcframework.sh` 并新建 Xcode 16+ App target。
- 签收状态以 [07-phase1-signoff.md](../docs/07-phase1-signoff.md) 为准；iOS 对等属于 Wave D，不阻塞 Android 内测候选。
