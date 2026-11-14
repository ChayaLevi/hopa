# Development story: how HOPA gets built, team by team

Status: **narrative for planning and onboarding.** Written 2026-11-14. It is not
a user story (that one is [`product-story.md`](product-story.md)); it is the
**build story**: one stage, one team, one capability, one integration, one demo.

Use it when people know neither the product nor the teams. Every stage explains
both in the same breath:

> Team X builds Part Y. Part Y is what lets the product do Z. It attaches to
> Part W at contract C. At the end of the stage, this is what you can show.

## How to read a stage

| Field | Meaning |
|---|---|
| **Capability** | what the product can do after the stage that it could not do before |
| **Team** | who owns the part, and therefore the capability |
| **Builds** | the concrete pieces in the repository |
| **Attaches to** | the earlier part it plugs into, and the contract at the seam |
| **Demo** | the acceptance: something a person can watch |

## The capability ladder

The product climbs one rung per stage. This is the one-slide version.

```
8  it proves itself          Product lab
7  it works across devices   Platform
6  it knows apps             Knowledge + Factory + Delivery + Farm
5  it decides and asks       Decision layer + Family
4  it remembers              Tasks + Notifications
3  it refuses                Loop + gate
2  it acts                   Execution
1  it answers                Clients + Architecture
0  it runs on two devices    the seed (already here)
```

A rung is not "done" until the demo at that rung works on both clients where the
rung applies.

## Stage 0 - the seed (already standing)

Android and Windows clients, the shared Kotlin engine in `packages/`, the bridge,
the gate, records and a release. The seed is not a rung to build; it is the
ground the first stage stands on.

What it proves: a person can type a goal and get an answer on the phone or the
PC. It does not yet act, remember, decide or prove.

---

## Stage 1 - the frame (Clients + Architecture)

**Capability:** *it answers.* A person opens the app on either device, types or
speaks, and gets a streamed answer in a HOPA-shaped window.

**Builds:**
- Architecture: the first contracts (`action`, `observation`, `policy`), the CI
  gate, the repository shape (`apps/`, `packages/`, `contracts/`).
- Clients: the shell (sidebar, chat, composer, settings, profile homes), the
  visual language, the sidecar on Windows.

**Attaches to:** nothing. This is the host that every later part plugs into.

**Demo:** on Android and Windows, type "hello" and get an answer; close the app
and reopen it; the window is still HOPA.

---

## Stage 2 - the hands (Execution)

**Capability:** *it acts.* HOPA performs one step on a real device and checks the
result.

**Builds:**
- one `DevicePort` contract, two implementations: the bridge `AdbDevice` and the
  Android accessibility service; on Windows, the UI Automation adapter.
- observation before and after every step; screenshots where allowed.
- honest refusals: a locked screen, an elevated window, an app with no tree.

**Attaches to:** the client's loop at `DevicePort` and `contracts/observation`.

**Demo:** from the Windows window, "open Settings on the phone" - the phone
changes; in Notepad on the PC, "type this" - the text lands and is read back.

---

## Stage 3 - the refusal (Loop + gate, Clients + Architecture)

**Capability:** *it refuses.* HOPA acts only after the gate, and every step is
recorded and verifiable.

**Builds:**
- the agent loop (observe, decide, act, verify, record) with a step limit and a
  stop;
- the policy: classification, approval for sensitive/payment, trusted apps;
- hash-chained run records and the run view.

**Attaches to:** Stage 2 at the executor; the client at the run view.

**Demo:** a payment action stops and asks; deny leaves `executed: false`; a
tampered record fails `verifyChain`; Stop ends a running turn.

---

## Stage 4 - the memory (Tasks + Notifications)

**Capability:** *it remembers.* A request survives the screen: it has a state, a
next step, a place to resume and a device to continue on.

**Builds:**
- `contracts/tasks` (the task noun) and `services/tasks`;
- states and transitions, idempotency, resume and handoff;
- notifications turned into candidate tasks (understanding, not yet trust).

**Attaches to:** Stage 1 (client inbox) and Stage 3 (a task drives turns);
contract `contracts/tasks`.

**Demo:** start a task on the phone, close everything, reopen on Windows and
continue it; a notification becomes a task with a date and an owner.

---

## Stage 5 - the decision and the family (Decision layer + Family)

**Capability:** *it decides and asks the right person, once.* The model proposes;
the product chooses `wait / observe / guide / ask / act / verify / escalate` with
a reason, a score and the promises still open.

**Builds:**
- the decision contract (the LAIX shape: verb, reason code, confidence,
  requires_approval, reversible, undo hint);
- a Kotlin decision layer in `packages/decision` (the same semantics on both
  clients), deterministic policies first;
