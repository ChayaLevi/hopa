# Decision layers

Status: D1-D2 done (2026-11-21); D3-D7 planned

HOPA will use **several decision layers**, not one. LAIX is the first to try, a
rules layer is the baseline, a small intent classifier may follow, and a
model-as-judge layer exists to compare against. The loop must not know which
one answered.

This document is the plan for that: the contract, the registry, how a layer is
chosen, how layers are compared, and how one is promoted or removed.

## Why a layer at all

Today the step after a plan is decided by the model's prose and the approval
policy. A decision layer makes that explicit: act, wait, ask, verify or
escalate, with a reason code, a confidence and a trace. For a closed set of
commands (open an app, toggle a setting, read the screen) a layer plus a small
intent classifier can skip the model entirely: milliseconds instead of minutes,
and a trail a person can read.

LAIX is exactly this shape - training-free, stateless, with reason codes,
commitments and an audit trail - and it ships a Kotlin/JVM implementation
(`packages/kotlin/laix`, Apache-2.0, 0.18 pre-1.0).

## The contract is ours

```kotlin
interface DecisionLayer {
    val metadata: LayerMetadata
    suspend fun decide(context: DecisionContext): LayerDecision
}

data class LayerMetadata(
    val id: String,
    val version: String,
    val latency: LatencyClass,      // MICRO | MILLI | SECONDS
    val decidesUpTo: ActionClass,   // READ | CHANGE | DANGEROUS
    val status: LayerStatus,        // EXPERIMENTAL | SHADOW | DEFAULT | RETIRED
    val conformance: ConformanceStatus, // NOT_RUN | PASSED | FAILED
    val author: String,
    val summary: String,
)

data class DecisionContext(
    val runId: String,
    val step: Int,
    val action: Action?,            // what is about to happen
    val screen: String,             // the current screen summary
    val history: List<String>,      // action signatures already taken
    val approvals: List<String>,    // signatures a person already allowed
    val attention: AttentionBudget, // how much interruption is left
    val cancelled: Boolean,
    val atStepLimit: Boolean,
)

data class LayerDecision(
    val verb: Verb,                // ACT | WAIT | ASK | VERIFY | ESCALATE
    val reason: String,            // a stable code, never prose
    val confidence: Double,        // 0..1
    val trace: String,             // one line for the run log
)
```

The output is `LayerDecision`, not `Decision`: `com.hopa.contracts.wire.Decision`
is already the model's wire answer (say / done / needUser), and two types with
one name in one loop is how a bug is born.

No vendor type crosses this boundary. LAIX, rules, a classifier or a model
judge are all adapters behind it, so a pre-1.0 API cannot leak into the loop.

## Registry and metadata

Every layer registers with:

| Field | Why |
|---|---|
| `id`, `version` | the run log must name what decided |
| `latency` | `MICRO` / `MILLI` / `SECONDS` - a phone cannot wait for the last one per step |
| `decidesUpTo` | the strongest action class it may decide alone |
| `status` | `EXPERIMENTAL` / `SHADOW` / `DEFAULT` / `RETIRED` |
| `conformance` | `NOT_RUN` / `PASSED` / `FAILED`; only `PASSED` layers may decide |
| `author`, `summary` | who to ask when it misbehaves, and what it is good at |

The registry lists layers; it does not decide which one runs. It refuses a
duplicate id at construction, so a misconfigured build fails in a test rather
than on a phone.

## Choosing a layer is policy, not a winner-take-all

A single global "best layer" is wrong: the class of action decides.

| Action class | Wire actions | Allowed layers |
|---|---|---|
| `READ` | `ui.observe`, `ui.back`, `ui.home`, `ui.launch`, `ui.scroll`, `ui.wait`, `ui.stop`, `web.read` | layers whose `decidesUpTo` is at least `READ` |
| `CHANGE` | taps, typing, swipes, app functions, navigation and calls | layers whose `decidesUpTo` is at least `CHANGE` |
| `DANGEROUS` | payments, delete, uninstall, reset, **and every unknown kind** | layers that cover `DANGEROUS` and passed conformance; the person still approves |

