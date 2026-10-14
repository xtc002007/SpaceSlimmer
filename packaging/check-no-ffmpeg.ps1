# Sprint 7: ensure release libss_core.so does not link FFmpeg / libavcodec symbols.

param(
    [string]$SoPath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SoPath)) {
    $releaseSo = Join-Path $PSScriptRoot "..\rust\target\aarch64-linux-android\release\libss_core.so"
    $jniSo = Join-Path $PSScriptRoot "..\android\core-android\src\main\jniLibs\arm64-v8a\libss_core.so"
    if (Test-Path $releaseSo) {
        $SoPath = $releaseSo
    } else {
        $SoPath = $jniSo
    }
}

$resolved = Resolve-Path $SoPath -ErrorAction Stop
Write-Host "Scanning $resolved for FFmpeg / libavcodec symbols..."

$patterns = @(
    "libavcodec",
    "libavformat",
    "libavutil",
    "libavfilter",
    "libswscale",
    "libswresample",
    "ffmpeg"
)

$found = @()
$llvmNm = Get-Command llvm-nm -ErrorAction SilentlyContinue
$strings = Get-Command strings -ErrorAction SilentlyContinue

if ($llvmNm) {
    $nmOut = & llvm-nm -g $resolved 2>$null
    if ($LASTEXITCODE -eq 0 -and $nmOut) {
        foreach ($pat in $patterns) {
            $hits = $nmOut | Where-Object { $_ -match $pat }
            if ($hits) { $found += $hits }
        }
    }
}

if (-not $found -and $strings) {
    $strOut = & strings $resolved 2>$null
    if ($LASTEXITCODE -eq 0 -and $strOut) {
        foreach ($pat in $patterns) {
            $hits = $strOut | Where-Object { $_ -match $pat }
            if ($hits) { $found += $hits }
        }
    }
}

if (-not $llvmNm -and -not $strings) {
    Write-Error "Neither llvm-nm nor strings found on PATH. Install LLVM or binutils to scan the .so."
}

if ($found.Count -gt 0) {
    Write-Error "FFmpeg-related symbols found in libss_core.so:"
    $found | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "OK: no FFmpeg / libavcodec symbols detected."
exit 0
