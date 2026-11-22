# Backend week 2, slice A: key model, admission, diag, sidecar

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or
> superpowers:subagent-driven-development) to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decide-and-build the household key model, the phone-vouches-for-PC admission
flow, the admin diagnostic path, device revocation, and the Windows sidecar's backend
integration — so a camera-scanned QR admits a PC that then runs encrypted tasks both
ways.

**Architecture:** Crypto primitives live in the shared `core/backend` client module
(`com.hopa.backend.crypto`) so the phone, the sidecar and the server's tests use one
implementation — the server itself only ever *stores* wrapped keys. Admission extends
`services/backend`'s existing pairing package: a new unauthenticated `pending_pairings`
session type plus an authenticated vouch that binds the household and stores the wrap,
reusing `PairingService.claim`'s single-use CAS + tokens-in-transaction semantics. The
sidecar (`com.hopa.sidecar`) includes `:backend` from `core/backend` exactly as
`apps/android` does and never talks to the LAN bridge.

**Tech Stack:** Kotlin 2.4.21, Ktor 3.6.1, JUnit 5, JDK XDH (X25519) + AES-GCM from
the JDK, HKDF-SHA256 hand-rolled (~20 lines), Argon2id via Bouncy Castle (`bcprov`,
new catalog entry), zxing `core` for the sidecar's QR, Testcontainers Postgres 16 via
the existing `PostgresHarness`.

**Spec:** [`../week2.md`](../week2.md) §3–§5, built from
[`docs/superpowers/specs/2026-11-22-pairing-accounts-design.md`](../../../../superpowers/specs/2026-11-22-pairing-accounts-design.md).

## Global Constraints

- Do not bump the shared catalog's pins (Kotlin 2.4.21 / Ktor 3.6.1 / JUnit 5).
- The phone number is never a key, never on the wire, never in a foreign key.
- `household_id` never appears on the wire; identity comes from the token.
- The server never sees a plaintext content key; wraps travel as `bytea`.
- `diag.*` are the only plaintext envelopes; every send is audit-logged with the
  admin's id.
- Revocation deletes the wrap row and revokes the token family in one transaction.
- Spec changes (`spec/openapi/backend.v1.yaml`) land in the same PR as their handlers.
- Every command runs from the repository root on Windows PowerShell; Gradle is always
  `apps\android\gradlew.bat` (there is no second wrapper).
- Migration `V2__accounts.sql` (slice B) lands **before** Tasks 4–6 here; if it has
  not, implement Tasks 1–3 only and stop.

## Review Focus

- A pending session vouched twice stores two wrap rows (double-vouch).
- A vouch from a device of household X for a session claimed by a device of
  household Y (swap between vouch and claim).
- A revoked device still holds a valid-looking refresh token (family already rotated).
- A `diag.reset` sent to a *revoked* device is queued forever.
- A wrap row deleted while the device is online: the socket must still close within
  5 s (revocation kicks the live session, not just future logins).

Each line has a test in Task 4 or Task 6.

---

### Task 1: ADR-0025 and the ADR-0022 housekeeping

