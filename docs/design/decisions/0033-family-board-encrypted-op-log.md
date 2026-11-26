# ADR-0033 - Family board: an encrypted op log with scope keys

- Status: proposed
- Date: 2026-11-25; amended 2026-11-26 (permissions instead of family roles, see
  "Amendment 1")
- Owners: Chaya (family board); review: Amir (lane A)
- Relates to: ADR-0016 (each client owns its loop), ADR-0018 (share the data,
  not the loop), ADR-0022 (backend language and shape), ADR-0026 (household
  content key)

## Context

The family board brings shared family logic to HOPA: tasks, events,
preparations, ownership, reminders, escalation, preferences, rules, shopping
lists. One family is one HOPA household. Every member sees a personal view of
one shared board, on several devices, and some of it must stay with the
parents.

ADR-0026 makes the backend content-blind: it stores ciphertext under one
household key and holds that key only wrapped per device. A board fits that
rule only if the server never needs to read a board item to order, route or
merge it. It also needs two things ADR-0026 does not give:

- **Merging without reading.** Several devices change the board at once,
  sometimes offline. The server cannot merge what it cannot read.
- **A second audience inside one household.** "Parents only" items must be
  unreadable on a teen's device. A server-side filter does not hold that,
  because the household key opens every household ciphertext.

The family logic is a port of an existing TypeScript reducer, and phase S of
the work proved the Kotlin port deterministic (`core/family`,
`BoardDeterminismTest`): two devices that replay the same ops in the same
order reach the same state hash, and a teen device that replays only the
`family` ops reaches the same `family` state as a parent device.

## Decision

1. **An append-only log of encrypted ops per household.** A board change is an
   *op*. A device encrypts the op and appends it. The server gives each op the
   next number in one per-household sequence (`seq`), stores it, and fans out
   `{scope, seq}` to the other devices allowed to read it. Every device replays
   the ops it can read in `seq` order through the same pure reducer
   (`core/family`, no Android, HTTP or crypto inside it). The server never
   merges, parses or logs an op body.

2. **Two scopes, two keys.** Scope `family` is encrypted under the existing
   household key. Scope `parents` is encrypted under a new 256-bit **parents
   key**, wrapped only for parent devices. "Parents only" holds by
   cryptography, not by a filter.

3. **The scope-key wrap reuses ADR-0026's `KeyWrap`** (static-static X25519,
   HKDF-SHA256, AES-256-GCM, the same salt and the same 60-byte blob) with one
   change: HKDF `info` = the UTF-8 bytes of `"hopa.scope.key.wrap.v1"`, so a
   household-key wrap can never be taken for a scope-key wrap or the reverse.
   Wraps live in `scope_key_wraps(household_id, scope, key_epoch, device_id,
   wrapped_key, wrapper_device_id, wrapped_at)`, one row per device, scope and
   epoch.

4. **Wraps go only to verified keys.** A device wraps the parents key only for
   a public key it verified itself: it admitted that device, or the person
   compared a fingerprint on both screens. The server could otherwise insert
   its own public key and receive a wrap.

5. **Ciphertext is bound to its place.** A board op is AES-256-GCM under its
   scope key with a fresh 12-byte IV and AAD =
   `"hopa.board.v1" || household || scope || opId || keyEpoch`. A ciphertext
   cannot be replayed under another op, scope, epoch or purpose (ADR-0026's
   payloads use empty AAD; task bodies keep it).

6. **The reducer is deterministic.** It never reads a clock and never mints an
   id: the proposing device puts `opId`, `at` and every new entity id inside
   the op. Conflicts are first-writer-wins: an op made against an old version
   of an item is rejected by the reducer, identically on every device. Undo is
   itself a version-checked op, never a blind restore.

