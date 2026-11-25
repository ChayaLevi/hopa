# Family board fixtures

Client-side contracts for the family board. The server never reads them: it stores
board ops as ciphertext and orders them. Every client replays the ops it can read
through the same pure reducer, so these files are what keeps the clients in step.

- Schemas: [`../../schemas/family/op.v1.schema.json`](../../schemas/family/op.v1.schema.json)
  (one op, and `$defs/logEntry`: the op plus what the server attests for its seq) and
  [`../../schemas/family/state.v1.schema.json`](../../schemas/family/state.v1.schema.json)
  (the board one device holds).
- Checker: `python tools/checks/check-family-schemas.py`

## Files

| File | What it is |
| --- | --- |
| [`seed.state.json`](seed.state.json) | Homemind's demo seed (`seedState` in `server/homemind/seed.ts`, branch `feat/homemind` of `ChayaLevi/digitalmate-web`), at the demo clock 2026-11-11 14:30 `Asia/Jerusalem`, in the v1 shape: each item moved into the scope named by its `visibility` (and the field dropped), the family roster cut to `id`, `name`, `nameZh`, `timezone` and members' `id`, `name`, `nameZh`, `role`, `hasAccount`; reminders, rules, preferences, shopping and the other non-board parts left out. |
| [`schema-cases.json`](schema-cases.json) | Shape cases: an accepted op of every kind, ops and states that must be refused (each says why), and log entries. |

## What the schemas cannot check

These rules need the state, so the reducer enforces them and the board fixtures test
them, not the schema cases:

- a change names the item's current `version`, or it is refused as stale (D8);
- an op in `family` never references an id that lives in `parents`, including
  `parentEventId` and an open `reason.eventId` (D11);
- only a parent creates or owns an item in `parents`;
- the actor is the log entry's `memberId`, and the reducer never reads a clock or mints
  an id (D10, D12).

## Changing a contract

The files are versioned. Adding a field or an op kind means a new `v` (ops) or
`schemaVersion` (state), so an older client stops instead of guessing (D15).
