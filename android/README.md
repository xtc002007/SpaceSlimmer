# Android app (Phase 1)

Kotlin + Jetpack Compose + `ss-core` via UniFFI.

## Requirements

- JDK 17+
- Android SDK (`ANDROID_HOME`), compileSdk 36
- **NDK 28.0.13004108** (16 KB page alignment)
- Rust stable, `cargo-ndk`, targets `aarch64-linux-android` and `x86_64-linux-android`

```powershell
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
sdkmanager "ndk;28.0.13004108"
```

## Build

From this directory:

```powershell
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\28.0.13004108"
gradle :app:assembleDebug
```

`core-android` runs `cargo ndk` then `uniffi-bindgen` before compiling Kotlin.

**Opus on Windows:** Debug builds default to **no** `opus-encode` (UI hides Opus). Release / `assembleRelease` and Linux CI use `opus-encode`. Override in `local.properties`:

```properties
rust.features=opus-encode
```

Requires NDK CMake on `PATH` and `CMAKE_TOOLCHAIN_FILE` → `$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake` (set automatically by `core-android` when `opus-encode` is enabled). If Windows Debug Opus build still fails, keep default Debug without Opus and use Release or Linux CI for Opus `.so`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Open **关于** — you should see `0.1.0` and `preset_v1.0`.

If `dl.google.com` is unreachable, `settings.gradle.kts` already lists Aliyun Google/Central mirrors first.

## Modules

| Module | Role |
|--------|------|
| `app` | Compose UI（雷达、预设、队列、保险箱、历史、小组件） |
| `core-android` | UniFFI `ss-core` + probe/SAF 导出 |
| `video-hw` | Media3 主路径 + MediaCodec Plan B + HEVC 黑名单 + 花屏启发式 |
| `audio-hw` | AAC MediaCodec；Opus 在 Rust `opus-encode` feature |
