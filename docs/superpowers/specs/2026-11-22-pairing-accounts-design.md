# HOPA pairing, accounts and console — design (week 2 slice)

Date: 22 Nov 2026 · Status: approved by Amir · Tracks: RFC agents-demo/hopa#32,
[`docs/project/plan/backend/weekly-plan.md`](../../project/plan/backend/weekly-plan.md) week 2 (22–26 Nov)

## Intent

Week 2 replaces the operator bootstrap flow with real user identity before the 3 Dec demo: the
phone registers with a phone number and password and becomes the household's first device; the PC
joins by being **admitted** through a QR the phone scans; the team gets a console that looks up
households and devices and can fire diagnostic tasks. It also settles the overdue week-1 decision:
the household key model, now accepted.

The backend stays a coordination service: it routes, stores, wakes and records. Real tasks travel
only between a user's own devices, encrypted end to end. The console never composes user tasks and
never sees conversation contents.

## Goals

1. Phone sign-up and sign-in with an **E.164 phone number + password**; household-of-one created
   in the same transaction; the phone is the first device.
2. SMS OTP as an **optional** credential behind a server flag (`HOPA_SMS_ENABLED`, default
   **false**, log-only sender). When on, a code alone logs you in — an *alternative* credential,
   never a replacement for passwords.
3. **PC admission by vouch**: PC posts a pending pairing session with its public key and shows a
   QR; the phone scans, confirms, and vouches; the household content key is wrapped to the PC;
   the PC claims once and receives tokens + wrapped key.
4. **Household key model decided** (see §2) and written as an ADR.
5. Console in week 2: admin sign-in, health, **household & device lookup** (member-id,
   household-id, phone, what is connected) — moved up from week 4 — plus a **diagnostic task
   sender** as the last item.
6. `diag.*` task family: admin-only, explicitly plaintext, audit-logged, exempt from E2E *in the
   key model itself*.
7. Android has exactly **two modes**: `backendUrl` configured → backend; unconfigured → local-only
   (local identity, local chats, pairing UI hidden). No LAN/bridge path — the bridge remains a
   standalone eval tool only.

## Non-goals (this slice)

- Console-composed real user tasks — rejected forever, not just deferred (§4).
- Household invites, member roles, family approvals (weeks 4–5); attachments and shared threads
  (week 6).
- SMS provider integration — `SmsPort` with a log-only implementation; no vendor contract.
- Multi-replica concerns — process-local rate limits, presence and dedupe stay, documented.
- Enrollment of a *third* device or another member — the admission flow is built generically but
  only exercised phone→PC this week.

## Anti-goals

- The phone number as a primary key, on the wire, in a token, or in any foreign key.
- The console showing conversation contents, or holding household keys.
- Weakening existing invariants: RLS `FORCE` tenancy, single-use pairing sessions,
  durability-before-wire, the payment gate.

## Constraints

- The 3 Dec demo must not be risked: **sync v0 is the protected exit check**; flex items slide
  before it does. The operator-minted QR (bootstrap token) stays working as the demo fallback.
- Existing schema keys everything on UUIDs with `household_id` + `member_id` and RLS on all
  tables; new tables follow (Flyway, migrations before the listener opens).
- Plan rules: no tick without evidence; `spec/` changes land with their handlers in one PR; a
  missed exit check carries forward, it is never lowered.
- Stack is Kotlin/Ktor on Render (single container, EU) — ADR-0022.

## Approaches considered

