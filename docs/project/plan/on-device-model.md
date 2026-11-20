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
| Classes | router/tiny, fast, balanced, tools, large, reasoning | one size does not fit a 4 GB and a 12 GB phone; the bench keeps one winner per class |

## Model catalogue

Licence is not a blocker while we experiment; every entry still carries its
licence and the app shows it before a download. Sizes are Q4_K_M and
approximate; speeds are CPU-only on a modern phone SoC and depend on the chip,
so the bench in S6 decides, not the table.

Each entry answers three questions for the person choosing: **strong at**,
**weak at**, and **worth knowing**.

### Router and tiny (0.2–0.6 GB)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| Gemma 3 270M | 0.2 GB | fastest answer, best instruction following at this size, multilingual | tool choice, multi-step plans | the cheapest first filter: "command or small talk" |
| SmolLM2-360M | 0.3 GB | speed, Apache-2.0, tiny memory | English-centric, weak plans | good fallback on 4 GB devices |
| Qwen3-0.6B | 0.4 GB | routing, short answers, thinking mode if wanted | anything multi-step | disable thinking for the loop or it burns the budget |

### Fast (0.8–1.2 GB)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| Qwen3-1.7B | 1.1 GB | best all-round at this size, decent tool choice, Apache-2.0 | long plans, rare apps | the default for ≤6 GB phones; thinking off for turns |
| Gemma 3 1B | 0.9 GB | instructions, 32k context, 140+ languages | tool choice, 256k vocabulary inflates the file | the multilingual pick when Russian matters |
| SmolLM2-1.7B | 1.0 GB | speed, Apache-2.0 | planning, tool choice | lighter alternative when 1.7B Qwen is too slow |
| Llama 3.2 1B | 0.8 GB | short English commands, mature tooling | long context in practice, licence terms | good baseline to measure others against |

### Balanced (2.2–3.0 GB)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| Qwen3-4B | 2.5 GB | planning, tool choice, reasoning, Apache-2.0 | speed on mid-range SoCs, heat | the default for 8 GB phones; thinking off |
| Phi-4-mini 3.8B | 2.5 GB | reasoning, precise instructions, MIT | slower, heavier context cost | best "careful" model for multi-step plans |
| Gemma 3 4B | 2.7 GB | 128k context, languages, instruction following | size, tool choice | the multilingual balanced pick |
| SmolLM3-3B | 2.2 GB | strong at 3B, dual-mode thinking, Apache-2.0 | younger ecosystem, fewer GGUFs | good middle point if 4B is too much |
| Granite 3.3 2B | 1.8 GB | strict JSON, enterprise phrasing | general chat, creativity | worth testing for schema discipline |
| Falcon3-3B | 2.2 GB | 32k context, permissive terms | tool choice | solid generalist, not tool-tuned |
| MiniCPM4-4B | 2.6 GB | efficiency, long context per byte | tool choice, smaller community | interesting for 6 GB phones |

### Tool-calling specialists (1–3 GB)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| xLAM-2-1B | 0.8 GB | function calling, compact | general reasoning, chat | the cheapest way to get a correct tool choice |
| xLAM-2-3B | 2.0 GB | tool choice close to big models | conversation, explanations | try it against Qwen3-4B on the bench |
| Hammer 2.1-3B | 2.2 GB | tool calling on a Qwen base | breadth of knowledge | verify the GGUF template before adding |

### Large (5 GB, 12 GB RAM and up, experimental)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| Qwen3-8B | 5.0 GB | the best quality we can run on a phone, Apache-2.0 | 2–5 tok/s, heat, memory pressure | offer only on 12 GB+ with a warning |
| Llama 3.1 8B | 4.9 GB | mature ecosystem, good general quality | licence, speed, heat | the alternative when Qwen3-8B misbehaves |

### Reasoning models (listed, not recommended for the loop)

| Model | Size | Strong at | Weak at | Worth knowing |
|---|---|---|---|---|
| Phi-4-mini-reasoning | 2.5 GB | math and logic | latency: long chains before an answer | fine for "explain", bad for "open the app" |
| DeepSeek-R1-Distill-Qwen-1.5B | 1.1 GB | small reasoning tasks | token-heavy, slow, plans arrive late | keep out of the default list |

### Rules for adding an entry

- A maintained GGUF must exist and its sha256 is pinned in the catalogue.
- The chat template and special tokens are checked on the emulator before the
  entry ships.
- Every entry states context size, expected speed class, RAM floor, licence and
  whether it is tool-tuned.
- An entry that scores worse than another in the same class on the S6 bench is
  removed, not kept "for choice".

Each model is a card, not a row: name, size, licence, **strong at**, **weak at**,
**worth knowing**, expected speed, RAM floor, and a "recommended for this phone"
badge computed from RAM and free storage.

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
- `LocalModelCatalogue`: every entry carries id, display name, class, parameters,
  quantisation, bytes, store URL, sha256, licence and licence URL, RAM floor,
  context size, expected speed class, whether it is tool-tuned, and three text
  lists the card renders: **strong at**, **weak at**, **worth knowing**.
  `LocalModelRecommendation` picks by `ActivityManager.memoryClass` and free
  storage.
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

