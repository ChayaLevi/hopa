# Refactoring backlog

Covers `packages/` (JVM core modules) and the Android app under `apps/android/`.
Items are grouped by scope; within each group roughly ordered by impact.
An independent critique agent reviewed this document and found several errors
and omissions — those are incorporated below.

---

## 1. Top-level folder

| Current | Proposed | Reason |
|---|---|---|
| `packages/` | `core/` | `packages/` communicates nothing about purpose. `core/` says "platform-independent brain of the product" and pairs cleanly with `apps/`. Do this rename **last**, after all module-level changes, to minimise blast radius on CI scripts, build files, and the sidecar. |

---

## 2. Module-level changes

### 2a. Merge `knowledge/` + `routing/` → `catalog/`

`routing` depends on `knowledge`; `knowledge` has zero outbound dependencies.
`AppRouter` walks the `Catalog` that `knowledge` loads — they are one concern
split into two modules. The combined name `catalog` is accurate: it is the system
that knows what apps are installed, what rules apply, and how to route a goal to
the right app.

Callers that need build-file updates: `execution`, `turn`, `prompt`
(all currently import `routing` directly).

No circular dependency risk — nothing in `knowledge` or `routing` imports
any other local module.

### 2b. Rename `execution/` → `loop/`, then merge with `turn/` → `agent/`

**Step 1 (agreed):** Rename `execution` → `loop`. The module is the agent's inner
action loop; `loop` is narrower and more precise than `execution`, and makes the
layering obvious when read alongside `turn`.

**Step 2:** Merge `loop/` + `turn/` → `agent/`. `turn` depends on `execution`/`loop`.
`turn` is the outer orchestrator (receive request → pick strategy → drive loop →
verify), `loop` is the inner engine (observe → decide → act). Together they form
the agent. The two steps can be done separately — rename first, merge later.

**Complication — the sidecar bypasses `turn` entirely.** The Windows sidecar
(`apps/windows/sidecar/Engine.kt`) constructs `AgentLoop` directly from
`execution` without using `TurnRunner` at all. After the merge, the new `agent`
module must still expose `AgentLoop` with a stable API, or the sidecar must be
updated to use `TurnRunner`. Decide which before merging.

**Complication — the Android app also imports `execution` directly.** `AppContainer`
and `RunCoordinator` import `ApprovalPolicy`, `ActionExecutor`, and `ModelStepPolicy`
from `execution` bypassing `TurnRunner`. The merge doesn't break anything but the
`agent` module's surface area is wider than `turn`'s public API alone.

**Consideration — `TurnContext` and `TurnResult` placement.** These are plain data
classes imported by the Android app's `RemoteTaskRunner` and the sidecar. They
currently live in `execution` but represent interface-level concepts. Consider
moving them to `contracts` so callers don't need to depend on the full `agent`
module just to name a type.

### 2c. Extract `contracts/json/` → new module `codec/`

`CanonicalJson` and `HopaJson` are serialization utilities, not shared contracts.
No module should need to depend on `contracts` just to get a JSON helper.

`contracts` would remain dependent on `codec` because `ObservationHasher` (still
in `contracts`) and `RunRecordSerializer` (also still in `contracts` — see 3b
why it cannot move) both use `CanonicalJson`/`HopaJson`.

Callers affected: `execution`, `records`, `observation`, `transport`,
`providers`, Android `data`, the sidecar — all currently get `HopaJson` through
`contracts` transitively. After the split they each gain an explicit `codec`
dependency.

Note: `KnowledgeCodec` in `knowledge` has its own private `Json` instance and
does **not** use `HopaJson`. It would not need a `codec` dependency.

---

## 3. Within-module cleanup

### 3a. Move `ObservationHasher` out of `contracts/`

`ObservationHasher` is implementation — it walks an `Observation` tree and
produces a SHA-256 digest. Its natural home is **`codec/`**, not `observation/`.

Do NOT move it to `observation/`: `records` and `transport` both call
`ObservationHasher` and neither currently depends on `observation`. Moving it
there would force both to add a new dependency on `observation`, which is wrong
coupling. Placing it in `codec` avoids new dependencies because `records` and
`transport` will already depend on `codec` (see 2c).

### 3b. `RunRecordSerializer` — leave in `contracts/wire/`

`RunRecord` is annotated `@Serializable(with = RunRecordSerializer::class)`.
In Kotlin/kotlinx.serialization this annotation must reference a class in the
same module as `RunRecord`. Moving `RunRecordSerializer` to `records` would
require removing the annotation from `RunRecord` and changing how serialization
is invoked — that is not a simple file move. Leave it where it is.

### 3c. Rename `StubModelClient` → `DemoModelClient`

`StubModelClient` is **not** test code. `ModelFactory.create()` returns it in
production when `ProviderChoice.DEMO` is selected (i.e., no API key configured).
It is the production fallback for a fresh install. Moving it to `testFixtures`
would break the production build.

The only issue is the name: `Stub` implies test double. Rename to `DemoModelClient`
to make it clear this is a production offline/demo path, not a test fake.

