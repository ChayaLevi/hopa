# Restructure Android app package structure

Deep critique and proposed new structure for
`apps/android/app/src/main/kotlin/com/hopa/app`.

---

## Architecture — the foundational problem

### `AppContainer` is a service locator, not a DI container

`AppContainer` is a 413-line singleton passed into every ViewModel constructor.
Because every screen receives the entire container, every screen can reach every
system. Nothing is explicit: you cannot tell what a feature actually needs without
reading all its code. This is the service locator anti-pattern.

Modern Android uses Hilt. Each ViewModel declares only what it needs:

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversations: ConversationStore,
    private val runs: RunCoordinator,
    private val gate: ApprovalGateway,
) : ViewModel()
```

The AppContainer pattern was acceptable in 2019. It is technical debt now.
Everything else in this document flows from this root cause — because features
get the whole container, there is no pressure to keep responsibilities focused.

---

## God-class violations

### `SettingsViewModel` — 731 lines, eight concerns

One ViewModel currently owns:
1. Provider/model selection and testing
2. Bridge device pairing and health checks
3. Backend gateway pairing
4. Screen mode selection (auto / background / bridge / onscreen)
5. Accessibility permission state
6. Notification testing
7. Wireless ADB pairing
8. Self-check diagnostics

Each of these is a screen in its own right. Each deserves its own ViewModel.
Modern practice: one ViewModel per screen, scoped to its NavBackStackEntry.

Proposed split inside `feature/settings/`:

```
settings/
  model/        ModelSettingsViewModel   provider, endpoint, key, test
  surface/      SurfaceSettingsViewModel screen mode, background helper
  pairing/      PairingViewModel         backend + bridge pairing flows
  diagnostics/  DiagnosticsViewModel     self-check, accessibility health
  character/    CharacterViewModel       tone, chattiness, vision
  notify/       NotifyViewModel          notification permission + test
  SettingsViewModel  ← thin coordinator for top-level settings only
```

### `ChatsSheet` — 436 lines, UI composable doing ViewModel work

A single `@Composable` currently handles: searching conversations, grouping by
date section (Today / Yesterday / 7 days / Older), swipe-to-dismiss, sync to
bridge, rendering cards. Search and grouping logic is embedded in the composable
and cannot be tested.

Move search, grouping, and sync coordination into `ConversationsViewModel`. The
composable receives lists and fires events; nothing else.

---

## Package-level problems

### `gateway/` is a 20-file bin

Five distinct concerns are flat in one directory:

| Concern | Files |
|---|---|
| Session / link lifecycle | `BackendSession`, `BackendIdentity`, `BackendLaunchName`, `BackendTaskState`, `LinkRefresh`, `LinkWindow` |
| Remote run execution | `RemoteTaskRunner`, `RemoteRunPlanner`, `RemoteRunPlan`, `RemoteRunRequest`, `RemoteScreenState`, `ScreenAccess`, `RunBudget` |
| Push / wake | `FirebaseToken`, `HopaMessagingService`, `PushTokenFetch`, `WakeRequest`, `WakeRequests` |
| Catalog | `KnowledgeUpdater` |
| Task dedup | `ProcessedTaskStore` |

Rename `gateway/` → `backend/` and give it subpackages:

```
backend/
  session/   connection lifecycle
  remote/    remote run execution
  push/      FCM + wake
  sync/      CatalogUpdater + ProcessedTaskStore
```

### `gate/` and `gateway/` are one character apart

`gate/` = approval dialog. `gateway/` = backend server. Someone scanning the
package list will misread one as the other. Rename `gate/` → `approval/`.

### `knowledge/` is stale terminology

The core module was renamed to `catalog`. The Android counterpart was not updated.

| Current | Proposed |
|---|---|
| `knowledge/` package | `catalog/` |
| `AssetKnowledgeSource` | `AssetCatalogSource` |
| `gateway/KnowledgeUpdater` | `backend/sync/CatalogUpdater` |

### `feature/sync/` is not a feature

`ChatSync`, `ChatSyncJson`, `ChatSyncReport` are background coordination code —
no UI, no Route, no Screen, no ViewModel. They belong in `backend/sync/`
alongside `CatalogUpdater`, not inside `feature/`.

### `LaunchPairing.kt` at root level

`LaunchPairing` handles one specific deep-link intent. The root package is for
Android entry points (`Application`, `Activity`, top-level composable).
`LaunchPairing` belongs in `navigation/`.

### `di/RemoteRuns` is misplaced

`RemoteRuns` is a thin adapter bridging a `RemoteRunRequest` from the gateway to
`RunCoordinator`. It is not a DI wiring class. It belongs in `run/`.

### `tasks/` should fold into `run/`

`ParallelRunner` and `TaskItem` manage multiple background goals running on hidden
displays — a variant of run coordination, not a separate domain. Folding them into
`run/` makes the relationship explicit.

---

## Feature-level problems

### Easy and Power modes are structured asymmetrically

`feature/power/` exists for the Power-mode home. Easy-mode screens have no
equivalent package — `EasyHistoryRoute`/`EasyHistoryScreen` live inside
`feature/conversations/`, and `HopaBottomBarEasy` lives inside `ui/component/`.

If Power earns its own feature package, Easy does too. Create `feature/easy/`
and move all Easy-specific screens there.

### `MemoryViewModel` manages both facts and skills

`SkillsRoute` borrows `MemoryViewModel` because facts and skills share
`MemoryUiState`. This is accidental coupling — adding a skill triggers a reload
of both facts and skills. A dedicated `SkillsViewModel` operating only on
`container.skills` removes the coupling and makes `feature/skills/` self-contained.

### `feature/settings/` is 40 files flat

Once `SettingsViewModel` is split, sub-ViewModels and their screens should live
in the matching subpackage. The settings directory should not be a flat list of
40 files.

---

## Proposed structure

```
com.hopa.app/

