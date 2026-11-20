# An on-device model for the agent loop

Status: planned (2026-11-20)

The step that calls a model API today (DeepSeek, or any OpenAI-compatible
endpoint) runs on the phone. Everything else stays as it is: the browser, the
backend gateway, the bridge, push and telemetry are out of scope.

## Where the model sits today

```
core/contracts/model/ModelPort          suspend fun complete(ModelRequest): ModelReply
core/inference/client/ModelFactory      create(provider, endpoint, apiKey, model)
  -> DemoModelClient                    no key, canned answer
  -> OpenAiClient                       OkHttp, /chat/completions
core/inference/choice/ProviderChoice    DEMO | CLOUD | LAN
core/inference/choice/ProviderState     CLOUD | LAN | DEMO | NO_MODEL
core/inference/choice/ProviderStateResolver, ProviderChoiceRules
```

The loop never touches HTTP: `ModelPlanSource`, `ModelStepPolicy` and
`ReplayAnswer` in `:runtime`, and `ChatDependencies.request/runTurn`, all take a
`ModelPort`. So the swap is one more `ProviderChoice` and one more `ModelPort`.

The prompt is assembled from stored facts, skills, the app catalogue and the
screen model; it is sized for a 128k-context API today.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Engine | llama.cpp via JNI, GGUF | grammar-constrained output, mmap, any GGUF, predictable on arm64 |
| Quantisation | Q4_K_M | the size/quality knee for 1–4B |
| Format discipline | GBNF grammar per schema | a 2–4B model does not hold a JSON schema in free mode; the loop parses it |
| Weights delivery | backend first, local fallback | the gateway will serve models the way it serves the catalogue; until that endpoint exists, a curated catalogue plus an imported file is the fallback |
| Provider | `ProviderChoice.ON_DEVICE` | one more branch in `ModelFactory`, loop untouched |
| Profiles | small: 1.5–2B, large: 3–4B, chosen by RAM | 6 GB devices cannot hold a 4B comfortably |

## Model catalogue

| Profile | Candidates | Q4 size | License | Expected |
|---|---|---|---|---|
| Small (≤6 GB RAM) | Qwen3-1.7B, Phi-4-mini-reasoning (1.5B) | 1.0–1.2 GB | Apache-2.0 / MIT | 10–20 tok/s |
| Large (8 GB+) | Qwen3-4B, Phi-4-mini (3.8B) | 2.4–2.6 GB | Apache-2.0 / MIT | 5–12 tok/s |

Gemma is excluded: its licence is not Apache/MIT.

## Where the weights come from

The catalogue already has this shape: `CatalogClient.fetch(knownVersion)` asks
`GET /v1/knowledge` with `If-None-Match`, `KnowledgeUpdater` polls at most once
per six hours, `FileKnowledgeStore` keeps the newest valid copy and falls back
to the previous one, then to the bundled asset. Models mirror it exactly, with
one port and two sources:

```
ModelSource                       manifest(knownVersion): ModelManifest?
  BackendModelSource              GET /v1/models, ETag = digest (future; no endpoint yet)
  LocalModelSource                the curated catalogue and an imported file (today)
```

- **Storage**: `filesDir/models/<id>-<digest>.gguf`, written to a `.part` file,
  hashed while streaming, renamed only when the sha256 matches. The previous
  file stays until the new one is verified, so a failed download is a no-op.
- **Fallback order**: downloaded model with a matching digest -> imported file ->
  explicit `ModelMissing` error. Never a silent cloud fallback.
- **Versioning**: the manifest carries `version: "sha256:..."` like the
  knowledge bundle, so a 304 keeps the current copy; the receipt can name the
  model version a turn used, the way it names the knowledge version.
- **Manifest draft** (wire names are the contract, so they are fixed here even
  though the endpoint does not exist yet):

```json
{
  "schemaVersion": 1,
  "version": "sha256:...",
  "models": [
    {
      "id": "qwen3-1.7b-q4km",
      "profile": "small",
      "file": "qwen3-1.7b-q4_k_m.gguf",
      "bytes": 1120000000,
      "sha256": "...",
      "license": "Apache-2.0",
      "minRamGb": 4,
      "contextTokens": 8192
    }
  ]
}
```

