# packages/engine — the client engine

The loop, the gate and the records that the Android app and the Windows sidecar
both run. No Android, no network, no UI: a plain JVM module with tests that need
neither a device nor a provider.

```
loop/         AgentLoop, TurnContext, TurnResult, LoopConfig, RunNarrator
gate/         ActionExecutor, ApprovalPolicy, ActionClassifier
prompt/       SystemPromptBuilder, AppCatalogBlock, InstalledAppsBlock, ScreenRenderer, ContextBudget
observation/  ObservationProducer (refs, redaction, caps, hash), ScreenSourceDevice, ScreenPathOrder
records/      RunChain, RunRecordFactory, RunRecorder (+ in-memory)
router/       AppRouter, CatalogRoutes, RouterHint
parser/       DecisionParser, BalancedObjectScanner, DecisionRules
```

The types these use live in `packages/contracts`: `Action`, `Decision`,
`Observation`, `RunRecord`, the ports, `ModelPort` and the JSON helpers.

## Rules

- **No Android imports.** `checkNoAndroidImports` (Gradle) and
  `tools/checks/check-android-structure.py` both fail the build if one appears.
- **No network.** The model and bridge clients live in `packages/transport`; this
  module only consumes `ModelPort`.
- **Generated data lives elsewhere.** The catalog, the policy data and the prompt
  rules are in `packages/knowledge`.
- **One class per file**, named after it; contracts may share a file.

## Where the decision comes from

`AgentLoop` asks a `StepPolicy` for the next step, and parses the reply. That
interface is the seam; `ModelStepPolicy` is the one implementation today and it
wraps a `ModelPort`:

| implementation | module | when |
|---|---|---|
| `ModelStepPolicy` over `OpenAiClient` | `packages/transport` | a provider over HTTP today |
| `ModelStepPolicy` over `StubModelClient` | `packages/transport` | no key, demo answers |
| `ModelStepPolicy` over a local runtime | its own module | on-device inference, once it exists |
| playbook policy | planned, implements `StepPolicy` | no model at all: anchors instead of reasoning |

Nothing in this module knows which of them is in use. A policy that never calls
out is the point of the interface: it can answer from a recipe, or stop and ask
when the screen does not match.

The loop reads the gate settings one step at a time (`ActionExecutor` takes a
supplier), so a `ApprovalPolicy` is an immutable snapshot: trust and autonomy
are fixed when the run starts and two runs in flight never share a mutable list.

## What a client must supply

`TurnContext` in, `TurnResult` out:

```
goal, history, memory, skills, installedApps, appFunctions, platform, vision, phoneAccess
  -> records, answer, options, narration, usage, error
```

The client also supplies the ports: a `DevicePort` (accessibility, hidden
display, bridge), an `ApprovalGateway` (the sheet), a `RunRecorder` (Room or
memory), a `Clock`, and a `ModelPort`.

## Tests

```
./gradlew :engine:test
```

`com.hopa.engine.conformance` reads `contracts/` from the repository, so it
checks the same JSON the other clients use.
