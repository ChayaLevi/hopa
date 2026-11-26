# Family board fixtures

Client-side contracts for the family board. The server never reads them: it stores
board ops as ciphertext and orders them. Every client replays the ops it can read
through the same pure reducer, so these files are what keeps the clients in step.

- Schemas: [`../../schemas/family/op.v1.schema.json`](../../schemas/family/op.v1.schema.json)
  (one op, and `$defs/logEntry`: the op plus what the server attests for its seq) and
  [`../../schemas/family/state.v1.schema.json`](../../schemas/family/state.v1.schema.json)
  (the board one device holds).
- Checker: `python tools/checks/check-family-schemas.py` (shape cases, and the board
  cases' log entries, final states and hashes)

## Files

| File | What it is |
| --- | --- |
| [`seed.state.json`](seed.state.json) | Homemind's demo seed (`seedState` in `server/homemind/seed.ts`, branch `feat/homemind` of `ChayaLevi/digitalmate-web`), at the demo clock 2026-11-11 14:30 `Asia/Jerusalem`, in the v1 shape: each item moved into the scope named by its `visibility` (and the field dropped), the family roster cut to `id`, `name`, `nameZh`, `timezone` and members' `id`, `name`, `nameZh`, `role`, `hasAccount`; reminders, rules, preferences, shopping and the other non-board parts left out. |
| [`schema-cases.json`](schema-cases.json) | Shape cases: an accepted op of every kind, ops and states that must be refused (each says why), and log entries. |
| `board-*.json` | Behaviour cases for the board reducer, grouped by topic (`create`, `ownership`, `done`, `undo`, `stale`, `scopes`, `visibility`, `split`, `time`, `versioning`). Generated, never edited by hand (see below). |
| `time-zones.json` | The family's clock rules from Homemind's `clock.ts`: local time to instant (daylight-saving gaps and overlaps in five zones), instant to local parts, occurrences of weekly items across clock changes, quiet hours and the evening summary time. |
| `time-reminders.json` | Reminders and escalation: Homemind's scheduler ticked every minute over a board, and every alert it delivered, with its stable key. A client's planner, run over the same board at the same minutes, must deliver exactly these. |
| `time-availability.json` | Availability per scope: conflicts, who is unavailable when (per member and per device), and the items after opening the conflicts. |
| [`cipher/board-cipher.v1.json`](cipher/board-cipher.v1.json) | Board op cipher vectors (ADR-0033 D13): the pinned AAD layout (`"hopa.board.v1"`, then household, scope and op id each as a big-endian u32 length and UTF-8, then the key epoch as a big-endian u32) and sealed bytes, made with Python's `cryptography` package, plus places a ciphertext must not open under. Read by `core/backend` (`BoardCipherTest`). |

## Board cases

Every case starts from `seed.state.json` and has:

- `log`: log entries in seq order (`seq`, `scope`, server-attested `memberId` and
  `fromDevice`, and the decrypted `op`);
- `results`: one per entry the reducer reached, `applied` or `rejected` with a
  `code` (`invalid`, `forbidden`, `not_found`, `stale`, `conflict`);
- `stoppedAt` (optional): the seq of an op with an unknown `kind` or a higher `v`.
  Replay stops there, and no later entry has a result (D15);
- `after`: the state of a parent device (both scopes) after the replay;
- `hashes`: `state` for the whole of `after`, `family` and `parents` for
  `after.scopes.family` and `after.scopes.parents`.

A device that holds only `family` (a teen) replays only the `family` entries. It must
reach the same `hashes.family`, with the same results for those entries. The generator
checks this for every case.

How a reducer applies one entry (the order of the checks decides the code):

1. Stop if `v` is not 1 or `kind` is unknown.
2. Reject as `invalid` a malformed `opId` or `at`, and reject as `conflict` an `opId`
   that is already an activity id in the scope.
3. Run the change against the op's own scope only. The items of the other scope do not
   exist for it (D11), so a reference across scopes, in either direction, is `not_found`
   or `invalid`, never `forbidden`. A create must carry its item id.
4. Take the actor from `memberId` and the time from `at`. The new activity's id is the
   `opId`, and each new split part takes the `id` given in the op (D10, D12). A split
   part whose id already exists in the scope is a `conflict`, checked after the
   change's own checks.

A rejected entry leaves the state unchanged. An entry the op schema refuses is always
rejected as `invalid` or stops the replay (the checker enforces this).

**Hash:** lowercase hex SHA-256 of the UTF-8 bytes of canonical JSON. Object keys are
sorted, there is no whitespace, and absent fields are left out. Strings are escaped as
`JSON.stringify` does: `\"`, `\\`, `\b \f \n \r \t`, other control characters as
lowercase `\u00xx`, and everything else as raw UTF-8. Integers are written in decimal.
For these types this is RFC 8785 (JCS). Python's
`json.dumps(v, sort_keys=True, separators=(",", ":"), ensure_ascii=False)` gives the
same bytes.

**Regenerate** (in `ChayaLevi/digitalmate-web`, branch `feat/family-fixtures`; it runs
Homemind's unchanged `applyChange` behind an adapter that applies D10, D11, D12 and D15):

```bash
npx tsx scripts/family-fixtures/generate.mts --out   <hopa>/spec/fixtures/family
npx tsx scripts/family-fixtures/generate.mts --check <hopa>/spec/fixtures/family
```

Each case states the outcome it expects for every step. The generator refuses to write
a case if the core does anything else.

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

## Scope helpers

The move cases in `board-visibility.json` (`move-open-task-to-parents-narrows-candidates`,
`move-task-owned-by-teen-opens-to-parents`, `move-recurring-event-keeps-exceptions`,
`undo-a-move-is-two-undos`) and `derive-busy-event-for-family` in `board-scopes.json` are
the exact ops a client's scope helpers write: a move is a cancel in the old scope and a
create of a copy in the new one; undoing it is two undos, newest first; a busy event
copies only the times and the unavailable members of a parents event into `family`.
`core/family` (`com.hopa.family.scope`) rebuilds these ops and compares them byte for byte.

## Time cases

The reminder cases replace Homemind's server scheduler with a pure planner on every
device (plan decision D5): reminders are a function of the board, so every device agrees.
An answer to a reminder is a board op (confirm, take, done), so it stops the alerts on
every device; "remind me later" is local to the device that pressed it. Quiet hours,
batching and the backup contact are device settings in v1, so the reminder cases run
without quiet hours and the clock rules are checked on their own in `time-zones.json`.

One finding is pinned on purpose: in a repeated hour (clocks going back), Homemind's
`zonedToUtc` does not always pick the earlier instant its comment promises
(`Asia/Jerusalem` 2026-10-25 01:30 gives 23:30Z, the later one). Clients follow the
fixture, so all of them land on the same instant.
