#!/usr/bin/env pwsh
# Install the app and its test APK the way this host allows, then run the
# device tests. `connectedDebugAndroidTest` uses `adb install`, which some
# machines refuse; this script stages both APKs with `adb exec-in dd` and lets
# the phone install them, then starts the instrumentation runner.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$appApk = Join-Path $root "apps\android\app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $root "apps\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$runner = "com.hopa.android.test/androidx.test.runner.AndroidJUnitRunner"

if (-not (Test-Path $adb)) { throw "adb not found at $adb" }
if (-not (Test-Path $appApk) -or -not (Test-Path $testApk)) {
    throw "build both first: cd apps/android; .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest"
}
$devices = & $adb devices | Select-String "device$"
if (-not $devices) { throw "no phone attached: enable USB or wireless debugging" }

function Invoke-AdbIn {
    param([string]$Path, [string]$Destination)
    $line = "`"$adb`" exec-in dd of=$Destination bs=65536 < `"$Path`""
    $out = cmd /c $line 2>&1
    if ($LASTEXITCODE -ne 0) { throw "staging failed ($LASTEXITCODE): $out" }
}

function Install-Staged {
    param([string]$Path, [string]$Remote, [string]$Name)
    $md5 = (Get-FileHash $Path -Algorithm MD5).Hash.ToLower()
    $size = (Get-Item $Path).Length
    Invoke-AdbIn -Path $Path -Destination $Remote
    for ($i = 0; $i -lt 20; $i++) {
        $remoteSize = (& $adb shell stat -c %s $Remote 2>$null | Select-Object -First 1)
        if ($remoteSize -and $remoteSize.Trim() -eq "$size") { break }
        Start-Sleep -Milliseconds 500
    }
    $remote = ((& $adb shell md5sum $Remote) -split "\s+")[0].Trim()
    if ($remote -ne $md5) {
        throw "$Name did not survive the transfer: local $md5, phone $remote"
    }
    $create = & $adb shell pm install-create -r -t 2>&1
    $session = ([regex]::Match(($create -join " "), "\[(\d+)\]")).Groups[1].Value
    if (-not $session) { throw "no install session: $($create -join ' ')" }
    & $adb shell pm install-write $session base $Remote | Out-Null
    $commit = & $adb shell pm install-commit $session 2>&1
    if (-not (($commit -join " ") -match "Success")) {
        throw "$Name was not installed: $($commit -join ' ')"
    }
    Write-Host "   $Name installed" -ForegroundColor Green
}

Write-Host "1/3 installing the app" -ForegroundColor Cyan
Install-Staged -Path $appApk -Remote "/data/local/tmp/hopa.apk" -Name "app"

Write-Host "2/3 installing the test APK" -ForegroundColor Cyan
Install-Staged -Path $testApk -Remote "/data/local/tmp/hopa-test.apk" -Name "test"

Write-Host "3/3 running the instrumentation" -ForegroundColor Cyan
$output = & $adb shell am instrument -w $runner 2>&1
$output | ForEach-Object { Write-Host "   $_" }
if (($output -join "`n") -match "FAILURES!!!") {
    throw "device tests failed"
}
Write-Host "device tests passed" -ForegroundColor Green
