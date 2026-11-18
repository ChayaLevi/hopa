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
- Wire types stay hand-written, and a drift check keeps them equal to
  `contracts/schemas`; see `docs/project/plan/wire-types.md`.
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
   `tools/checks/check-wire-types.py` keeps them equal to `contracts/schemas`.
   Generation was spiked and dropped; `docs/project/plan/wire-types.md` has the
   evidence.
3. Event-driven screen access: a `StateFlow` from the accessibility service
   instead of polling in `AccessibilityState` and the Act pill.
4. Hidden screen as a service (`VirtualDisplay` / MediaProjection) instead of
   `app_process` plus the dex staged in `/data/local/tmp`.
5. Versions: Kotlin/AGP/Gradle and JUnit 5, as their own small PR.

## Product constraints that survive the refactor

- No factory, no verification farm, no signed recipes, no 20-task release gate.
- The app's knowledge is built by hand (`apps/*/APP.md` plus playbooks).
- A playbook that cannot match the screen stops and asks; it never guesses.
- Taps are execution; the decision layer is consulted at gates, not per tap.
- Simple English in UI and docs; tappable things look tappable.
