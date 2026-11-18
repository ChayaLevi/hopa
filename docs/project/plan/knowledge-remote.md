# Knowledge from the backend

Status: **K1 done** (`ad2396a`), **K2 format done**. The engine reads a `Knowledge`
value; the bundled snapshot is the mock source; the wire format
(`KnowledgeBundle` + `KnowledgeCodec`) round-trips and rejects an unknown schema
or an empty catalog. Nothing is downloaded yet.

## K2, format done

`KnowledgeBundle` (schemaVersion, version, catalog, sites, promptRules) and
`KnowledgeCodec` live in `packages/knowledge`; the generated catalog and prompt
types are `@Serializable`. Approval is not in the bundle: a download may change
which app opens, never how careful the gate is.

Still to do for K2: the Python script that writes the same JSON from the three
`contracts/` sources, and the committed `contracts/knowledge/knowledge.v1.json`
that the backend will serve. The Kotlin generators still emit the bundled values;
they stop only when the asset is the only copy (K5).

## K1, done

`packages/knowledge` now has the hand-written `Knowledge`, `KnowledgeSource` and
`BundledKnowledge`. `CatalogRoutes.targets`, `AgentLoop` and `TurnRunner` take
the value; `AppContainer.knowledge` comes from `knowledgeSource`, which is
`BundledKnowledge` today. A test supplies a one-app `Knowledge` and proves the
whole catalog is replaced, so the source drives the engine rather than decorating
constants. Behaviour is unchanged.

Deliberate: the approval table stays in the bundled snapshot. A downloaded
catalog may change which app opens; it must not make the gate less careful.

Still to wire when a real source exists: the launchpad and settings screens call
`CatalogRoutes` with the bundled default, so they would keep showing the old
catalog until they take `container.knowledge` too.

## Why

Every catalog or policy change today needs a new app build: `packages/contracts`
generates `packages/knowledge` as Kotlin constants, and `engine` reads them as
globals. The backend can serve the same data with a version, so an installed app
can pick up a new catalog without a store release.

The app stays offline-first: the bundled snapshot is the floor, and an update is
an improvement, never a requirement.

## Current state

- `contracts/catalog/catalog.v1.json`, `contracts/policy/approval.v1.json` and
  `contracts/prompts/rules.v1.json` are already the sources.
- `tools/scripts/build-*.py` render them into `packages/knowledge`:
  `APP_CATALOG`, `SITE_CATALOG`, `ApprovalPolicyData`, `PromptRules`.
- `engine` imports the catalog in `CatalogRoutes` and the approval table in
  `ActionClassifier` and `ApprovalPolicy`.
- `PromptRules` is generated but imported by nobody.

## Target

One knowledge bundle, one loader, one place the engine reads it from.

```
bundled JSON ─┐
              ├─> KnowledgeStore ─> Knowledge(value) ─> TurnRunner ─> engine
downloaded ───┘
```

```kotlin
data class Knowledge(
    val version: String,
    val schemaVersion: Int,
    val catalog: List<CatalogApp>,
    val sites: List<CatalogSite>,
    val approval: ApprovalTable,
    val promptRules: List<PromptRule>,
)
```

`CatalogRoutes(catalog)`, `ActionClassifier(table)` and
`SystemPromptBuilder(rules)` take the value. No module-level constants; a test
passes a five-entry fixture instead of the 56 KB catalog.

### Bundle

```json
{
  "schemaVersion": 1,
  "version": "2026-11-18.1",
  "minAppVersion": "0.1.0",
  "catalog": { "...": "..." },
  "sites": { "...": "..." },
  "approval": { "...": "..." },
  "promptRules": [ "...", "..." ]
}
```

The backend serves this one document. `GET /v1/knowledge` with `If-None-Match`;
`304` means the app keeps its copy. The response carries the bundle's SHA-256, a
`version` header and a size cap (the current catalog is ~56 KB).

### Loader and storage

1. Start from the bundled asset. It always loads, even offline.
2. If the backend is configured and reachable, ask in the background; never
   block the first screen or a turn.
3. Accept only when `schemaVersion` is known, `minAppVersion <= current`, the
   size is under the cap, the digest matches and the JSON parses.
4. Write to a temp file, then rename; keep the previous version for rollback.
   Any failure keeps the old copy and says nothing to the person.
5. A new bundle applies from the next turn, not mid-turn.

Receipts and bench results record `knowledgeVersion`, so two runs can be compared
honestly.

## Safety floor

The bundle is untrusted input. It can route differently, but it can never make
the app less careful:

- Payments, `system.delete`, `system.reset` and uninstall always ask, whatever
  the bundle says.
- Remote approval data may add requirements, never remove the local floor. The
  app combines them with "most restrictive wins".
- Only data, never executable rules.
- TLS and the device token are the transport; the digest catches corruption. A
  signature can be added later without changing the loader's shape.

## Steps

| step | what | gates |
|---|---|---|
| **K1** | `Knowledge` value and injection into `CatalogRoutes`, `AgentLoop` and `TurnRunner`; bundled values assembled once; **done** (`ad2396a`) | `:engine:test`, `:turn:test`, `:app:assembleDebug`, sidecar test, one live turn; behaviour unchanged |
| **K2** | `KnowledgeBundle` (kotlinx.serialization) + `KnowledgeCodec` + round-trip tests; **format done**. The Python bundle writer and the committed JSON come with the backend | `:knowledge:test`, `:contracts:check`, generator `--check` |
| **K3** | `KnowledgeStore` port, file implementation, bundled fallback, atomic swap, previous-version rollback | temp-dir tests for accept/reject/rollback; `:app:assembleDebug` |
| **K4** | `GET /v1/knowledge` in the backend; startup check in the app; `knowledgeVersion` in the receipt and bench results | backend tests (ETag, 304, digest, cap), contract test, live update and rollback on the phone |
| **K5** | Drop the compiled catalog from the APK, keep a minimal bootstrap | offline start, APK size, full CI |

K1 and K2 come before generated wire types (C in the modernisation list): they
decide that knowledge is runtime JSON, and code generation should not harden the
old compile-time format first.

## Open decisions

- Signature now or later. The loader already has a digest check, so a signature
  is an additive step.
- One bundle for all platforms or a platform-scoped one. The catalog already
  names per-platform targets, so one bundle is enough for now.
- Whether the update rides the gateway socket or plain HTTPS. HTTPS with the
  device token keeps it usable before a gateway session exists.
- How often to ask: app start, plus a manual "Check knowledge" in Act.

## Non-goals

- No network call on the first screen; no turn waits for an update.
- No new prompt text outside `promptRules`; no code or expressions in the bundle.
- No silent removal of an approval step.
