# Screen paths: which surface a run drives

One turn can reach another app in four ways. The client picks one order and uses
the first that answers; the choice is a setting, and the run records which path
was used.

| path | surface | needs | leaves the phone's screen alone |
|---|---|---|---|
| `background` | a hidden virtual display owned by a helper | helper running as shell + phone access | yes |
| `bridge` | the phone's real screen, driven from a PC over adb | paired bridge + adb | no |
| `accessibility` | the phone's real screen, driven on device | phone access | no |
| `none` | no surface: the turn refuses with the reason | — | — |

## Order

```
AUTO           background → bridge → accessibility
BACKGROUND     background only
BRIDGE         bridge only
ONSCREEN       accessibility only
```

`AUTO` is the only path a user needs. A forced path is tried alone so that a
comparison is honest; `ScreenPathOrder` is pure and unit-tested.

## The background display

```
Capabilities → Running apps ──"Start the background screen"──► bridge /helper
                                              │ adb shell
                                              ▼
                          app_process …ShellMain  (uid 2000)
                                              │ abstract socket "hopa-display"
                                              ▼
              DisplayHost: createVirtualDisplay + ImageReader
              DisplayInput: InputManager.injectInputEvent(displayId)
              DisplayLauncher: cmd package resolve-activity + am start --display
                                              │ frames (JPEG), top package
                                              ▼
                     ShellClient → ShellDisplayDevice (DevicePort)
```

- The helper is our own code started as `uid 2000`, so it can create a public
  virtual display, launch apps onto it and inject input. There is no root and no
  Shizuku.
- The app never sees a `Surface`: the helper owns the `ImageReader` and sends
  JPEG frames over the socket, so screenshots do not use the accessibility
  screenshot API.
- The UI tree comes from the accessibility window **on that display**
  (`NodeTreeReader(displayId)`), which is why phone access is still required.
- The app talks to the helper on **loopback** (`127.0.0.1:47821`), not on an
  abstract UNIX socket: SELinux denies `untrusted_app` a connection to a
  socket owned by the shell, and that cannot be changed without root. The
  first line is `auth <token>`; the token is created by the app, lives in its
  private files and is passed to `app_process` when the app starts the helper.
- The protocol is one line per command (`display`, `launch`, `tap`, `swipe`,
  `key`, `top`, `shot`, `release`, `appfn`, `mute`, `unmute`, `finish`) and
  one line per reply, `ok …` or `err …`. `DisplayProtocol` is plain Kotlin and
  unit-testable.

## What runs where

- `ChatViewModel` builds the source list from the setting, gives each source a
  budget (8 s background, 15 s bridge, 8 s accessibility) and releases the
  background display when the turn ends.
- Before releasing, the run sends `finish <packages>` for everything it started
  and the helper force-stops those packages. Otherwise the system would move the
  leftover tasks onto the person's own screen the moment the display disappears.
- After a successful launch the helper sets the AppOps `PLAY_AUDIO` mode of that
  package to `ignore`, so a video or a voice message on the hidden display is not
  heard. The mode is returned to `default` before the package is closed; a silent
  failure is reported, not hidden.
- `ScreenSourceDevice` keeps using the source that answered for the whole turn,
  so observation and action never disagree about which screen is being driven.

## Tests

- JVM: `ScreenPathOrderTest`.
- Device: `app/src/androidTest/.../BackgroundDisplayTest` — the display is not
  the phone's own, it draws a frame, Settings starts on it, a tap changes the
  frame, and the phone's own screen is pixel-identical before and after a launch
  on the background display. Without a running helper the cases are skipped with
  a reason, so a machine with no phone stays green.

```powershell
# one-time per boot; the helper class sits in a secondary dex, so the script
# extracts the right classesN.dex and starts app_process with it
adb shell sh /data/local/tmp/hopa_display.sh
# (the same script is pushed by tools/scripts/android-device-checks.ps1)

.\gradlew.bat :app:connectedDebugAndroidTest
```

## Pairing inside the app

Capabilities → Running apps → *Pair this phone* takes the address and
six-digit code Android shows under Wireless debugging, calls `adb pair` through
`libadb-android` (SPAKE2, Apache-2.0), then connects over TLS to the port adbd
advertises and runs the helper with `shell:`. After that the phone needs no PC;
the key and certificate live in the app's private storage and are reused.

If pairing is unavailable (wireless debugging off, different network, mDNS
blocked), the same button path falls back to the PC bridge, and the chat line
says which surface was actually used.

## What has been proven on hardware

- The display, the launch, the mute and the release were exercised on a Galaxy
  S24 (Android 17) on 2026-11-16; see
  `docs/project/evidence/hidden-display-2026-11-16.md`. The phone's own screen
  stayed on HOPA throughout.

## Not here yet

- Ending a task mid-way: the run closes what it opened, but a task cancelled
  while an app is mid-flow leaves that app's own state behind.
- Audio isolation depends on the `PLAY_AUDIO` AppOps flag existing on the
  device; where it is missing the helper says so and the run is audible.
- Tap, swipe and typing **on** the hidden display, a full model turn against it,
  two parallel tasks, and the wireless pairing handshake are still unproven.
