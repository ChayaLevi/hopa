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
Settings ──"Start the background screen"──► bridge /helper
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
- The protocol is one line per command (`display`, `launch`, `tap`, `swipe`,
  `key`, `top`, `shot`, `release`) and one line per reply, `ok …` or `err …`.
  `DisplayProtocol` is plain Kotlin and unit-testable.

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
# one-time per boot
adb shell CLASSPATH=$(pm path com.hopa.android | cut -d: -f2) `
    app_process / com.hopa.capabilities.display.ShellMain

.\gradlew.bat :app:connectedDebugAndroidTest
```

## Not here yet

- Pairing inside the app: today the helper is started by the bridge (or the
  command above), not by a wireless-debugging handshake in the app.
- Ending a task mid-way: the run closes what it opened, but a task cancelled
  while an app is mid-flow leaves that app's own state behind.
- Audio isolation depends on the `PLAY_AUDIO` AppOps flag existing on the
  device; where it is missing the helper says so and the run is audible.
- The assertion that the visible screen is unchanged during a run belongs in the
  device suite; it needs a real phone to compare window focus before and after.
