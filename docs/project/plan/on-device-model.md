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
| Weights delivery | user-imported file (SAF) into `filesDir`, sha256 pinned | nothing hidden; no multi-GB in the APK |
| Provider | `ProviderChoice.ON_DEVICE` | one more branch in `ModelFactory`, loop untouched |
| Profiles | small: 1.5–2B, large: 3–4B, chosen by RAM | 6 GB devices cannot hold a 4B comfortably |

## Model catalogue

| Profile | Candidates | Q4 size | License | Expected |
|---|---|---|---|---|
| Small (≤6 GB RAM) | Qwen3-1.7B, Phi-4-mini-reasoning (1.5B) | 1.0–1.2 GB | Apache-2.0 / MIT | 10–20 tok/s |
| Large (8 GB+) | Qwen3-4B, Phi-4-mini (3.8B) | 2.4–2.6 GB | Apache-2.0 / MIT | 5–12 tok/s |

Gemma is excluded: its licence is not Apache/MIT.

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

### S2 - weights

- Import through SAF (`ACTION_OPEN_DOCUMENT`), copy to
  `filesDir/models/<name>.gguf` with a progress state, sha256 check against a
  pinned value, size and free-space checks, delete action.
- `LocalModelCatalogue`: the two profiles with their file names, sizes, hashes,
  licences and minimum RAM; `LocalModelRecommendation` picks by
  `ActivityManager.memoryClass` and free storage.
- The page shows: no file / verifying / ready / too large / wrong hash / engine
  missing, plus the recommended profile.
- Tests: catalogue selection by RAM, hash mismatch, size rejection. Screenshot
  goldens for the page states.

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

## Risks

| Risk | Mitigation |
|---|---|
| A small model plans badly | grammar, few steps, retry once, prefer the small tool set |
| Memory pressure on 6 GB devices | small profile by default, `isLowRamDevice` check, single session |
| Heat and battery | short plans, explicit "on-device turns are slower" note, no background inference |
| NDK build complexity in CI | one ABI for release, `x86_64` only for the emulator, pinned NDK and llama tag |
| Licence | Apache-2.0 / MIT candidates only, recorded in the catalogue |
| Weights shipping | import only; no downloader in the first version |

## Out of scope

Browser traffic, the backend gateway, pairing and push, the PC bridge, voice
transcription (that goes to the bridge today) and telemetry. Baseline profile
stays deferred for the same reason as before: it needs a device run.
