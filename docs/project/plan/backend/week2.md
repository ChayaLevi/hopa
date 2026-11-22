# Week 2 · 22–26 Nov 2026: accounts, PC admission, console, sync v0

Status: **planned.** Written 2026-11-22. Parent: [`weekly-plan.md`](weekly-plan.md),
tracked in [#32](https://github.com/agents-demo/hopa/issues/32). Design source:
[`docs/superpowers/specs/2026-11-22-pairing-accounts-design.md`](../../../superpowers/specs/2026-11-22-pairing-accounts-design.md)
(decisions below argue from it, not from this file).

The week replaces the operator bootstrap flow with a real user: the phone signs up
with a phone number and password and becomes the household's first device, the PC
joins by a QR the phone scans and vouches for, and the team gets a console that
looks up households and fires diagnostic tasks. Sync v0 — the original centrepiece —
stays the protected exit check.

## 1. Goal

A user signs up on the phone (E.164 number + password), admits their Windows PC by
scanning the PC's QR with the camera, and the two devices exchange an
end-to-end-encrypted task in each direction; a turn typed on either device's chat
syncs to the other in ≤ 2 s (target); the console shows the household, the member,
the phone number and both devices' live state, and can send a `diag.ping` that
arrives. A revoked device is cut off within 5 s.

Exit check: §8.

## 2. Non-goals

- No household invites, roles or approvals (weeks 4–5); no attachments or shared
  threads (week 6); no third-device admission exercises (the flow is generic but
  only phone→PC is proven).
- No SMS provider: `SmsPort` ships with a log-only implementation behind
  `HOPA_SMS_ENABLED=false`. OTP endpoints exist but no vendor contract is signed.
- No recovery-code endpoints or UI — only the crypto primitive (design §2).
- No console real-task composition, ever (design §4); no conversation contents in
  the console, ever.
- No LAN bridge path in the Android app. The bridge stays a standalone eval tool.
- No changes to the relay (`services/relay`) — it runs unchanged for shipped
  clients.

## 3. Decisions taken

All from the design doc; the table records the ones an implementer must not
re-open.

| Decision | Value | Why |
|---|---|---|
| User identity | `accounts.id` UUID; phone E.164 unique column, argon2id hash, `verified_at` null while SMS is off | Numbers change and recycle; they are login lookup only, never keys, never on the wire |
| Member link | `members.account_id` nullable | Week-4 invites join an existing account to a household without re-keying |
| SMS OTP | `HOPA_SMS_ENABLED` (default false), log-only `SmsPort`; alternative credential; verified login takes over an unverified account | No vendor surface until wanted; squatters never block the real owner |
| Household key | One 256-bit content key per household, stored only as `household_key_wraps` rows (X25519 ECDH → HKDF → AES-GCM) | ADR-0025; the server is content-blind by construction |
| PC admission | Unauthenticated pending session (5/min/IP, 5-min TTL) + authenticated vouch from the phone + single-use claim with tokens and wrapped key in one transaction | A QR never becomes a device unless an authenticated device says yes |
| QR content | `hopa://pair?v=1&s=<secret>&u=wss://…` — no household id | Binding happens at vouch; no tenant ids leak to a screen |
| Console tasks | `diag.ping`, `diag.state`, `diag.reset` only; plaintext, admin-only, audit-logged | The console holds no household keys; the exemption is written into the key model |
| Admin access | Own `admins` table, own sessions; `SECURITY DEFINER` lookups for tenant rows; phone-number reads logged | An operator is never a household member; RLS `FORCE` stays untouched |
| PC client | In the Kotlin sidecar, reusing the JVM `backend` client module | ADR-0021's shape; one client library for Android and Windows |
| Android QR input | CameraX + ML Kit scan; paste of the `hopa://` string stays as fallback | Camera is the user flow; paste keeps the flow usable if camera work slips |
| Android modes | `backendUrl` configured → backend; null → local-only | The unconfigured state already exists; dev pointing via debug `buildConfigField` from `local.properties` |

## 4. API additions (spec changes land with their handlers)

| Route | Auth | Body → Result |
|---|---|---|
| `POST /v1/accounts` | none (rate-limited) | `{phone, password, platform, name, publicKey, alg, selfWrappedKey, pushToken?}` → account + household-of-one + member + device + wrap row + tokens, one transaction |
| `POST /v1/session` | none (rate-limited) | `{phone, password, platform, name, publicKey, pushToken?}` → signed-in device + tokens (no history without a wrap) |
| `POST /v1/session/otp` | none | `{phone}` → code sent via `SmsPort` (log-only until a vendor exists) |
| `POST /v1/session/otp/verify` | none | `{phone, code, platform, name, publicKey}` → verified login; binds and stamps `verified_at` on an unverified account |
| `POST /v1/pair/pending` | none (5/min/IP) | `{platform, name, publicKey, alg}` → `{sessionId, secret, qr, expiresAt}` |
| `POST /v1/pair/pending/resolve` | none (30/min/IP) | `{sessionSecret}` → `{sessionId, state, name, platform, publicKey, fingerprint, expiresAt}`; the PC polls by the secret it minted |
| `POST /v1/pair/vouch` | device Bearer | `{sessionSecret, wrappedKey}` → binds household + member; stores the wrap; session becomes vouchable |
| `POST /v1/pair/claim` | none | extended: a pending session claim returns tokens, `wrappedKey` and the voucher's `voucherPublicKey` for the PC's key |
| `GET /v1/devices` | device Bearer | the household's device list |
| `DELETE /v1/devices/{id}` | device Bearer | revoke: token family + wrap row, 5 s cut-off (exit check) |
| `POST /v1/admin/session` | none (rate-limited) | admin sign-in (email + password) |
| `GET /v1/admin/households?query=` | admin Bearer | lookup by phone, member-id or household-id (access-logged) |
| `GET /v1/admin/devices?household=` | admin Bearer | device list + live presence + `last_seen_at` |
| `POST /v1/admin/devices/{id}/diag` | admin Bearer | `{household, type: diag.ping \| diag.state \| diag.reset, body?}` → server-authored task |

Envelope: `diag.ping`, `diag.state`, `diag.reset` join the allowed types; they are
the only plaintext bodies. Everything else stays as frozen in week 1 (§4 of
[`week1.md`](week1.md)); no version bump is needed because adding types is not a
breaking change — unknown types are already refused with `E_UNSUPPORTED`.

## 5. Data model additions (V2 migration)

| Table | Key columns | Notes |
|---|---|---|
| `accounts` | `id`, `phone` unique, `password_hash`, `verified_at`, `created_at`, `deleted_at` | **not** tenant-scoped; RLS off, read only by auth/admin paths |
| `household_key_wraps` | `household_id`, `device_id`, `wrapped_key`, `wrapped_at` | tenant-scoped, RLS on; deleted on revoke |
| `admins` | `id`, `email` unique, `password_hash`, `role`, `created_at` | not tenant-scoped; first row seeded from `HOPA_ADMIN_EMAIL`/`HOPA_ADMIN_PASSWORD` at boot |
| `admin_sessions` | `id`, `admin_id`, `token_hash`, `expires_at` | 8 h, opaque, SHA-256 at rest |
| `pending_pairings` | `id`, `code_hash`, `platform`, `name`, `public_key`, `alg`, `household_id` null, `member_id` null, `vouched_at`, `expires_at`, `claimed_at` | single use, 5-min TTL; binding written by vouch |
| `members` (alter) | + `account_id uuid null references accounts` | null for operator-paired members |

Plus four `SECURITY DEFINER` functions owned by the non-login `hopa_admin`
(`BYPASSRLS`) role, each returning minimum columns and audit-logged by the caller:
`admin_resolve_member(member uuid)` and `admin_resolve_household(household uuid)`
return `(household_id, member_id, display_name)`; `admin_resolve_phone(phone text)`
also returns `account_id` (four columns); `admin_list_devices(household uuid)`
returns `(device_id, platform, name, last_seen_at, revoked_at)`. The app role has
no `BYPASSRLS` and no blanket tenant GUC.

Sync v0's tables (`threads`, `turns`, `device_cursors` — naming to be settled by
Monday's mini-design) land in the same migration only if that design is agreed
Monday; otherwise a `V3` follows mid-week.

## 6. Work plan

| Day | Lane A (Amir) | Lane B (Batya) |
|---|---|---|
| Sun 22 | Read the design; ADR-0025 drafted | Read the design; `V2__accounts.sql` drafted |
| Mon 23 | **ADR-0025 agreed**; `KeyWrap` primitives (X25519+HKDF+AES-GCM, recovery-code derivation) with unit tests | V2 migration + Testcontainers tests (RLS on `household_key_wraps`, non-tenant accounts/admins, definer functions); E.164 + argon2 utilities; **sync v0 mini-design written** |
| Tue 24 | pending-session + vouch + claim-with-wrap endpoints (spec in the same PR) | accounts sign-up/sign-in endpoints (rate limits, no enumeration, uniform `E_AUTH`); admin seed + `POST /v1/admin/session` |
| Wed 25 | admin presence + diag-send endpoints; `diag.*` in the validator; server-authored tasks | console health page on the admin API; lookup page; access log |
| Thu 26 | device list / revoke / refresh (original item); admission end-to-end with a real PC — sidecar: pending session, QR render, poll, claim, unwrap | **sync v0 implementation (protected)**; console lookup wired to live presence |
| Fri 27 | sidecar gateway envelopes both directions; wrap-deleted-on-revoke | diag sender UI; Android sign-in screen + local-only mode + camera QR scanner |

Flex order (what slides to week 3 first): sidecar polish (fallback: operator QR) —
then diag sender UI — then camera scanner (paste covers) — then Android polish.
Sync v0 never slides.

## 7. Risks

| Risk | Effect | Mitigation | Owner |
|---|---|---|---|
| Week 2 was already the heaviest week; adding admission + console overloads it | Sync v0 slips and the demo is at risk | Flex order above; operator QR as demo fallback; sync v0 never slides | both |
| Sidecar PC work is new surface (no backend code exists there today) | The admission exit check fails late | Reuse the JVM `backend` client module Android already uses; lane A owns it Thu–Fri; fallback path is already deployed | A |
| Argon2id on the free tier | Slow sign-up under load | Tune cost parameters once, measure in CI; it is a sign-in-path cost, not per-message | B |
| Camera + ML Kit is a new Android dependency | Build size, permission friction, device variance | Paste fallback ships in the same PR; camera is additive | B |
| `SECURITY DEFINER` functions widen access by mistake | Tenant leak — the one bug that ends the beta | Four functions return minimum columns only; CI calls them as the app role and asserts the shape; every call is audit-logged | B |
| Plaintext `diag.*` becomes a smuggling channel | Server-readable task bodies reappear | Validator allows plaintext for the three `diag.*` types only; admin-auth required; audit per send | A |
| Phone-number enumeration via sign-up errors | Account discovery before any SMS | Uniform `E_AUTH` for wrong phone and wrong password; per-IP and per-phone rate limits | B |
| Sync v0 redesign mid-week (mini-design slips past Monday) | B loses Thursday | The mini-design is one page, Monday-only; if it slips, sync v0 keeps the bridge store's rules verbatim and the design lands as a PR comment | B |

## 8. Exit check (Fri 27 Nov)

| # | Check | Target | Where it is proven |
|---|---|---|---|
| 1 | A turn typed on the phone appears on the PC, and back | ≤ 2 s (target, measure and record) | evidence file `B2-accounts.md` with timestamps |
| 2 | A revoked device is cut off | in-flight socket closed ≤ 5 s; token refresh refused; wrap row gone | integration test + manual timing in the evidence file |
| 3 | A PC admitted by a camera-scanned QR runs one task each way | 20/20 with encrypted bodies; the PC unwraps its key | evidence file; falls back to operator QR with the gap carried, never skipped |
| 4 | Sign-up → sign-in round trip on the phone | new household of one; second sign-in gets a signed-in device | integration test |
| 5 | SMS clash policy (flag on, fake `SmsPort`) | a verified login binds and stamps the unverified account | unit test |
| 6 | Console lookup by phone, member-id, household-id | correct row; every read appears in the access log | integration test + log line |
| 7 | `diag.ping` from the console to an offline phone | queued, FCM wake, arrives; audit row names the admin | integration test with FakeFcm |
| 8 | Cross-tenant suite still green | 1,000 attempts, 0 rows (extended with accounts + wraps + pending pairings) | CI job |
| 9 | No plaintext payload or phone number in logs | grep of the week's logs is clean | evidence file, command shown |

## 9. Checklist

Every box is a change, a test or an artifact, not a meeting. Slices:
[`tasks/week2a.md`](tasks/week2a.md) (lane A), [`tasks/week2b.md`](tasks/week2b.md)
(lane B); sync v0's slice is written Monday from the mini-design.

### W2.0 Design and ADR — Monday

- [ ] `docs/design/decisions/0025-household-content-key.md` written and indexed in
      `docs/design/decisions/README.md`.
- [ ] ADR-0022 status flipped to accepted; stale version/module references fixed.
- [ ] Sync v0 mini-design (one page: tables, cursors, tombstone rules, ciphertext)
      agreed and linked here.

### W2.1 Schema and utilities — Monday

- [ ] `V2__accounts.sql`: accounts, household_key_wraps, admins, admin_sessions,
      pending_pairings, `members.account_id`; RLS on tenant tables; the four
      `hopa_admin`-owned definer functions.
- [ ] Testcontainers: migration applies fresh and idempotent; app-role reads of
      `household_key_wraps` outside the tenant return zero rows; definer functions
      return exactly their minimum columns.
- [ ] E.164 normalization + argon2id hashing utilities with unit tests.
- [ ] Admin seed from `HOPA_ADMIN_EMAIL`/`HOPA_ADMIN_PASSWORD` at boot, upsert.

### W2.2 Key wrapping — Monday/Tuesday

- [ ] `KeyWrap`: X25519 ECDH → HKDF-SHA256 → AES-256-GCM wrap/unwrap; round-trip
      unit tests; tamper test (flipped bit → refusal); recovery-code derivation
      (Argon2id → AES-GCM) unit-tested, endpoints deferred.

### W2.3 Accounts and sessions — Tuesday

- [ ] `POST /v1/accounts` and `POST /v1/session` per §4; uniform `E_AUTH`; rate
      limits per IP and per phone; `spec/openapi/backend.v1.yaml` updated in the
      same PR.
- [ ] `POST /v1/session/otp` + `/verify` behind `HOPA_SMS_ENABLED` with the
      log-only `SmsPort`; clash policy test.
- [ ] Integration: sign-up creates account + household + member + device + own
      wrap in one transaction (failure leaves nothing).

### W2.4 Admission — Tuesday/Wednesday

- [ ] pending-session + resolve-by-secret + vouch + claim-with-wrap endpoints per
      §4; single-use, 5-min TTL, one vouch; QR string `hopa://pair?v=1&s=…&u=…`.
- [ ] Integration: unauthenticated session never becomes a device without vouch;
      double claim refused; wrap stored once; PC receives the wrapped key and the
      voucher's public key at claim.

### W2.5 Devices and admin API — Wednesday/Thursday

- [ ] `GET /v1/devices`, `DELETE /v1/devices/{id}` (token family + wrap row, ≤ 5 s
      cut-off), `POST /v1/token/refresh` reuse detection already proven.
- [ ] `POST /v1/admin/session`, `GET /v1/admin/households?query=`,
      `GET /v1/admin/devices?household=` (live presence), access log on every
      phone-number read.
- [ ] `diag.ping/state/reset` in the validator; server-authored tasks through
      `TaskRouter`; audit row names the admin.

### W2.6 Console — Wednesday/Thursday

- [ ] Admin sign-in page, health page, household & device lookup page (member-id,
      household-id, phone, connected devices), diag sender button (last).
- [ ] No conversation contents anywhere in the console (asserted by test).

### W2.7 Clients — Thursday/Friday

- [ ] Sidecar: pending session, QR render on **stderr** (image + `hopa://` string;
      stdout is the protocol channel), 5 s resolve-by-secret poll, claim, unwrap,
      credentials stored, gateway envelopes both directions.
- [ ] Android: sign-in screen, local-only mode when `backendUrl` is null, debug
      `buildConfigField` from `local.properties`, camera QR scanner with paste
      fallback, vouch sends the wrapped key.

### W2.8 Evidence — Friday

- [ ] `docs/project/evidence/B2-accounts.md` records §8 with commands, trace ids
      and timestamps; `python tools/scripts/plan-status.py --plan
      docs/project/plan/backend` stays green.
- [ ] `services/backend/README.md` rewritten to match the shipped Kotlin/Ktor
      service (it still sketches a Python layout).
