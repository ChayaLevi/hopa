# ADR-0027 — Approval and choice cards are A2UI 1.0 surfaces

- Status: proposed
- Date: 2026-11-22
- Owners: backend lane A (live path); Android, Windows and HarmonyOS client leads
- Relates to: ADR-0016 (clients own the loop), ADR-0018 (share the data and the
  fixtures, not the loop), ADR-0021 (Windows client stack), ADR-0024 (device
  identity and approval provenance), the "A2UI relay" row in
  `docs/project/plan/backend/weekly-plan.md`, and the plan in
  `docs/project/plan/a2ui-plan.html`

## Context

HOPA asks the person two kinds of question during a run. One is an
**approval** (`ApprovalRequest`, answered by `ApprovalGateway`). The other is a
**choice** (`Decision.Ask(answer, options)`, which reaches Android's chat as
chips). Each client draws them with its own hard-coded UI:

- Android: `ApprovalSheet` in Compose
- HarmonyOS: `ApprovalSheet.ets` and the laptop's `LaptopApprovals`
- Windows: the host window behind the sidecar's `approval.request` /
  `approval.result`

Since ADR-0024 an approval can cross devices. The gateway routes
`task.needs_approval` with a typed `approval` object in its body, and the
answer comes back as `task.result` with `body.approval.allow`. Week 5 adds
family approvals, where a parent's phone answers for a child's device, and
the roadmap names an "A2UI relay": cards drawn on whichever device is in hand.

That needs one card format that:

1. the three native stacks (Compose, ArkUI, WinUI) can all draw natively;
2. rides inside the gateway `body`, which becomes ciphertext and which the
   server never renders;
3. cannot run code, because a card can arrive from another device and, later,
   from an outside agent.

A2UI (Agent-to-User Interface, an Apache-2.0 project started by Google, now
at `github.com/a2ui-project/a2ui`, spec at `a2ui.org`) is exactly that. It is
a declarative JSON description of UI. Components come from a catalog the
renderer owns, values bind through JSON Pointer paths, and user actions come
back as data.

Upstream state on 2026-11-22 (`specification/versions.json`):

| Version | Status |
| --- | --- |
| v0.8 | deprecated |
| v0.9, v0.9.1 | previous (closed) |
| v1.0 | current and stable; it was earlier called 0.10 |

1.0 was created on 2025-11-20 and last updated on 2026-06-08. The last
upstream commit touching `specification/v1_0/` is
`d4f2761b4629028b7995fdd861a7792eeba529ff` (2026-11-19).

## Decision

### 1. Pin A2UI 1.0, the current upstream version

`spec/a2ui/VERSION` holds `1.0`. This is the exact wire value: every
message's required `"version"` field is the string `"1.0"`, not `"v1.0"`. A
receiver refuses any other value with an `error` message. Moving to a later
version takes a new ADR, plus a codec change and fixture updates in one pull
request.

v1.0 replaces v0.9 because v0.9 is closed upstream and gets no fixes. v1.0 is
also the version outside agents will speak (the roadmap's MCP/A2A gateway
row). No HOPA code depended on the version yet, so the switch cost nothing.

### 2. The 1.0 subset we use

The schemas are `agent_to_renderer.json`, `renderer_to_agent.json` and
`common_types.json` in upstream `specification/v1_0/json/`. Each message has
`"version"` and exactly one payload key.

**Agent to renderer** (the device asking):

- `createSurface`: requires `surfaceId`. We always send `catalogId`;
  `sendDataModel` is absent or `false`. Components and the data model may be
  sent inline, and HOPA's builders send a whole card as one `createSurface`.
- `updateComponents`: `surfaceId` and `components`.
- `updateDataModel`: `surfaceId`, `value` (required) and `path` (default
  `/`). A `null` value deletes.
- `deleteSurface`: `surfaceId`.

**Renderer to agent** (the device answering):

- `action`: `name`, `surfaceId`, `sourceComponentId`, `timestamp` and
  `context`.
- `error`: either the validation shape (`code` of `VALIDATION_FAILED`,
  `UNALLOWED_PARENT` or `UNALLOWED_CHILD`, plus `path`, `message` and
  `surfaceId`) or the generic shape (`code`, `message` and `surfaceId`).

**Components** are a flat list of `{"id", "component", …props}`. Ids are UAX
#31 identifiers: `allowButton` is valid, `allow-button` is not. Exactly one
component has the id `root`. Containers use `child` or `children` (an array
of ids). Bindings are `{"@path": "/…"}` JSON Pointers. A button's action is
`{"event": {"name": …, "context": {…}}}`.

**Surface ids** are unique for the renderer's lifetime (reusing one is an
`INTEGRITY_ERROR`). HOPA uses `appr_<task>_<n>` for approvals and
`ask_<turn>_<n>` for choices.

**Not used in v1, and refused by receivers:**

- the function-RPC messages: `callRendererFunction`, `agentFunctionResponse`,
  `callAgentFunction` and `rendererFunctionResponse`;
