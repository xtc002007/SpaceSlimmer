#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Rust = Join-Path $Root "rust"
$Jni = Join-Path $Root "android\core-android\src\main\jniLibs"
$BindgenOut = Join-Path $Root "android\core-android\build\generated\source\uniffi\debug\java"

New-Item -ItemType Directory -Force -Path $Jni | Out-Null

$Abis = @{
    "arm64-v8a" = "aarch64-linux-android"
    "x86_64"    = "x86_64-linux-android"
}

$env:CMAKE_POLICY_VERSION_MINIMUM = if ($env:CMAKE_POLICY_VERSION_MINIMUM) { $env:CMAKE_POLICY_VERSION_MINIMUM } else { "3.5" }
Push-Location $Rust
try {
    cargo ndk -t arm64-v8a -t x86_64 build --release -p ss-core --features opus-encode
    foreach ($entry in $Abis.GetEnumerator()) {
        $built = Join-Path $Rust ("target\{0}\release\libss_core.so" -f $entry.Value)
        $dest = Join-Path $Jni ("{0}\libss_core.so" -f $entry.Key)
        if (-not (Test-Path $built)) {
            throw "Missing $built"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        Copy-Item $built $dest -Force
    }
    $so = Join-Path $Jni "arm64-v8a\libss_core.so"
    if (-not (Test-Path $so)) {
        throw "Missing $so"
    }
    New-Item -ItemType Directory -Force -Path $BindgenOut | Out-Null
    cargo run -p uniffi-bindgen -- generate --lib-file $so $BindgenOut/libss_core.rlib --language kotlin --out-dir $BindgenOut
}
finally {
    Pop-Location
}

Write-Host "Android libss_core.so and Kotlin bindings generated."