| Decision | Alternatives | Picked, and why |
| --- | --- | --- |
| User id | (a) E.164 number *as* the id; (b) UUID id + unique phone column | (b). Numbers change and get recycled — as a key that means re-keying every reference; as a column it's a one-row update and a recycled number can't inherit history. Keys land in FKs, traces and logs; a number there is PII, against the week-7 GDPR exit check. Matches the schema as built. |
| Credential storage | (a) columns on `members`; (b) separate `accounts` table, `members.account_id` nullable | (b). Keeps *who you are* apart from *where you are*; week-4 invites (an existing account joining a household) won't need re-keying. (a) also works for v1 but glues login to a household-bound row. |
| PC pairing direction | (a) PC displays QR, phone scans; (b) phone displays QR, PC scans with webcam | (a). Standard pattern, better UX, and the trust direction is right: the authenticated device admits, never the reverse. Already the plan's week-6 commitment. |
| Pairing session authority | (a) keep operator bootstrap mint; (b) unauthenticated pending session + authenticated vouch; (c) phone mints and transfers | (b). A QR must never become a device unless an already-authenticated device says yes. (a) is not a user flow (stays for dev/ops); (c) forces the phone into the PC's display path before the PC has a session. |
| Console task authority | (a) console composes real tasks; (b) diagnostic-only family | (b). (a) breaks the E2E invariant — the console holds no household keys by design (it can't even read conversations). The `diag.*` exemption is written into the key model so it survives encryption. |
| Dev mode on the phone | (a) build variant with stubbed backend; (b) unconfigured `backendUrl` = local-only | (b). The unconfigured state already exists in the app; a variant duplicates it and discards the "backend unreachable" handling you want anyway. Dev pointing via a debug `buildConfigField` from `local.properties`, or an operator QR. |
| SMS now | (a) full OTP with a provider; (b) flag + `SmsPort` with log-only sender | (b). No vendor, cost or rate-limit surface until wanted; the flag flips behavior without schema changes. |

## Decided design

### 1 · Identity model

- New table `accounts`:

  ```sql
  accounts(
    id uuid pk,
    phone text not null unique,      -- E.164, normalized server-side
    password_hash text not null,     -- argon2id
    verified_at timestamptz null,    -- null while SMS is off
    created_at timestamptz not null default now(),
    deleted_at timestamptz null
  )
  ```

  `members.account_id uuid null references accounts` — null for today's operator-paired members.
  RLS: accounts are *not* household-tenant rows; they sit above tenants and are readable only by
  the auth/admin paths.

- **Sign-up** `POST /v1/accounts` `{phone, password, platform, name, publicKey, alg,
  selfWrappedKey, pushToken?}` → one transaction: normalize + validate E.164, hash (argon2id),
  insert account, household-of-one, owner member, device row, a `household_key_wraps` row for the
  phone's own key (the phone generates the content key and wraps it for itself — the server never
  sees plaintext), access + refresh tokens. This replaces the bootstrap mint for real users; the
  operator mint remains for dev/ops.
- **Sign-in** `POST /v1/session` `{phone, password, publicKey, …}` → verifies, creates a device
  row for the signing-in device (the password is the root credential; an implicitly admitted
  *signed-in device*). History stays unreadable until the household key is wrapped for it by an
  existing device or the recovery code (§2).
- **Login hardening**: rate limit per IP *and* per phone (reuse `RateLimiter`); constant-time
  verify; uniform `E_AUTH` refusal (no user enumeration); lookup skips `deleted_at` rows.
- **E.164**: normalize and validate server-side; store the canonical form only; reject the rest
  with `E_SCHEMA`.
- **SMS OTP** (when `HOPA_SMS_ENABLED=true`): `POST /v1/session/otp` requests a code (`SmsPort`,
  log-only impl for now), a second call verifies it. OTP login is an *alternative* credential.
  **Clash policy**: the first SMS-verified login on a number that already has an unverified
  account binds to that account and stamps `verified_at` — verified wins, squatters never block
  the real owner.
- The number is used **only** at login lookup. Tokens stay opaque, resolving to
  `DeviceIdentity(deviceId, householdId, memberId)` as today. The number never appears on the
  wire, in a token, or in a foreign key.

### 2 · Household key model (accepted 22 Nov 2026 — becomes ADR-0026)

- One **256-bit symmetric content key per household**, generated on the phone at sign-up. The
  backend stores it **only wrapped**: table `household_key_wraps(household_id, device_id,
  wrapped_key, wrapped_at)`, tenant-scoped under RLS.
- Wrapping: X25519 ECDH with the device public key (already collected at pairing) → HKDF →
  AES-GCM wrap of the content key. Unwrapping happens only on devices.
- Payloads (task bodies, sync rows) are encrypted client-side with the content key
  (AES-GCM); the server stores ciphertext (`tasks.payload` is already `bytea`). Sync v0 ships
  ciphertext-only, as the plan already demands.
