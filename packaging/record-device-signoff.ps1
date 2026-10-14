#Requires -Version 5.1
$ErrorActionPreference = "Stop"

<#
.SYNOPSIS
Interactive helper to record Phase 1 device sign-off rows in docs/07-phase1-signoff.md.

.DESCRIPTION
Cannot run real device smoke on CI Windows hosts. This script captures operator input
and patches the sign-off table + optional memory profiling row after manual runs of:
  packaging/run-device-smoke.ps1
  packaging/memory-profiling-guide.md
  docs/failure-injection.md (manual §)
#>

$Root = Split-Path -Parent $PSScriptRoot
$Signoff = Join-Path $Root "docs\07-phase1-signoff.md"

if (-not (Test-Path $Signoff)) {
    throw "Missing $Signoff"
}

function Read-Line($Prompt, $Default = "") {
    $value = Read-Host "$Prompt [$Default]"
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value.Trim()
}

Write-Host "SpaceSlimmer device sign-off recorder" -ForegroundColor Cyan
Write-Host "Run packaging/run-device-smoke.ps1 on a device first, then fill this form."
Write-Host ""

$rows = @(
    @{ Key = "Photo"; Label = "Photo smoke" },
    @{ Key = "Video"; Label = "Video smoke" },
    @{ Key = "Audio"; Label = "Audio smoke" },
    @{ Key = "Product"; Label = "Product / radar / vault" },
    @{ Key = "Quality gate"; Label = "Quality gate" },
    @{ Key = "Failure injection (manual)"; Label = "Failure injection manual" }
)

$date = Read-Line "Sign-off date (YYYY-MM-DD)" (Get-Date -Format "yyyy-MM-dd")
$peakHeap = Read-Line "Peak native heap for 50 JPEG batch (bytes, or blank to skip)" ""
$device = Read-Line "Device model" ""
$memDate = if ($peakHeap) { $date } else { "" }

$content = Get-Content $Signoff -Raw
foreach ($row in $rows) {
    $signer = Read-Line $row.Label "signer"
    $pattern = "\| $($row.Key) \|.*\|.*\|.*\|"
    $replacement = "| $($row.Key) | $signer | $date | manual smoke OK |"
    if ($content -match [regex]::Escape("| $($row.Key) |")) {
        $content = [regex]::Replace($content, $pattern, $replacement, 1)
    }
}

if ($peakHeap) {
    $content = [regex]::Replace(
        $content,
        "\| Peak native heap \(50 JPEG batch\) \| _待测_ \| \| \|",
        "| Peak native heap (50 JPEG batch) | $peakHeap | $device | $memDate |",
        1
    )
}

Set-Content -Path $Signoff -Value $content -NoNewline
Write-Host "Updated $Signoff" -ForegroundColor Green
Write-Host "Review docs/smoke-checklist.md and set status to 已签收 when all gates pass."
