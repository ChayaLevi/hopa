# Hidden display verified on a phone (2026-11-16)

The background display is real: a virtual display that is not the phone's own, an
app launched onto it, the audio muted, and everything closed again at the end. The
person's screen stayed on HOPA throughout.

## Environment

| Item | Value |
|---|---|
| Phone | Samsung Galaxy S24, `SM-S926B` |
| Android | 17 (SDK 37), build `CP2A.260605.016.S926BXXSIEZJ1` |
| APK | `0.1.0-261116-2014` (debug, minSdk 29); the read/tap and parallel runs were on 1825 and 1955 |
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
   `com.hopa.capabilities.display.helper.ShellMain`; `app_process` starts it by name, so
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

## A first model turn on the hidden display

Asking "Open Settings and tell me the first screen" with plan-first on: the plan
was approved, the run went to the hidden display (helper log: `display`, `top`,
`launch`, `mute`, `unmute`, `finish`, `release`), and the model answered honestly
that the screen came back with no readable text or elements.

So the launch works and the turn does not touch the phone. What did not work yet
was **reading the hidden display**: `NodeTreeReader(displayId)` found no window
for the virtual display, even though the accessibility service is bound and the
app was drawn there.

### Why the display id could not be used

`AccessibilityWindowInfo.getDisplayId()` cannot be trusted on this build: every
window — SystemUI, the launcher, HOPA itself — reports `display=0`, so matching a
virtual display by display id finds nothing. On top of that, a run probed the
surface **before** opening anything, and a display with no windows has no root to
read; the strict check then threw and the turn fell back to the phone's screen
(one answer described Settings with the phone's own account card, which is how the
fallback was spotted).

The rule that follows: an existing but empty hidden display is a normal start (it
returns "The hidden screen is empty. Open an app with ui.launch"), and the window
is identified by the package the run just launched, not by a display id.

### Reading now works

The newest accessibility window event carries the hidden tree even when the window
list does not. The first attempt kept one such source globally and its node had no
readable children. Two changes fixed that:

1. an event for the same package is replaced only by a richer one (more children),
   so a window-state event with an empty root cannot evict the content event;
2. sources are kept per package, so two runs cannot evict each other.

Asking "List the categories in Settings" then answered with the real screen:

> Your account card (the signed-in Samsung account), then Warranty and care,
> Connections, Connected devices, Galaxy AI, Modes and Routines, Sounds and
> vibration, Notifications, Display, Battery, Wallpaper and style, Themes, Home
> screen, and Lock screen and AOD.

The helper log for that run shows `launch com.android.settings` and no tap. The
readable tree in the event had `children=19`.

### Taps on the hidden display now work

`injectInputEvent` moved on Android 17: `InputManager.getInstance()` is gone and
the two-argument overload does not exist. Two failed attempts were reported
honestly, first as "every tap came back refused", then as "the screen is still
coming back empty". Both causes are fixed:

- the helper resolves `InputManagerGlobal` (with the old class as fallback) and
  looks for `injectInputEvent` by name and arity (2 or 3 arguments, `targetUid=-1`
  for the targeted overload);
- when reflection is unavailable or returns `false`, the helper runs the system's
  own `input touchscreen -d <display> …` (one gesture per call, exit code is the
  result) — this is what actually delivered on this build;
- the cached tree for a package is dropped on `launch` and on `tap`, and the read
  after a tap waits up to 6 s for a fresh tree, so the answer never describes the
  previous screen.

Proof: "Go into Connections from the Settings list and tell me what is there"
finished with **2 steps, 2 confirmed** and the answer listed the Connections page:

> Wi-Fi with its toggle, Wi-Fi Calling with its toggle, Bluetooth (2 devices
> connected) with its toggle, and NFC and contactless payments with its toggle.
> Then SIM manager, Mobile networks, Data usage, Mobile Hotspot and Tethering,
> and More connection settings.

Helper log for the same run: `display 1080 2340 450` (flags `17dc9`), `top`,
`launch com.android.settings`, `mute`, `tap 359 1340`, `unmute`, `finish`,
`release`. The phone's own screen stayed on HOPA.

## Two tasks at once

Two goals were sent three seconds apart through the app's own `hopa://ask` link,
with plan-first off:

- "Open Settings and open Connections"
- "Open Calculator and tell me its buttons"

The helper log shows two connections (`ping` twice), two displays created at the
same time, and two launches that diverged:

```
cmd: ping
cmd: display 1080 2340 450
cmd: ping
cmd: display 1080 2340 450
display flags: 17dc9
display flags: 17dc9
cmd: top
cmd: launch com.sec.android.app.popupcalculator
...
cmd: release
cmd: launch com.android.settings
cmd: tap 359 1340
...
```

The calculator run finished and released its display while Settings was still
tapping. Both answers described their own screen: the first listed the calculator
keys (history, unit converter, scientific mode, the number pad), the second the
Connections page. `run_history` has two rows nine seconds apart, both
`background`, and the chat showed the second task's chip with its own dismiss
button.

This is also the proof that the per-package event cache was needed: a single
global source would have let one run read the other's screen.

## Still not exercised

- The Android 10 MediaProjection path; there is no Android 10 phone here.
- Other OEMs; this was one Samsung device with Android 17.

## Wireless pairing is closed by the platform

The in-app flow asks for the address and the six-digit code Android shows under
"Pair device with pairing code", then calls `AdbPairingManager.pair`. On this
phone it cannot succeed, and the reason is not the app:

1. With the pairing dialog in front, the pairing port accepts a TCP connection
   (`tcp reachable while dialog is foreground`). The dialog showed
   `192.168.1.110:41563`.
2. Switching to HOPA — with `am start` and, separately, with the Recents key —
   makes the same port refuse connections immediately (`ECONNREFUSED`).
3. The app's own attempt therefore ends as
   `Pairing failed: failed to connect to /192.168.1.110 (port …) … ECONNREFUSED`
   in the `HopaWireless` log.

So the pairing listener lives only while the dialog holds the window focus, and
a code has to be typed in another app — the one thing that cannot be done while
it is in front. adbd's key list also stayed at one key (the PC) after both
attempts: no pairing was half-completed.

A QR flow is the shape that could work here: the app opens the QR dialog,
captures its own screen through accessibility, decodes the service name,
password and port, and pairs from the background while the QR dialog stays in
front. That is a feature, not a fix, and it has not been built. Until then the
PC bridge is the working way to start the helper without a cable.
