# packaging/

Build helpers for the shared Rust core.

## Android (Sprint 0)

Requires:

- Rust stable + targets `aarch64-linux-android`, `x86_64-linux-android`
- `cargo install cargo-ndk`
- Android SDK + **NDK 28.0.13004108** (16 KB page size)
- `ANDROID_HOME` set

From repo root:

```powershell
./packaging/build-android-core.ps1
```

Or Unix:

```bash
./packaging/build-android-core.sh
```

Then:

```bash
cd android
./gradlew :app:assembleDebug
```

Gradle `core-android` already runs `cargo ndk` + `uniffi-bindgen` on `assembleDebug` (AGP 9.3, custom Exec tasks; the cargo-ndk Gradle plugin is not used because AGP 9 removed `libraryVariants`).

## Phase 1 quality gate

```powershell
./packaging/run-quality-gate.ps1          # Rust tests + release APK + .so size (CI uses pwsh on ubuntu-latest)
./packaging/run-quality-gate.ps1 -SkipApk # Rust + .so only
```

Device smoke fixtures (not committed):

```powershell
./packaging/prepare-testdata.ps1
./packaging/push-testdata-to-device.ps1
```

Sign-off helpers:

```powershell
./packaging/validate-signoff-readiness.ps1   # automated gate + 07 pending markers
./packaging/run-device-smoke.ps1             # adb push fixtures + instrumented smoke
./packaging/record-device-signoff.ps1        # interactive 07 sign-off rows
```

See `docs/smoke-checklist.md` and `docs/07-phase1-signoff.md`.

## iOS XCFramework (Phase 2)

See [xcframework.sh](xcframework.sh). Not used in Sprint 0.