7. **Scope partition.** A `family` op may never reference an id that lives in
   `parents`; the reducer rejects it on every device. Moving an item between
   scopes is a tombstone in one scope plus a new item in the other. A parent
   device may derive a `family` op from `parents` data (for example "Mom is not
   available Tue 17:00-21:00") without the reason.

8. **The actor is server-attested.** The server records `member_id` and
   `from_device` for each op from the access token. The reducer takes the actor
   from there, never from inside the ciphertext, so a teen device cannot forge
   a parent's approval. `household_id` likewise comes only from the token.

9. **Versioned ops.** Every op carries `v` and `kind`. A client that meets an
   unknown `kind` or a higher `v` stops applying at that seq, keeps the rest
   queued and shows "update required". It never skips an op, so clients of
   different versions cannot diverge. Stored local state carries a schema
   version with migrations.

10. **Reminders are computed on the device.** Reminders and alerts come from
    the board on each device; the server has no scheduler and no alert table.
    A recall is a board change, and each device cancels its own alert by a
    stable key. Lane B's planned reminder pushes are for approvals, not for
    board reminders; the two are not merged.

11. **A minimal backend v1.** Append, ack, fan-out, pull, head, cursor and
    scope keys, nothing else: no pruning, no snapshots, no `E_GONE`. The
    `key_epoch` column is reserved now, always the current epoch in v1, so key
    rotation later needs no wire change.

12. **A separate board log.** The board uses its own tables (`board_heads`,
    `board_ops`, `board_cursors`), not a shared log with lane B's sync v0. Names
    and shape follow sync v0 where they can (`*_cursors`, ciphertext plus
    metadata columns). Who counts as a parent is decided by a permission, not
    a family role (Amendment 1).

### What the server sees

The plaintext metadata is exactly this, and nothing else may be added without a
change to this ADR:

| Where | Plaintext |
| --- | --- |
| Each op | household, `seq`, `scope`, `op_id`, `from_device`, `member_id`, `created_at`, size, body hash, `key_epoch` |
| Gateway frames | `board.op {scope, ct, keyEpoch}` (envelope `id` = `op_id`), `board.op.ack {scope, seq, duplicate}`, `board.changed {scope, seq}` |
| Cursors | per device and scope, the last applied `seq` |
| Wraps | per device, scope and epoch, the wrapped key and who wrapped it |

These fields sit next to ADR-0026's `diag.*` exemption as the only board data
the server reads. What they leak is accepted for v1: **who wrote, when, how
big, and in which scope**. The sequence is shared by both scopes, so a teen
device sees gaps in the `family` seqs where parents wrote `parents` ops.

### The scope rule

A device reads or writes `parents` only if it has a `scope_key_wraps` row for
`parents` at the current epoch. The rule is enforced in the board service and
tested on every route and on `board.op`; a refusal is `E_SCOPE`. Row-level
security stays household-level, as on every tenant table: a device-level policy
would change `Database`, which is out of scope for v1. `board.changed` and the
board wake push go only to devices that may read the scope, never to the
sender.

### Bootstrap

The first parents key in a household is created by a parent device when no
`parents` wrap exists for any epoch, under a Postgres advisory lock on
(household, scope), so two parents cannot bootstrap two keys. If every wrap is
later gone (all parent devices revoked), a new bootstrap creates epoch + 1. The
old `parents` ops then stay unreadable; the API says so and the client must show
it.

### Revocation and permission change

Revoking a device deletes its scope wraps in the same transaction as its device
row, next to ADR-0026's household wrap and token family, and its socket is
closed. A member who loses `board.private` (Amendment 1) loses the `parents`
wraps of all their devices in one transaction, and their sockets are closed.
From then on the server gives them no `parents` op and no new wrap.

### Idempotency

The envelope `id` of `board.op` is the `op_id`. The same `op_id` with the same
body hash and scope returns the stored seq with `duplicate: true` and writes
nothing, so a retry after a reconnect is safe. The same `op_id` with a
different body or scope is `E_CONFLICT`.

### Limits

One op ciphertext is at most 44 KiB raw, so the frame stays under the gateway's
64 KiB cap; the frame check fires first (`E_SCHEMA`). A page of ops is at most
500. Board wake pushes have their own budget, separate from task wakes, and
coalesce to the latest seq per device.

## Consequences

- A teen device holds no parents key, so a server bug, a dump or an operator
  cannot hand it `parents` content it can read.
- Every device keeps a full decrypted copy of the board it may read. A new
  device replays the whole log; with no snapshots this gets slower as the log
  grows. Snapshots are a later, separate ADR.
- Conflicts surface to the person ("someone changed this first") instead of
  being merged silently.
- Clients must ship reducer changes behind a new `kind` or `v`. An old client
  stops and asks for an update rather than diverging.
- Two new error codes join the gateway: `E_SCOPE` (scope not allowed) and
  `E_CONFLICT` (same `op_id`, different body or scope).

### Residuals accepted for v1

- **No key rotation.** The parents key never changes in v1, so a device that
  once held it can read any `parents` ciphertext it obtains later. The server
  stops serving such a device, which is the only barrier.
- **A member who loses `board.private` keeps what they already pulled.** Ops and the key already
  on their devices are outside the server's reach. Rotation through
  `key_epoch` is the later fix.
- **Metadata leaks** as listed under "What the server sees".

## Alternatives considered

- **Server-side merge of plaintext board state.** Rejected: it breaks
  content-blindness outright (ADR-0026).
- **One key with a server-side "parents only" filter.** Rejected: the household
  key opens every ciphertext, so the filter protects nothing from a dump, an
  operator or a server bug.
- **CRDTs instead of a total order.** Rejected for v1: the reducer's rules
  (ownership, version checks, undo) assume one order, and a server-assigned
  `seq` gives it without the server reading content.
- **Server snapshots for fast first sync.** Rejected for v1: a device cannot
  verify a snapshot the server supplies without replaying the log anyway.
- **One generic household log shared with lane B's sync v0** (a `stream`
  column for `chat` and `board`). Not chosen now: it would tie the board to
  lane B's schema and timing. It can be revisited once both exist.
- **A device-level row-level security policy for `parents`.** Not chosen: it
  needs a per-device setting in `Database`; the scope rule in the board service,
  with tests on every route, is enough for v1.

## Amendment 1 (2026-11-26): permissions instead of family roles

The team wants a household to be a **group** (a family is one kind; `households`
will be renamed later), where some members have rights others do not. So "parent"
is no longer a role stored on the server: it is a **permission**, and clients
show it with family words.

- `member_permissions (household_id, member_id, permission)` (V7) holds a set of
  permissions per member, with RLS enabled and forced like every tenant table.
  Two exist:
  - `board.private`: read and write the `parents` scope and hold its key.
    Clients show its holders as parents and everyone else as children.
  - `group.manage`: change the members' permissions.
- The member who creates a household gets both, in the transaction that creates
  it (sign-up and pairing a new household). A member who joins gets none until a
  manager grants them.
- `GET /v1/members` lists the group's members with their permissions;
  `PUT /v1/members/{id}/permissions` sets them, only for a caller holding
  `group.manage`, and never leaves a group without a manager.
- Every rule above that says "parent" means "holds `board.private`": only such a
  device bootstraps, hands out or receives the parents key.
- Losing `board.private` drops the member's `parents` wraps under the same
  advisory lock as wrap writes, so a wrap racing the change is either written
  first and dropped, or refused.
- V7 replaces V6's `members.family_role`: a `parent` becomes a `board.private`
  holder, each household's first owner gets both permissions, and the column is
  dropped. `teen` is gone: there is "with the private scope" and "without it";
  a small child without a phone is a member with no account.
- `members.role` (`owner`/`member`) is unchanged.
