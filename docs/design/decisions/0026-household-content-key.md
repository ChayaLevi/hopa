# ADR-0026 - One content key per household, wrapped per device

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

2. **Wrap.** Wrapping uses the X25519 key pairs already collected at pairing.
   Public keys are the **raw 32-byte RFC 7748 u-coordinate** (little-endian
   octets), never base64 text. The
   wrapping device computes the static-static X25519 ECDH shared secret with
   the receiving device's raw public key, then derives a key-encryption key
   with HKDF-SHA256: IKM = the 32-byte shared secret; salt =
   `SHA-256("hopa.v1.keywrap" || recipientPublicKey)`, where the first term is
   the ASCII/UTF-8 bytes of the literal and the second is the raw 32-byte
   recipient public key; info = the UTF-8 bytes of `"hopa.content.key.wrap"`;
   output = 32 bytes. The content key is then sealed with AES-256-GCM under that
   key-encryption key, using a 12-byte random IV, a 128-bit tag and **empty
   AAD**. The wrapped blob is `IV(12) || ciphertext(32) || tag(16)`, **60
   bytes**. Because both device keys are static and known at pairing time, no
   ephemeral public key travels alongside the wrap.

3. **Payloads.** Task bodies and sync rows are AES-GCM ciphertext under the
   content key, produced and consumed on devices only. Each message gets a fresh
   random 12-byte IV, the blob is `IV || ciphertext || tag`, and the AAD is
   empty; an IV is never reused under the same content key. `tasks.body` stores
   bytes; the server never parses, re-encrypts or re-wraps them.

4. **Recovery code.** The owner may generate a recovery code: the content key
   wrapped under a key derived with Argon2id (`m = 64 MiB`, `t = 3`, `p = 4`)
   over a 16-byte random salt. The passphrase is the UTF-8 bytes as given; the
   primitive does **no** normalization, callers normalize before calling. The
   raw 32-byte Argon2id output is used directly as the AES-256-GCM key, with a
   12-byte random IV, a 128-bit tag and empty AAD. The code is stored as
   `salt(16) || IV(12) || ciphertext(32) || tag(16)`, **76 bytes**. Week 2
   ships the primitive only, wrap, unwrap and derivation; the endpoints and
   phone UI land with later admission work.

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
- Adding a device is one wrap. Authenticated admission through a device that
  already holds the key is the normal path; the recovery code is the exception
  when no such device exists. Neither re-encrypts history or rotates the key.
- The admin console cannot decrypt by construction, so its diagnostic view is
  metadata plus the `diag.*` payloads it authored. Support cannot read a task
  body, by design.
- `accounts` and `admins` sit outside tenancy but hold no keys, so a console
  compromise yields identity data, not content.
- The wrap table grows one small row per device, and its RLS policy is the same
  one every tenant table uses. A missing wrap row is not an error: it means the
  device is not yet admitted to the content key, or its wrap was revoked.
- Residual accepted for week 2: an attacker who photographs the PC's QR can claim
  the session after the phone vouches but before the PC does, receiving household
  device tokens (not the content key, because the wrap is bound to the PC's key);
  the window is the vouch-to-claim gap inside five minutes and needs a screen
  capture. A second claim secret returned only to the PC is the fix when admission
  generalizes.

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