Under `Automatic`, the first `DEFAULT` layer allowed for the class is applied.
A pinned layer that cannot cover the class, is retired or has not passed
conformance is skipped with a notice, and `Automatic` decides instead. The
`requiresApproval` flag stays true for `DANGEROUS` whatever is selected.

The policy lives in `core/decision` (`:decision`) and is configuration, not code
in the loop.

## Conformance kit

A layer may not enter `safe` or `dangerous` decisions until it passes:

- an empty or unknown action never yields `ACT`;
- a refusal is `ASK`, not `ACT`;
- a repeat of the same step is `VERIFY` or `WAIT`, not `ACT` again;
- a cancelled turn never produces a later `ACT`;
- the trace names the reason code the layer returned.

The kit is a plain JVM test suite; every adapter runs it.

## Comparison harness

- `spec/decisions/` holds a versioned corpus: 10-20 scenarios with the decision
  a careful person would make, the class of action and the allowed verbs.
- The runner takes **any** registered layer and reports: agreement with the
  corpus, latency p50/p95, confidence calibration, trace completeness.
- One report lists every layer side by side. CI runs the runner on changed
  layers; the full table is produced before a promotion.
- Same harness for "no layer" (today's path) so the comparison is honest.

## Shadow mode

Every active layer votes on real turns; only the selected layer is applied.
All votes land in the run log as `decision.vote` records (layer id, verb,
reason, confidence, whether it was applied). Nothing is blocked.

This is how "which one works best" becomes data: after a week the run log shows
where LAIX disagrees with the rules, where it is over-confident, and where the
model path was the only correct answer.

## Lifecycle of a layer

1. **Experimental**: module + adapter + conformance kit. Not registered for real
   traffic.
2. **Shadow**: registered, votes, never applies.
3. **Default / allowed for a class**: promotion requires a table that beats the
   current choice on task success and latency with **no** growth in dangerous
   mistakes.
4. **Retired**: removed from the registry and the build. A layer that loses in
   its class is deleted, not kept "for choice" - the repository must not become
   a zoo of dead adapters.

## How LAIX reaches the build

| Option | When | Why |
|---|---|---|
| Included build (`includeBuild("../../laix/packages/kotlin/laix")`) | local experiment only | zero ceremony, but depends on a path outside the repo |
| **Vendored sources** with a pinned commit and the Apache-2.0 notice | merging into `main` | reproducible, offline, no surprise upgrades |
| Published artifact (JitPack tag or Maven) | later, if LAIX stabilises | a real dependency, but a pre-1.0 API in CI is a risk |

Decision: included build for the spike, vendored sources for anything merged.
No `latest`, no SNAPSHOT.

## In the build now (D1-D2)

| Where | What |
|---|---|
| `core/decision` (`:decision`) | `DecisionLayer`, `LayerMetadata`, `DecisionContext`, `LayerDecision`, `DecisionRegistry`, `DecisionSelector`, `DecisionCoordinator`, `DecisionVote`, `ConformanceKit`, `ComparisonHarness` |
| `core/decision-rules` (`:decision-rules`) | `RulesDecisionLayer`, the `decisionReport` task |
| `spec/decisions/corpus.v1.json` | 17 cases, each with a reference answer and the defensible answers |
| `spec/decisions/README.md` | the case format and the rules for adding one |

Commands, from `apps/android`:

```
gradlew :decision:test :decision-rules:test        # contract, kit, rules, corpus
gradlew :decision-rules:decisionReport             # the comparison table
```

The rules layer currently matches all 17 reference answers, makes no dangerous
`ACT`, and passes the conformance kit. Nothing is wired into the loop yet: the
app behaves exactly as before D1.

## Stages

### D1 - the port and the registry (1 PR) - done

`:decision` (the plan said `:core:decision`): `DecisionLayer`, `DecisionContext`,
`LayerDecision`, the registry, the class policy, the coordinator and tests.
Nothing is wired into the loop.

### D2 - the rules layer and the harness (1 PR) - done

`:decision-rules`: heuristics for the known commands (the fallback when no other
layer is available). The corpus in `spec/decisions/` and the runner that prints
the comparison table for any layer.

### D3 - shadow logging (1 PR)

The loop calls the registry in shadow mode and writes `decision.vote` into the
run log. Behaviour unchanged, data starts flowing.

### D4 - LAIX adapter (1 PR)

Vendored `:decision-laix` (`core/decision-laix`), the mapping from
`Action`/`ApprovalRequest` to LAIX's request and back, the conformance kit, and
its row in the comparison table.

### D5 - intent classifier for closed commands (1-2 PRs)

15-20 canonical commands, a small JVM classifier with a confidence threshold,
and a path that skips the model when the intent is clear. Falls back to the
model otherwise.

### D6 - enable for safe actions (1 PR)

The class policy enforces decisions for `ui.launch`, `ui.read` and navigation.
Payments, delete, reset and uninstall keep the current approval policy, which
is not replaced by a layer.

### D7 - measurement and the go/no-go (1 PR)

Ten tasks across three modes: model-only, shadow, decisions-on. Metrics: task
success, p50/p95 latency, model calls, false refusals, trace completeness. The
result decides whether the command set grows or the layer stays an audit trail.

## Choosing in Settings

The person decides whether layers are used at all, and which one. One page:
`Settings -> Decisions`.

```
Decisions
  ( ) Off - the model decides, as today
  (o) Automatic - the best allowed layer for each action
  ( ) Rules only
  ( ) LAIX
  ( ) Intent classifier            [experimental]
  [x] Record what other layers would have decided (shadow)
  Active now: Automatic -> Rules + LAIX (safe actions)
```

- **Off** is the honest default until a layer beats the model on the corpus. The
  model path stays exactly as it is.
- **Automatic** applies the class policy in `:core:decision`; the page shows
  which layers that means for safe, careful and dangerous actions.
- **A specific layer** pins it everywhere it is allowed; a layer that is not
  allowed for an action class is skipped there and the page says so.
- **Shadow** can be on in any mode: votes are logged, nothing changes.
- Each row shows the layer's status badge (`default`, `shadow`, `experimental`),
  its latency class and one line of what it is good at - the same honesty the
  model page uses.
- Dangerous actions (payments, delete, reset, uninstall) always keep the
  approval step, whatever is selected.

Storage: `decisionMode` and `shadowDecisions` in `AppSettings`; the selected
layer id is a string, empty for Automatic. A layer that disappears from the
build leaves the setting pointing at nothing, and the page falls back to
Automatic with a notice instead of breaking.

## Acceptance criteria

1. Adding a layer touches no loop, UI or approval code - a new module plus a
   registry entry.
2. Every layer passes the conformance kit before it can decide a `safe` action.
3. The comparison table exists for at least three layers plus "no layer".
4. A promoted layer beats the previous choice on task success and latency with
   no increase in dangerous mistakes.
5. A retired layer leaves no dead module, flag or branch behind.
6. The run log names the layer, its version and its reason code for every turned
   decision.

## Risks

| Risk | Mitigation |
|---|---|
| LAIX is pre-1.0 | vendored pinned sources; our contract in front of it |
| Layer zoo | lifecycle rules; a loser is deleted |
| Over-confidence | calibration reported by the harness; `ASK` required below a threshold |
| Dangerous actions decided by a layer | class policy keeps approvals and forbids layers there |
| Two sources of truth | the loop consults only `DecisionLayer`; LAIX's own trace is stored as a field, not as the decision |