- `functionCall` actions and `@call`, so no catalog functions, including
  `openUrl` and `formatString`;
- collection templates, `@checks` and per-component `@catalogId`;
- `event.userMessage`;
- `sendDataModel: true` and inline catalogs.

### 3. HOPA owns its catalog

Catalog id: `https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json`
(`spec/a2ui/hopa-catalog.v1.json`). As 1.0 asks, `$id` and `catalogId` are
set to that same value, and `"protocolVersion": "1.0"`. It is a narrowed
subset of the 1.0 basic catalog: Text, Row, Column, Card, Button, Divider and
Icon. On top of that it adds one HOPA component, `PaymentNotice`, which shows
the amount, the merchant, the consequence and an acknowledgement. Every
renderer must draw it at full size. It uses 1.0's `allowedParents` and
`allowedChildren`:

- a `Card` is only ever the root;
- a `PaymentNotice` sits only directly in a `Column`;
- a `Button` holds only a `Text` or an `Icon`.

**Interaction stays out of the data model.**

- A choice is one `Button` per option, and its event carries the option as
  `context.value`. The basic catalog's `ChoicePicker` isn't used.
- The payment acknowledgement belongs to `PaymentNotice`, and the basic
  catalog's `CheckBox` isn't used. A renderer keeps the approve button
  disabled until the acknowledgement is ticked. That state is local and is
  never sent back.

This keeps `sendDataModel: false` and makes every answer a single `action`.

Text is drawn as plain text, never as Markdown.

Some basic-catalog components stay out because a question card doesn't need
them and each one widens the attack surface:

- Image and Video, because remote URLs leak to the network;
- TextField and DateTimeInput, because they're free-form input;
- CheckBox and ChoicePicker, because they need two-way binding;
- AudioPlayer, Modal, Tabs, Slider and List.

**Two strict receiver rules.** HOPA is stricter than upstream here:

- **Bindings must resolve.** Every binding must name a string in the data
  model when a surface is checked. A sender therefore sends data before, or
  together with, the components that use it, so an amount can never render
  blank. Upstream would render a missing value as empty.
- **The first problem wins.** A receiver reports the first problem in
  message order, so every renderer returns the same `error` for the same
  message.

