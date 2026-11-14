# W6 parity: the look, Harmony laptop vs Windows

Reference values are read from the code on both sides, not from screenshots:
`apps/harmony/laptop/src/main/ets` and `apps/windows/Hopa.Windows/MainWindow.xaml`.
The Windows screenshot is `docs/media/windows-w4.png`.

## Tokens

| Token | Harmony | Windows | Same? |
|---|---|---|---|
| page background | `#F7F4EE` | `#F7F4EE` | yes |
| rail background | `#F3EFE7` | `#F3EFE7` | yes |
| accent | `#E8703A` | `#E8703A` | yes |
| ink | `#1C1B18` | `#1C1B18` | yes |
| muted | `#9A9488` | `#9A9488` | yes |
| secondary ink | `#6B665C` | `#6B665C` | yes |
| message radius | 9 / 10 | 10 | yes |
| small radius | 4 / 6 / 8 | 9 / 10 | close |
| type scale | 9, 9.5, 10.5, 11.5, 12.5, 15 | 9, 9.5, 10.5, 11.5, 12.5, 15 | yes |
| spacing rhythm | 4-based | 4-based with 14/16 paddings | close, not equal |

Tokens changed on 2026-11-14 to close this table:

- every `FontSize` moved onto the Harmony scale;
- message radius 16 -> 10, composer 18 -> 10, attachment 12 -> 9;
- a real bug the table found: `Send` and `Save` were `#0A59F7` (Windows blue),
  not the accent `#E8703A`. Fixed.

The screenshot `docs/media/windows-w4.png` is the result.

## Components

| Component | Harmony | Windows | Notes |
|---|---|---|---|
| rail with title, search, archived toggle | yes | yes | same structure |
| conversation row: title, pin marker, time, archived marker | yes | yes | Windows adds a pin label |
| message bubbles, user right / assistant left | yes | yes | Windows max width 640 |
| composer with attach, mic, send/stop | yes | yes | Windows adds a copy button |
| attachment bar above the composer | yes | yes | same 4 MiB cap |
| approval sheet | yes | yes | Windows is a separate window |
| settings window | yes | yes | provider, key, bridge, tone |
| empty state | yes | yes | "Ask me something."; search now has a placeholder |
| error state | inline text | status line under the composer | Windows is quieter |
| long text | wraps | wraps | screenshot needed at 200% |

## States

| State | Harmony | Windows |
|---|---|---|
| hover | custom tint | `#F3EFE7` tint, one template for every button and row |
| focus ring | custom | `#E8703A` 1.5 px ring on the focused control |
| pressed | darker tint | `#E9E4DA` |
| selected conversation | tinted row | `#E9E4DA` row |
| disabled control | dimmed | 0.45 opacity, arrow cursor |
| busy (turn running) | send becomes stop | send becomes stop, tray says "working" |

The states live in `App.xaml`: one `Button` template and one `ListBoxItem`
template, so a new button cannot drift.

Keyboard: `KeyboardNavigation.TabNavigation=Cycle`, 17 focusable controls, 13 of
them named for UIA (`w4-check.txt`).

Screenshots so far (all rendered by the app itself, not captured by hand):

| Screenshot | Shows |
|---|---|
| `docs/media/windows-w4.png` | rail, conversation row, composer, empty state |
| `docs/media/windows-w6-settings.png` | settings, provider fields, orange Save |
| `docs/media/windows-w6-approval.png` | approval sheet with amount, merchant, consequence |
| `docs/media/windows-w6-steps.png` | run details, one line per step |

Still missing from the inventory: bubbles with real content, the attachment bar,
the error state and long text. They need a turn, not a render, which is why they
are not ticked yet.

## Keyboard and scaling

- Shortcuts: Ctrl+N, Ctrl+F/K, Enter, Shift+Enter, Esc — present on both, verified
  on Windows in `w4-check.txt`.
- Full tab-order pass: **not done**.
- 200% scaling: **not verified**; 150% is proven by `density=1.5` in W3.
- High contrast: detected (`SystemParameters.HighContrast`), but the palette is
  fixed, so high contrast does **not** yet change the look.

## What closing W6 means

1. One type scale and one radius scale, taken from the Harmony code.
2. Hover and focus states written once and shared by every control.
3. A high-contrast palette that switches on `SystemParameters.HighContrast`.
4. Screenshots side by side at 100%, 150% and 200%, then the table above with no
   "no" or "not verified" left.
