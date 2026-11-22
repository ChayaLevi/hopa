# Architecture of one turn

This page is the architecture of a single turn on the phone: which parts exist,
what each one is responsible for, and in what order they run. It replaces
[`docs/media/hopa-turn-flow.png`](../../media/hopa-turn-flow.png), which predates
on-device models, decision rules and written paths for known apps.

Names in **bold** are the real classes and settings; the sentence after each one
says what it does in plain words.

- **TurnRunner** - tries the places a step can come from, in a fixed order.
- **AgentLoop** - asks the model for one step at a time.
- **ActionExecutor** - checks the step, asks the person when needed, does it and
  verifies the result.
- **DecisionRuntime** - lets the decision rules change a step, within limits.
- A **written path** is a few hand-written steps for an app HOPA knows
  (Settings, Clock, Camera).
- A **token** is the unit a model is paid by; `0 tokens` means no model was
  asked.

The colours mean: blue is input/output, green is HOPA's own code, amber is a
model, purple is storage, red is a refusal.

## The parts of a turn

```mermaid
flowchart TD
  classDef io fill:#EAF1FB,stroke:#5B7FD4,color:#1C2B4A,stroke-width:1.5px
  classDef core fill:#E7F4EA,stroke:#3F8F5A,color:#16301F,stroke-width:1.5px
  classDef model fill:#FFF4D6,stroke:#C9962F,color:#3A2C08,stroke-width:1.5px
  classDef result fill:#E0F2F1,stroke:#2F7D7A,color:#0F2E2C,stroke-width:1.5px
  classDef store fill:#F0EAFB,stroke:#7A5AB8,color:#241A3A,stroke-width:1.5px
  classDef stop fill:#FBE4E4,stroke:#BF5252,color:#3D1414,stroke-width:1.5px
  UI["Chat screen<br/>text, voice, a file, a quick reply"]
  RC["RunCoordinator<br/>claims the phone for one turn"]
  TR["TurnRunner<br/>chooses where the steps come from"]
  GS["HandGraphSource<br/>written paths for Settings, Clock, Camera"]
  IC["IntentClassifier<br/>open, back, home, scroll, wait"]
  RP["RecipeStore + Replayer<br/>repeat steps that worked before"]
  PL["Planner<br/>writes a plan, runs nothing"]
  AL["AgentLoop<br/>one model step at a time"]
  MF["ModelFactory<br/>cloud, LAN or on-device"]
  DR["DecisionRuntime<br/>asks the rules, applies the allowed answer"]
  EX["ActionExecutor<br/>risk gate, approval, device call, verify"]
  AP["ApprovalGateway<br/>one question, the answer is stored"]
  DV["Device port<br/>accessibility, shell or bridge"]
  OB["Observation<br/>screen tree, text, hashes"]
  RR["RunRecorder<br/>append-only, hash-chained record"]
  TO["TurnOutcome<br/>answer, records, usage, surface"]
  CV["ChatViewModel<br/>saves the answer and its receipt"]
  DB["turns, run_records, decision_votes"]

  UI --> RC --> TR
  TR --> GS
  TR --> IC
  TR --> RP
  TR --> PL
  TR --> AL
  AL --> MF
  AL --> DR
  AL --> EX
  EX --> AP
  EX --> DV
  DV --> OB
  EX --> RR
  TR --> TO
  TO --> CV
  CV --> DB
  DR --> DB

  class UI,AP,DV,OB,CV io
  class RC,TR,GS,IC,RP,PL,AL,DR,EX,TO core
  class MF model
  class RR,DB store
```

## The order TurnRunner tries

The order is fixed in `TurnRunner.run`: something already written beats something
generated. The labels name the branch, not the person's feelings.