**Capabilities.** A device that can render cards advertises 1.0's
`a2uiRendererCapabilities` (`{"1.0": {"supportedCatalogIds": ["https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json"]}}`)
in its `device.hello` body. A sender attaches A2UI only for a target that
advertised it. Until the gateway tells one device what another advertised
(it doesn't today), a sender attaches the card every time; a device that
doesn't know `a2ui` ignores it, so this is safe, only wasteful.

### 4. Transport: inside the existing envelope, no new types

- **Asking.** A `task.needs_approval` body keeps its typed `approval` object
  and gains an optional `a2ui` array, which holds the 1.0 messages for the
  card in send order. A device that doesn't know `a2ui` ignores it and draws
  from `approval` as it does today. The schema's "unknown fields are ignored"
  rule makes this a minor, compatible change.
- **Answering.** `task.result` keeps `body.approval.allow` and gains
  `body.approval.action`, which holds the A2UI `action` message for the tap.
  The acting device takes the answer from `allow` and checks that
  `action.name` agrees with it (`approve` means true and `deny` means false)
  and that `action.surfaceId` is the surface it sent. Any disagreement counts
  as **deny** (`ApprovalSurfaces.answered`). An answer with no `action` is
  from a client without A2UI and is read from `allow` alone.
- **Refusing a card.** A receiver that refuses a card draws nothing and
  answers `allow: false`, with the A2UI `error` message in
  `body.approval.error` when the card named a surface.
- **Choices.** A choice card uses the same format locally from phase 2 on.
  Sending a choice to another device needs an envelope type the gateway does
  not have yet. That is a separate decision; this ADR fixes only the format.

### 5. Safety rules (non-negotiable)

- **The model never writes an approval card.** Approval and payment surfaces
  are built only by deterministic code from `ApprovalRequest`, in a UI-free
  builder in `core/contracts`. No prompt, recipe or model output can author,
  patch or restyle one.
- **The typed request is the source of truth.** A card shows values copied
  from the `approval` object. The acting device verifies the pending action
  and never trusts anything from the card it gets back. A renderer refuses
  an approval surface that lacks a `PaymentNotice` (when `isPayment`), an
  `approve` button or a `deny` button.
- **A sent card is checked against the request.** The receiver draws the
  card it was sent, not one it rebuilds, so the sender owns the layout and
  wording. Before drawing, it checks what consent depends on
  (`ApprovalSurfaces.matches`):
  - the title and body are shown;
  - on a payment, the `PaymentNotice` shows the request's amount, merchant
    and consequence, and HOPA's own acknowledgement;
  - a card that isn't a payment has no `PaymentNotice`, and still shows any
    amount and merchant;
  - the buttons read "Allow" and "Deny" and carry no accessibility text of
    their own.

  A card that fails any check is refused. The cases are in
  `spec/fixtures/a2ui/surfaces.json` (`receivedCases`).
- **Validation before rendering.** Every renderer checks each message against
  the pinned schema and the HOPA catalog: the version is `"1.0"`, components
  are known, ids are valid, the id graph is a tree reachable from `root`, the
  parent and child rules hold, bindings resolve, and size limits hold. A
  message that fails is not drawn and is answered with an `error` message.
- **The gate does not move.** A remote tap is input, not permission:
  `ActionExecutor` on the acting device still runs `guards → payment approval
  → execute → verify → record` (CLAUDE.md golden rules 1 and 2). Timeouts,
  late answers and malformed answers all mean deny. No flag, setting or test
  hook makes a remote answer count without these checks. Week 5's signed
  decisions add to these checks and never replace them.
- **The body is private.** Cards carry amounts and merchants, so they travel
  only inside the encrypted `body`. The server routes them and never parses
  them.

### 6. Our own code, the upstream idea

We write our own codec, validator and renderers in Kotlin, ArkTS and C#. No
upstream renderer targets our stacks. `NOTICE` credits A2UI for the format.
Unmodified upstream 1.0 schemas and the basic catalog are vendored under
`spec/a2ui/upstream/` at the pinned commit, with the Apache-2.0 licence and
their hashes beside them (`UPSTREAM.md`). They are used only by
`tools/checks/check-a2ui.py`, to prove that the HOPA catalog and every accepted
fixture are valid upstream A2UI 1.0. No HOPA code loads them.

## Consequences

- One card format for all three clients, with shared fixtures in
  `spec/fixtures/a2ui/` (ADR-0018). Each renderer covers a small catalog,
  roughly ten components, not the whole basic catalog.
- Old and new clients interoperate. Today's typed `approval` object and
  `allow` boolean stay, and capabilities in `device.hello` tell a sender who
  can render. The A2UI fields can roll out one client at a time.
- The gateway schema and router need no change for approvals. A later choice
  envelope will.
- We track the current, stable upstream version. Outside agents that speak
  1.0 can target our catalog once the gateway lets them.
- 1.0 is wider than what we use (function RPC, multiple catalogs, templates).
  Our receivers refuse every part outside the subset, so a newer sender can't
  widen what HOPA draws.
- The local approval sheet goes through the same path as a card from another
  device: build, encode, decode, check, then draw. A request that can't
  become a valid card is denied, not drawn from the raw request. A real
  approval request is a few hundred bytes, far below the 32 KiB message limit.
- On the PC → phone path the Kotlin sidecar builds the card and checks the
  answer. The C# window only carries the card to the phone and the tap back,
  so the rule exists once, in `core/contracts`.
- On Windows the window checks the sidecar's card with a C# port of the same
  rules (`Hopa.Windows/Cards/`) and draws it in WPF. The port replays the
  shared fixtures, so it refuses exactly what the phone refuses. A question
  whose card is missing or fails is denied without a window.
- HarmonyOS is frozen (ADR-0020), so it has no card renderer. Its renderer is
  part of any revival, together with the conformance adapter ADR-0020 already
  requires.
- Three renderers have to be kept in step. The fixture suite is the contract,
  and a client that fails it doesn't ship the A2UI path.

## Alternatives considered

- **Keep hand-coded sheets and send only the typed `approval` object.**
  Rejected as the long-term format. It works for one question type, but each
  new card (choices, family approvals, results) would mean new UI code on
  three stacks and no common format for outside agents. The typed object stays
  as the source of truth regardless.
- **HTML in a web view, the "MCP Apps" style.** Rejected. It runs code that
  arrives from another device, it doesn't look native on three stacks, and it
  breaks the rule that bodies are data.
- **Our own card JSON.** Rejected. It would reinvent A2UI's surface, catalog
  and binding ideas without interop with outside agents.
- **A2UI v0.9.** Rejected. It is closed upstream, gets no fixes, and would
  force a migration before outside agents could send cards. An earlier draft
  of this ADR pinned it and was changed before any code depended on it.
- **A2UI v0.8.** Rejected because upstream has deprecated it.
- **The receiver draws whatever card arrives, after the approval rules.**
  Rejected. Those rules prove an Allow, a Deny and a notice exist, not that
  the notice shows the right amount or that "Allow" is on the approve button.
- **The receiver rebuilds the card from the typed request and refuses any
  difference.** Rejected. It is safe, but every wording change on the sender
  would make older receivers deny every approval until they update. The
  card would become a checksum, and HarmonyOS would need a builder whose
  output matches the Kotlin builder exactly.
- **New envelope types (`ui.surface`, `ui.action`) for approvals.** Rejected.
  `task.needs_approval` and `task.result` already exist, are idempotent and
  traced, and carry provenance (ADR-0024). Adding A2UI to their bodies costs
  no new routing.
