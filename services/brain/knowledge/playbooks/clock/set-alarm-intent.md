---
id: clock.set-alarm-intent
app: clock
platform: android
package: com.sec.android.app.clockpackage
intent: set alarm
params: [hour, minute]
needs: []
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# Clock - set an alarm at 07:30, through the system intent

The person says "set an alarm for 7:30". No screen is read and no tap is
made: Android's own `SET_ALARM` intent hands the clock app an alarm, and the app
shows it. This is the cheapest path in the whole product, and the first one to
try.

## Why this path first

There is nothing to see and nothing to guess. If the intent is delivered, the
alarm exists; if it is not, the step fails with a clear reason.

## Steps

```yaml
- id: alarm
  do: intent
  value: android.intent.action.SET_ALARM
  extras:
    android.intent.extra.alarm.HOUR: 7
    android.intent.extra.alarm.MINUTES: 30
    android.intent.extra.alarm.MESSAGE: "HOPA"
    android.intent.extra.alarm.SKIP_UI: false
  wait_ms: 4000
  expect:
    package: com.sec.android.app.clockpackage
    node_text: ["07:30", "7:30", "07.30"]
  on_mismatch: stop
```

## What "it worked" looks like

The clock app is in front and the alarm list shows the new alarm at 07:30.
Nothing else counts: a toast saying "Alarm set" is not proof, because the alarm
may still have been rejected.

## When it does not match

| what you see | what it means | what to do |
|---|---|---|
| no clock app in front | the intent was not delivered, or a chooser appeared | `stop`, and fall back to `set-alarm-ui.md` |
| clock app, but no 07:30 | the app opened on another tab, or the alarm was not saved | `stop`; read the screen and record what is there |
| a permission or "allow" dialog | first run on this phone | ask the person once, then run again |

## What to confirm before trusting this file

- The extra names. `SKIP_UI: true` sets the alarm without opening the app on
  many builds; if it works on this phone, the whole playbook becomes one step
  with no screen at all.
- The time format follows the phone's locale. A 12-hour phone shows `7:30 AM`,
  so add that to `node_text` after the first run.
- The package name is Samsung's clock. A Google build uses
  `com.google.android.deskclock`; a different phone needs its own line here,
  not an edit to this one.
