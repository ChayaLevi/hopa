---
id: clock.set-alarm-ui
app: clock
platform: android
package: com.sec.android.app.clockpackage
intent: set alarm
params: [hour, minute]
needs: [screen access]
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# Clock - set an alarm at 07:30, by tapping

The fallback for `set-alarm-intent.md`: the same outcome, reached through the
UI. Read this file when the intent is not delivered, or when the person wants to
see it happen.

**This playbook is half-written on purpose.** The first steps are anchors; the
time picker is a wheel with no text, and the file says so instead of pretending.
Until that step is solved on a real phone, keep the intent path as the default.

## Steps

```yaml
- id: open
  do: launch
  value: com.sec.android.app.clockpackage
  wait_ms: 3000
  expect:
    package: com.sec.android.app.clockpackage
  on_mismatch: stop

- id: alarm-tab
  do: tap
  anchor:
    text: ["Alarm", "Alarms"]
    desc: ["Alarm tab", "Alarms"]
  expect:
    node_text: ["Alarm", "Alarms"]
  on_mismatch: stop

- id: add
  do: tap
  anchor:
    desc: ["Add alarm", "Create alarm"]
    text: ["+"]
  wait_ms: 3000
  expect:
    present:
      desc: ["Save", "OK", "Done"]
  on_mismatch: stop

# The picker is the fragile part. On Samsung it is a wheel: the hour and the
# minute are usually `NumberPicker`-like nodes or a drawn dial with no readable
# text. A swipe needs a point, and a point needs `verified_on`.
#
# If the dump shows the wheel as two scrollable nodes, drag the hour one until
# it reads 07, then the minute one until it reads 30, checking after each drag.
# If there is no readable value at all, stop here: this screen is not a
# playbook screen on this build.

- id: hour
  do: swipe
  by: point
  from: [540, 1150]
  to: [540, 1000]
  times: 1
  unverified: true
  expect:
    node_text: ["07", "7"]
  on_mismatch: ask

- id: minute
  do: swipe
  by: point
  from: [800, 1150]
  to: [800, 1010]
  times: 1
  unverified: true
  expect:
    node_text: ["30"]
  on_mismatch: ask

- id: save
  do: tap
  anchor:
    text: ["Save", "Done", "OK"]
    desc: ["Save"]
  wait_ms: 3000
  expect:
    node_text: ["07:30", "7:30", "07.30"]
  on_mismatch: stop
```

## What "it worked" looks like

The alarm list shows the new alarm at 07:30. Same check as the intent file: one
element's text, nothing inferred.

## When it does not match

| where it stops | what it means | what to do |
|---|---|---|
| `alarm-tab` | the app opened on another tab, or the tab label differs | `stop`; read the tab row and add the label |
| `add` | the `+` has no description, or the list is empty in a different way | `stop`; find the control in the dump and add its `desc` |
| `hour` / `minute` | the wheel does not expose its value | `ask` - and this is the honest answer: the screen cannot be checked, so a person should set it |
| `save` | the picker was changed but the value is not 07:30 | `stop`; the run may have set the wrong time, so say so |

**Never** let a wrong alarm stand: if any picker step is uncertain, the playbook
must end with `ask`, and the person confirms the time on the phone.

## What to confirm before trusting this file

- Whether the picker exposes `NumberPicker` nodes with a readable value. If it
  does, the whole thing becomes text anchors and this file loses its `by: point`
  steps.
- The `+` button. On Samsung it is often an `ImageButton` with the description
  `Add alarm`; on a phone in another language the label is translated, so take
  it from a dump.
- The tabs in the header. Samsung's clock sometimes opens on the world clock
  tab, so `alarm-tab` is not optional in practice.
- Run it **after** `set-alarm-intent.md` on the same phone: if the intent works,
  this file is only for demos and for phones that ignore the intent.