- **Today**: the endpoint returns 401/404, `BackendModelSource` returns null, and
  the app uses the curated catalogue with an imported file, exactly the way the
  knowledge updater keeps the bootstrap until the endpoint exists.

## Slices

Each slice compiles, keeps CI green, and can be merged on its own.

### S1 - the seam (no native code)

- `ProviderChoice.ON_DEVICE`, `ProviderState.ON_DEVICE`;
  `ProviderChoiceRules.choose` returns it when the provider string is
  `on-device` and a model file is present.
- `LocalModel : ModelPort` in a new `:inference-local` module. Its first
  version has no engine: `complete` fails with `ModelNotLoaded` and the state
  flow says why.
- `ModelFactory` returns `LocalModel` for `ON_DEVICE`; `OpenAiClient` is never
  constructed in this mode (asserted in a test).
- Settings: a "Model on the phone" page that shows provider, file, size and
  engine status; the toggle is disabled with a reason when no file is present.
- Tests: provider resolution, factory branch, stub error text.

### S2 - weights and the source port

- `ModelSource` with `BackendModelSource` (a `CatalogClient`-style call to
  `GET /v1/models`, ETag/304, paired token; returns null while the endpoint
  returns 401/404) and `LocalModelSource` (the curated catalogue, plus whatever
  the person imported). The default wiring prefers the backend and falls back
  to local without a visible failure.
- Import through SAF (`ACTION_OPEN_DOCUMENT`) for the no-endpoint case: copy to
  `filesDir/models/` through a `.part` file, hash while copying, atomic rename,
  size and free-space checks, delete action.
- `LocalModelCatalogue`: the two profiles with their file names, sizes, hashes,
  licences and minimum RAM; `LocalModelRecommendation` picks by
  `ActivityManager.memoryClass` and free storage.
- The page shows: no file / downloading / verifying / ready / too large / wrong
  hash / engine missing, plus the recommended profile and where the file came
  from (gateway or imported). Every installed row has Delete with its size.
- A cleanup pass at app start removes stale `.part` files and downloaded files
  that no manifest or selection refers to; the page shows the total the models
  occupy and offers "Delete all".
- Tests: catalogue selection by RAM, hash mismatch, size rejection, a null
  manifest keeping the local file, delete clearing both file and selection, the
  cleanup pass leaving a current version and dropping orphans. Screenshot
  goldens for the page states, including the delete confirmation.

### S3 - prompt budget and grammar (pure Kotlin, no JNI)

- `PromptBudget`: input ceiling per profile (small 2.5k, large 6k tokens; the
  context floor is 4k/8k). Trimming order: catalogue to the 12 most relevant
  entries, skills to 3, facts to 8, history to the last 4 messages, screen model
  to its two fields. Every trim is explicit and logged into the run record.
- `PlanGrammar` (GBNF): the plan object (`steps[]`, each with `kind`,
  `target`, `reason`) and the tool-call object; a rejection table of malformed
  answers.
- `ModelReply` carries `truncated` and `grammar` fields so the loop can retry
  once with the error appended instead of guessing.
- Tests: trimming order and ceilings; grammar accepts the schema and rejects a
  free-text answer.

### S4 - the engine

- `:inference-local` gains `src/main/cpp`: llama.cpp pinned by tag, CMake,
  `arm64-v8a` (+ `x86_64` for the emulator), JNI bridge with `load`, `complete`,
  `cancel`, `release`.
- One session at a time, weights `mmap`ed, KV cache sized by profile.
- `ModelPort.complete` blocks on the native call and maps tokens into
  `ModelReply`; cancellation maps to `llama` abort.
- Proguard keep rules for the JNI entry points; NDK version pinned.
- Tests: the JVM side of the reply parser and the grammar string builder; the
  native path is covered on the emulator in S5.

### S5 - integration on the emulator

- Import a 0.5–1B model on the API 35 emulator, select on-device, run "open the
  calculator" and assert: steps stream into chat, the action runs, the run
  record appears, no `OpenAiClient` is constructed, and the app survives a
  cancel mid-generation.

### S6 - measurement

- tok/s, first-token latency, RSS, and a 10-task success comparison against the
  cloud model (open an app, toggle a setting, read a screen). The numbers go
  into this document; the default stays cloud until they justify the switch.

### S7 - distribution from the gateway (blocked on the endpoint)

