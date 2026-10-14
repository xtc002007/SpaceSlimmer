# Push generated testdata/ to a connected device for manual smoke (API 26+).

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

$Src = Join-Path $Root "testdata"

$Dest = "/sdcard/Download/SpaceSlimmer-testdata"



if (-not (Test-Path $Src)) {

    Write-Error "testdata/ missing. Run packaging/prepare-testdata.ps1 first."

}



$adb = Get-Command adb -ErrorAction SilentlyContinue

if (-not $adb) {

    Write-Error "adb not found. Install Android platform-tools and connect a device."

}



$devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }

if (-not $devices) {

    Write-Error "No adb device in 'device' state."

}



& adb shell "mkdir -p $Dest/photo $Dest/video $Dest/audio $Dest/corrupt"

Get-ChildItem -Path $Src -Recurse -File | ForEach-Object {

    $rel = $_.FullName.Substring($Src.Length).TrimStart('\', '/')

    $remote = "$Dest/$($rel -replace '\\','/')"

    & adb push $_.FullName $remote

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

}



Write-Host "Pushed to $Dest on device."

Write-Host "Open files via picker or Files app; follow docs/smoke-checklist.md and docs/failure-injection.md."

