# Phase 1 device smoke: runs host gates, then adb steps when a USB device is present.
# Manual Photo/Video/Audio/Product/Quality ticks still go into docs/smoke-checklist.md
# and docs/07-phase1-signoff.md. Simulators are not accepted for video items.

param(
    [switch]$SkipQualityGate,
    [switch]$SkipInstall,
    [switch]$SkipInstrumented
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Packaging = $PSScriptRoot

function Get-AdbDevice {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        return $null
    }
    $lines = & adb devices 2>$null
    foreach ($line in $lines) {
        if ($line -match '^\s*(\S+)\s+device\s*$') {
            return $Matches[1]
        }
    }
    return $null
}

Write-Host "================================================================================"
Write-Host "SpaceSlimmer Phase 1 — device smoke"
Write-Host "================================================================================"

if (-not $SkipQualityGate) {
    Write-Host "== 1. Host quality gate =="
    Push-Location $Packaging
    try {
        & .\run-quality-gate.ps1
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "== 1. Host quality gate skipped (-SkipQualityGate) =="
}

Write-Host "== 2. Generate local testdata =="
Push-Location $Packaging
try {
    & .\prepare-testdata.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

$device = Get-AdbDevice
if (-not $device) {
    Write-Host ""
    Write-Host "No USB Android device in 'device' state (API 26+ required)."
    Write-Host "Host fixtures are ready. Connect a phone, enable USB debugging, then re-run:"
    Write-Host "  $Packaging\run-device-smoke.ps1 -SkipQualityGate"
    Write-Host "Then complete docs/smoke-checklist.md, docs/failure-injection.md,"
    Write-Host "packaging/memory-profiling-guide.md, and packaging/record-device-signoff.ps1."
    Write-Host "Simulators are not accepted for video smoke."
    exit 2
}

Write-Host "== 3. Push testdata to $device =="
Push-Location $Packaging
try {
    & .\push-testdata-to-device.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

if (-not $SkipInstall) {
    Write-Host "== 4. Build and install Release APK =="
    if (-not $env:ANDROID_NDK_HOME -and $env:ANDROID_HOME) {
        $env:ANDROID_NDK_HOME = [IO.Path]::Combine($env:ANDROID_HOME, "ndk", "28.0.13004108")
    }
    $env:CMAKE_POLICY_VERSION_MINIMUM = "3.5"
    $androidDir = Join-Path $Root "android"
    Push-Location $androidDir
    try {
        & (Join-Path $androidDir "gradlew.bat") :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $apk = Join-Path $androidDir "app\build\outputs\apk\release\app-release.apk"
        if (-not (Test-Path $apk)) {
            throw "Missing $apk"
        }
        & adb -s $device install -r $apk
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "== 4. APK install skipped (-SkipInstall) =="
}

if (-not $SkipInstrumented) {
    Write-Host "== 5. Instrumentation smoke (connectedDebugAndroidTest) =="
    if (-not $env:ANDROID_NDK_HOME -and $env:ANDROID_HOME) {
        $env:ANDROID_NDK_HOME = [IO.Path]::Combine($env:ANDROID_HOME, "ndk", "28.0.13004108")
    }
    $env:CMAKE_POLICY_VERSION_MINIMUM = "3.5"
    $androidDir = Join-Path $Root "android"
    Push-Location $androidDir
    try {
        & (Join-Path $androidDir "gradlew.bat") :app:connectedDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "== 5. Instrumentation smoke skipped (-SkipInstrumented) =="
}

Write-Host @"

== 6. Manual remaining (cannot be automated) ==
Device testdata: /sdcard/Download/SpaceSlimmer-testdata
Checklist:       docs/smoke-checklist.md  (Photo/Video/Audio/Product/Quality)
Failure inject:  docs/failure-injection.md (7 manual rows; HEVC on HEVC-capable phone)
Memory:          packaging/memory-profiling-guide.md (50 JPEG Profiler peak)
Record:          packaging/record-device-signoff.ps1 after the tables are filled
Sign-off:        docs/07-phase1-signoff.md

Host + adb install finished for device $device. Sign-off stays unsigned until those tables are filled.
"@

exit 0
