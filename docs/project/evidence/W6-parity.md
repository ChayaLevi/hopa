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
| message radius | 9 / 10 | 16 | **no** |
| small radius | 4 / 6 / 8 | 10 / 12 | **no** |
| type scale | 9, 9.5, 10.5, 11.5, 12.5, 15 | 9, 10, 11, 14, 15, 18 | **no** |
| spacing rhythm | 4-based | 4-based with 14/16 paddings | close, not equal |

Colour is identical; shape and type are not. That is the W6 work.

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
| empty state | yes | yes | "Ask me something." |
| error state | inline text | status line under the composer | Windows is quieter |
| long text | wraps | wraps | screenshot needed at 200% |

## States

| State | Harmony | Windows |
|---|---|---|
| hover | custom tint | default WPF |
| focus ring | custom | default WPF |
| selected conversation | tinted row | tinted row |
| disabled control | dimmed | dimmed |
| busy (turn running) | send becomes stop | send becomes stop, tray says "working" |

Hover and focus are the visible difference: Harmony has its own, Windows uses the
Windows theme.

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
