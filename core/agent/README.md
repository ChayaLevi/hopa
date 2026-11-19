# agent - the turn, the loop and who decides

One turn for every client. `TurnRunner` owns the order, `AgentLoop` owns the
inner cycle, and a `DecisionPolicy` answers "what next" - a model, a navigation
graph or a rule layer, the loop never learns which.

No Android, no network, no UI. Plain JVM.

## Packages, bottom first

A package may name only packages in an earlier layer; inside a layer the order is
alphabetical.

### Layer 0 - no engine package below them

| package | what lives here | depends on |
|---|---|---|
| `classify` | `ActionKindRules`, `ActionClassifier`: what each action kind needs, the table the approval check and the executor share | contracts |
| `prompt` | `SystemPromptBuilder`, `AppCatalogBlock`, `InstalledAppsBlock`, `ScreenRenderer`, `ContextBudget`: text for the model | catalog, contracts |

### Layer 1 - on top of layer 0

| package | what lives here | depends on |
|---|---|---|
| `action` | `ActionExecutor`, `ApprovalPolicy`, `ValidationResult`, `ScrollGeometry`: what a step can be, whether it needs a person, and how it is performed | catalog, classify, contracts |
| `decision` | `Decision` (`Act`, `Ask`, `Say`, `NoPath`, `Failed`), `DecisionInput`, `DecisionOutcome`, `DecisionPolicy`, `StepPolicy`: the seam | contracts |
| `goal` | `Checkpoint`, `GoalVerification`, `GoalVerifier`: did the turn arrive | contracts, observation |

### Layer 2 - policies and replay

| package | what lives here | depends on |
|---|---|---|
| `decision/graph` | `ScreenKey`, `EdgeGuard`, `NavGraph`, `NavGraphStore`, `GraphDecisionPolicy`, `FallbackDecisionPolicy`, `GraphBuilder`: navigation without a model | decision, observation |
| `decision/model` | `ModelDecisionPolicy`, `ModelStepPolicy`, `DecisionParser`, `DecisionRules`, `ParseOutcome`, `ModelReplyText`, `BalancedObjectScanner`: everything model-shaped | action, classify, decision, prompt |
| `recipe` | `Recipe`, `RecipeStore`, `Replayer`, `ReplayAnswer`, `ReplayOutcome`: a path that worked, and its replay | action, decision/model, prompt |

### Layer 3 - the loop and the plan

| package | what lives here | depends on |
|---|---|---|
| `loop` | `AgentLoop`, `LoopConfig`, `RunNarrator`: observe, decide, execute, record, repeat; a step said in one human line | action, classify, decision, decision/model, prompt |
| `plan` | `PlanSource`, `ModelPlanSource`: the steps before the first tap | contracts |

### Layer 4 - the turn

| package | what lives here | depends on |
|---|---|---|
| `turn` | `TurnRunner`, `TurnRequest`, `TurnOutcome`, `TurnSource`: the plan gate, the recipe path, one deadline, the final screen check | action, decision, goal, loop, plan, recipe |

## The decision seam

```kotlin
interface DecisionPolicy {
    val name: String
    suspend fun decide(input: DecisionInput): DecisionOutcome
}
```

`DecisionInput` carries the goal, the observation, the step number, notes from
earlier steps and one screenshot. It does **not** carry a prompt: a policy that
never calls a model must not pay for text it will not use.

Three implementations today:

- `ModelDecisionPolicy` - builds the prompt, asks the provider through
  `StepPolicy`, parses the reply. The only place that knows about models.
- `GraphDecisionPolicy` - takes the best guarded edge from the screen it
  recognizes; `NoPath` when the screen is unknown.
- `FallbackDecisionPolicy` - tries the graph, asks the model when there is no
  path. This is how a graph is learned app by app without changing the turn.

`NoPath` is a decision, not an error: the loop stops with a question instead of
guessing a tap.

## The gate

Every action goes through `ActionExecutor`, which consults `ApprovalPolicy` on
the way. A model, a recipe and a graph all hit the same check, so no path can
skip a payment prompt.

`ApprovalPolicy` is an immutable snapshot read one step at a time: trust and
autonomy are fixed when the run starts and two runs in flight never share a
mutable list.

## What a client supplies

`TurnContext` and `TurnResult` live in `contracts`, so a client describes a
request without depending on this module. The client also supplies the ports: a
`DevicePort`, an `ApprovalGateway`, a `RunRecorder`, a `Clock`, and a model
behind `ModelPort`.

## Tests

```
./gradlew :agent:test :agent:ktlintCheck
```

`com.hopa.agent.conformance` reads `spec/` from the repository, so the decisions
and records of this module are checked against the same JSON as every other
client.
