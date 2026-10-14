# Sprint 7: ensure libss_core.so stays under 15 MB per ABI.
param(
    [string]$SoPath = "",
    [int]$MaxBytes = 15728640
)

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
$size = (Get-Item $resolved).Length
Write-Host "libss_core.so size: $size bytes (max $MaxBytes)"

if ($size -gt $MaxBytes) {
    Write-Error "SO size exceeds limit"
    exit 1
}

Write-Host "OK"
exit 0
