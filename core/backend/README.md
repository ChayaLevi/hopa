# backend - the link to the server

The client side of the backend: task delivery over the socket, the envelope
format, pairing the device, and fetching the knowledge bundle. No Android, no
UI: the sidecar and the app both use it.

## Packages, bottom first

A package may name only packages in an earlier layer; inside a layer the order is
alphabetical. Three packages, no folder with a single file.

### Layer 0 - the wire

| package | what lives here | depends on |
|---|---|---|
| `wire` | `BackendEnvelope`, `BackendCodec`, `BackendReplies`: what goes on the socket and how it is read | contracts |

### Layer 1 - identity

| package | what lives here | depends on |
|---|---|---|
| `pairing` | `BackendPairing`, `BackendPairingFailure`, `BackendCredentials`, `BackendUrls`, `PairingLink`, `PairingLinks`, `X25519Keys`: proving this device is this device | contracts |

### Layer 2 - the connection and what runs over it

| package | what lives here | depends on |
|---|---|---|
| `link` | `BackendClient`, `BackendLink`, `BackendListener`, `BackendState`, `BackendClose`, `ProcessedTaskLog`, `CatalogClient`: the socket, its lifecycle, the dedup of delivered tasks, and the knowledge fetch | pairing, wire |

`ProcessedTaskLog` and `CatalogClient` sit here because both exist only on top
of a live connection; giving each its own folder would mean two folders with one
file.

## Rules

1. **No Android.** `checkNoAndroidImports` enforces it.
2. **No UI and no turn.** This module moves bytes and proves identity; what to do
   with a task is `agent`'s business.
3. **Pairing is not optional.** A socket that did not complete `pairing` does not
   deliver tasks.
4. **Every frame is a typed envelope.** `wire` is the only place that knows the
   JSON shape; nothing else parses a payload by hand.
5. **A task is executed once.** `ProcessedTaskLog` survives a reconnect, so a
   redelivery is dropped before it reaches the turn.

## Tests

```
./gradlew :backend:test :backend:ktlintCheck
```

The codec and pairing tests replay the same fixtures as `spec/backend.v1.yaml`.