- **Recovery**: the owner may generate a recovery code — the content key wrapped to an
  Argon2id-derived passphrase key — stored as a single row; using it on a fresh device re-wraps
  the key for that device. **Week 2 ships the primitive only** (wrap/unwrap + derivation); the
  endpoints and phone UI land with week 6's general admission — or week 3 if demo rehearsal
  needs a second phone.
- **`diag.*` exemption**: server-authored diagnostic tasks are the *only* plaintext payloads,
  allowed by the envelope validator for `diag.*` types only, and always audit-logged. This
  exemption is part of the key model so it survives the encryption rollout (ciphertext-only
  enforcement for everything else hardens as sync v0 lands).
- **Revocation**: revoking a device deletes its wrap row and revokes its token family — it cannot
  decrypt anything new.

### 3 · PC admission flow (vouch and claim)

```
PC:      keypair x25519 → POST /v1/pair/pending {platform, name, publicKey, alg}
         (unauthenticated, 5/min/IP, 5-min TTL) → {sessionId, qr}
PC:      displays QR   hopa://pair?v=1&s=<session-secret>&u=wss://…/v1/gateway
         (no household id in the QR — binding happens at vouch)
Phone:   scans → resolves s + u → shows "Windows PC '<name>' wants to join"
         + key fingerprint (first 8 hex of sha256(publicKey)) → user confirms
Phone:   POST /v1/pair/vouch {sessionSecret, wrappedKey}  with its own device Bearer token
PC:      polls GET /v1/pair/pending/{id} → on vouch: claims
         POST /v1/pair/claim (existing single-use CAS semantics)
         → device row + tokens + wrapped key, ONE transaction
```

- Security properties: possession of the QR secret alone is worthless — vouch requires an
  authenticated device token; the session is single-use, 5-minute TTL, one vouch; the wrap is
  computed **on the phone** (only devices ever hold the content key plaintext) and travels as
  ciphertext.
- The existing `PairingService.claim` machinery (CAS on `claimed_at`, tokens in-transaction) is
  kept; the mint-with-bootstrap-token path stays for operators.
- Week 6 generalizes the same flow to any new device and shared threads; this week exercises it
  for `windows`.

### 4 · Task authority

- **Real tasks: only between a user's own devices** (existing same-household routing), payloads
  ciphertext under the content key.
- **`diag.*` family** (admin-only, plaintext, audit-logged): `diag.ping` (echo with timestamp),
  `diag.state` (capabilities, battery, app version), `diag.reset` (demo data reset — week 3
  prep). Sent through the existing `TaskRouter` (idempotency, queue, FCM wake if offline) as
  server-authored rows with the sending admin in the audit trail.

### 5 · Admin console and API

- `admins` table (id, email, password_hash argon2, role) with its own sessions — **an operator is
  never a household member**. Console sign-in is separate from account sign-in.
- Endpoints: `GET /v1/admin/households?query=` (by phone, member-id or household-id) returning
  member-id, household-id, phone, account status; `GET /v1/admin/devices?household=` returning
  the device list + live presence (from `DeviceRegistry`, exposed by one new admin endpoint) +
  `last_seen_at`; `POST /v1/admin/devices/{id}/diag` for §4.
- Every admin read of a phone number is **access-logged** (PII shown to the team). The console
  never shows conversation contents — it could not decrypt them anyway.
- **Reading tenants as an admin under `FORCE` RLS**: `accounts` sits above tenancy (phone lookups
  are direct), but looking a member up by id cannot resolve the household first. Mechanism:
  dedicated `SECURITY DEFINER` lookup functions in the migration (by member-id, by-phone →
  household), audit-logged, returning the minimum columns — **not** a `BYPASSRLS` app role and
  not a blanket admin GUC; the RLS invariant stays untouched.
- **First admin row**: seeded once at boot from `HOPA_ADMIN_EMAIL` + `HOPA_ADMIN_PASSWORD` env
  (upsert, mirroring the existing bootstrap-token pattern); Render env holds them.
- Week-2 console scope: sign-in, health, the lookup page, the diag sender (last item). The web
  front-end shape (server-rendered vs static + JSON) is the builder's call per repo conventions.

### 6 · Android: two modes, no bridge path

