# Refactor: one turn for every client

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
- Generated wire types keep the same Kotlin API when item 4 lands.
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

Green at `142ada2`: `:engine:test` (108), `:engine:check`, `:turn:test` (20),
`:turn:check`, `:contracts:check`, `:stores:test`, sidecar `test`, all python
checks.

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

## Next: slice 8, `RunCoordinator` in app

- `ChatViewModel` keeps the screen state; `TurnRunner` owns plan-first, recipe
  replay, verify and the answer.
- `ParallelRunner` and `RemoteTaskRunner` become adapters that call the same
  runner with their own device.
- One `RunState` flow instead of `busy`/`cancelRequested`/`pendingGoals`
  scattered on the container.

Gates: `:app:assembleDebug`, `:app:testDebugUnitTest`, sidecar test, and a live
turn on the phone (hidden screen, recipe replay with an answer, accessibility
banner truthful).

## After that, in this order

1. Generate wire types from `contracts/schemas`; drop the hand-written
   `RunRecordSerializer` if the generated one keeps the same API.
2. Event-driven screen access: a `StateFlow` from the accessibility service
   instead of polling in `AccessibilityState` and the Act pill.
3. Hidden screen as a service (`VirtualDisplay` / MediaProjection) instead of
   `app_process` plus the dex staged in `/data/local/tmp`.
4. Versions: Kotlin/AGP/Gradle and JUnit 5, as their own small PR.

## Product constraints that survive the refactor

- No factory, no verification farm, no signed recipes, no 20-task release gate.
- The app's knowledge is built by hand (`apps/*/APP.md` plus playbooks).
- A playbook that cannot match the screen stops and asks; it never guesses.
- Taps are execution; the decision layer is consulted at gates, not per tap.
- Simple English in UI and docs; tappable things look tappable.
