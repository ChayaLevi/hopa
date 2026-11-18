# packages/contracts — the shared types

Everything two modules must agree on: the wire shapes, the ports a client plugs
in, the model request and reply, and the JSON helpers. It depends on nothing of
ours, so every other module can depend on it without a cycle.

```
Action.kt        Action (+ ActionKind)
Decision.kt      Decision
Observation.kt   Observation, ObservationNode, ScreenSize
ObservationHasher.kt
RunRecord.kt     RunRecord (+ the serializer)
HopaError.kt     HopaError, ErrorCodes
RiskLevel.kt
ApprovalRequest.kt, ApprovalGateway.kt, PaymentContext.kt, ExecutionResult.kt
ports/           DevicePort, SystemPort, BrowserPort, SandboxPort, Clock, PackageCatalog, InstalledApp
model/           ModelPort, ModelRequest, ModelReply, ModelMessage, ModelUsage
json/            HopaJson, CanonicalJson
```

## Rules

- **No logic.** A type, a port or a pure helper; the loop and the gate live in
  `packages/engine`.
- **No dependencies of ours.** If a change here needs `engine`, the type is in
  the wrong module.
- **Wire names are the contract.** `docs/reference/contracts/` describes the same
  JSON that these classes serialize; the conformance tests check both sides.
- **Contracts may share a file** (interfaces, enums, sealed and data classes);
  anything with behaviour keeps its own file, named after it.
