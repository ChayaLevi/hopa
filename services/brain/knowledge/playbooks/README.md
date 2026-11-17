# Playbooks: steps a phone can run without a model

A playbook is one use case for one app, written as a short list of steps with an
anchor and an expectation each. An executor can run it with no model call: it
looks for the anchor in the screen tree, acts, then checks that the expectation
holds. If anything does not match, it stops - it never guesses.

Nothing here is generated yet: these files are hand-written and hand-checked,
the way `apps/*/APP.md` is. The catalog generator ignores this folder.

## File shape

```markdown
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

# Calculator - one expression

One paragraph: what the person says, and what the phone ends up showing.

```yaml
- id: open
  do: launch
  value: com.sec.android.app.popupcalculator
  expect:
    package: com.sec.android.app.popupcalculator
  on_mismatch: stop
```
```

## Step fields

| field | meaning |
|---|---|
| `id` | short name, unique inside the file |
| `do` | `launch`, `tap`, `type`, `wait`, `assert`, `intent`, `back`, `scroll` |
| `value` | what to type, which package to launch, or the intent action |
| `extras` | key/value pairs for an intent |
| `anchor` | how to find the element: see below |
| `expect` | what must be true after the step: see below |
| `optional` | `true` when the step may be skipped, e.g. an ad that is not there |
| `unverified` | `true` on a step that has not run on a real phone yet |
| `needs_approval` | the person is asked before this step; without a yes, the run stops |
| `approval_text` | what the question says, e.g. `Send this to {{contact}}?` |
| `when` | the step runs only if this parameter was given, e.g. `when: pick` |
| `wait_ms` | how long to wait for `expect` before deciding it failed (default 2000) |
| `on_mismatch` | `stop` (default), `ask`, or `retry:2` for screens that load slowly |

A templated number - `index: "{{pick}}"` - is substituted and then parsed as a
number. An out-of-range index fails the step; it never falls back to another row.

## Parameters

A value in double braces is filled from the use case before matching:

```yaml
- id: type-message
  do: type
  anchor: {type: ["EditText"]}
  value: "{{message}}"
  expect:
    node_text: ["{{message}}"]
```

`{{contact}}` and `{{message}}` come from the goal, after the deterministic
step has read them. Substitution happens on the string, so the same playbook
serves every contact and every text.

## Anchors

```yaml
anchor:
  text: ["7"]                 # exact node text, after trimming, case-insensitive
  desc: ["Add alarm"]         # contentDescription, for icon buttons
  type: ["Button"]            # optional class guard
  near: {text: "Alarm"}       # another anchor within 200 px, to break ties
  index: 0                    # which match to take when several are equal
  by: point                   # last resort: recorded coordinates, needs verified_on
```

A `do: swipe` step has no anchor; it carries the points instead:

```yaml
  do: swipe
  by: point
  from: [540, 1150]
  to: [540, 1000]
  times: 2
```

Rules:

1. `text` and `desc` lists are alternatives, not a sequence: any one match wins.
2. List every label the phone may show. The phone's language decides the text,
   so a phone set to another language shows a translated label: take it from a
   dump and add it here, never from memory.
3. An icon with no text is only usable through `desc`. If a screen has neither,
   it is not a playbook screen - stop.
4. `by: point` is allowed only when the file says `verified_on` with a real
   device and date. A coordinate from another phone is a lie.

## Expectations

```yaml
expect:
  package: com.sec.android.app.popupcalculator  # the app in front
  node_text: ["84"]        # some element shows this text
  text: ["Transfer"]       # the joined screen text contains this
  absent: ["Error"]        # and this must not be there
  focused: true            # an editable field has focus
  present:                 # an anchor that must exist on the new screen
    desc: ["Save", "Done"]
```

Every step carries one. A step without an expectation is not a step, it is a
hope.

## Collecting a list

Some use cases do not end in a tap: the answer is what is on the screen. A
`collect` step reads a repeated shape and returns it as the answer.

```yaml
- id: results
  do: collect
  from: {type: ["RecyclerView"]}          # the container that holds the rows
  rows:
    title: {type: ["TextView"], take: 1}  # per row: the first TextView
    price: {text_matches: "^(US \\$|€|£)"} # per row: the node that looks like a price
  take: 5                                 # at most five rows
  expect:
    list_at_least: 3                      # fewer than three means the page did not load
  on_mismatch: stop
```

Rules:

1. `from` picks the container; each direct child of it is one row.
2. `rows` names the fields to read out of a row. `take: 1` means the first match
   in that row, in tree order.
3. `text_matches` is a regular expression, matched against the node's text.
4. The collected rows become the answer, in the order they appear. Nothing is
   tapped.
5. If the use case also needs to open a row, a later `tap` step uses `pick` to
   choose which one; `pick` defaults to the first.

## When it does not match

| `on_mismatch` | what happens |
|---|---|
| `stop` | the run ends, the receipt says which step and what was on screen |
| `ask` | the person is asked, and the answer becomes the next attempt |
| `retry:2` | only for waits and slow screens; never for a wrong anchor |

Default is `stop`. That is the whole point: without a model, the honest answer
to an unknown screen is "I do not know this screen", not an improvised tap.

## Writing one

1. Open the app on **Your screen** and walk the use case once by hand.
2. Take the tree from that screen (`ELEMENTS`) and copy exact labels into
   `text` / `desc`. Do not type them from memory.
3. Put the shortest list of steps that still ends in something checkable -
   a number, an address, a title, a saved item.
4. Run it on Your screen, then on the hidden screen. Same file, both places.
5. Record the device and date in `verified_on` only after both runs pass.
