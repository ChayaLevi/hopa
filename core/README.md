# core - the client engine

Nine Kotlin modules that the Android app and the Windows sidecar both build. No
`main`, no UI, no Android SDK.

| module | holds | package | depends on |
|---|---|---|---|
| `contracts` | wire types, port interfaces, model types, approval data classes | `com.hopa.contracts.*` | `codec` |
| `codec` | JSON helpers: `HopaJson`, `CanonicalJson` | `com.hopa.codec.*` | nothing |
| `catalog` | the app catalogue, approval vocabulary, prompt rules, and the router that walks them | `com.hopa.catalog.*` | `contracts`, `codec` |
| `observation` | the screen model: nodes, anchors, the observer, the screen source | `com.hopa.observation.*` | `contracts` |
| `records` | a run as records, and the chain that links them | `com.hopa.records.*` | `contracts`, `codec` |
| `prompt` | the system prompt, catalogue and screen blocks, the context budget | `com.hopa.prompt.*` | `contracts`, `catalog` |
| `agent` | one turn for every client, the loop, the gate, the executor, recipes | `com.hopa.agent.*` | all of the above |
| `providers` | the adapters behind `ModelPort`: provider choice, the OpenAI-compatible client, the demo client | `com.hopa.providers.*` | `contracts`, `codec` |
| `transport` | device and backend transport: the PC bridge client and the gateway client | `com.hopa.transport.*` | `contracts`, `codec` |

```
apps/android --+
                +--> agent --> prompt --> catalog --> contracts --> codec
apps/windows ---+      |  \--> records -------^
                       \--> observation -----^
                providers --> contracts
                transport --> contracts
```

## Rules

1. **`codec` is the leaf.** JSON helpers only; no types, no logic.
2. **`contracts` is the vocabulary.** The types every other module passes around;
   it depends on `codec` because `RunRecordSerializer` is bound to `RunRecord` by
   annotation and needs the shared `Json`.
3. **`catalog` is one concern.** The app catalogue, the approval vocabulary and
   the router that picks a target always change together. Most of it is generated
   by `tools/scripts/build-*.py`, so the module carries no ktlint.
4. **`agent` is the only place a goal becomes steps.** Recipes, the plan gate,
   checkpoints and the loop live here, so chat, a parallel run and a delivered
   task all take the same path. A client supplies ports, not policy.
5. **The engine imports no Android, no HTTP.** `checkNoAndroidImports` in Gradle
   and `tools/checks/check-android-structure.py` fail the build on a violation.
6. **Only `providers` and `transport` speak HTTP.** `contracts` defines
   `ModelPort`; `providers` implements it, and changes when a provider's API does.
   A device reached over the bridge (`AdbDevice`) is a port in `transport`.
7. **A local model is not `providers`.** An on-device runtime is a separate
   adapter behind the same `ModelPort`.

## Generated data

`catalog` is mostly written by scripts from `spec/` and `services/brain/knowledge`.
Do not edit those files by hand; `build-*.py --check` runs in CI and fails on a
stale commit. See `catalog/README.md`.
