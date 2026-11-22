# ADR-0025 - One content key per household, wrapped per device

- Status: accepted
- Date: 2026-11-22
- Owners: Amir (lane A), Batya (lane B)
- Relates to: ADR-0016 (each client owns its loop), ADR-0018 (share the data,
  not the loop), ADR-0022 (backend language and shape)

## Context

ADR-0022 fixes the backend as a Kotlin service over Postgres. It does not say
what the service may read. Week 1 left the household key model undecided, and
sync v0 cannot ship without it: two devices in one household must read the same
task bodies and sync rows, while the server must stay content-blind. A task
payload already travels as `bytea`, so the schema is ready; only the key
ownership rule is missing.

The constraint is not storage, it is trust. The console, the operators and the
service itself must not be able to turn a database dump into a family's
history. Whatever holds the key must therefore be a device, and the server may
hold only ciphertext it cannot unwrap.

## Decision

**One 256-bit symmetric content key per household, generated on the first
phone at sign-up.** The server stores it only wrapped.

1. **Storage.** The wrapped key lives in
   `household_key_wraps(household_id, device_id, wrapped_key bytea, wrapped_at)`,
   tenant-scoped under row-level security like every other tenant table. One
   row per admitted device; no row means the device holds no key. The server
   never sees the content key itself.

2. **Wrap.** Wrapping uses the device key pairs already collected at pairing:
   X25519 ECDH between the wrapping device's private key and the receiving
   device's public key, then HKDF-SHA256 with salt
   `SHA-256("hopa.v1.keywrap" || recipientPublicKey)`, info
   `"hopa.content.key.wrap"` and a 32-byte output (the key-encryption key), then
   AES-256-GCM over the content key. A 12-byte random IV is prepended to the
   ciphertext and the 128-bit tag is appended, so a wrapped 32-byte key is 48
   bytes. Because both device keys are static and known at pairing time, no
   ephemeral public key travels alongside the wrap.

3. **Payloads.** Task bodies and sync rows are AES-GCM ciphertext under the
   content key, produced and consumed on devices only. `tasks.payload` and
   `tasks.body` store bytes; the server never parses, re-encrypts or re-wraps
   them.

4. **Recovery code.** The owner may generate a recovery code: the content key
   wrapped under a key derived with Argon2id (`m = 64 MiB`, `t = 3`, `p = 4`)
   over a 16-byte random salt, stored as `salt || ciphertext`. Week 2 ships the
   primitive only, wrap, unwrap and derivation; the endpoints and phone UI land
   with later admission work.

5. **`diag.*` exemption.** Server-authored diagnostics, `diag.ping`,
   `diag.state` and `diag.reset`, are the **only** plaintext payloads. They are
   admin-only and every send is audit-logged. The exemption is part of the key
   model, recorded here so it survives ciphertext-only enforcement for
   everything else.

6. **Revocation.** Revoking a device deletes its `household_key_wraps` row and
   revokes its token family. It is never handed a wrap again and its refresh
   path is refused, so it cannot decrypt anything new: no route to fresh
   ciphertext and no route to a fresh wrap. Ciphertext or key material already
   on the device is outside the server's reach, which is why the wrap row and
   the token family are revoked together.

## Consequences

- Losing every device loses the household's history unless the recovery code
  is kept. This is the deliberate cost of content-blindness; the recovery code
  is the escape hatch and must be presented as such.
- Adding a device is one wrap. Admission needs an authenticated device that
  already holds the key; there is no re-encryption of history and no key
  rotation event.
- The admin console cannot decrypt by construction, so its diagnostic view is
  metadata plus the `diag.*` payloads it authored. Support cannot read a task
  body, by design.
- `accounts` and `admins` sit outside tenancy but hold no keys, so a console
  compromise yields identity data, not content.
- The wrap table grows one small row per device, and its RLS policy is the same
  one every tenant table uses; the absence of a row is a revocation, not an
  error to repair by hand.

## Alternatives considered

- **One key per thread.** Rejected: every device change re-wraps every thread
  the household owns, so adding or revoking a phone becomes a re-wrap storm
  proportional to history, and partial failure leaves threads unreadable.
- **A server-held household key.** Rejected: it breaks content-blindness
  outright. A dump, an operator or a console bug would expose every body the
  server can already reach.
- **A password-derived content key.** Rejected: a password change or a
  recovery would re-encrypt every payload, and a forgotten password would
  destroy history that a separate recovery code can still save.
