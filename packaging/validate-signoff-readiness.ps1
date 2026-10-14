# Validates automated Phase 1 gates before device smoke sign-off.
# Does NOT replace manual smoke / memory profiling / failure injection on device.

param(
    [switch]$SkipApk
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Signoff = Join-Path $Root "docs\07-phase1-signoff.md"

Write-Host "== Automated quality gate =="
Push-Location $PSScriptRoot
try {
    if ($SkipApk) {
        .\run-quality-gate.ps1 -SkipApk
    } else {
        .\run-quality-gate.ps1
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "== Sign-off document check =="
if (-not (Test-Path $Signoff)) {
    Write-Error "Missing $Signoff"
}
$content = Get-Content $Signoff -Raw
if ($content -match "未签收|内测候选") {
    Write-Host "OK: 07-phase1-signoff.md still pre-sign-off (内测候选/未签收 — expected until device run)."
} else {
    Write-Warning "07-phase1-signoff.md no longer marked pre-sign-off — verify device smoke was completed."
}

$pending = @()
if ($content -match "\[ \].*真机 smoke") { $pending += "真机 smoke (docs/smoke-checklist.md)" }
if ($content -match "_待测_") { $pending += "50 JPEG 内存 profiling (packaging/memory-profiling-guide.md)" }
if ($content -match "\| Photo \| \| \|") { $pending += "07 签字栏 (Photo/Video/Audio/Product/Quality/Failure injection)" }

Write-Host ""
Write-Host "== Remaining device-only items =="
if ($pending.Count -eq 0) {
    Write-Host "No obvious pending markers found in 07 — review manually."
} else {
    foreach ($item in $pending) {
        Write-Host "  - $item"
    }
}

Write-Host ""
Write-Host "Run: .\run-device-smoke.ps1 for full procedure."
Write-Host "Automated gates passed. Complete device items before changing 07 status to 已签收."
exit 0