---

## 4. Security / design concern (needs discussion)

### 4a. `ApprovalPolicyData` lives in `knowledge` alongside downloadable content

`ApprovalPolicyData` is a security-critical file: it determines which actions
require user approval before execution. It lives in the `knowledge` module,
which also contains `FileKnowledgeStore` — a store that downloads and replaces
its catalog from the backend.

`FileKnowledgeStore` already says in comments: "The approval table stays with
the bundled snapshot on purpose. A downloaded catalog may say which app to open;
it must not be able to make the gate less careful." So the intent is correct, but
the enforcement is by convention only. If `knowledge` becomes `catalog` (after
merge 2a), the approval policy data would sit in the same module as the routing
catalog — still protected only by comment, not by module boundary.

Options to consider:
- Keep approval policy in `contracts` (zero upstream deps, guaranteed safe),
- Or keep it in `knowledge`/`catalog` with explicit documentation of the
  invariant and test coverage asserting the bundled table is always used.

---

## 5. Investigation items

### 5a. Android `feature/recipes/` naming confusion

The Android codebase uses two completely different concepts with overlapping names:
- **Skills** (`DbSkillStore`): user-defined text trigger+action pairs stored in
  a database. The UI screen for this is in `feature/recipes/SkillsScreen.kt` but
  labelled "Skills" — the package says `recipes`, the class says `Skills`.
- **Recipes** (`turn/recipe/RecipeStore`): recorded sequences of UI actions
  captured during a successful run, used for replay. The UI for this is in
  `feature/settings/SettingsRecipesScreen.kt`.

The Android app imports `turn.recipe.Recipe` directly (no duplication — the
investigation item in the previous draft was based on a wrong premise). The naming
inconsistency is the real issue: `feature/recipes/` manages *skills*, not recipes.
Rename `feature/recipes/` → `feature/skills/` and restore the original name.

### 5b. `BalancedObjectScanner` placement

`BalancedObjectScanner` is a JSON object boundary detector that scans model
replies for embedded `{...}` blocks. It is specific to `execution`'s
`DecisionParser`. It does not belong in `codec` (which handles well-formed data).
Leave it in `execution`/`agent`.

---

## 6. Result after all changes

```
core/
  contracts/      shared wire types, domain types, port interfaces
  codec/          JSON serialization utilities  ← extracted from contracts/json + ObservationHasher
  observation/    screen reading and accessibility tree processing
  prompt/         LLM prompt building and screen rendering
  providers/      LLM provider adapters (OpenAI + DemoModelClient)
  records/        run history recording and chaining
  transport/      ADB bridge and backend gateway
  catalog/        app catalog, routing, rules  ← merged knowledge + routing
  agent/          agent loop, turn orchestration, recipe replaying  ← merged execution + turn
```

Net change: 10 modules → 7 modules.
(10 − 2 from catalog merge − 2 from agent merge + 1 for codec = 7, then rename to `core/`)

---

## 7. Recommended order of execution

1. Rename `StubModelClient` → `DemoModelClient` (safe, no structural change)
2. Extract `codec/` from `contracts/json/` + move `ObservationHasher` to `codec/`
3. Merge `knowledge/` + `routing/` → `catalog/`
4. Rename `execution/` → `loop/`
5. Decide sidecar strategy (update sidecar to use `TurnRunner`, or document
   that `agent` keeps `AgentLoop` as public API)
6. Merge `loop/` + `turn/` → `agent/` (and move `TurnContext`/`TurnResult`
   to `contracts/` if decided in 2b)
7. Rename `packages/` → `core/` last
8. Rename Android `feature/recipes/` → `feature/skills/` (Android-only, independent)

---

## Done - 2026-11-19

All of the above landed in one branch (`refactor/modules`), one APK build at the
end:

| plan item | result |
|---|---|
| 2a knowledge + routing | `core/catalog`, package `com.hopa.catalog`; the three generators, the bundle copies and the test helpers moved with it |
| 2b execution + turn | `core/agent`, package `com.hopa.agent`; the intermediate `loop` rename was skipped - it would have moved the same files twice |
| 2b TurnContext/TurnResult | `contracts`, where the client boundary belongs |
| 2c contracts/json | `core/codec`, package `com.hopa.codec` |
| 3a ObservationHasher | **stayed in `contracts`**: it walks wire types and `contracts` depends on `codec`, so moving it would create a cycle |
| 3b RunRecordSerializer | left in `contracts/wire` as planned |
| 3c StubModelClient | `DemoModelClient` |
| 5a feature/recipes | `feature/skills` |
| 1 packages -> core | done last, as planned |
| sidecar strategy | it keeps using `AgentLoop`; only its import lines changed |

Module count: 10 -> **9** (`contracts`, `codec`, `catalog`, `observation`,
`records`, `prompt`, `agent`, `providers`, `transport`). The earlier estimate of
7 subtracted two modules per merge; a merge removes one.

`catalog` carries no ktlint: most of it is generated, and reformatting the
generated files would break `build-*.py --check`.
