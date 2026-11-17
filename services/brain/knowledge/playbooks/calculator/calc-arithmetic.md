---
id: calculator.arithmetic
app: calculator
platform: android
package: com.sec.android.app.popupcalculator
intent: calculate
params: [expression]
needs: []
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# Calculator - work out one expression

The person says "work out 12 × 7". The phone opens the calculator, presses the
keys, and the display ends at `84`. This is the smallest complete playbook:
taps on a stable grid, and a result that is either right or wrong with nothing
in between.

## Why this one first

- No network, no login, no permissions, no money.
- The outcome is a number the phone itself prints, so the check is exact.
- If a step is wrong, the final number is wrong, and the run stops there.

## Steps

```yaml
- id: open
  do: launch
  value: com.sec.android.app.popupcalculator
  wait_ms: 3000
  expect:
    package: com.sec.android.app.popupcalculator
  on_mismatch: stop

- id: clear
  do: tap
  anchor:
    text: ["AC", "C"]
    desc: ["Clear"]
  optional: true
  expect:
    node_text: ["0"]
  on_mismatch: stop

- id: one
  do: tap
  anchor:
    text: ["1"]
  expect:
    node_text: ["1"]
  on_mismatch: stop

- id: two
  do: tap
  anchor:
    text: ["2"]
  expect:
    node_text: ["12"]
  on_mismatch: stop

- id: times
  do: tap
  anchor:
    text: ["×", "x", "*"]
    desc: ["Multiply"]
  expect:
    node_text: ["12×", "12x", "12 ×", "12 *"]
  on_mismatch: stop

- id: seven
  do: tap
  anchor:
    text: ["7"]
  expect:
    node_text: ["7"]
  on_mismatch: stop

- id: equals
  do: tap
  anchor:
    text: ["="]
    desc: ["Equals"]
  expect:
    node_text: ["84"]
  on_mismatch: stop
```

## What "it worked" looks like

Some element shows `84` - the result display, not the history line and not the
keypad. Read it once from a dump to know which node it is; after that the check
is exact.

## When it does not match

| where it stops | what it means | what to do |
|---|---|---|
| `open` | the popup calculator did not come to the front | `stop`; try the full-screen activity, or record the launcher point |
| `clear` | no `C`/`AC` on this build | it is optional, so the run continues; if the display is not `0`, stop and check by hand |
| `one` / `two` | the keypad is not a grid of text buttons on this phone | `stop`; the display is drawn, the keys are not - this app is not a playbook app |
| `times` | the multiplication sign differs (`×`, `x`, `*`) | add the label you saw; never guess the sign |
| `equals` | the result is not `84` | the taps were right but the state was wrong (something left from before); run `clear` first |

## What to confirm before trusting this file

- **Popup or full screen.** Samsung's "popup calculator" can open as a floating
  window; the tree then has two roots and the keypad may be in the smaller one.
  If the popup is awkward, launch the full-screen activity instead and update
  `open`.
- **Key labels.** Take them from a real dump on this phone in this language.
  Buttons are usually `Button` with `text`; the display is one `TextView`.
- **History strip.** If a history line shows an older `84`, `node_text: ["84"]`
  can pass for the wrong reason. Point the check at the display node, by adding
  `near: {text: "="}` once you know where it sits.

## The point of this file

Two numbers, seven taps, no model, and a check that cannot be argued with. If
this playbook passes on Your screen and on the hidden screen, the no-model path
is real; if it does not, nothing else will.
