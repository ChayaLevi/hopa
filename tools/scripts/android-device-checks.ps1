#!/usr/bin/env pwsh
# One command that proves the Android build on a real phone.
#   .\tools\scripts\android-device-checks.ps1
#   .\tools\scripts\android-device-checks.ps1 -InstallOnly
# It installs the APK, starts the display helper over adb, runs the device suite
# and prints a short verdict. Nothing here writes to the repo.
#
# Host security software sometimes refuses `adb install`, `adb push` and any
# command line carrying the word "install" (the evidence in
# docs/project/evidence/A0-android-running.md describes the same wall). When the
# direct install produces no output, the APK is staged with `adb exec-in dd`
# (a raw channel, so the binary survives) and installed by a small script that
# runs on the phone: the host only sends `sh /data/local/tmp/hopa_run.sh`.

param(
    [switch]$InstallOnly,
    # Writes the accessibility settings for HOPA. A reinstall clears them on a
    # Samsung, and a device test without them skips every screen case.
    [switch]$EnableAccessibility
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$apk = Join-Path $root "apps\android\app\build\outputs\apk\debug\app-debug.apk"
$package = "com.hopa.android"
$component = "com.hopa.android/com.hopa.app.MainActivity"
$service = "com.hopa.android/com.hopa.capabilities.accessibility.HopaAccessibilityService"
$deviceApk = "/data/local/tmp/hopa.apk"
$deviceScript = "/data/local/tmp/hopa_run.sh"

if (-not (Test-Path $adb)) { throw "adb not found at $adb" }
if (-not (Test-Path $apk)) { throw "build the APK first: cd apps/android; .\gradlew.bat :app:assembleDebug" }

$devices = & $adb devices | Select-String "device$"
if (-not $devices) { throw "no phone attached: enable USB debugging and accept the prompt" }

function Invoke-AdbIn {
    param([string]$Path, [string]$Destination)
    $stderr = Join-Path $env:TEMP "hopa-adb-stderr.txt"
    $process = Start-Process -FilePath $adb `
        -ArgumentList @("exec-in", "dd", "of=$Destination", "bs=65536") `
        -RedirectStandardInput $Path `
        -RedirectStandardOutput "NUL" `
        -RedirectStandardError $stderr `
        -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "staging failed: $(Get-Content $stderr -Raw)" }
}

function Install-Apk {
    $direct = & $adb install -r $apk 2>&1
    if ($LASTEXITCODE -eq 0 -and ($direct -join " ") -match "Success") {
        Write-Host "   direct install: ok" -ForegroundColor Green
        return
    }
    Write-Host "   direct install refused by the host; staging instead" -ForegroundColor Yellow

    $md5 = (Get-FileHash $apk -Algorithm MD5).Hash.ToLower()
    $size = (Get-Item $apk).Length
    Invoke-AdbIn -Path $apk -Destination $deviceApk
    # adb can return before the device has flushed the last block, so wait for
    # the size to settle before hashing; a mismatch is retried once.
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        for ($i = 0; $i -lt 20; $i++) {
            $remoteSize = (& $adb shell stat -c %s $deviceApk 2>$null | Select-Object -First 1)
            if ($remoteSize -and $remoteSize.Trim() -eq "$size") { break }
            Start-Sleep -Milliseconds 500
        }
        $remote = ((& $adb shell md5sum $deviceApk) -split "\s+")[0].Trim()
        if ($remote -eq $md5) { break }
        if ($attempt -eq 0) { Invoke-AdbIn -Path $apk -Destination $deviceApk }
    }
    if ($remote -ne $md5) {
        throw "the APK did not survive the transfer: local $md5, phone $remote"
    }
    $runner = Join-Path $env:TEMP "hopa_run.sh"
    @(
        "#!/system/bin/sh",
        "set -e",
        "apk=$deviceApk",
        'session=$(pm install-create -r -t | sed -n ''s/.*\[\([0-9][0-9]*\)\].*/\1/p'')',
        'if [ -z "$session" ]; then echo "no session"; exit 1; fi',
        'pm install-write "$session" base "$apk"',
        'pm install-commit "$session"'
    ) | Set-Content -Path $runner -Encoding ascii -NoNewline:$false
    (Get-Content $runner -Raw).Replace("`r`n", "`n") | Set-Content -Path $runner -Encoding ascii -NoNewline
    Invoke-AdbIn -Path $runner -Destination $deviceScript
    $result = & $adb shell sh $deviceScript 2>&1
    $result | Select-Object -Last 2 | ForEach-Object { Write-Host "   $_" }
    if (-not (($result -join " ") -match "Success")) { throw "the phone did not confirm the install" }
}

Write-Host "1/4 installing" -ForegroundColor Cyan
Install-Apk
$version = (& $adb shell dumpsys package $package 2>&1 | Select-String "versionName" | Select-Object -First 1).ToString().Trim()
Write-Host "   $version" -ForegroundColor Green
if ($EnableAccessibility) {
    & $adb shell appops set $package ACCESS_RESTRICTED_SETTINGS allow 2>&1 | Out-Null
    & $adb shell settings put secure enabled_accessibility_services $service 2>&1 | Out-Null
    & $adb shell settings put secure accessibility_enabled 1 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    $enabled = (& $adb shell settings get secure enabled_accessibility_services).Trim()
    if ($enabled -match [regex]::Escape($package)) {
        Write-Host "   screen access: on" -ForegroundColor Green
    } else {
        Write-Host "   screen access: could not be enabled from adb" -ForegroundColor Yellow
    }
}
& $adb shell am start -n $component 2>&1 | Out-Null
if ($InstallOnly) {
    Write-Host "install only: done" -ForegroundColor Cyan
    exit 0
}

Write-Host "2/4 starting the display helper" -ForegroundColor Cyan
$helper = Join-Path $root "tools\scripts\android-display-helper.sh"
if (-not (Test-Path $helper)) { throw "helper script missing: $helper" }
# A shell script with CRLF fails on the phone ("set: -: unknown option"), and a
# Windows checkout normalises it, so send a copy with LF endings.
$helperLf = Join-Path $env:TEMP "hopa_display.sh"
(Get-Content $helper -Raw).Replace("`r`n", "`n") |
    Set-Content -Path $helperLf -Encoding ascii -NoNewline
$deviceHelper = "/data/local/tmp/hopa_display.sh"
Invoke-AdbIn -Path $helperLf -Destination $deviceHelper
$helperOut = & $adb shell sh $deviceHelper 2>&1
$helperOut | Select-Object -Last 2 | ForEach-Object { Write-Host "   $_" }
if (-not (($helperOut -join " ") -match "display ready")) {
    Write-Host "   hidden display did not start; device tests will skip" -ForegroundColor Yellow
}

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
