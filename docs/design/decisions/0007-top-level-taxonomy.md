# ADR-0007 — Top-level taxonomy and what the word "agent" means

- Status: accepted
- Date: 2026-11-12

## Context

The repository has grown to a HarmonyOS app, a Python bridge, a Kotlin brain
service, a Python evaluation rig and a set of contracts. Two future clients
(Android, Windows) and a possible backend make the top level carry more weight
than it does today.

Three problems motivated this record:

1. `harness/` described both "the quality rig" and, in its `agent/` subfolder,
   something that reads like the product's agent. New readers assume it is the
   thing that ships.
2. The word **agent** names the product loop, the evaluation driver, and a
   package name, so a folder cannot be understood from its name.
3. Nothing stated which top-level folders may be imported by which, so a
   `services/` package could quietly acquire a device dependency.

## Decision

- Top level is: `apps/`, `services/`, `evals/`, `spec/`, `tools/`, `docs/`.
  Each answers one question before it is opened:

  | Folder | Answers | Ships? |
  |---|---|---|
  | `apps/` | what a person holds | yes |
  | `services/` | what runs with nobody watching | deployable, not shipped to a device |
  | `evals/` | how we know it got better | never |
  | `spec/` | what both sides may share | data only |
  | `tools/` | how we build and check | never |
  | `docs/` | why it is like this | never |

- `build-logic/` holds the Gradle convention plugins (addendum 2026-11-18).
  It is a composite build, not a module: the Android app, the Windows sidecar
  and the backend each `includeBuild` it, because all three include `core/`
  and the module files cannot belong to one of them. It ships nothing.

- **`agent` is a product word**: `apps/harmony/core/agent`, and later
  `services/backend/runtime`. Evaluation code is a *baseline* or a *driver*.
- `evals/` replaces `harness/`. The evaluation driver lives in
  `services/evals/baseline/`; the tasks, runner and scoring in `services/evals/bench/`.
- `services/bridge` and `services/relay` are separate deployables (see ADR-0009).
- Dependency direction is one-way: `apps → contracts`; `services → contracts`;
  `evals → contracts` (and services as a client); **nothing imports `evals/`**.
- `docs/project/archive/` and the retired rebuild plan (tag `rebuild-plan-2026-11-13`)
  are frozen
  evidence: they may keep old paths. Every other document tracks the tree.
- No `utils/`, `helpers/`, `common/`, `misc/`, `shared/` anywhere.

## Consequences

- The repository map in `README.md` and `CONTRIBUTING.md` must equal the table
  above, and a CI check fails when a new top-level folder appears.
- Android and Windows are `apps/android` and `apps/windows`; the platform-specific
  work is a device adapter in the bridge, not a fork of the app.
- Old documents that name `evals/` are updated once, except frozen history.
- The target structure recorded in `docs/design/architecture/target-structure.md` is
  superseded where it disagrees (top-level names, the harness rename).

## Alternatives considered

- **Keep `harness/`.** Rejected: the name says "test rig", not "quality
  measurement", and the `agent/` subfolder actively misleads.
- **`testbed/` or `rig/`.** Honest but less common; `evals/` is the standard split
  next to `tests/` and matches "bench" inside it.
- **Nest evaluation under `tools/`.** Rejected: it is a first-class deliverable
  with its own packages and reports, not a build script.
- **Keep a counts inventory in this ADR.** Rejected: counts rot; they live in the
  plan document instead.

## Addendum 2026-11-14: `core/` for the client engine

The Kotlin clients (Android now, Windows next) share one client engine, and
`apps/android/core` was the wrong home: the Windows client would have had to
import the Android app. The engine moved out of `apps/`:

| Folder | What it holds | Who may import it |
|---|---|---|
| `core/contracts` | shared data types (`Action`, `Observation`, `RunRecord`, `Decision`), port interfaces (`DevicePort`, `ModelPort`, ...), `ApprovalRequest`, `ApprovalGateway`, `TurnContext`, `TurnResult` | clients |
| `core/codec` | JSON helpers (`HopaJson`, `CanonicalJson`); the leaf every module may use without taking the vocabulary with it | clients |
| `core/catalog` | the app catalogue, the approval vocabulary, the prompt rules and the router that walks them (was `knowledge` + `routing`) | clients |
| `core/observation` | the screen model: nodes, anchors, the observer, the screen source | clients |
| `core/records` | a run as records, and the chain that links them | clients |
| `core/prompt` | the system prompt and its blocks | clients |
| `core/agent` | one turn for every client, the loop, the gate, the executor, decisions, recipes (was `execution` + `turn`) | clients |
| `core/transport` | device and backend transport: the PC bridge client and the gateway client (was `capabilities`, then `remote`; `com.hopa.capabilities.*` stays with the Android adapters) | clients |
| `core/providers` | the adapters behind `ModelPort`: provider choice and state, the OpenAI-compatible client, the demo client (was `net.llm`) | clients |

Dependency chain (one direction only):

```
codec       (no deps)
contracts   → codec
catalog     → contracts
observation → contracts
records     → contracts + codec
prompt      → contracts + catalog
agent       → contracts + catalog + observation + records + prompt
transport   → contracts + codec
providers   → contracts + codec
```


`providers` and `transport` depend on `contracts` and `codec`, not on `agent`.
This keeps them decoupled from loop changes and reduces HAP/APK size for modules
that only need data types and port interfaces.

2026-11-18 addendum: `stores` is no longer a shared package. The Windows sidecar
imported only the chat vocabulary, which now lives in `spec/chat`; the store
interfaces and their in-memory implementations moved to `apps/android/stores`,
next to the Room, DataStore and Keystore classes that implement them. The backend
also dropped an unused `:stores` dependency.

2026-11-18 addendum 2: `net.llm` moved to `core/providers`. It is the adapter
layer behind `ModelPort` and changes with provider APIs, while `net` changes with
device and backend protocols; both are HTTP clients, and the README now says so
instead of claiming `net` was the only one.

Rules:

- `core/` is neither an app nor a service: no UI, no `main`, no Android SDK.
- `apps/android` and `apps/windows` never import each other; both import
  `core/`.
- `apps/harmony` (ArkTS, frozen) does not use `core/`; its own kit stays in
  `apps/harmony/app`.
- The folder is `packages`, not `shared`: `shared/` remains rejected as vague,
  and `libs/` would collide with the Gradle version catalog in this repository.
- Package names stay `com.hopa.*`; the move changes Gradle paths only.

2026-11-19 addendum: the engine is nine modules under `core/`, and the folder is
not called `packages/` any more - the name said nothing about purpose.
`knowledge` and `routing` are `catalog` (the router walks the catalogue it
loads), `execution` and `turn` are `agent` (the inner loop and the outer turn are
one path no caller used separately), and the JSON helpers (`HopaJson`,
`CanonicalJson`) are their own leaf, `codec`. `ObservationHasher` stayed in
`contracts`: it walks wire types, and `contracts` needs `codec` for
`RunRecordSerializer`, so moving it would make the two modules import each other.
