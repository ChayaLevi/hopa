# W4 — desktop fit (code; visual checks pending)

Date: 2026-11-14. The session was locked, so this phase is written and tested
headlessly; the visual checks wait for an unlocked desktop.

## What exists

- **History**: search box filters titles and message text; the row menu offers
  Rename, Export and Delete; export writes the conversation JSON through a save
  dialog.
- **Tray**: a notification-area icon with Open and Exit; an answer that arrives
  while the window is unfocused shows a balloon with its first 140 characters.
- **Clipboard**: a Copy button puts the last answer on the clipboard.
- **Read aloud**: a Speak button uses `System.Speech`; Escape stops it.
- **Keyboard**: `Ctrl+N` new chat, `Ctrl+F`/`Ctrl+K` focus the search, `Esc`
  stops speech.
- **Window state**: position, size and maximized state are saved in
  `settings.json` and restored on start.
- **Accessibility**: names on the composer, send, search, conversations, New
  chat, Settings, Copy, Speak and Run.
- Sidecar operations: `conversation.search`, `conversation.rename`,
  `conversation.delete` (soft, tombstoned), `conversation.export`.

## Verified headlessly

```
Hopa.Windows.exe --history-check out.txt
pin: True
archive: True; hidden: True; shown when asked: True
created: ok
renamed: Groceries
search hits: 1
export has title: True
delete: True
conversations left: 5
```

Pinned conversations sort first; archived ones leave the list unless "Show
archived" is on; the row menu toggles both. The seven JVM tests for the stores
also run in CI (`gradlew -p apps/windows/sidecar test`).

## Voice

- **Read aloud** now uses the bridge `/speak` when an address and token are
  configured, and falls back to `System.Speech` otherwise.
- **Dictation**: the composer has a Mic button; it records 16 kHz mono PCM with
  NAudio, wraps it in a WAV header and posts it to the bridge `/transcribe`; the
  text lands in the composer and is never sent automatically.
- On a 401 the voice client calls `/refresh` with the stored refresh token and
  retries once, persisting the rotated pair; the sidecar also sends its rotated
  pair to the host (`session`) so the two halves stay in step.

Round-trip through the real bridge (no microphone needed):

```
Hopa.Windows.exe --voice-check http://127.0.0.1:8897 out.txt
speak: ok (82040 bytes)
transcribe: 'Hello from hope that'
round-trip: ok
```

The bridge synthesised "hello from hopa" with the PC voice, the client posted the
WAV back to `/transcribe`, and the text came out recognisable.

Microphone capture itself is wired but **not verified**: the session is locked
and no capture was exercised.

## Files and the clipboard

- Dropping a file on the window copies it into `%USERPROFILE%\.hopa\windows\attachments`
  with the phone's 4 MiB cap, and shows a pending row above the composer
  (name, size, type, Remove). Sending without a caption uses
  `Shared an image: <name>` or `Shared a file: <name>`.
- The sidecar stores the attachment on the user turn, so it survives a restart.
- Copy puts the last answer on the clipboard; that is the share-out path.

```
Hopa.Windows.exe --attach-check <png> out.txt
attached: hopa-attach.png (28910 bytes)
in conversation: True
copy exists: True
```

## Stop

While a turn runs the Send button becomes Stop, the tray offers Stop and sets its
tip to "HOPA (working)", and Escape cancels. The sidecar sets a cancel flag that
the loop checks between steps.

```
Hopa.Windows.exe --cancel-check out.txt
answer: Stopped.
cancelled: True
steps: 0
```

A turn that was told to be slow was cancelled after one second; the loop returned
"Stopped." with no steps. A model call already in flight finishes first, because
the check sits between steps rather than inside the provider call.

## Desktop close-out (2026-11-14)

The window-only checks now run without a person: `Hopa.Windows.exe --w4-check
<dir>` writes a transcript and a screenshot.

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

- Tray, balloon, Stop state and tooltip are exercised.
- Window restore now refuses a saved position that is no longer on any screen;
  that is the multi-monitor case after a monitor is unplugged.
- Dictation is wired (`TranscribeAsync`); the round-trip needs a paired bridge.
- Contrast: high contrast is *detected*, not yet applied — the palette is fixed.

The row-by-row comparison with the Harmony laptop app is in `W4-parity.md`; the
look is in `W6-parity.md`.

## Not done

- **Share out** through the Windows share sheet is not built; Copy covers the
  clipboard path.
- Full keyboard navigation pass (tab order, visible focus).
- High-contrast palette and a 200% scaling pass.
