# ADR-0023 - The hidden screen keeps a shell helper and stops staging the dex

- Status: accepted
- Date: 2026-11-18

## Context

The "hidden screen" runs another app's UI on a display the person cannot see, so
a turn can work without taking over the phone. Today it is a helper process:

- `ShellMain` is started as uid 2000 (`shell`) with
  `setsid app_process / com.hopa.capabilities.display.ShellMain --token=…`,
  owns a virtual display through `DisplayManager`, injects input, and answers
  the app on loopback with a token as the guard.
- To get the class onto that classpath the app unzips `classes*.dex` from the
  installed APK into `/data/local/tmp/hopa-dex`, greps them for
  `com/hopa/capabilities/display/ShellMain`, and points `CLASSPATH` at the file
  that matched. The comment that justified it said `app_process` reads only the
  dex named in `CLASSPATH`, and library classes land in `classes2.dex` or later.
- That 50 MB copy is recreated on every start, and the same one-liner exists
  twice (Kotlin `DisplayStart`, Python `device_android.py`), kept in sync by
  `tools/checks/check-helper-command.py`.

The refactor list asks for "VirtualDisplay / MediaProjection instead of
`app_process` plus the dex staged in `/data/local/tmp`". The first half of that
cannot be done, the second half should not.

## Decision

**Keep the shell-uid helper. Remove the staging.**

The helper stays because a normal app cannot host another app's UI off-screen.
What changes is how its code is found: `CLASSPATH` points at the **installed
APK itself**.

```sh
apk=$(pm path <package> | cut -d: -f2)
CLASSPATH="$apk" setsid app_process / com.hopa.capabilities.display.ShellMain --token=<token>
```

Verified on the Galaxy S8 (Android 17): the class lives in `classes2.dex`
(`classes.dex` does not contain it), and `app_process` still finds it. The old
belief was wrong - a zip on the classpath goes through an ordinary
`PathClassLoader`, which loads every dex in it. The APK is world-readable
(`0644`), so `shell` needs no copy.

The Kotlin and Python strings change together, and
`check-helper-command.py` asserts the new fragments.

**MediaProjection is not a substitute.** It captures the default display, needs
consent for each session and a `mediaProjection` foreground service; it cannot
create a place for another app to run. The app already uses it for vision, and
that stays.

**A virtual display from the app is not a substitute either.** An app may create
a private display and draw into it, but launching a third-party activity onto a
non-trusted display is refused (`ActivityOptions.setLaunchDisplayId` requires a
public, trusted display); no app can drive another app's UI off-screen without
`shell`, root or a device-owner setup.

## Consequences

- `/data/local/tmp/hopa-dex` disappears, along with its 50 MB, the `rm -rf` on
  every start, the dex scan, and the failure "no dex carries ShellMain: rebuild
  with the keep rule" - the one that appeared when D8 moved the class between
  dex files.
- Start is one line and has no write to shared storage, so a phone with a full
  or read-only `/data/local/tmp` still works.
- The helper still needs `adb`: the phone's own wireless debugging (paired in
  the app) or the PC bridge. That is the price of hosting another app's UI, and
  it does not change here.
- A helper started from the old APK keeps running after an app update; the next
  start reads `pm path` again.
- **Supervision landed with `HelperSupervisor`**: one owner starts the helper
  when nothing answers, says whether it is alive, and stops it when the person
  chooses "Your screen". A start that never binds is a named failure instead of
  a socket that only looks connected. A helper left behind by a previous
  install was observed as `ppid=1` while the app believed the display was
  stopped; that is the state the supervisor makes visible.

## Alternatives considered

- **`VirtualDisplay` from the app instead of a helper** - rejected: it cannot
  host another app's activities.
- **`MediaProjection` instead of a helper** - rejected: capture only.
- **Extract the dex into the app's own cache and let `shell` read it** -
  rejected: `shell` cannot read app-private storage, that is the wall the whole
  design already respects.
- **Keep staging but reuse it when the APK is unchanged** - rejected once
  pointing `CLASSPATH` at the APK worked: reusing a copy is a smaller prize than
  not having one.
