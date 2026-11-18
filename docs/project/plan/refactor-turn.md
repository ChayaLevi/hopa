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

Green at `c5f774b`: `:engine:test` (107), `:engine:check`, `:turn:test`,
`:turn:check`, `:contracts:check`, `:stores:test`, sidecar `test`, all python
checks.

## Next: slice 7, `TurnRunner` (no Android)

In `packages/turn`:

- `run/TurnRequest`: goal, history, memory, skills, settings-ish facts the
  caller decides to pass, `surfaces: suspend () -> ScreenSourceDevice`,
  `gate`, `approval`, `recorder`, `clock`, budget/limits.
- `run/TurnOutcome`: records, answer, checkpoints met, tokens, error, the final
  observation, and whether the answer came from a recipe (cheap) or the model.
- `run/TurnRunner`: recipe first (with `Replayer` + `ReplayAnswer`), otherwise
  `AgentLoop` through the caller's `StepPolicy`; one deadline for the whole turn.
- `goal/GoalVerifier`: given an `Observation` and the goal's anchors, say which
  checkpoints hold. Anchors come from `NodeAnchor` keys, not screen hashes:
  the end hash is evidence, not a gate (window ids change between launches).

Tests use fake ports: a fake `DevicePort` whose tree is a list of screens, a
scripted `StepPolicy`, an in-memory recorder. No Android, no model.

## Then: slice 8, `RunCoordinator` in app

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