### S8 - per-chat model (optional, after S6)

- A model chip in the composer writes the choice onto the conversation; new
  chats inherit the active model.
- The prompt budget, grammar and step ceiling follow that model; the receipt
  names it.
- Only worth building if S6 shows the small model is good enough for the short
  commands and bad enough for planning that switching pays off.

## Footprint: RAM, disk, APK

- **APK**: weights are never inside. The native engine adds roughly 3–4 MB for
  `arm64-v8a` in a release build (llama.cpp + ggml, stripped), and about 3–4 MB
  more when `x86_64` is built for the emulator, so a debug APK grows ~7–8 MB.
- **Disk per model**: router 0.2–0.6 GB, fast 0.8–1.2 GB, balanced 1.8–3.0 GB,
  large ~5 GB. The Model page states the size before a download and the total
  installed.
- **Peak during an update is twice the file**: the previous copy stays until the
  new one passes sha256, so a 2.5 GB model needs ~5 GB free to update in place.
  The free-space check uses that number, not the file size.
- **RAM**: model + KV cache + the app. 4 GB phone: a 1.1 GB model. 6 GB: a 1.1–2
  GB model. 8 GB: a 2.5 GB model with background apps being evicted. 12 GB and
  above: the 5 GB large model, still at 2–5 tok/s on CPU.
- **Verdict for an 8 GB flagship (S24 class)**: an 8B Q4 (~5 GB) technically
  loads but leaves no headroom for the system, hits low-memory kills and
  throttles within minutes. It stays in the Large class, greyed out below 12 GB.
- **NPU**: the first version is CPU-only. Exynos/Snapdragon NPUs are not
  engaged by llama.cpp, so the numbers are CPU numbers; NPU offload would be a
  separate investigation.

## First run and download

- **First open downloads nothing.** The app starts in cloud/demo mode and works
  immediately; an optional card on Capabilities offers "Run the model on this
  phone (1.1 GB)".
- **On-device is a deliberate choice** on `Settings -> Model`: the card states
  size, peak space, licence, expected speed and "downloading never starts by
  itself".
- **The download** runs in a foreground service (`dataSyncing`) with a system
  notification: progress, Pause, Cancel. It survives the app being backgrounded
  or the screen going off, and resumes after a reboot. Wi-Fi only by default,
  with an explicit "use mobile data" override and a time estimate.
- **While it downloads the app is fully usable**: chat answers through the
  cloud, runs and history work, and the chat header names the model answering.
  The on-device option stays disabled until the file verifies.
- **Finish**: sha256 verified -> "Model on the phone is ready" notification ->
  the switch becomes active and a one-tap switch shows the "On-device, data
  stays on this phone" banner in the composer.
- **Updates are never automatic**: the page shows "Update available (2.5 GB)"
  and waits for a tap.
- **Failures**: a dropped connection resumes; insufficient space is reported
  with both numbers and a smaller recommendation; a corrupt file is deleted and
  the previous copy kept; a missing file with on-device selected is an explicit
  composer notice, never a silent cloud fallback.
- If the product ever ships as on-device-only, first run shows a setup screen
  with two actions (download the recommended model, import a file) and chat
  stays unavailable until the model verifies - the same components, a different
  entry point.

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
- Every model is a card, not a row: size, licence, **strong at**, **weak at**,
  **worth knowing**, expected speed, RAM floor, and a "recommended for this
  phone" badge. A model that cannot fit this phone is still visible, greyed out,
  with the reason ("needs 12 GB RAM").
- A language note is part of the card: which models handle Russian well, because
  that changes the choice more than raw parameters.
- The chat header shows which model produced the turn ("on-device" or the model
  name), so a person never wonders where the answer came from.
- A missing file is an explicit notice in the composer with a shortcut to the
  page, not a silent switch to the cloud.
- The Capabilities "Finish setup" card links to the same page.
- Screenshot goldens cover the page in its import, ready and error states.

## Several models

A model is a catalogue entry plus an installed file, so "installed models" is a
list and one of them is active. Nothing in the loop cares which: it sees a
`ModelPort` and the model's spec.

- **Tiers in the catalogue**: Fast (1.5–2B), Balanced (3–4B), Large (8B,
  experimental, only offered at 12 GB RAM and above). Each carries its own
  context size, prompt budget, maximum plan steps and licence.
- **Installed list**: radio for the active model, size, source (gateway or
  imported), state, Delete per row, and the total at the bottom.
- **Import any GGUF**: the file is checked for the magic bytes, architecture and
  context size from its metadata before it is accepted; an unknown architecture
  is refused with the reason, not a crash.
- **One model in memory at a time**: switching unloads the current engine and
  loads the next, with a `Loading` state (seconds, not minutes). The previous
  weights stay on disk, so switching back needs no download.
- **The spec follows the model**: prompt budget, grammar and step ceiling are
  read from the selected model, not from a global setting.
- **Per-chat override** (a later slice): a chip in the composer picks a model
  for that conversation only, stored on the conversation; the run receipt
  already names the model and its version, so two runs stay comparable.

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