- Serve the manifest at `GET /v1/models` and the blob at `GET /v1/models/{id}`,
  with the same pairing token and ETag behaviour as `/v1/knowledge`.
- `BackendModelSource` starts returning a manifest; the download path in S2 is
  reused unchanged, so the phone goes from "import a file" to "tap Download"
  without a second storage or verification implementation.
- The receipt names the model version, like `MessageProof` names the knowledge
  version today.
- Until the backend endpoint exists this slice stays documented and unimplemented;
  no placeholder task or dead screen ships in the app.

## Choosing the model in the app

Yes, there is a place, and it is one screen:

```
Settings -> Model
  Provider:  [ Cloud (API key) ] [ LAN ] [ On-device ]
  On-device:
    recommended: Qwen3-1.7B Q4 (small phone)   [ Download / Import file ]
    installed:   qwen3-1.7b-q4km.gguf  1.1 GB  gateway | imported   [ Delete ]
    state:       ready | verifying | missing | wrong hash | engine missing
  Cloud/LAN keep the fields they have today.
```

- The provider choice is a segmented control at the top of the existing Model
  page; choosing On-device hides the endpoint and key fields and shows the model
  list instead.
- The chat header shows which model produced the turn ("on-device" or the model
  name), so a person never wonders where the answer came from.
- A missing file is an explicit notice in the composer with a shortcut to the
  page, not a silent switch to the cloud.
- The Capabilities "Finish setup" card links to the same page.
- Screenshot goldens cover the page in its import, ready and error states.

## Storage lifecycle

A model is 1–3 GB; it must be as easy to remove as it is to add, and nothing
may stay behind.

- **Delete** sits on every installed model row with its size ("1.1 GB"). It
  unloads the engine first, then removes the file and the selection.
- If the deleted model was the active provider, the app does not fall back
  silently: it switches to cloud and says so ("On-device model removed, switched
  to the cloud") in the composer and on the Model page.
- **The page accounts for the total**: "On-device models use 1.1 GB", with a
  "Delete all" action next to it.
- **Cleanup pass** at app start and after every successful download removes:
  `.part` files older than 24 hours (a crash mid-download), and any downloaded
  file that neither the current manifest nor the current selection refers to.
- **Retention**: current version plus the previous one. When free space drops
  below twice the largest catalogue model, the previous version goes first.
- **Imported files are copies we own**: SAF import copies into `filesDir`, the
  original in Downloads is untouched, so Delete is always safe and always frees
  the space.
- Deleting a model touches nothing else: chats, the run log and settings stay.

## Acceptance criteria

1. In `ON_DEVICE` with the network off, "open <app>" completes and the action
   is recorded in the run log.
2. The plan answer parses as JSON 20/20 on five fixed prompts.
3. No `OpenAiClient` is constructed in `ON_DEVICE` (unit test on the factory).
4. RSS: under 1.5 GB for the small profile, under 3.5 GB for the large one.
5. First token under 3 s after the model is warm; the APK grows by less than
   5 MB without weights.
6. A missing or corrupt model file produces an explicit error, never a silent
   cloud fallback.
7. With the backend endpoint absent (401/404 today), the app keeps working from
   the local catalogue and an imported file; when the endpoint arrives, the same
   path downloads and verifies without new storage or hashing code.
8. Deleting a model frees its bytes immediately, leaves no `.part` file, and an
   active on-device provider falls back with a visible notice.

## Risks

| Risk | Mitigation |
|---|---|
| A small model plans badly | grammar, few steps, retry once, prefer the small tool set |
| Memory pressure on 6 GB devices | small profile by default, `isLowRamDevice` check, single session |
| Heat and battery | short plans, explicit "on-device turns are slower" note, no background inference |
| NDK build complexity in CI | one ABI for release, `x86_64` only for the emulator, pinned NDK and llama tag |
| Licence | Apache-2.0 / MIT candidates only, recorded in the catalogue |
| Weights shipping | import only in the first version; the APK never carries a multi-GB file |
| Partial or corrupt download | `.part` file, hash while streaming, atomic rename, the previous copy stays |
| The gateway has no model endpoint yet | the local source is the fallback; no dead screen or placeholder task ships |

## Out of scope

Browser traffic, the backend gateway, pairing and push, the PC bridge, voice
transcription (that goes to the bridge today) and telemetry. Baseline profile
stays deferred for the same reason as before: it needs a device run.
