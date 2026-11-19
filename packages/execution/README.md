# packages/execution - the loop and the gate

`AgentLoop` decides the next step, `ActionExecutor` performs it, and the gate
decides which steps need a person. No Android, no network, no UI: a plain JVM
module with tests that need neither a device nor a provider.

The rest of what used to be one engine module lives next door: `routing` picks
the app, `observation` renders the screen, `prompt` writes the system prompt and
`records` keeps the run. `packages/README.md` has the dependency order.

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
| `ModelStepPolicy` over `OpenAiClient` | `packages/providers` | a provider over HTTP today |
| `ModelStepPolicy` over `StubModelClient` | `packages/providers` | no key, demo answers |
| `ModelStepPolicy` over a local runtime | its own module | on-device inference, once it exists |
| playbook policy | planned, implements `StepPolicy` | no model at all: anchors instead of reasoning |

Nothing in this module knows which of them is in use. A policy that never calls
out is the point of the interface: it can answer from a recipe, or stop and ask
when the screen does not match.

The loop reads the gate settings one step at a time (`ActionExecutor` takes a
supplier), so an `ApprovalPolicy` is an immutable snapshot: trust and autonomy
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
./gradlew :execution:test
```

`com.hopa.execution.conformance` reads `spec/` from the repository, so it
checks the same JSON the other clients use.