## Where the work lands

One rule decides the split: `core/*` modules are pure JVM (no Android imports,
enforced by `hopa.no-android` and `check-android-structure.py`), and everything
that needs a file, a context, a service or a native library lives under
`apps/android`. So the provider seam, the catalogue metadata, the prompt budget
and the grammar are JVM; storage, download, SAF and JNI are Android.

The only new module is `:core:localmodel` (`apps/android/core/localmodel`).

### S1 - the seam

| Where | What |
|---|---|
| `core/inference` | `ProviderChoice.ON_DEVICE`, `ProviderState.ON_DEVICE`, `ProviderChoiceRules`, `ProviderStateResolver`, the `ModelFactory` branch |
| `core/inference` | `LocalModel : ModelPort` in its first form: no engine, `ModelNotLoaded` |
| `apps/android/app/di` | the binding that hands the model source to the factory |
| `apps/android/feature/settings` | provider segmented control on the existing Model page, the On-device section stub |
| `apps/android/stores` | the provider string already exists; no new key yet |
| tests | `core/inference/src/test` (choice, factory, no `OpenAiClient`), `feature/settings/src/test` |

### S2 - weights and the source port

| Where | What |
|---|---|
| `core/inference` | `ModelSource` port, `ModelManifest` codec, `LocalModelCatalogue` metadata, `LocalModelRecommendation`, and `FileModelStore` (`.part`, sha256, atomic rename, cleanup, totals) — all JVM and tested on the JVM |
| `apps/android/core/localmodel` (new) | SAF import, the download service, and from S4 the engine host; it receives the storage root from the app |
| `apps/android/feature/settings` | model cards, import/download, delete, totals, states |
| `apps/android/stores` | the selected model id and the provider |
| `apps/android/app` | Hilt bindings, the service entry in the merged manifest |
| `apps/android/screenshots` | goldens for ready / downloading / error / too-large states |
| tests | catalogue and recommendation (JVM), hash/part/cleanup over a temp dir, settings view model |

### S3 - prompt budget and grammar

| Where | What |
|---|---|
| `core/inference` | `PromptBudget`, `PlanGrammar` (GBNF builders), rejection table |
| `core/contracts` | `ModelReply` gains `truncated` and `grammar` |
| `core/agent` | prompt assembly consumes the budget (facts, skills, catalogue, screen) |
| `core/agent/plan` | `ModelPlanSource` retries once on a rejected answer |
| tests | `core/inference` (ceilings, trimming order, grammar accept/reject), `core/agent` |

### S4 - the engine

| Where | What |
|---|---|
| `apps/android/core/localmodel` | `src/main/cpp` with the pinned llama.cpp, CMake, JNI (`load`/`complete`/`cancel`/`release`), the `LocalEngine` implementation, proguard keep rules |
| `core/inference` | `LocalModel` delegates to the `LocalEngine` interface, so the JVM side stays testable with a fake |
| `apps/android/app` | load/unload lifecycle, the engine is built with the verified file path |
| tests | JVM parser and grammar tests; the native path is covered in S5 |

### S5 - integration on the emulator

| Where | What |
|---|---|
| `apps/android/app` | wiring, service stop/start around a turn |
| `apps/android/feature/chat` | the "on-device" chip and the missing-model notice in the composer |
| `apps/android/feature/settings` | engine status and the switch |
| `apps/android/screenshots` | the chat-with-on-device banner, model page states |
| tests | none new; the emulator run is the test, recorded in this document |

### S6 - measurement

| Where | What |
|---|---|
| `apps/android/localbench` (debug-only, new) | the 10-task bench, tok/s and RSS capture, a JSON result file |
| `docs/project/plan/on-device-model.md` | the numbers and the winner per class |
| `apps/android/screenshots` | any UI the numbers change |

### S7 - distribution from the gateway

| Where | What |
|---|---|
| `services/backend` | `GET /v1/models` and `/v1/models/{id}`, ETag, paired token, manifest generation |
| `core/backend` | `ModelClient` next to `CatalogClient` (manifest fetch, 304) |
| `core/inference` | nothing; the manifest codec already exists from S2 |
| `apps/android/core/localmodel` | reuse the S2 downloader unchanged |
| `spec/` + `tools/scripts` | a manifest fixture and a drift check, the way the knowledge bundle has one |
| tests | backend endpoint tests, `core/backend` client test, the drift check in CI |

### S8 - per-chat model

| Where | What |
|---|---|
| `core/contracts` | `Conversation` gains a nullable model id |
| `apps/android/data` | Room migration for the new column |
| `apps/android/stores` | conversation store writes it |
| `apps/android/feature/chat` | the composer chip and the view model wiring |
| `core/contracts/chat/MessageProof.kt` | the receipt names the model actually used |
| tests | Room migration test, chat view model test |

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
9. Two installed models can be switched with one tap; the switch shows a
   loading state and needs no network. An imported GGUF with an unknown
   architecture is refused with the reason.
10. Every card states strong at, weak at and worth knowing before a download,
    and a model that cannot fit the phone is visible but disabled with the
    reason shown.

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
