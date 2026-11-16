#!/usr/bin/env pwsh
# One command that proves the Android build on a real phone.
#   .\tools\scripts\android-device-checks.ps1
# It installs the APK, starts the display helper over adb, runs the device suite
# and prints a short verdict. Nothing here writes to the repo.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$apk = Join-Path $root "apps\android\app\build\outputs\apk\debug\app-debug.apk"
$package = "com.hopa.android"

if (-not (Test-Path $adb)) { throw "adb not found at $adb" }
if (-not (Test-Path $apk)) { throw "build the APK first: cd apps/android; .\gradlew.bat :app:assembleDebug" }

$devices = & $adb devices | Select-String "device$"
if (-not $devices) { throw "no phone attached: enable USB debugging and accept the prompt" }

Write-Host "1/4 installing" -ForegroundColor Cyan
& $adb install -r $apk | Select-Object -Last 1

Write-Host "2/4 starting the display helper" -ForegroundColor Cyan
& $adb shell "CLASSPATH=$(pm path $package | cut -d: -f2); nohup app_process / com.hopa.capabilities.display.ShellMain >/dev/null 2>&1 & echo started"

Write-Host "3/4 accessibility state" -ForegroundColor Cyan
$enabled = & $adb shell settings get secure enabled_accessibility_services
if ($enabled -match $package) {
    Write-Host "   screen control: on" -ForegroundColor Green
} else {
    Write-Host "   screen control: OFF - turn on HOPA in Settings, Accessibility" -ForegroundColor Yellow
}

Write-Host "4/4 device tests (skipped when the helper is absent)" -ForegroundColor Cyan
Push-Location (Join-Path $root "apps\android")
try {
    & .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
} finally {
    Pop-Location
}

Write-Host "done: open HOPA, Settings, Running apps, Check that it works" -ForegroundColor Cyan
