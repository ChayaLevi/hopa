# Hidden display verified on a phone (2026-11-16)

The background display is real: a virtual display that is not the phone's own, an
app launched onto it, the audio muted, and everything closed again at the end. The
person's screen stayed on HOPA throughout.

## Environment

| Item | Value |
|---|---|
| Phone | Samsung Galaxy S24, `SM-S926B` |
| Android | 17 (SDK 37), build `CP2A.260605.016.S926BXXSIEZJ1` |
| APK | `0.1.0-261116-1625` (debug, minSdk 29) |
| Helper | `tools/scripts/android-display-helper.sh`, started with `adb shell sh` as uid 2000 |
| Transport | loopback `127.0.0.1:47821`, token from the app's private files |

## What was exercised

Running apps -> **Check that it works**, with screen access on and the helper
running. The screen reported:

```
HOPA can see your screen
Background mode is on
App functions: this Android supports them
Tested it: Settings opened out of sight
```

The helper log for the same run:

```
cmd: ping
cmd: appfn com.android.settings
cmd: display 1080 2340 450
cmd: launch com.android.settings
cmd: mute com.android.settings
cmd: unmute com.android.settings
cmd: finish com.android.settings
cmd: release
```

Afterwards `uiautomator dump` still showed HOPA's Running apps screen, so the
launch never reached the person's display.

## Defects this run found and fixed

1. **R8 removed the entry class.** Nothing references
   `com.hopa.capabilities.display.ShellMain`; `app_process` starts it by name, so
   the class was absent from the APK. A keep rule fixes it.
2. **The wrong dex was named in `CLASSPATH`.** Library classes land in
   `classes2.dex` or later and `app_process` reads only the dex named there. The
   helper script extracts the `classesN.dex` that carries the class.
3. **SELinux blocked the socket.** An abstract UNIX socket owned by `shell`
   cannot be reached by `untrusted_app`; the app now talks over loopback with a
   token the app creates and keeps in its private files.
4. **No system context in the helper.** `ActivityThread.systemMain()` needs a
   Looper on the calling thread; without one the display manager was `null`.
5. **`packageName must match the owner uid`.** The display is created from a
   `com.android.shell` context, because the helper runs as uid 2000.
6. **`NetworkOnMainThreadException`.** The client is blocking socket I/O and was
   called from the main thread; every call now runs on `Dispatchers.IO`.
7. **Tooling races.** CRLF in the shell script broke `sh` on the phone; the
   readiness check ran before the helper printed its line; the APK hash was taken
   before the device flushed the last block.

## Still not exercised

- Taps, swipes and typing **on** the hidden display (the check launches and reads,
  it does not act).
- A full model turn against the hidden display, and two parallel tasks.
- The in-app wireless pairing handshake.
- Other OEMs; this was one Samsung device with Android 17.