- commitments (promises with due dates) and a human-attention budget;
- family roles: who may approve what; delegated approval; denial on death.

**Attaches to:** Stage 3 (the gate asks the decision layer instead of a bare
classification) and Stage 4 (the task carries the commitment);
contracts `decision` and `family`.

**Demo:** a payment asks exactly the right person, once, and stops on deny; a
promise made on Monday changes Tuesday's decision; a child's request waits for a
parent; the attention budget refuses to interrupt five times.

---

## Stage 6 - the know-how (Knowledge + Factory + Delivery + Farm)

**Capability:** *it knows apps, and survives their updates.*

**Builds:**
- `contracts/recipes`: steps, selectors, postconditions, app version pins,
  signatures;
- a recipe library and the first real app end to end;
- a factory (models propose, humans review) that produces candidates;
- delivery: signed versions, canary, rollback;
- the verification farm: emulators and phones re-check recipes after an app
  update.

**Attaches to:** Stage 2 (execution consumes a recipe) and Stage 5 (a task step
asks for one); contracts `recipes`.

**Demo:** a new app is supported in a day; an app update breaks a step; the farm
catches it and delivery rolls back to the working version before a person
notices.

---

## Stage 7 - the network (Platform)

**Capability:** *it works across devices and networks, safely.*

**Builds:**
- pairing, token rotation and the single token holder;
- the bridge and relay paths, target switching (phone or PC);
- the API between clients and services (`contracts/openapi`), audit and limits.

**Attaches to:** every stage that crosses a device boundary (2, 4, 5); it owns
transport and none of the semantics.

**Demo:** pair a new phone with a code; rotate the token mid-run and watch the
next call succeed; run a task on the phone from the PC.

---

## Stage 8 - the proof (Product lab)

**Capability:** *it proves itself.* The product can say what works, for whom, and
what to fix next.

**Builds:**
- the benchmark and conformance suite (decisions, recipes, continuity);
- pilot metrics from the journals: time saved, steps, repeats, approval latency;
- parity checks across clients and a failure taxonomy.

**Attaches to:** all stages through the run records; it changes nothing, it
decides what changes next.

**Demo:** one page of numbers that ranks the next three fixes; a benchmark that
fails when a policy regresses.

## The integration map

```
                 +-----------------------------+
   Stage 8  -->  |            PROOF            |  Product lab
                 +--------------^--------------+
                                | journals
                 +--------------+--------------+
   Stage 6  -->  |          KNOW-HOW           |  Knowledge + Factory + Delivery + Farm
                 +--------------^--------------+
                                | recipes
                 +--------------+--------------+
   Stage 5  -->  |     DECISION + FAMILY       |  Decision layer + Family
                 +--------------^--------------+
                                | approval / verb
                 +--------------+--------------+
   Stage 4  -->  |           MEMORY            |  Tasks + Notifications
                 +--------------^--------------+
                                | tasks
                 +--------------+--------------+
   Stage 3  -->  |      LOOP + GATE + RECORDS  |  Clients + Architecture
                 +--------------^--------------+
                                | actions / observations
                 +--------------+--------------+
   Stage 2  -->  |            HANDS            |  Execution
                 +--------------^--------------+
                                | device contract
                 +--------------+--------------+
   Stage 1  -->  |            FRAME            |  Clients + Architecture
                 +-----------------------------+
   Stage 0  -->    the seed: repo, engine, bridge, release
```

Every arrow is a contract; every box is one owner. A stage can start when the
arrow below it is stable, not when the box below is "perfect".

## Who is on stage when

| Weeks | Stages running | Teams entering |
|---|---|---|
| 0 | 0-1 | Architecture, Clients |
| 1-4 | 1-3 | Execution joins; Architecture keeps the gate contracts |
| 4-10 | 4-5 | Tasks, Decision layer, Family |
| 8-16 | 6 | Knowledge, Factory, Delivery, Farm |
| 6-14 | 7 | Platform (in parallel; the bridge already exists) |
| 10-24 | 8 | Product lab (continuous from Stage 3) |

The overlaps are deliberate: a stage starts when its lower contract is stable.
No team waits for a perfect box below it.

## The one-slide version

```
We build the product one capability at a time.
Each capability is one team, one contract, one demo.

answers -> acts -> refuses -> remembers -> decides and asks ->
knows apps -> connects -> proves

Frame  -> Hands -> Loop+Gate -> Memory -> Decision+Family ->
Know-how -> Network -> Proof

At every arrow there is a contract, and at every box exactly one owner.
```

## What this answers

- **People who do not know the product** see the ladder: what the product can do
  after each stage, in plain verbs.
- **People who do not know the teams** see exactly one team enter per stage,
  with the reason it exists and the demo it must produce.
- **Everyone** sees where their part plugs in: the arrow under their box.
