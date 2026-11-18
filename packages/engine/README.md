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
- **No network.** The model and bridge clients live in `packages/net`; this
  module only consumes `ModelPort`.
- **Generated data lives elsewhere.** The catalog, the policy data and the prompt
  rules are in `packages/knowledge`.
- **One class per file**, named after it; contracts may share a file.

## Where the decision comes from

`AgentLoop` asks a `ModelPort` for the next step. That port is the seam:

| implementation | module | when |
|---|---|---|
| `OpenAiClient` | `packages/net` | a provider over HTTP today |
| `StubModelClient` | `packages/net` | no key, demo answers |
| local runtime | its own module | on-device inference, once it exists |
| playbook policy | planned | no model at all: anchors instead of reasoning |

Nothing in this module knows which of them is in use.

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
