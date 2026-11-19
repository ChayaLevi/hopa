# Refactor: the engine becomes five modules

`core/engine` is one Gradle module with five distinct jobs inside it. The
module boundary hides nothing: `routing` does not touch `execution`, `records`
does not touch the screen, and only `execution` needs all four others. Splitting
it makes those rules visible to the compiler instead of to a review.

This is a structural change only. No behaviour moves, no file is rewritten
beyond its `package` line and imports.

## The five modules

| module | holds | depends on |
|---|---|---|
| `core/routing` | `AppRouter`, `CatalogRoutes`, `RouteMatch`, `RouterHint`, `RouteTarget` | `contracts`, `knowledge` |
| `core/observation` | `ObservationProducer`, `NodeAnchor`, `ObservationLimits`, `ScreenPath`, `ScreenPathOrder`, `ScreenSource`, `ScreenSourceDevice` | `contracts`, coroutines |
| `core/records` | `RunChain`, `RunRecorder`, `RunRecordDraft`, `RunRecordFactory`, `InMemoryRunRecorder` | `contracts`, serialization |
| `core/prompt` | `SystemPromptBuilder`, `AppCatalogBlock`, `InstalledAppsBlock`, `ScreenRenderer`, `ContextBudget` | `contracts`, `routing` |
| `core/execution` | `AgentLoop`, `ActionExecutor`, `ApprovalPolicy`, `ActionClassifier`, `ActionKindRules`, `DecisionParser`, `DecisionRules`, `ParseOutcome`, `ValidationResult`, `BalancedObjectScanner`, `ModelStepPolicy`, `StepPolicy`, `LoopConfig`, `TurnContext`, `TurnResult`, `ModelReplyText`, `ScrollGeometry` | all four above, `contracts`, `knowledge`, serialization |

The packages follow their modules, the rule the earlier renames established:
`com.hopa.routing`, `com.hopa.observation`, `com.hopa.records`, `com.hopa.prompt`,
`com.hopa.execution`. Every consumer import changes with them; that is the bulk
of the diff, and it is mechanical.

`core/engine` disappears. Its `api(project(":contracts"))` and
`api(project(":knowledge"))` become per-module declarations: `api` where a type
appears in a public signature, `implementation` otherwise. Cross-module edges
are `api` too, so a consumer of `execution` still sees `NodeAnchor` and
`RunRecorder` without naming the module.

## Tests travel with their subject

- `execution` keeps its twelve execution tests plus `conformance/`
  (`ConformanceTest`, `ConformanceExecutionTest`, `SchemaValidationTest`) and the
  `spec/` resource root: the fixtures validate decisions and records, which is
  execution's job. This module keeps `json-schema-validator` and `slf4j-nop`.
- `observation`, `records`, `prompt`, `routing` each take their own tests.
- The fakes in `engine.testing` substitute `contracts` ports, so they move to
  `core/contracts` as `testFixtures` (`FakeDevice`, `FakeClock`,
  `FakeApproval`). `TestKnowledge` loads `/knowledge.v1.json`, so it moves to
  `core/knowledge` as a test fixture. Modules that need them declare
  `testImplementation(testFixtures(project(...)))`; nothing in `main` sees them.

## Builds

Three Gradle builds include the modules today: `apps/android`,
`apps/windows/sidecar` and `services/backend`. Each one replaces
`include(":engine")` with five includes, and its sidecar/app project gains the
dependencies it actually imports:

- `app`: `execution`, `observation`, `records`, `routing`
- `turn`: all five
- `sidecar`: `execution`, `observation`, `records`, `routing`
- `data`: `records`
- `capabilities`: `observation`

`apps/windows` source stays at zero changes; only its `build.gradle.kts` lists
the new projects. The guard still is `./gradlew -p ../windows/sidecar test`.

## Gates

1. `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`,
   `:capabilities:testDebugUnitTest`, `:capabilities:compileDebugAndroidTestKotlin`,
   `:data:testDebugUnitTest`, `:stores:test`.
2. `:routing:test`, `:observation:test`, `:records:test`, `:prompt:test`,
   `:execution:test`, `:turn:test`, `:contracts:check`, `:knowledge:test`.
3. ktlint on every module that had it (`routing`, `observation`, `records`,
   `prompt`, `execution`), and the no-Android check on each.
4. `./gradlew -p ../windows/sidecar test` and
   `./gradlew -p ../../services/backend test`.
5. The Python checks, with `check-tree.py` and the CI job taught the five names.

One commit per module, in dependency order: `routing`, `observation`, `records`,
`prompt`, `execution`. The last one deletes `core/engine` and updates the
three builds.
