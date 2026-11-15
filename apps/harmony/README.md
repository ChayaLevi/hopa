# HOPA for HarmonyOS (frozen)

Status: **frozen** (ADR-0020). Android is the active client; Windows is next.

This tree is ArkTS and ArkUI. It does **not** use `packages/` (the Kotlin client
engine): that code belongs to the Android and Windows clients. The shared kit
for this app lives in `apps/harmony/app`.

The code is kept as the reference for the visual language and for the Harmony
build, which still runs in CI when the Huawei toolchain secret is present.

## Building and installing on a phone

The DevEco command-line tools do not inherit the IDE environment, so both
variables must be set in the shell before `hvigorw`:

```powershell
cd apps/harmony
$env:DEVECO_SDK_HOME = "C:\Program Files\Huawei\DevEco Studio\sdk"
$env:PATH = "C:\Program Files\Huawei\DevEco Studio\tools\node;" + $env:PATH
& "C:\Program Files\Huawei\DevEco Studio\tools\hvigor\bin\hvigorw.bat" `
    --mode module -p module=entry@default -p product=default assembleHap --no-daemon
```

The module is named `entry` even though its folder is `phone`; `-p module=phone`
fails with `Unknown module 'phone'`.

Output: `phone/build/default/outputs/default/entry-default-signed.hap`.

```powershell
$hdc = "C:\Program Files\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe"
& $hdc install -r phone\build\default\outputs\default\entry-default-signed.hap
& $hdc shell aa start -a EntryAbility -b com.hopa.agent
& $hdc shell snapshot_display -f /data/local/tmp/s.jpeg
& $hdc file recv /data/local/tmp/s.jpeg out\harmony-screen.jpeg
```

The bundle is `com.hopa.agent`. `adb` sees nothing: HarmonyOS NEXT runs HAPs,
not APKs.