**Files:**
- Create: `docs/design/decisions/0025-household-content-key.md`
- Modify: `docs/design/decisions/README.md` (index row for 0025; flip 0022's status)
- Modify: `docs/design/decisions/0022-backend-kotlin-and-shape.md` (status →
  accepted; fix the stale Kotlin 2.0.21/Ktor 3.0.3 pins and `packages/` module
  references to match the shipped 2.4.21/3.6.1 and `core/*` split)

**Interfaces:**
- Consumes: design doc §2.
- Produces: the ADR text later tasks cite; no code.

- [ ] **Step 1: Write ADR-0025** — status *accepted*, deciding: one 256-bit content
  key per household generated on the first phone; stored only as
  `household_key_wraps(household_id, device_id, wrapped_key, wrapped_at)`; X25519 →
  HKDF-SHA256 (salt `SHA-256("hopa.v1.keywrap" || recipientPublicKey)`, info
  `"hopa.content.key.wrap"`, 32 bytes) → AES-256-GCM (12-byte IV prepended, 128-bit
  tag); recovery code = content key under an Argon2id-derived key (m=64 MiB, t=3,
  p=4, 16-byte salt, format `salt || ciphertext`); `diag.*` plaintext exemption;
  revocation deletes the wrap. Name the rejected alternatives: per-thread keys
  (re-wrap storms), server-held key (content-blindness breaks).
- [ ] **Step 2: Fix ADR-0022 and both indexes** — status accepted; pins and module
  names corrected; `docs/design/decisions/README.md` gains the 0025 row.
- [ ] **Step 3: Verify links** — Run: `py -3 tools/checks/check-links.py`
  Expected: `0 broken link(s)`.
- [ ] **Step 4: Commit**

```powershell
git add docs/design/decisions
git commit -m "docs(backend): ADR-0025 household content key; ADR-0022 accepted"
```

---

### Task 2: KeyWrap and Argon2 primitives in the shared module

**Files:**
- Create: `core/backend/src/main/kotlin/com/hopa/backend/crypto/KeyWrap.kt`
- Create: `core/backend/src/main/kotlin/com/hopa/backend/crypto/Argon2.kt`
- Test: `core/backend/src/test/kotlin/com/hopa/backend/crypto/KeyWrapTest.kt`
- Modify: `apps/android/gradle/libs.versions.toml` (`bcprov` entry), `core/backend`
  build if it declares dependencies explicitly

**Interfaces:**
- Consumes: nothing (JDK + BouncyCastle only).
- Produces (used by Tasks 4, 7, 8 and slice B's accounts):

```kotlin
object KeyWrap {
    fun generateContentKey(): ByteArray                                  // 32 random bytes
    fun wrapForDevice(contentKey: ByteArray, peerPublicKey: ByteArray,
                      ownPrivateKey: ByteArray): ByteArray               // X25519+HKDF+AES-GCM
    fun unwrapByDevice(wrapped: ByteArray, peerPublicKey: ByteArray,
                       ownPrivateKey: ByteArray): ByteArray
    fun wrapWithPassphrase(contentKey: ByteArray, passphrase: CharArray): ByteArray   // salt||ct
    fun unwrapWithPassphrase(wrapped: ByteArray, passphrase: CharArray): ByteArray
}
object Argon2 {
    fun hashPassword(password: CharArray, salt: ByteArray): String      // argon2id, encoded string
    fun verifyPassword(password: CharArray, encoded: String): Boolean  // constant-time
}
```

- [ ] **Step 1: Write the failing tests** — `KeyWrapTest`: round-trip `wrapForDevice`
  → `unwrapByDevice` with two XDH keypairs returns the same 32 bytes; a flipped bit
  in the wrap throws `AEADBadTagException`; wrap for A does not unwrap by B;
  passphrase wrap/unwrap round-trips and a wrong passphrase fails; two wraps of the
  same key differ (random IV/salt). Use `KeyPairGenerator.getInstance("XDH")` with
  `NamedParameterSpec.X25519` for test keys.
- [ ] **Step 2: Run to watch them fail** — Run:
  `apps\android\gradlew.bat -p apps/android :backend:test --tests "*KeyWrapTest*"`
  Expected: compile error — unresolved `KeyWrap`.
- [ ] **Step 3: Implement** — `KeyWrap` per the ADR-0025 parameters; HKDF is
  extract-then-expand with `Mac("HmacSHA256")`; `Argon2` delegates to BouncyCastle's
  `Argon2BytesGenerator` with the ADR parameters and encodes as
  `argon2id$m,t,p$salt$hash` (base64url, no `=` padding).
- [ ] **Step 4: Run to watch them pass** — same command, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit**

```powershell
git add core/backend apps/android/gradle/libs.versions.toml
git commit -m "feat(backend): household key wrap and argon2 primitives"
```

---

### Task 3: Pending pairing sessions (mint and poll)

**Files:**
- Create: `services/backend/src/main/kotlin/com/hopa/backend/pairing/PendingPairing.kt`
- Modify: `spec/openapi/backend.v1.yaml` (`/v1/pair/pending`, `/v1/pair/pending/{id}`)
- Test: `services/backend/src/test/kotlin/com/hopa/backend/pairing/PendingPairingTest.kt`

**Interfaces:**
- Consumes: `Database.token`, `Tokens.mint/hash/hex`, `RateLimiter`,
  `receiveBounded`, `PairingOutcome` (existing).
- Produces (Task 4 and the sidecar rely on these):

```kotlin
data class PendingRequest(val platform: String, val name: String,
                          val publicKey: String, val alg: String)
data class PendingMinted(val sessionId: String, val secret: String,
                         val qr: String, val expiresAt: String)
// qr = hopa://pair?v=1&s=<secret>&u=wss://<publicHost>/v1/gateway   (no household id)
class PendingPairingService(db: Database, clock: () -> Long, publicHost: String,
                            perMinute: Int = 5) {
    fun mint(request: PendingRequest, remoteAddress: String): PairingOutcome<PendingMinted>
    fun state(sessionId: String): PairingOutcome<PendingState>   // pending | vouched | claimed | expired
}
```

- [ ] **Step 1: Write the failing tests** (Ktor `testApplication`, no DB — assertions
  against refusals and the QR shape; DB paths land in Task 4's integration test):
  mint with `alg != "x25519"` or a non-32-byte key → `E_SCHEMA`; the 6th mint from
  one IP in a minute → `E_RATE`; the QR matches
  `^hopa://pair\?v=1&s=[A-Za-z0-9_-]+&u=wss://.+/v1/gateway$` and contains no `h=`;
  `state` of an unknown id → `E_SCHEMA`; of an expired session → the `Expired`
  state.
- [ ] **Step 2: Run to watch them fail** — Run:
  `apps\android\gradlew.bat -p services/backend test --tests "*PendingPairingTest*"`
  Expected: compile error.
- [ ] **Step 3: Implement** — insert into `pending_pairings` (hash the secret like
  `PairingService` does, 5-min TTL, X-Forwarded-For-aware limiter 5/min/IP); `state`
  reads one row by id and maps `vouched_at`/`claimed_at`/`expires_at`. Routes in the
  same file: `POST /v1/pair/pending`, `GET /v1/pair/pending/{id}`. Update
  `backend.v1.yaml` in this commit.
- [ ] **Step 4: Run to watch them pass** — same command, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit**

```powershell
git add services/backend spec/openapi/backend.v1.yaml
git commit -m "feat(backend): pending pairing sessions with QR"
```

---

### Task 4: Vouch and claim-with-wrap (integration)

**Files:**
- Modify: `services/backend/src/main/kotlin/com/hopa/backend/pairing/PendingPairing.kt` (vouch + claim)
- Modify: `services/backend/src/main/kotlin/com/hopa/backend/pairing/PairingService.kt` (claim falls through to pending)
- Modify: `spec/openapi/backend.v1.yaml` (`/v1/pair/vouch`; claim response gains `wrappedKey`)
- Test: `services/backend/src/test/kotlin/com/hopa/backend/pairing/AdmissionTest.kt` (Testcontainers)

**Interfaces:**
- Consumes: Task 3's `PendingPairingService`; `PgAuth` device tokens; `KeyWrap`;
  `PostgresHarness` (roles `hopa_owner`/`hopa_app`, real RLS).
- Produces:

```kotlin
data class VouchRequest(val sessionSecret: String, val wrappedKey: ByteArray) // wrappedKey ≤ 512 B
fun vouch(identity: DeviceIdentity, request: VouchRequest): PairingOutcome<Vouched>
// Vouched(sessionId, householdId, memberId)
// claim: POST /v1/pair/claim {session} now also resolves pending_pairings;
//        Claimed gains wrappedKey: ByteArray?  (null for operator sessions)
```

- [ ] **Step 1: Write the failing integration tests** — full journey: phone signs in
  via slice B's `POST /v1/accounts` (or the operator mint if B's endpoints lag: use
  the existing bootstrap mint to make the phone a device), mints a pending session
  "as the PC", vouches with a `KeyWrap.wrapForDevice` blob, then claims as the PC
  with its X25519 key → device row + tokens + the same wrapped bytes, one
  transaction. Refusals: vouch with a bad device token → `E_AUTH`; vouch twice →
  second returns `E_SCHEMA` and no second wrap row; claim before vouch →
  `E_SCHEMA`; claim after expiry → `E_SCHEMA`; **cross-session swap**: vouch session
  1, claim session 2's secret → `E_SCHEMA`. Assert `household_key_wraps` has exactly
  one row per admission.
- [ ] **Step 2: Run to watch them fail** — Run:
  `apps\android\gradlew.bat -p services/backend test --tests "*AdmissionTest*"`
  Expected: compile error on `vouch`.
- [ ] **Step 3: Implement** — `vouch`: CAS `update pending_pairings set
  household_id=?, member_id=?, vouched_at=now() where code_hash=? and vouched_at is
  null and expires_at > now()`; then insert the wrap row **in the same tenant
  transaction** (`db.tenant(identity.householdId)`), so an unvouched session can
  never leave a stray wrap. `claim`: fall through to `pending_pairings` when
  `pairing_sessions` misses; require `vouched_at` set; reuse the existing CAS +
  `tokens.issueAccessOn/issueRefreshOn` block; return the wrap bytes.
- [ ] **Step 4: Run to watch them pass** — same command, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit**

```powershell
git add services/backend spec/openapi/backend.v1.yaml
git commit -m "feat(backend): phone vouch admits a PC with a wrapped household key"
```

---

### Task 5: Device list and revocation

**Files:**
- Create: `services/backend/src/main/kotlin/com/hopa/backend/pairing/DeviceRoutes.kt`
- Modify: `spec/openapi/backend.v1.yaml` (`/v1/devices`, `/v1/devices/{id}`)
- Test: `services/backend/src/test/kotlin/com/hopa/backend/pairing/DeviceRevokeTest.kt` (Testcontainers)

**Interfaces:**
- Consumes: `PgAuth`, `DeviceRegistry`, `PgDevices`.
- Produces: `GET /v1/devices` → `[{id, platform, name, lastSeenAt, revokedAt,
  current}]`; `DELETE /v1/devices/{id}` → 204; revoke =
  `PgAuth.revoke` (token family + cache clear) **+** delete the `household_key_wraps`
  row **+** `DeviceRegistry.kick(deviceId)` closing the live socket.

- [ ] **Step 1: Write the failing tests** — a connected FakeDevice is revoked:
  its socket closes within 5 s; a refresh with its token → `E_AUTH`; the wrap row is
  gone; the device still appears in `GET /v1/devices` with `revokedAt` set;
  revoking another household's device id → `E_TENANT` and no row changed. Add the
  Review-Focus case: revoke with a pre-rotated refresh token (family already
  rotated once) is still cut off.
- [ ] **Step 2: Run to watch them fail** — expected compile error on `DeviceRoutes`.
- [ ] **Step 3: Implement** — add `fun kick(deviceId: String)` to `DeviceRegistry`
  (close the session; `leave` already handles roster fan-out) and
  `fun revokeDevice(id: String)` to `PgDevices` running all three effects in one
  tenant transaction. Routes use the standard bearer auth + `respondError`.
- [ ] **Step 4: Run to watch them pass.**
- [ ] **Step 5: Commit**

```powershell
git add services/backend spec/openapi/backend.v1.yaml
git commit -m "feat(backend): device list and 5-second revocation"
```

---

### Task 6: diag types, admin auth and the diagnostic sender

**Files:**
- Create: `services/backend/src/main/kotlin/com/hopa/backend/admin/AdminRoutes.kt`
- Create: `services/backend/src/main/kotlin/com/hopa/backend/admin/AdminAuth.kt`
- Modify: `services/backend/src/main/kotlin/com/hopa/backend/protocol/EnvelopeValidator.kt` (`diag.*` types)
- Modify: `services/backend/src/main/kotlin/com/hopa/backend/tasks/TaskRouter.kt` (server-authored create)
- Modify: `spec/openapi/backend.v1.yaml`, `spec/schemas/gateway.v1.schema.json` (three new types)
- Test: `services/backend/src/test/kotlin/com/hopa/backend/admin/DiagSendTest.kt` (Testcontainers + FakeFcm + FakeDevice)

**Interfaces:**
- Consumes: slice B's `admins`/`admin_sessions` tables + seed (Task B2); `TaskRouter`,
  `DeviceRegistry`, `JsonEventLog`.
- Produces: `class AdminAuth(db, clock) { fun adminFor(token: String):
  AdminIdentity? }` (reads `admin_sessions`; the `POST /v1/admin/session` route
  that issues them belongs to slice B Task 5 — land in either order, keep these
  signatures identical); `GET /v1/admin/devices?household=` (rows + `online` from
  the registry — slice B's lookup page calls this); `POST /v1/admin/devices/{id}/diag` → creates a
  server-authored task; `EnvelopeValidator.TYPES` gains `diag.ping`, `diag.state`,
  `diag.reset`; `TaskRouter.createServerTask(adminId, identity-of-target, type, body)`
  with `from_device` null (V2 made it nullable) and an audit event
  `{kind: "diag.send", adminId, deviceId, taskId, trace}`.

- [ ] **Step 1: Write the failing tests** — an admin token (issued by slice B's
  Task 5 route, or minted directly into `admin_sessions` in the test if that task
  has not landed) authorizes the routes; `diag.ping` to an **online** FakeDevice
  arrives as a plaintext envelope
  with `from: "server"` and echoes back a `task.result`; to an **offline** device it
  queues and FakeFcm records exactly one high-priority wake; a **revoked** target →
  `E_SCHEMA` (not queued forever); a household device token used on
  `/v1/admin/*` → `E_AUTH`; a diag send with `type: "task.create"` (smuggling) →
  `E_SCHEMA`; the audit log line names the admin and the task.
- [ ] **Step 2: Run to watch them fail.**
- [ ] **Step 3: Implement** — diag bodies bypass payload encryption by type in the
  validator (plaintext allowed **only** for the three `diag.*` types); schema/fixture
  files gain the types; admin routes use `AdminAuth` exactly as the gateway uses
  `AuthPort`.
- [ ] **Step 4: Run to watch them pass** — full module:
  `apps\android\gradlew.bat -p services/backend test` — everything green (Tasks 3–6
  plus all pre-existing tests).
- [ ] **Step 5: Commit**

```powershell
git add services/backend spec
git commit -m "feat(backend): admin diag tasks and presence, audit-logged"
```

---

### Task 7: Sidecar — admission client (QR, poll, claim, unwrap)

**Files:**
- Create: `apps/windows/sidecar/src/main/kotlin/com/hopa/sidecar/BackendPairing.kt`
- Create: `apps/windows/sidecar/src/main/kotlin/com/hopa/sidecar/QrRender.kt`
- Modify: `apps/windows/sidecar/settings.gradle.kts` (include `:backend` →
  `../../core/backend`, mirroring `apps/android/settings.gradle.kts:50-51`)
- Modify: `apps/android/gradle/libs.versions.toml` (zxing `core`)
- Test: `apps/windows/sidecar/src/test/kotlin/com/hopa/sidecar/BackendPairingTest.kt`
  (MockWebServer, like the Android session fixtures)

**Interfaces:**
- Consumes: `core/backend` client types (`BackendClient` or the pairing helpers the
  Android app uses), `KeyWrap`, `BackendCredentials`.
- Produces: `class BackendPairing(client, credentials) { suspend fun
  admit(backendUrl: String): AdmissionResult }` — generates the XDH keypair, POSTs
  the pending session, renders the QR (Unicode block matrix to stdout **and** a PNG
  next to the sidecar log) plus the `hopa://` string for paste fallback, polls until
  `vouched`, claims, unwraps the content key, stores credentials + key; and prints
  the key fingerprint `sha256(publicKey)[0..8]` the phone also shows.

- [ ] **Step 1: Write the failing test** — against a MockWebServer serving the
  pending/vouch/claim sequence: `admit` returns a stored credential with the
  unwrapped 32-byte key equal to the test's content key; the rendered QR matrix
  encodes the exact `hopa://pair?...` string (decode it back with zxing and compare).
- [ ] **Step 2: Run to watch it fail** — Run:
  `apps\android\gradlew.bat -p apps/windows/sidecar test --tests "*BackendPairingTest*"`
  Expected: compile error.
- [ ] **Step 3: Implement** — zxing `QRCodeWriter` → `BitMatrix` → Unicode blocks +
  PNG; poll every 2 s with a 5-min deadline; no LAN-bridge code anywhere in the file.
- [ ] **Step 4: Run to watch it pass.**
- [ ] **Step 5: Commit**

```powershell
git add apps/windows/sidecar apps/android/gradle/libs.versions.toml
git commit -m "feat(sidecar): admit the PC by QR with a wrapped household key"
```

---

### Task 8: Sidecar — gateway envelopes both directions

**Files:**
- Create: `apps/windows/sidecar/src/main/kotlin/com/hopa/sidecar/BackendGateway.kt`
- Test: `apps/windows/sidecar/src/test/kotlin/com/hopa/sidecar/BackendGatewayTest.kt`

**Interfaces:**
- Consumes: Task 7's stored credentials; the Android app's `BackendLink`/envelope
  client from `core/backend`; `KeyWrap`.
- Produces: `class BackendGateway(link, keys) { suspend fun run() }` — connects
  `device.hello`, drains the queue, decrypts incoming `task.create` bodies with the
  content key, hands them to the existing sidecar engine, encrypts and sends
  `task.result`/`task.error` back; `diag.ping` answers with a plaintext echo per the
  exemption.

- [ ] **Step 1: Write the failing test** — a real Ktor WebSocket server double (the
  mirror of Android's `FakeDevice`): sends an encrypted `task.create`, expects a
  ciphertext `task.result` that decrypts to the engine's answer; sends a plaintext
  `diag.ping`, expects the echo; killed-socket reconnect resends `hello` and drains.
- [ ] **Step 2: Run to watch it fail.**
- [ ] **Step 3: Implement** — reuse the shared link/client; no new protocol code
  beyond what `core/backend` already provides.
- [ ] **Step 4: Run the whole sidecar suite** — `apps\android\gradlew.bat -p
  apps/windows/sidecar test` — green.
- [ ] **Step 5: Manual end-to-end (evidence)** — one real phone + one real PC
  against the deployed EU instance: camera-scan admission, one task each way with
  encrypted bodies, recorded with trace ids in
  `docs/project/evidence/B2-accounts.md` (§8 rows 3).
- [ ] **Step 6: Commit**

```powershell
git add apps/windows/sidecar docs/project/evidence/B2-accounts.md
git commit -m "feat(sidecar): encrypted gateway tasks both directions"
```

---

## Slice boundary

Slice B (`tasks/week2b.md`) owns the accounts/session endpoints, the V2 migration
with `admins` and the security-definer lookups, the console, and the Android client
work. Tasks 4–6 here **depend on B's migration landing first**; Tasks 1–3 are
independent and can start Sunday. Sync v0 is B's protected item and is planned in a
Monday slice of its own.
