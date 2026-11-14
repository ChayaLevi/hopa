# W4 parity: behaviour, Harmony laptop vs Windows

Reference: `apps/harmony/laptop/src/main/ets`. Every row is a behaviour, not a
look; the look is W6 (`W6-parity.md`).

| Behaviour | Harmony laptop | Windows | Evidence | Gap |
|---|---|---|---|---|
| Chat with a model | yes | yes | W1-shell; W2-loop | none |
| Sidebar of conversations | yes | yes | `docs/media/windows-w1.png` | none |
| History: search, rename, delete, export | yes | yes | W4-desktop "Verified headlessly"; 7 store tests | none |
| Pin and archive | yes | yes | W4-desktop | none |
| Approvals for sensitive and payment steps | yes | yes | W2-loop allow/deny; pending survives restart | none |
| Settings: provider, key, bridge, tone | yes | yes | W1-shell settings round-trip; Credential Manager | none |
| Attachment in, drag and drop | share intent | file drop + picker | W4-desktop attach-check; 4 MiB cap | Android share target has no Windows twin yet |
| Share out | share sheet | clipboard copy | W4-desktop Copy button | a real share sheet is not built |
| Read aloud | system TTS | bridge `/speak`, `System.Speech` fallback | W4-desktop "Voice"; Escape stops | none |
| Dictation | system speech | mic → bridge `/transcribe` | `TranscribeAsync`; needs a paired bridge | not exercised headlessly |
| Toast while unfocused | in-app | tray balloon | `w4-check.txt` "toast: shown" | none |
| Tray with run state and Stop | n/a (tab app) | icon, tooltip "HOPA (working)", Stop | `w4-check.txt`: Stop disabled before a turn | none |
| Shortcuts | keyboard-first | Ctrl+N, Ctrl+F/K, Enter, Shift+Enter, Esc | `w4-check.txt` shortcuts line | full tab-order pass not done |
| Window state restored | n/a | size, position, maximized | `w4-check.txt`: on-screen true, off-screen refused | per-monitor size not verified |
| Accessibility names | ArkUI labels | 13 named controls | `w4-check.txt` | high contrast and 200% not verified |

Transcript (`Hopa.Windows.exe --w4-check <dir>`):

```
tray icon created: True
tray Stop disabled before a turn: True
tray tooltip: 'HOPA'
high contrast active: False
shortcuts: Ctrl+N new chat; Ctrl+F or Ctrl+K search; Enter send; Shift+Enter newline; Esc stop
window bounds: 1986,93 1140x760
window on a screen: True
off-screen restore refused: True
automation names on controls: 13
toast: shown through the tray balloon
screenshot: docs/media/windows-w4.png
```

Known gaps, in order of user impact: a real share sheet, full keyboard
navigation, high-contrast theming, and dictation exercised against a live bridge.