```mermaid
flowchart TD
  classDef io fill:#EAF1FB,stroke:#5B7FD4,color:#1C2B4A,stroke-width:1.5px
  classDef core fill:#E7F4EA,stroke:#3F8F5A,color:#16301F,stroke-width:1.5px
  classDef model fill:#FFF4D6,stroke:#C9962F,color:#3A2C08,stroke-width:1.5px
  classDef result fill:#E0F2F1,stroke:#2F7D7A,color:#0F2E2C,stroke-width:1.5px
  classDef store fill:#F0EAFB,stroke:#7A5AB8,color:#241A3A,stroke-width:1.5px
  classDef stop fill:#FBE4E4,stroke:#BF5252,color:#3D1414,stroke-width:1.5px
  A["TurnRunner.run"] --> B{"surfaceAvailable<br/>did a screen answer?"}
  B -->|"no"| B1["TurnSource.UNAVAILABLE"]
  B -->|"yes"| C{"stepSource<br/>from How HOPA acts"}
  C -->|"graph"| D["graph()"]
  D -->|"hit"| D1["TurnSource.GRAPH"]
  D -->|"miss"| D2["refuse: written paths only"]
  C -->|"auto or model"| E{"target(goal).completes<br/>can a written path finish it?"}
  E -->|"yes"| F["1. graph()"]
  E -->|"no"| G["1. command()"]
  F -->|"hit"| F1["TurnSource.GRAPH"]
  F -->|"miss"| H["2. command()"]
  G -->|"hit"| G1["TurnSource.COMMAND"]
  G -->|"miss"| I["2. graph()"]
  H -->|"hit"| H1["TurnSource.COMMAND"]
  H -->|"miss"| J{"planFirst<br/>and not approved?"}
  I -->|"hit"| I1["TurnSource.GRAPH"]
  I -->|"miss"| J
  J -->|"yes"| J1["TurnSource.PLAN<br/>steps only"]
  J -->|"no"| K{"replay<br/>and a recipe for these words?"}
  K -->|"hit"| K1["TurnSource.RECIPE"]
  K -->|"miss"| L["fromModel()"]



  class A,B,C,D,E,F,G,H,I,J,K core
  class D1,F1,G1,H1,I1,K1 result
  class B1,D2 stop
  class J1,L model
```

Two consequences, both visible in the receipt:

- a written path that can finish the goal runs before `command()`, so "open
  camera" ends in `TurnSource.GRAPH`, not `COMMAND`;
- with `stepSource = graph`, neither `command()` nor the model is allowed to
  answer instead.

## One step inside AgentLoop

```mermaid
flowchart TD
  classDef io fill:#EAF1FB,stroke:#5B7FD4,color:#1C2B4A,stroke-width:1.5px
  classDef core fill:#E7F4EA,stroke:#3F8F5A,color:#16301F,stroke-width:1.5px
  classDef model fill:#FFF4D6,stroke:#C9962F,color:#3A2C08,stroke-width:1.5px
  classDef result fill:#E0F2F1,stroke:#2F7D7A,color:#0F2E2C,stroke-width:1.5px
  classDef store fill:#F0EAFB,stroke:#7A5AB8,color:#241A3A,stroke-width:1.5px
  classDef stop fill:#FBE4E4,stroke:#BF5252,color:#3D1414,stroke-width:1.5px
  A["AgentLoop<br/>step n"] --> B["build the prompt<br/>goal, history, skills, apps, memory,<br/>knowledge, the screen"]
  B --> C["StepPolicy.next(ModelRequest)"]
  C --> D["ModelFactory<br/>cloud, LAN or the phone's engine"]
  D --> E["DecisionParser<br/>action, say, done, needUser"]
  E --> F{"what came back?"}
  F -->|"text, plan or done"| F1["answer, no action"]
  F -->|"an action"| G["DecisionRuntime.observe<br/>every layer votes"]
  G --> H{"decisions.appliesTo(class)?"}
  H -->|"READ"| H1["verb: ACT, VERIFY, WAIT,<br/>ASK or ESCALATE"]
  H -->|"CHANGE, DANGEROUS"| H2["the model's action stands"]
  H1 --> I["ActionExecutor.execute"]
  H2 --> I
  I --> J{"risk from ActionClassifier<br/>needs approval?"}
  J -->|"safe"| K["Device port performs it"]
  J -->|"sensitive or payment"| L["ApprovalGateway<br/>the person answers"]
  L -->|"allowed"| K
  L -->|"refused or timeout"| L1["record REFUSED, step stops"]
  K --> M["observe again<br/>before/after hashes"]
  M --> N["RunRecorder.append<br/>RunRecord + chain"]
  N --> O{"deadline or maxSteps?"}
  O -->|"continue"| A
  O -->|"stop"| P["TurnOutcome"]
  F1 --> P


  class A,B,C,E,F,G,H,H1,I,J,O core
  class D model
  class F1,P result
  class H2,K,L,M io
  class L1 stop
  class N store
```

## Decision layers

`DecisionRuntime` is the only way the loop asks a rule. A layer returns a
`LayerDecision` (verb, reason, confidence, trace, chosen); the runtime applies it
only to actions the layer is allowed to decide, and only to reading steps.

