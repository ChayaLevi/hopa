# Elevated windows and the uiAccess helper

Windows separates normal processes from elevated ones. A normal process cannot
read the UI Automation tree of an elevated window, click in it, or type into it;
the system refuses at the boundary. This is a security feature, not a bug in HOPA.

## What HOPA does today

- It detects the boundary: if the foreground window belongs to an elevated
  process and HOPA is not elevated, the action fails with a message that names the
  app and points here.
- It does **not** try to work around it. No synthetic input into the secure
  desktop, no prompt-stealing, no silent retry.
- The error is reported to the turn, so the person sees why nothing happened.

The message reads:

> `'<app>' runs elevated and HOPA does not; Windows blocks input across that
> boundary. Install the signed uiAccess helper
> (docs/guides/windows/uiaccess.md) or run HOPA elevated on purpose.`

## The helper

Windows allows exactly one exception: a binary marked `uiAccess="true"` in its
manifest, **signed**, and installed under `%ProgramFiles%`. Such a binary may send
input to elevated windows.

Steps, once a code-signing certificate exists:

1. Build the helper as a separate executable (`Hopa.Windows.UiAccess`), with a
   manifest containing `requestedExecutionLevel level="asInvoker" uiAccess="true"`.
2. Sign it with a certificate whose subject is trusted on the machine (a
   self-signed certificate installed in *Trusted Root Certification Authorities*
   is enough for internal use).
3. Install it under `C:\Program Files\HOPA\`.
4. HOPA looks for the helper at that path, starts it only for a step that needs
   it, and passes the action through a local named pipe.
5. If the helper is missing or unsigned, HOPA keeps refusing with the message
   above; it never falls back to something unsafe.

## What is not claimed

- `uiAccess` does not open the secure desktop (UAC consent, lock screen). Those
  remain refused by the operating system.
- The helper does not run by default and is not installed by the MSI until it is
  signed.
- Without a certificate, the feature is **not** shipped. The refusal path is.
