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
├─ core/ui/        shared Compose chrome: theme, bars, sheets, page scaffolds, the mascot
├─ core/privacy/   the turn log: what left the phone, kept on the phone
├─ core/share/     inbound shares turned into an attachment
├─ core/approval/  the prompt gateway that waits for a person
├─ feature/usage/  the usage screen, as the first feature module
├─ feature/memory/ the facts page
├─ feature/skills/ the procedures page
├─ feature/profiles/ the everyday / easy / power choice
├─ feature/history/ activity, run history and the saved repeats
├─ feature/runs/    the audit trail of every action
├─ feature/browser/ the in-app browser
├─ feature/easy/    the Easy profile history
├─ feature/power/   the Power profile home
├─ feature/launchpad/ apps, sites and the app card
├─ feature/capabilities/ what HOPA can use and how it acts
├─ feature/conversations/ the chats sheet
├─ feature/settings/ the settings hub, model, alerts and diagnostics
├─ feature/execution/ Act, run setup, preferences and the computer link
├─ feature/chat/    the conversation, composer and plan sheet
└─ data/           Room and DataStore: sessions, settings, secrets, history

core/
|-- contracts/     wire types and port interfaces, JSON helpers
|-- catalog/       app catalogue, approval data, router
|-- observation/   the screen model and the source that feeds it
|-- runlog/        a run as records and their chain
|-- runtime/       the run gate: who owns the phone right now
|-- inference/     model adapters
|-- bridge/        the PC companion link
|-- backend/       the backend link: pairing, tasks, catalog fetch
`-- agent/         the turn, the loop, the approval check, recipes
```

The eight core modules and the ports have no Android imports, so they are
unit-testable on the JVM and reusable by the Windows sidecar. `capabilities` and
`data` keep Android and Room behind their edges.

## App packages

| package | what lives there |
|---|---|
| `navigation/` | destinations, the nav host, launch deep links |
| `di/` | containers and ViewModel factories |
| `runtime/` | run coordinator, gate, state, parallel runners, remote adapter |
| `link/session/` | the backend socket and its pairing lifecycle |
| `link/remote/` | remote run planning and execution, task dedup |
| `link/push/` | FCM token, messaging service, wake |
| `link/sync/` | catalog updates and chat sync |
| `feature/` | one package per feature: screens, routes, ViewModels |
| `service/` | Android foreground services |
| `ui/` | shared chrome, theme, and the approval dialog gateway |

## Tabs

The three bottom-bar tabs are navigation, not ownership: a feature module may be
reached from more than one tab.

| tab | opens |
|---|---|
| Chat | `chat`, `conversations`, `profiles` (the mode), `easy` / `power` shells |
| Activity | `history`, `runs`, `usage` |
| Capabilities | `capabilities`, `execution` (Act), `browser`, `memory`, `skills`, `launchpad` |
| overlay | `settings` opens from Capabilities; About lives inside it |

A feature package may use `runtime`, `link`, `catalog`, `ui`, `service` and the
libraries; it never imports another feature, except `feature/shell`, which owns
the navigation shell. No package holds a single file; the bundled catalog source
lives in the `data` module, not in the app.

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

- JVM: `:catalog:test`, `:agent:test`, `:stores:test`, `:app:testDebugUnitTest`.
- Screenshots: `:screenshots` renders Settings, Activity and Runs through
  Robolectric on the JVM, so a layout regression fails without a device. CI runs
  `:screenshots:verifyRoborazziDebug`; after an intended change, re-record with
  `:screenshots:recordRoborazziDebug` and review the PNGs in the diff.
- Device: `app/src/androidTest` (background display, certificate, scroll
  geometry), one command in `tools/scripts/android-device-checks.ps1`.
- Evals and shared fixtures live in `spec/` and `services/evals`.

## State

A route is a thin adapter: it asks Hilt for the screen's view model, collects
one `StateFlow<UiState>` with `collectAsStateWithLifecycle`, and passes plain
callbacks down. It holds no screen data.

- Screen data lives in the module's `@HiltViewModel` state holder as one
  immutable `UiState`; every mutation is a method on that holder.
- `remember` is for genuine UI-local state only: a dialog, an expanded row, a
  text field, the page depth inside Settings.
- The holder that needs Android (the self check, the accessibility switch) lives
  in `app` and takes the container; the rest take their narrow interface.

## Toolchain

The build is on the 2026 line and the versions are not incidental: a newer
AndroidX artifact refuses to build below them.

| Piece | Version | Why |
|---|---|---|
| Gradle | 9.6.0 | the minimum the Android Gradle plugin 9.5 accepts |
| Android Gradle plugin | 9.5.1 | new DSL only; supports API 37 |
| Kotlin | 2.4.21 | KGP that the built-in Kotlin support uses |
| KSP | 2.3.14 | KSP2; speaks to built-in Kotlin (2.2.x does not) |
| Hilt | 2.60.1 | first line that applies on the AGP 9 DSL |
| Compose BOM | 2026.11.01 | Compose 1.13, Material 3 1.5 |
| Navigation | 2.10.2 | type-safe destinations |
| Lifecycle | 2.11.1 | `collectAsStateWithLifecycle`, view model scopes |
| compileSdk | 37 minor 1 | Compose 1.13 declares a 37.1 floor |
| targetSdk | 36 | the level Play asks for in 2026; 37 runtime behaviour is untested here |

Built-in Kotlin means no module applies `org.jetbrains.kotlin.android`; the
`compileOptions` target (17) is the Kotlin JVM target too. KSP and the Compose
compiler stay as plugins because they are compilers, not the Kotlin plugin.

## Minimum version

`minSdk 29` (Android 10). On API 29 the screen is read from the accessibility
tree and a screenshot needs a one-time MediaProjection consent held by a
foreground service; API 30+ uses `AccessibilityService.takeScreenshot()` and no
extra grant.