- `backendUrl` in settings: null → **local-only** (local identity, local chats, pairing UI
  hidden); set → backend. No third path — the app never talks to the LAN bridge (the bridge is a
  leftover of the PC-as-adb/hdc-bridge architecture and stays a standalone eval tool only).
- Dev pointing: debug `buildConfigField` fed from `local.properties` (the Android cousin of
  `.env`), release builds ship null; or scan an operator-minted QR.
- Sign-in screen (phone + password) against §1; tokens land in the existing
  `BackendCredentials` store.

### 7 · Spec and docs in the same PRs (plan rule)

- `spec/openapi/backend.v1.yaml`: `/v1/accounts`, `/v1/session` (+`/otp`), `/v1/pair/pending`
  (+poll), `/v1/pair/vouch`, `/v1/admin/*`.
- ADR-0026 *household content key* (accepted). Housekeeping folded in: `services/backend/README.md`
  rewritten to match reality (it still sketches a Python service); ADR-0022 flipped to accepted
  with stale version/module refs fixed; stale schema-path comments in `backend.v1.yaml` fixed.

## Week 2 fit — lanes A and B (Amir / Batya)

Week 2 was already the heaviest week. The protected exit is **sync v0 + revoke**; flex items
slide before it does, and the operator-minted QR remains the demo fallback if admission slips.

**Out of scope here:** sync v0 is *not* designed by this document — it keeps its original
weekly-plan scope (threads/turns, cursors, tombstones, ciphertext only, the bridge store's
dedupe/title/tombstone rules) and gets its own short design by B on Monday before any handler.

| Day | A: gateway, tasks, security | B: accounts, data, console |
| --- | --- | --- |
| Mon | ADR-0026 written + wrap/unwrap primitives (X25519+HKDF+AES-GCM, recovery code) | `V2__accounts.sql` (+`household_key_wraps`, `admins`); E.164 + argon2id utilities; sync v0 mini-design |
| Tue–Wed | pending-session + vouch + claim-with-wrap endpoints; admin presence + diag-send endpoints | accounts sign-up/sign-in endpoints (rate limits, no enumeration); console admin sign-in + health |
| Thu | device list / revoke / token refresh (original week-2 item); admission end-to-end with a real PC — Kotlin sidecar: pending session, QR render, poll, claim | console lookup page; **sync v0 (protected)** |
| Fri | sidecar gateway envelopes both directions + wrap-on-revoke | diag sender UI; Android sign-in screen + local-only mode + camera QR scanner |
| Flex order (slides to week 3 first) | sidecar polish (fallback: operator QR) | diag sender UI → camera scanner (paste covers) → Android polish |

Exit checks: the original two (cross-device turn ≤ 2 s target; revoked device cut off in 5 s)
**plus**: a PC admitted by a **camera-scanned QR** sends and receives one task with its wrapped
key. If admission slipped, the operator-QR fallback carries the gap forward — the check is not
lowered.

## Decisions closed 22 Nov (Amir)

