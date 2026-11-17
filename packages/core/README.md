# packages/core — the client engine

The loop, the gate and the records that the Android app and the Windows sidecar
both run. No Android, no network, no UI: a plain JVM module with tests that need
neither a device nor a provider.

```
loop/         AgentLoop, TurnContext, TurnResult, LoopConfig
gate/         ActionExecutor, ApprovalPolicy, ActionClassifier, ApprovalGateway
prompt/       SystemPromptBuilder, AppCatalogBlock, InstalledAppsBlock, ScreenRenderer, ContextBudget
observation/  ObservationProducer (refs, redaction, caps, hash), ScreenSourceDevice, ScreenPathOrder
records/      RunChain, RunRecordFactory, RunRecorder (+ in-memory)
router/       AppRouter, CatalogRoutes, RouterHint
parser/       DecisionParser, BalancedObjectScanner
contracts/    Action, Decision, Observation, RunRecord, errors
ports/        DevicePort, SystemPort, BrowserPort, SandboxPort, Clock, PackageCatalog
model/        ModelPort, ModelRequest, ModelReply (no HTTP — see packages/remote)
```

## Rules

- **No Android imports.** `checkNoAndroidImports` (Gradle) and
  `tools/checks/check-android-structure.py` both fail the build if one appears.
- **No network.** The model and bridge clients live in `packages/remote`; this
  module only defines `ModelPort`.
- **Generated data lives elsewhere.** The catalog, the policy data and the prompt
  rules are in `packages/know`.
- **One public declaration per file**, named after the file.

## What a client must supply

`TurnContext` in, `TurnResult` out:

```
goal, history, memory, skills, installedApps, appFunctions, platform, vision, phoneAccess
  -> records, answer, options, narration, usage, error
```

The client also supplies the ports: a `DevicePort` (accessibility, hidden
display, bridge), an `ApprovalGateway` (the sheet), a `RunRecorder` (Room or
memory), a `Clock`, and a `ModelPort` (from `packages/remote`, or a stub).

## Tests

```
./gradlew :core:test
```

Conformance tests read `contracts/` from the repository, so they check the same
JSON the other clients use.
