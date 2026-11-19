# contracts - the vocabulary

The types every other module passes around, and the few pure helpers that belong
to them. No behaviour that reaches out: no network, no storage, no Android.

## Packages, bottom first

`json` and `wire` reference each other on purpose: `RunRecordSerializer` needs the
shared `Json`, and `ObservationHasher` walks wire types. They are one layer.

### Layer 0 - the vocabulary itself

| package | what lives here | depends on |
|---|---|---|
| `json` | `HopaJson`, `CanonicalJson`, `ObservationHasher`: the one `Json` configuration and the canonical digest | wire (hash only) |
| `wire` | `Action`, `Decision`, `Observation`, `RunRecord`, `RunRecordSerializer`, `ScreenSize`, `ActionKind`: what crosses every boundary | json |

### Layer 1 - what the vocabulary names

| package | what lives here | depends on |
|---|---|---|
| `error` | `HopaError`, `ErrorCodes`: a typed failure both clients can print | - |
| `model` | `ModelPort`, `ModelRequest`, `ModelReply`, `ModelMessage`, `ModelUsage`: the model boundary | - |
| `ports` | `DevicePort`, `BrowserPort`, `SandboxPort`, `SystemPort`, `Clock`, `InstalledApp`, `PackageCatalog`: what a client must supply | wire |

### Layer 2 - the flows the types describe

| package | what lives here | depends on |
|---|---|---|
| `approval` | `ApprovalRequest`, `ApprovalGateway`, `PaymentContext`, `RiskLevel`: the question a payment asks | wire |
| `chat` | `Conversation`, `ChatMessage`, `Attachment`, `AttachmentRules`, `MessageProof`: the client's own chat shapes | wire |
| `turn` | `TurnContext`, `TurnResult`, `ExecutionResult`: a client describes a request without depending on `agent` | model, ports, wire |

## Rules

1. **Types and pure functions only.** A `data class`, an interface, or a helper
   that has no dependency beyond this module.
2. **No Android, no HTTP, no storage.** Enforced by `checkNoAndroidImports`.
3. **One public declaration per file**, named after it. This is the one module
   where contracts may share a file - interfaces, enums, sealed and data classes
   carry no behaviour.
4. **`wire` is the JSON shape.** A field that crosses a socket or a database is
   declared here first; `spec/schemas` is the other half and `check-wire-types`
   keeps them equal.
5. **`turn` is not `agent`.** These are the request and the result; the run itself
   lives in `agent`.

## Tests

```
./gradlew :contracts:test :contracts:check
```

`WireSchemaTest` compares every wire class with `spec/schemas`; nothing here needs
a device.
