# The Android app

Status: the active client. The loop runs **in the app** (ADR-0016), execution has
several surfaces behind one port (ADR-0017, extended by `ScreenPathOrder`), and
everything about opening another app lives on the Act page.

## Modules

```
apps/android/
├─ app/            Compose UI, navigation, ViewModels, state holders
├─ capabilities/   DevicePort adapters: accessibility, hidden display,
│                  wireless shell, system intents, package inventory
└─ data/           Room and DataStore: sessions, settings, secrets, history

packages/
├─ spec/      wire types, port interfaces, model types, approval data classes
├─ engine/         loop, prompt blocks, router, gate, records
├─ net/            model client, bridge client (AdbDevice)
├─ stores/         store interfaces and session models
└─ knowledge/      generated catalog, policy data and prompt rules
```

`contracts` and `engine` have no Android imports, so they are unit-testable on
the JVM and reusable by the Windows sidecar. `capabilities` and `data` keep
Android and Room behind their edges.

## What runs where

```
AgentLoop → ActionExecutor → DevicePort
                             ├─ AccessibilityDevice     the phone's own screen
                             ├─ ShellDisplayDevice      a hidden virtual display
                             ├─ AdbDevice               a paired PC over adb
                             └─ ScreenSourceDevice      picks one, keeps it for the turn
```

`ScreenSourceDevice` asks each source for one observation and uses the first that
answers; every action in the turn goes through that same source. A source that
cannot act throws instead of returning a friendly string, so a turn is never
recorded on a surface that is not there.

## Where a person changes it

Capabilities has one card at the top, **Act**:

- a summary card: This phone / My computer, and Hidden screen / Your screen;
- the apps the catalog knows, with See all into the Launchpad;
- what a run needs: screen access, hidden screen, my computer;
- three run switches: plan first, look at the screen, repeat what worked.

The summary opens **How HOPA acts**, which asks two questions: who sends the
taps (This phone or My computer), and — on this phone — whether the app opens on
the hidden screen or on your screen. Apps always stay on this phone; the
computer is only another driver for this phone's visible screen.

Settings no longer shows anything about other apps. The Launchpad is the catalog
(two sections, search, status per row); a row opens the app card, which carries
the status, the surface, the trust switch, the catalog's guardrails and the
Play Store fallback. Screen access, the hidden screen and My computer are
reached from Act.

## Model and keys

- OpenAI-compatible client, same request shape as Harmony, plus optional image
  frames (`ModelRequest.images`).
- The key lives in Android Keystore; the settings screen never prints it.
- Provider, endpoint and model are settings, not build constants.

## Data

- Sessions, turns, run records, skills, pending approvals and run history in
  Room (version 7).
- Settings and the trusted-app list in DataStore; keys in Keystore.
- Screenshots are files or frames in memory, never blobs in the database.

## Testing

- JVM: `:engine:test`, `:transport:test`, `:stores:test`, `:app:testDebugUnitTest`.
- Device: `app/src/androidTest` (background display, certificate, scroll
  geometry), one command in `tools/scripts/android-device-checks.ps1`.
- Evals and shared fixtures live in `spec/` and `services/evals`.

## Minimum version

`minSdk 29` (Android 10). On API 29 the screen is read from the accessibility
tree and a screenshot needs a one-time MediaProjection consent held by a
foreground service; API 30+ uses `AccessibilityService.takeScreenshot()` and no
extra grant.
