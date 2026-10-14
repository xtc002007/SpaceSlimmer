# Phase 1 automated quality gate (CI + local). Device smoke remains manual per docs/smoke-checklist.md.

param(

    [switch]$SkipApk

)



$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot



Write-Host "== Rust tests =="

Push-Location (Join-Path $Root "rust")

try {

    cargo test --workspace

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

}

finally {

    Pop-Location

}



if (-not $SkipApk) {

    Write-Host "== Android release APK =="

    if (-not $env:ANDROID_NDK_HOME -and $env:ANDROID_HOME) {

        $env:ANDROID_NDK_HOME = [IO.Path]::Combine($env:ANDROID_HOME, "ndk", "28.0.13004108")

    }

    $androidDir = Join-Path $Root "android"

    Push-Location $androidDir

    try {

        if ($IsLinux -or $IsMacOS) {

            & ./gradlew :app:assembleRelease

        } else {

            & (Join-Path $androidDir "gradlew.bat") :app:assembleRelease

        }

        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    }

    finally {

        Pop-Location

    }

}



Write-Host "== Release .so size =="

Push-Location $PSScriptRoot

try {

    .\check-so-size.ps1

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

}

finally {

    Pop-Location

}



Write-Host "== FFmpeg symbol scan =="

Push-Location $PSScriptRoot

try {

    .\check-no-ffmpeg.ps1

    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

}

finally {

    Pop-Location

}



Write-Host "Automated quality gate passed. Complete device smoke per docs/smoke-checklist.md before sign-off."

exit 0