1. **PC client work lives in the Kotlin sidecar** (per ADR-0021's shape), lane A, landing
   Thu–Fri after the vouch endpoints: pending session, QR render, poll, claim, unwrap, gateway
   envelopes — reusing the JVM `backend` client module Android already uses (`BackendClient`,
   `BackendCredentials`, `X25519Keys`, `PairingLink`).
2. **The phone scans a real QR with the camera** (CameraX + ML Kit), added to week 2's Android
   scope. Paste/manual entry of the `hopa://pair?…` string stays as the built-in fallback —
   `PairingLink` already parses it — so the camera work can slide to week 3 only if sync v0 is
   at risk, without leaving the flow unusable.

## Weekly-plan amendment (applies to `docs/project/plan/backend/weekly-plan.md`)

1. **"What the backend must do" table, Accounts row**: `Sign-in (email link or passkey)` →
   `Sign-in (phone number + password; SMS OTP optional, off by default; verified wins)`.
2. **Week-2 row**: Deliverables become — `Sign-in by phone number + password with a household of
   one (E.164, argon2; SMS OTP behind a flag, off); device list, revoke, token refresh; sync v0
   of threads and turns (cursors, tombstones, ciphertext only); PC admitted by a phone-scanned
   QR (vouch + household-key wrap, ADR-0026); console: admin sign-in, health, household and
   device lookup (moved from week 4), diagnostic task sender`. Exit check gains: `a PC admitted
   by a camera-scanned QR runs one task in each direction (fallback: operator QR, gap carried)`.
3. **Week-4 row**: remove `console: household and device lookup` (moved to week 2).
4. **Week-6 row**: `a new device admitted by an existing one` → `admission generalized to any
   device (third+ device, re-wrap), shared household threads` — the admission *basics* moved to
   week 2.
5. **Decisions list**: tick the key-model box — decided 22 Nov (§2); ADR-0026 lands with the
   first week-2 PR.
6. **Risks table**: add `Console shows phone numbers (PII)` → `Admin-only role, separate from
   household accounts; every read access-logged`.
7. **Two-lane table (week 2 row)**: replace with the A/B day split above.

## RFC issue update (agents-demo/hopa#32)

Post as a comment on #32 (and tick the three Phase-1 decision boxes in the body):

> **Phase 2 design deltas — 22 Nov** (design doc:
> `docs/superpowers/specs/2026-11-22-pairing-accounts-design.md`)
>
> - **Key model decided** (tick `Key model for end-to-end encryption`): one household content
>   key, wrapped per device X25519 key; recovery code at the owner; `diag.*` tasks are the only
>   plaintext payloads, exempt in the key model. → ADR-0026.
> - **Sign-in is phone number + password** (E.164 unique, argon2id), not email/passkey. SMS OTP
>   is optional behind `HOPA_SMS_ENABLED` (default off, log-only sender); a verified login takes
>   over an unverified account on the same number. `user_id` stays a UUID — the number is only
>   the login lookup, never a key.
> - **PC joins by vouch**: pending pairing session + QR on the PC, authenticated vouch from the
>   phone (which wraps the household key), single-use claim with tokens + wrapped key in one
>   transaction. Operator-mint stays as dev/ops fallback.
> - **Console grows in Phase 2**: household & device lookup (member-id, household-id, phone,
>   live connections) moves here from Phase 4, plus a diagnostic-only task sender
>   (`diag.ping/state/reset`); admins are never household members; phone-number reads are
>   access-logged. The console never composes user tasks.
> - **Android is two-mode**: backend or local-only; no LAN bridge path in the app. It gains a
>   camera QR scanner (CameraX + ML Kit) with paste as fallback, and the PC client work lands
>   in the Kotlin sidecar reusing the JVM backend client module (lane A).
> - Phase 2 exit check gains: a PC admitted by a camera-scanned QR runs one task each way
>   (fallback: operator QR).

## What's left to the builder

- Console front-end shape (server-rendered ktor pages vs static + JSON) and styling.
- OTP details when enabled: code length, TTL, resend/backoff limits.
- Admin session mechanics (reuse the `TokenIssuer` pattern with a separate table vs minimal
  separate issuance) — pick the smaller.
- `diag` task actor representation (nullable `member_id` + `source='console'` audit row vs a
  system member row).
- Android local-mode store specifics, following existing app conventions.
- Migration numbering/shape (`V2__accounts.sql` combining accounts, wraps, admins, or split).
- Timing for hardening ciphertext-only enforcement outside `diag.*`.

## Calls made without asking (flag any you'd change)

1. OTP is an alternative credential, not a password replacement; verified-wins clash policy.
2. Sign-in on a fresh device creates an implicitly admitted *signed-in device*; history needs a
   wrap from an existing device or the recovery code.
3. No household id in the new pending-pairing QR (`s` + `u` only) — binding happens at vouch.
4. Admin accounts live outside households entirely; every phone-number read is logged.
5. Housekeeping (README rewrite, ADR-0022 status, stale path comments) rides with these PRs.
6. Admin tenant reads go through audit-logged `SECURITY DEFINER` functions — no `BYPASSRLS`
   app role, no blanket admin GUC.
7. The first admin is seeded from `HOPA_ADMIN_EMAIL`/`HOPA_ADMIN_PASSWORD` at boot.
8. Recovery code is primitive-only in week 2; endpoints and UI land week 6 (or week 3 if the
   demo rehearsal needs a second phone).
9. Sync v0 stays out of this doc — B designs it Monday per the original weekly-plan scope.
