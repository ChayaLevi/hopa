# The Harmony laptop look, read from its code

Source: `apps/harmony/laptop/src/main/ets` (page `LaptopIndex.ets`, `ChatRail.ets`,
`CommandPalette.ets`) and `resources/base/element/color.json`. These are the values
the Windows client is now built to; the comparison lives in `W6-parity.md`.

## Colour (resource names in the Harmony app)

| Token | Value | Used for |
|---|---|---|
| bg | `#F7F4EE` | window |
| surface | `#FFFFFF` | composer pill, selected session row, rail card |
| surface_alt | `#F3EFE7` | sidebar |
| surface_muted | `#F1ECE3` | search field, round mic button, "Not now" |
| text_primary | `#1C1B18` | titles, answer text |
| text_secondary | `#6B665C` | session titles, verbs |
| text_hint | `#9A9488` | subtitles, meta, labels |
| **primary** | **`#0A59F7`** | **send button, Allow, Save, user bubble** |
| primary_soft | `#E8F0FF` | step numbers, approval card border |
| accent | `#E8703A` | brand mark only |
| danger | `#C0392B` | errors, failed step dot |
| success | `#2E7D5B` | succeeded dot |
| warning | `#B26A00` | blocked step |
| divider | `#E9E4DA` | every 1 px rule |

The single most common mistake: the accent orange is **brand**, not the action
colour. Actions are blue.

## Geometry

| Part | Size |
|---|---|
| sidebar | 212 wide, full height, `surface_alt`, 1 px right divider |
| sidebar gutter | 12 left/right, 14 top, 8 bottom |
| new chat row | 30 high, radius 8 |
| search field | 34 high, radius 8, "Ctrl K" hint inside on the right |
| session row | 30 high, radius 7, selected = `surface` |
| group label | 9 bold, letter-spacing 0.7, uppercase |
| header | 56 high, 22 gutter, bottom divider |
| header mark | 26 |
| header title | 15 medium, letter-spacing -0.2 |
| header subtitle | 10 hint |
| window buttons | 30 × 28, radius 6 |
| conversation gutter | 22 left/right, 10 between turns |
| user bubble | radius 14, padding 14/10, 13.5/21, max width 62 % |
| answer | mark 22 + text 13.5/22, no bubble |
| answer meta | 9.5 hint ("HOPA on this laptop · Copy") |
| composer gutter | 22 left/right, 8 top, 18 bottom |
| composer pill | radius 24, 1 px divider border, padding 20/3/8/3 |
| composer text | 13.5, min height 44 |
| send / stop / mic | 44 × 44, radius 22 |
| right rail | 258 wide, card radius 14, 1 px border, padding 14/12/14/10, 10 side gutters, 68 top |

## Type scale

9 · 9.5 · 10 · 10.5 · 11 · 11.5 · 12 · 12.5 · 13 · 13.5 · 14 · 15 · 16 · 17 · 18
on the Harmony side; the windows in use are **9, 9.5, 10, 10.5, 11.5, 12.5, 13.5,
15**, which is what the Windows client now uses.

## Component behaviour

- **Composer button**: one 44 circle, four faces — mic when empty, blue arrow up
  when there is text, grey square while running, red square while listening.
- **Send**: Enter sends, Shift+Enter makes a line.
- **Session row**: hover shows "⋯" for Rename/Delete; selected row turns white.
- **Approval**: lives in the right rail when the rail is visible, otherwise as a
  card above the composer; two buttons, "Not now" muted and "Allow" blue.
- **Rail**: `THIS ANSWER`, status dot + line, facts, steps, optional frame,
  earlier runs, measured latency, bridge line at the bottom.
- **Empty**: mascot 64–108 (scales with width), tagline, one promise line, and a
  key hint if no key is present.

## What the Windows client now matches

Sidebar width and colours, header height and type, conversation gutter, user
bubble geometry and blue fill, answer as mark + text, composer pill with the 44
circle, rail at 258 with the same labels, and the blue primary action colour.
