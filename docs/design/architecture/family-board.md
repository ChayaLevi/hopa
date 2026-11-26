# Family board: the server side

The family board is one household's shared list of tasks, events and shopping,
kept as an **append-only log of encrypted operations**. The server orders and
relays the ops; it never reads them. Every device replays the ops it may read,
in order, through the same pure reducer (`core/family`), so they all reach the
same state. The decisions are in
[ADR-0033](../decisions/0033-family-board-encrypted-op-log.md); this page shows
how the backend serves them.

## Two scopes, two keys

| Scope | Encrypted with | Who reads and writes it |
|---|---|---|
| `family` | the household key (ADR-0026) | every device of the household |
| `parents` | the parents key, epoch `n` | only a device holding a wrap of the parents key at the current epoch |

The server enforces the `parents` rule in `BoardService` (row-level security
stays household-level, as on every table). The cryptography enforces it too: a
teen device never receives a wrap, so it could not decrypt a `parents` op even
if it got one.

## Appending an op

```
device ──board.op {scope, ct, keyEpoch}──▶ gateway (GatewayRoutes)
                                              │ EnvelopeValidator: shape, ct 28 B..44 KiB
                                              ▼
                                           BoardGateway.append
                                              │ span "board.append"
                                              ▼
                                           BoardService.append ── scope rule, epoch rule
                                              │
                                              ▼
                                           BoardStore.append ── next seq (board_heads row lock),
                                              │                 op id idempotency (E_CONFLICT)
                                              ▼
device ◀──board.op.ack {scope, seq, duplicate}
                                              │ span "board.fanout" (new ops only)
                                              ├─▶ online devices that may read: board.changed {scope, seq}
                                              └─▶ offline ones with a push token: PushPort.wakeBoard
```

- The envelope `id` is the op id. The same op id and body again is acked with
  the stored seq and `duplicate: true`, and tells nobody. The same op id with
  another body or scope is `E_CONFLICT`.
- The actor is the token's device and member (D12); nothing in the frame names
  a household, member or device.
- Acks and errors come back in the order the ops were sent on the socket.
- `seq` is one counter for the household, shared by both scopes.

## Pulling

| Route | Answer |
|---|---|
| `GET /v1/board/ops?scope=&after=&limit=` | ops above `after`, ascending, at most 500, with `seq, opId, fromDevice, memberId, at, keyEpoch, ct` and `hasMore` |
| `GET /v1/board/head` | the latest seq of every scope this device may read |
| `PUT /v1/board/cursor` | `{scope, seq}`: what this device has applied |

A device that wakes (`board.changed` or a push) pulls from its cursor until
`hasMore` is false, applies, and moves its cursor.

## Wakes

Board wakes are data-only FCM messages `{type: board.changed, scope, seq}`.
`GuardedPush` gives them their own budget (six a minute per device), separate
from task wakes, and coalesces them: inside ten seconds of a device's last board
wake a new one is held and replaced by any later seq; a flush every two seconds
sends the latest once the window has passed.

## Scope keys

| Route | Answer |
|---|---|
| `GET /v1/scope-keys/parents` | this device's wrap at the current epoch and the wrapper's public key; 404 with `currentEpoch`, `bootstrapEpoch`, `lostEpoch` otherwise |
| `POST /v1/scope-keys/parents` | `{deviceId, wrappedKey, keyEpoch}`: store a wrap for a parent's device |

`ScopeKeyRules` decide, on a snapshot read under a Postgres advisory lock on
(household, scope):

1. The target must be a live device of the household (`E_TENANT`), and both the
   caller and the target must belong to a member whose family role is `parent`
   (`E_SCOPE`).
2. With no wrap in the household, a parent's device bootstraps the key for
   itself at `bootstrapEpoch`, one above any epoch used before. Of two concurrent
   bootstraps one wins; the other is `E_CONFLICT`.
3. Otherwise only a device holding the current epoch may wrap, at that epoch.

If every wrap is gone (all parent devices revoked or demoted), the next
bootstrap creates a new epoch and the ops of the old one stay unreadable; the
404 and the bootstrap answer say which epoch was lost, and the client must show
it.

## Losing the parents scope

- **Revoking a device** deletes its scope wraps in the same transaction as its
  device row, tokens and household wrap; its socket is closed.
- **A member who stops being a parent** loses the `parents` wraps of all their
  devices in one transaction (`FamilyRoles.setFamilyRole`), and their sockets are
  closed without a tombstone, so they reconnect at once with `family` rights
  only. No members or roles API calls it yet: lane B's role changes must.

## What the server stores

| Table | Plaintext | Ciphertext |
|---|---|---|
| `board_heads` | household, last seq | |
| `board_ops` | household, seq, scope, op id, device, member, key epoch, size, body hash, time | `body` |
| `board_cursors` | household, device, scope, last seq, time | |
| `scope_key_wraps` | household, scope, key epoch, device, wrapper device, time | `wrapped_key` |

All four have forced row-level security with the household policy. A test dumps
them after a run and finds no trace of the plaintext the ops were sealed from.
