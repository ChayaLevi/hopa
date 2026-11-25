# family - the family board

One family is one HOPA household, and every member's device holds the same board.
The server stores the board as an append-only log of encrypted ops and gives them one
order (`seq`). Each device decrypts the ops it can read and replays them, in that order,
through the reducer in this module. Devices that replay the same ops reach the same
state.

This module is Layer 1: it depends on `contracts` only. It has no clock, no random, no
crypto, no HTTP and no Android.

## Packages, bottom first

| package | what lives here | depends on |
|---|---|---|
| `model` | `BoardState` (scopes `family` and `parents`, each with items and activity), `Item`, `Ownership`, `Activity`, `BoardOp` (one class per op kind), `LogEntry` | contracts |
| `board` | `BoardReducer` (one entry → applied, rejected or stopped), `BoardReplay` (a log on a device that holds some scopes), `BoardHash`, `BoardJson` | model |

## Rules

1. **The contract is in `spec/`.** The shapes come from
   `spec/schemas/family/{op,state}.v1.schema.json`. The behaviour comes from
   `spec/fixtures/family/board-*.json`, which are generated from Homemind's core.
   `BoardFixturesTest` replays every case. If a fixture and this code disagree, the
   fixture wins: stop and ask, never edit one side to match.
2. **No clock, no minted ids (D10).** Every time comes from the op's `at`, and every id
   from the op (`opId` becomes the activity id; items carry their own ids).
3. **The actor is the server's (D12).** The reducer takes `LogEntry.memberId`, never a
   field inside the op.
4. **One op, one scope (D11).** An op sees only the items and activity of its own scope.
   A reference across scopes finds nothing.
5. **Unknown means stop (D15).** An unknown `kind` or a `v` other than 1 stops replay at
   that seq. Nothing after it is applied.
6. **The order of checks is the contract.** It decides which code a bad op gets. See
   `BoardReducer` and `spec/fixtures/family/README.md`.
