# How HOPA is built: one story, seven teams

Status: **narrative for staffing and onboarding.** Written 2026-11-14. It tells
the same architecture as [`teams.md`](teams.md), but as the life of one request,
so each team can see where it enters and what it hands on.

Read this first, then the team table and the edges in [`teams.md`](teams.md).

## The promise

A person asks for something in their own words. HOPA does it on their devices,
asks before anything irreversible, shows what it did, and gets better at it.

Everything below is one trip through that sentence.

## The cast

| Team | Enters when... |
|---|---|
| Architecture and security | the story needs a word or a rule that more than one team uses |
| Client applications | a person touches the product |
| Tasks and notifications | a request has to survive longer than one screen |
| Family and approvals | someone other than the asker must say yes |
| Decision layer (future; see LAIX below) | a step needs an explicit "act / ask / wait / verify" |
| Execution and verification | something must happen on a real device |
| App knowledge | a step needs to know how one app works |
| Knowledge factory and delivery | that knowledge must be produced, signed and shipped |
| Verification farm | knowledge or a device path must be re-checked |
| Product lab | someone must decide what to fix and prove the effect |
| Platform and integrations | two devices or services must find each other |

## Act 0 - the words (Architecture)

Before anyone ships a screen or a service, Architecture writes the nouns down:
what a request is, what a step is, what an approval is, what a record is.
Contracts live in `contracts/`, one copy, versioned, with fixtures.

This act has no demo. Its output is that every later act can be built twice
(phone and PC) without inventing a second vocabulary.

## Act 1 - a request is born (Client + Tasks)

The person types, speaks, shares a photo, or drops a file.

- **Client applications** turns that into a request: the text, the attachment,
  the profile (Easy, Kid, Power), the device.
- **Tasks and notifications** gives the request a life: an id, a state, an
  owner, a due time, and a place to resume. A request that is not finished
  becomes an open loop, not a lost message.

Hand-off: the task says "this is what the person wants; here is what we already
know".

## Act 2 - the decision (Decision layer + Family)

This is the moment the product becomes trustworthy instead of clever.

The model proposes options; it does not get the last word. The decision layer
picks one verb, explicitly:

```
wait     not yet - the time is wrong
observe  look again before acting
guide    tell the person what to do
ask      a human must confirm
act      do it
verify   check that it actually happened
escalate hand over to a person
```

Each decision carries a reason code, a confidence, a score, whether approval is
needed, whether it is reversible, and what the undo would be.

- **Family and approvals** supplies the rules: who may approve this, how much
  attention the person has left today, which promises are still open.
- The decision layer carries those commitments and the attention budget into the
  next decision, so HOPA does not ask five times or forget what it promised.

Hand-off: "act on this step, under these conditions".

## Act 3 - the step (Execution + App knowledge)

**Execution** performs one step on one device and proves it:

- on the phone through the bridge or the accessibility service;
- on the PC through UI Automation;
- always with before/after evidence.

**App knowledge** tells execution how: which control, which intent, which
postcondition. Knowledge is a signed, versioned recipe - step by step, with the
check that says it worked.

If the recipe is missing, the step fails honestly and the failure becomes a
request for knowledge.

Hand-off: "this step is done or failed, with the evidence and the record".

## Act 4 - the proof (Verification + Records + Product lab)

**Execution and verification** writes a record: action, classification,
approved, executed, verified, error, and a hash that chains to the previous one.

**Product lab** reads those journals and answers the only questions that matter
to the next sprint:

- which steps fail most, and why;
- how much time a request really takes;
- whether approvals are correct and not exhausting.

Its output is decisions and numbers, not a test report.

Hand-off: "fix this first; these numbers are true".

## Act 5 - learning (Knowledge factory, delivery, farm)

When a recipe is missing or breaks:

- **Knowledge factory** produces a candidate (models propose, humans review).
- **Verification farm** re-checks it on the app matrix, including the new app
  version.
- **Knowledge delivery** signs the version, rolls it out gradually, and can roll
  it back.

The client never builds its own mapping factory; it consumes signed packages.

Hand-off: "this app is supported again, on this version".

## Act 6 - the network (Platform)

**Platform and integrations** carries the acts: pairing, tokens, the bridge, the
relay, the API between devices. It is under every act and owns none of their
semantics.

## Epilogue - the loop closes

The person sees the answer and the receipt. The task closes. The commitment
settles. The record joins the journal. The lab learns. The next request starts
from a better place, and every act has one owner who can show their part.

## The seams, in story order

| When | One team hands | To | The artifact |
|---|---|---|---|
| A request is captured | Client | Tasks | the request |
| The request needs a decision | Tasks | Decision layer | situation, options, commitments |
| The decision is "ask" | Decision layer | Family | who approves, attention left |
| The decision is "act" | Decision layer | Execution | one step |
| A step needs know-how | Execution | App knowledge | "how do I do X in app Y?" |
| Knowledge is missing | App knowledge | Factory | a knowledge request |
| A recipe is ready | Factory | Delivery | signed candidate |
| Delivery ships | Delivery | All clients | version + pinned apps |
| A step runs | Execution | Product lab | journal entry |
| Measurements are done | Product lab | Everyone | "fix this first" |
| Any two devices talk | Platform | Everyone | transport |

## Timeline

```
Weeks 1-2   Architecture: the decision, task and recipe contracts
Week 3      Clients finish their gates; Tasks skeleton; Knowledge one app
Weeks 3-12  One request travels the whole story; weekly demo per act
Week 12     End-to-end check: person -> decision -> step -> proof
Week 24     Keep, park or expand each act
```

## Where LAIX fits

LAIX is a decision layer for agents: one contract for the decision, explicit
verbs (`wait`, `observe`, `guide`, `ask`, `act`, `verify`, `escalate`), reason
codes, commitments, attention budget, approvals, receipts, and a continuity
benchmark. It is Python and TypeScript, training-free, and the host keeps
execution and storage.

It maps onto Act 2 almost exactly. The recommended use is **the contract and the
policies, ported into the client engine**, not a Python runtime beside the loop:
the same decision semantics must hold on Android and Windows, and the clients
are Kotlin. The Product lab can run LAIX itself first, on HOPA's episodes, to
prove the value before anyone ports it.