HopaApp.kt
HopaApplication.kt
MainActivity.kt

navigation/
  HopaDestination.kt
  HopaNavHost.kt
  LaunchPairing.kt        ← was root level

di/
  AppContainer.kt
  Surfaces.kt
  SettingsModelPort.kt

service/                  ← unchanged

ui/                       ← unchanged
  component/
  theme/

privacy/                  ← unchanged

share/                    ← unchanged

approval/                 ← was gate/
  UiApprovalGateway.kt

catalog/                  ← was knowledge/
  AssetCatalogSource.kt   ← was AssetKnowledgeSource.kt

run/                      ← expanded
  RunCoordinator.kt
  RunGate.kt
  RunState.kt
  RemoteRuns.kt           ← was di/RemoteRuns.kt
  ParallelRunner.kt       ← was tasks/ParallelRunner.kt
  TaskItem.kt             ← was tasks/TaskItem.kt

backend/                  ← was gateway/, now with subpackages
  session/
    BackendSession.kt
    BackendIdentity.kt
    BackendLaunchName.kt
    BackendTaskState.kt
    LinkRefresh.kt
    LinkWindow.kt
  remote/
    RemoteTaskRunner.kt
    RemoteRunPlanner.kt
    RemoteRunPlan.kt
    RemoteRunRequest.kt
    RemoteScreenState.kt
    ScreenAccess.kt
    RunBudget.kt
    WakeRequest.kt
    WakeRequests.kt
    ProcessedTaskStore.kt
  push/
    FirebaseToken.kt
    HopaMessagingService.kt
    PushTokenFetch.kt
  sync/
    ChatSync.kt             ← was feature/sync/ChatSync.kt
    ChatSyncJson.kt         ← was feature/sync/ChatSyncJson.kt
    ChatSyncReport.kt       ← was feature/sync/ChatSyncReport.kt
    CatalogUpdater.kt       ← was KnowledgeUpdater.kt

feature/
  browser/
  capabilities/
  chat/
  conversations/            ← ChatsSheet, ConversationsViewModel only
  easy/                     ← new
    EasyHistoryRoute.kt     ← was conversations/EasyHistoryRoute.kt
    EasyHistoryScreen.kt    ← was conversations/EasyHistoryScreen.kt
  execution/
  history/
  launchpad/
  memory/                   ← facts only
  power/
  profiles/
  runs/
  settings/
    SettingsRoute.kt        ← top-level router only
    SettingsScreen.kt
    model/                  ← ModelSettingsViewModel + screen
    surface/                ← SurfaceSettingsViewModel + screen
    pairing/                ← PairingViewModel + backend + bridge screens
    diagnostics/            ← DiagnosticsViewModel + self-check screen
    character/              ← CharacterViewModel + screen
    notify/                 ← NotifyViewModel + screen
    shared/                 ← SettingsDivider, SettingsRow, shared UI
  shell/
  skills/                   ← SkillsViewModel independent from memory
  usage/
```

---

## Priority order

**Do first — architecture:**
1. Split `SettingsViewModel` into per-screen ViewModels
2. Add subpackages to `backend/` (was `gateway/`)
3. Move `feature/sync/` → `backend/sync/`

**Do next — naming and placement:**
4. `gate/` → `approval/`
5. `knowledge/` → `catalog/`, rename `AssetKnowledgeSource` and `KnowledgeUpdater`
6. `LaunchPairing.kt` → `navigation/`
7. `di/RemoteRuns` → `run/`
8. Fold `tasks/` into `run/`

**Do after — feature structure:**
9. Create `feature/easy/` for Easy-mode screens
10. `SkillsViewModel` independent from `MemoryViewModel`
11. Organise `feature/settings/` into subpackages
12. Move search/grouping/sync out of `ChatsSheet` into `ConversationsViewModel`
