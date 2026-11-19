# Refactor: one turn for every client

> Archived: this records a past state. The module names have changed since
> (`core/`, `catalog`, `runlog`, `inference`, `bridge`, `backend`); see
> [restructure-modules.md](restructure-modules.md).

Branch: `refactor/turn`. Base: `main` = `039f5ad` (mirror of `origin/main`,
PR #53 squashed). Do not merge this branch until the gates below are green and
the phone has run a turn.

## What this refactor is for

One goal used to become steps in three places: `ChatViewModel` (Android), the
Windows sidecar `Engine.kt`, and `RemoteTaskRunner` (a delivered task). Every
behaviour change had to be ported by hand. `packages/turn` is being built as the
single path, with clients supplying ports.

Rules that must hold:

- `apps/windows` changes stay at zero. The guard is
  `./gradlew -p ../windows/sidecar test` after every slice; `DevicePort` is not
  removed until a later decision.
- Wire types stay hand-written, and a drift check keeps them equal to
  `spec/schemas`; see `docs/project/plan/wire-types.md`.
- No feature work rides along. One slice per commit, gates before the next.

## Done

| slice | commit | what |
|---|---|---|
| 1 | `69289d3` | `packages/turn` with `Recipe`, `RecipeStore`, `Replayer`, `ReplayOutcome`, `ReplayAnswer`. `Replayer` takes `executorFor(device)` and `installedApps()`, not `AppContainer`. |
| 2 | `8b05da1` | the module is in `check-tree`, the Android CI job runs `:turn:test`, `packages/README` and ADR-0007 describe it |
| 3 | `68c0efb` | `ActionKindRules`: one table for what a kind needs; `DecisionRules`, `ActionExecutor` and `AgentLoop` read it |
| 4 | `760154c` | `:turn:ktlintCheck` |
| 5 | `746e06d` | `hopa.no-android` convention plugin; every `hopa.kotlin-jvm` module runs `checkNoAndroidImports` |
| 6 | `c5f774b` | ktlint on `contracts` and `stores` (`knowledge` stays out: generated) |
| 7 | `142ada2` | `TurnRunner` + `TurnRequest`/`TurnOutcome`, `GoalVerifier`, `ModelPlanSource`; the flat parser shape now asks `DecisionRules` too |
| 8 | `c98f746` | `RunCoordinator`; chat, parallel and delivery run the same turn; `AppContainer.loop`, `loopFor()` and the duplicate executor removed |

Green at `c98f746`: `:app:assembleDebug`, `:app:testDebugUnitTest`,
`:engine:test` (108), `:engine:check`, `:turn:test` (20), `:turn:check`,
`:contracts:check`, `:stores:test`, sidecar `test`, all python checks.

Live gate, 2026-11-18, Galaxy S926B, build `0.1.0-261118-1347`, path
"This phone / Your screen", accessibility on:

- first ask showed the plan sheet ("Here is the plan", 4 steps, "Nothing has
  been tapped yet."); after **Do it** the turn launched Settings and answered
  from the real screen - receipt `1 step, 1 confirmed, 9.7k tokens`;
- the same ask again ran the stored recipe: same answer from the final screen,
  **2.3k tokens**, no loop;
- the receipt said "1 steps"; fixed after the build in `73d6a93`.

## Done: slice 7, `TurnRunner` (no Android)

`packages/turn/src/main/kotlin/com/hopa/turn/run/`:

- `TurnRequest` carries what the caller decided to pass: goal, device, ids,
  history, memory, skills, installed apps, platform, vision, typed functions,
  `phoneAccess`, checkpoints, `planFirst`/`planApproved`, `replay`, one
  `timeoutMillis`, and what the surfaces answered (`surfaceName`,
  `surfaceAvailable`, `surfaceError`).
- `TurnOutcome` carries answer, records, narration, options, error, usage,
  pending plan, checkpoint verification, final screen, replay steps, whether a
  recipe was saved, and the source (`RECIPE`, `MODEL`, `PLAN`, `UNAVAILABLE`).
- `TurnRunner` is the order the app has always used, written once: plan gate,
  then an unavailable surface, then a recipe, then `AgentLoop` under one
  deadline, then the final screen against the checkpoints. Recipe saving is a
  decision of the runner: clean, something verified, checkpoints met, and every
  ref step anchored.
- `goal/GoalVerifier` verifies visible labels or full `NodeAnchor` keys, exactly
  (not substrings); a hidden label does not count.
- `plan/ModelPlanSource` holds the planning prompt; `PlanSource` is the port a
  playbook can implement later.

Tests: `TurnRunnerTest` (9) with fake device/policy/clock and a temp
`RecipeStore`; `GoalVerifierTest` (5). No Android, no provider.

Found on the way: `DecisionParser`'s flat `{"kind": ...}` shape bypassed
`DecisionRules`, so a targetless tap reached the executor and failed there. It
now validates against the same table; `AgentLoopTest`'s payment fixture gained
the target the table rightly demands.

## Done: slice 8, `RunCoordinator` in app

`apps/android/app/src/main/kotlin/com/hopa/app/run/RunCoordinator.kt` builds the
`TurnRequest` from the person's settings and the resolved surface, and owns the
one `TurnRunner` instance. The three clients are adapters now:

- `ChatViewModel` keeps the screen state: a plan becomes `pendingPlan`, a
  `RECIPE` outcome becomes a receipt with the replay's steps, a `MODEL` outcome
  becomes the bubble and the run history. Its `replayIfPossible`,
  `fetchPlanSteps`, `buildMemoryContext` and `typedFunctions` are gone.
- `ParallelRunner` still opens its hidden display and supplies that display's
  typed functions, then runs the same turn; its local replay and recipe save are
  gone.
- `RemoteRuns` is the coordinator plus `TurnOutcome.asTurnResult()` for the
  socket. A delivered task now also takes the recipe path.

The plan gate builds a request with no surface (`planRequest`), so asking for a
plan never opens the hidden display. Dead code removed with the old pipeline:
`AppContainer.loop`, `loopFor()` and the duplicate `executor`.

One deliberate fix: the turn's goal is the body - the attachment description
when there is no text - so an attachment-only message no longer starts a turn
with an empty goal.

The live gate above has run; the branch is ready to merge when wanted.

## Next: the remaining modernisation, in this order

1. **Done (D1, `fab7be1`)**: one `RunGate`/`RunState` instead of `busy` and
   `cancelRequested`, claimed atomically by chat or by a delivery.
   `pendingGoals` stays a channel: that is a queue, not state.
2. **Done (C1/C2)**: the wire types stay hand-written, and
   `tools/checks/check-wire-types.py` keeps them equal to `spec/schemas`.
   Generation was spiked and dropped; `docs/project/plan/wire-types.md` has the
   evidence.
3. **Done (D2)**: screen access is an event. `AccessibilityMonitor` holds one
   `StateFlow<AccessibilityBinding>`; the service pushes bind and unbind, a
   resume reads Android once, and a disconnect is held for three seconds so a
   rebind does not blink the banner. The chat banner, the settings pill, the Act
   page and the Capabilities tab collect it; nothing polls.
4. **Partly done (E)**: the dex is no longer staged - the helper starts from the
   installed APK (`ADR-0023`) - and `HelperSupervisor` owns its life instead of a
   fire-and-forget start. The helper stays a shell process: a normal app cannot
   host another app's UI off-screen, and `MediaProjection` only captures.
5. **Done (F)**: Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 with KSP 2.2.21-2.0.5
   and Room 2.7.2 (KSP2 needs it), and JUnit 5.14.4 for every test source.
   AGP 9 and Kotlin 2.4 wait for a DSL migration and for KSP to support 2.4.
6. **Done**: `packages/engine` became five modules by role - `routing`,
   `observation`, `records`, `prompt`, `execution` - with packages named after
   them and the shared fakes in test fixtures. See
   [engine-split.md](engine-split.md). The Android build, the Windows sidecar and
   the backend name the five instead of one engine.

## Open, for the next session with the phone

- **E2 smoke**: start the hidden screen from the app, `kill` the helper, then run
  a turn. It must name the failure and start a new helper instead of driving a
  socket that only looks connected.
- **D2 smoke**: turn screen access off without touching the app and watch the
  banner appear, then turn it on and watch it go; a fast off-on cycle must show
  nothing, because the grace is three seconds.
- **After the toolchain bump**: build, install and run once. AGP, Kotlin, Room
  and JUnit all changed under the app; the unit tests say it compiles, a phone
  says it works.
- **E2 restart**: the supervisor's health path has run on the phone, but
  `ensure()` starting a dead helper needs wireless debugging paired with the app
  again. The pairing was attempted and failed on two Samsung specifics, both in
  `WirelessSetup`: the automatic path scrolls the list 18 times and never presses
  "Wireless debugging" (the text sits on a child, the pressable node is the
  parent row), and the wait path did not see an open pairing dialog (it reads the
  Settings window under it, not the topmost one). Typing the code by hand cannot
  work: the port stops listening as soon as the dialog loses focus. Fix those two
  before the restart path can be exercised.
- **The local gate should compile androidTest**, as CI does:
  `:capabilities:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin`.
  A renamed class broke an instrumented test while every local task was green.
- **`fb352f1`** is a broken intermediate commit (staged renames committed with a
  settings-path fix). The head is correct; rewriting it would force-push over a
  published branch, so it is recorded here instead.
- The backend implements `GET /v1/knowledge` but it is not deployed, so a phone
  still updates from the bootstrap asset.
- The accessibility service moved to
  `com.hopa.capabilities.accessibility.state.HopaAccessibilityService`; after the
  next install it has to be enabled again with that component name.

## Product constraints that survive the refactor

- No factory, no verification farm, no signed recipes, no 20-task release gate.
- The app's knowledge is built by hand (`apps/*/APP.md` plus playbooks).
- A playbook that cannot match the screen stops and asks; it never guesses.
- Taps are execution; the decision layer is consulted at gates, not per tap.
- Simple English in UI and docs; tappable things look tappable.
