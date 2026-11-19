# App knowledge: one description, four clients

How HOPA knows that "Bilibili" exists, what it is for, how to open it on each
platform, and when it must ask before acting.

## One source of truth

Every app is one file: `services/brain/knowledge/apps/<id>/APP.md`. The front
matter names identifiers per platform; the body holds the purpose paragraph and
up to four guardrail bullets.

```yaml
name: bilibili
display: Bilibili
aliases: [Bilibili, B站, bili]
pace: careful
harmony: yylx.danmaku.bili
harmony_ability: EntryAbility
harmony_verified: Mate 80, 2026-11-08
android: tv.danmaku.bili
android_activity:
android_verified:
windows:
windows_window:
windows_verified:
```

- Every file carries all three platforms with the same nine keys. A blank
  `id` means "no launch target known here", not "does not exist".
- Keys are flat `key: value` lines; the generator's parser has no YAML
  dependency and does not need one.
- `*_verified` records the device and date that confirmed the identifier.
  Missing means unconfirmed: it may be hinted, never the only launch path.
- The folder is also the slot for evidence (screenshots, device dumps).

The catalog renders that as one block per platform, identical in shape:

```json
"platforms": {
  "harmonyos": {"id": "yylx.danmaku.bili", "detail": "EntryAbility",
                "confirmed": true, "verified": true,
                "device": "Mate 80, 2026-11-08", "state": "verified"},
  "android":   {"id": "tv.danmaku.bili", "detail": "",
                "confirmed": true, "verified": false,
                "device": "", "state": "unconfirmed"},
  "windows":   {"id": "", "detail": "",
                "confirmed": false, "verified": false,
                "device": "", "state": "unknown"}
}
```

`state` is derived, never written: `verified` (id plus a device), `unconfirmed`
(id, no device), `unknown` (no id).

## Generation

```
services/brain/knowledge/apps/*/APP.md
services/brain/knowledge/sites/*/SITE.md
        │  tools/scripts/build-app-catalog.py
        ▼
spec/catalog/catalog.v1.json        every app, every platform
        │
        ├─ AppCatalog.ets   (Harmony)  reads platforms.harmonyos only
        ├─ AppCatalog.kt    (Kotlin)   reads all three blocks
        └─ catalog.v1.json  (tools, services/evals)
```

CI runs `build-app-catalog.py --check`; a stale generated file fails the build.
Adding a platform block never changes another platform's generated file — the
ArkTS output is byte-identical when only `platforms.android` changes.

## What a client uses

| client | launch identifier | inventory | prompt block |
|---|---|---|---|
| Android | `platforms.android.id` | launcher activities from PackageManager | installed apps, label - package |
| Harmony phone / laptop | `platforms.harmonyos.id` + `detail` | bundle manager | catalog names + purposes |
| Windows | `platforms.windows.id` | running windows + start menu (UIA) — not built yet | catalog names + purposes |

Shared by every client: id, display, aliases, purpose, guardrails, risk classes
(`spec/policy/approval.v1.json`), and the site fallback.

Different per client: only the identifier that `ui.launch` receives.

Every launchpad lists only the entries that have a target on that client:
Android hides catalog entries with no Android package, Harmony hides entries
with no HarmonyOS bundle, Windows hides entries with no exe. The catalog stays
whole in `spec/`; the filter happens where the target is resolved, so a row
on screen is always a row that can open.

## Runtime rules

1. The model names an app by display name or id, never by package.
2. The client resolves the catalog entry to *its* identifier
   (`CatalogRoutes.targets(installed, platform)`); only that function may read a
   platform column, and the structure check enforces it.
3. No id on this platform, or no id at all → nothing is guessed: the router says
   no launch target is verified and offers the site fallback.
4. On Android an id is used when it is `verified`, or when the package is really
   in the installed inventory. An unconfirmed id the phone does not have falls
   back to a label match; with no match the target stays empty.
5. Windows uses the catalog's `windows` column as its app list (there is no
   installed inventory yet), so a wrong exe fails honestly at launch.
6. A launch that the device refuses is reported as a device error, not as a
   success.
7. An app with a target but no install still gets a row: on Android, tapping it
   offers the Play Store page for that package, and a package Play answers 404
   for says so instead of opening a blank page.
8. The catalog block the model reads carries the guardrails on the same
   line as the name and target ("payments always ask", "never read a code"), so
   an app's rules travel with it instead of living only in the UI.
9. Trust is per app and lives in settings: a trusted app skips the question for
   its typed calls, messages and closing, while payments, uninstalling and
   deleting always ask. The list is shared with Harmony through
   `spec/policy/approval.v1.json`.
10. On Android a row opens the app's card: status, the surface a run would use,
   the package, and the catalog's guardrail bullets. The card, not Settings, is
   where a person judges whether HOPA should touch that app.

## Coverage today

54 apps. 44 have an Android package, 16 an exe, 26 a Harmony bundle (all
verified on a device). Android packages for the Galaxy S24 set and Windows exes
for the desktop set are unconfirmed until a run on that device; the inventory
check above is what keeps them safe in the meantime.

## Vocabulary versus truth

The catalog is vocabulary: names, aliases, purposes, guardrails. The device is
truth: what is installed, what can be started. The prompt shows the model the
device; the router uses the catalog to understand the sentence.

## What is not here yet

- Windows inventory: the sidecar cannot list installed apps, so Windows has no
  installed-app prompt block. Only the catalog's `windows` column is used.
- Windows identifiers: four apps (WeChat, Feishu, WPS, Notepad) carry an exe;
  the other 31 are `unknown` until we map them.
- Per-platform guardrail differences: the bullets are shared even when one
  platform has no equivalent action.
- Vision: a screenshot can ride with the model request on Android (opt-in, off
  by default), so a canvas or a video is not text-only any more.