```mermaid
flowchart LR
  classDef io fill:#EAF1FB,stroke:#5B7FD4,color:#1C2B4A,stroke-width:1.5px
  classDef core fill:#E7F4EA,stroke:#3F8F5A,color:#16301F,stroke-width:1.5px
  classDef model fill:#FFF4D6,stroke:#C9962F,color:#3A2C08,stroke-width:1.5px
  classDef result fill:#E0F2F1,stroke:#2F7D7A,color:#0F2E2C,stroke-width:1.5px
  classDef store fill:#F0EAFB,stroke:#7A5AB8,color:#241A3A,stroke-width:1.5px
  classDef stop fill:#FBE4E4,stroke:#BF5252,color:#3D1414,stroke-width:1.5px
  A["AppSettings.decisionMode<br/>off, auto or a layer id"]
  B["DecisionRegistry<br/>Rules, LAIX (SHADOW)"]
  A --> C["DecisionRuntime"]
  B --> C
  C --> D["DecisionSelector<br/>decidesUpTo(class)"]
  D --> E["DecisionLayer.decide(DecisionContext)"]
  E --> F["LayerDecision<br/>verb, reason, confidence, trace"]
  F --> G["DecisionCoordinator<br/>apply if the class allows"]
  G --> H{"ActionClass"}
  H -->|"READ"| H1["the verb replaces the step"]
  H -->|"CHANGE, DANGEROUS"| H2["the model's step stands"]
  F --> I["DecisionVoteSink<br/>decision_votes"]
  J["shadow mode<br/>votes are recorded, not applied"] --> I


  class A,H2,J io
  class B,C,D,E,F,G,H,H1 core
  class I store
```

A rule never bypasses `ActionExecutor`: the approval question for a change or a
payment is still asked.

## Where the model comes from

```mermaid
flowchart LR
  classDef io fill:#EAF1FB,stroke:#5B7FD4,color:#1C2B4A,stroke-width:1.5px
  classDef core fill:#E7F4EA,stroke:#3F8F5A,color:#16301F,stroke-width:1.5px
  classDef model fill:#FFF4D6,stroke:#C9962F,color:#3A2C08,stroke-width:1.5px
  classDef result fill:#E0F2F1,stroke:#2F7D7A,color:#0F2E2C,stroke-width:1.5px
  classDef store fill:#F0EAFB,stroke:#7A5AB8,color:#241A3A,stroke-width:1.5px
  classDef stop fill:#FBE4E4,stroke:#BF5252,color:#3D1414,stroke-width:1.5px
  A["AppSettings.provider"] --> B["openai<br/>HTTP, endpoint + key"]
  A --> C["lan<br/>HTTP, your own network"]
  A --> D["on-device<br/>LlamaEngine"]
  E["AppSettings.activeModelId"] --> F["the chosen .gguf file"]
  F --> D
  D --> G["native chat template, sampling<br/>tokens streamed"]
  B --> H["ModelReply"]
  C --> H
  G --> H


  class A,E io
  class B,C,D model
  class F store
  class G core
  class H result
```

On-device files carry a licence, an exact size and a sha256; the download is
refused without the licence and re-asked when the digest changes. A missing file
is a named error, never a silent cloud fallback.

## What is written down

| Where | What |
|---|---|
| `turns` | the message and `MessageProof`: source, steps, tokens, time, knowledge version |
| `run_records` | one row per executed step, with before/after hashes and a chain |
| `decision_votes` | every layer's opinion per step, including shadow ones |
| `run_history` | the per-turn summary the Activity page reads |

The receipt under an answer is `MessageProof.source`: `graph`, `command`,
`recipe` or `model`. The status line while the turn runs uses the same source, so
a turn cannot claim one thing while running and another in the receipt.

## For developers

| Concern | Code |
|---|---|
| Turn order and the source branches | `core/agent/.../turn/TurnRunner.kt` |
| The step loop, prompt, verification | `core/agent/.../loop/AgentLoop.kt` |
| Decision contract, registry, selector, coordinator | `core/decision/...` |
| Rules, LAIX, commands, written paths | `core/decision/.../{rules,laix,classifier,graph}` |
| Executor, gate, approval | `core/agent/.../action/ActionExecutor.kt`, `apps/android/.../UiApprovalGateway.kt` |
| Model choice and the on-device engine | `apps/android/app/.../di/AppContainer.kt`, `core/inference/...` |
| Receipt and live label | `apps/android/feature/chat/.../ChatViewModel.kt` |
| One turn at a time | `core/runtime/.../RunGate.kt` |
