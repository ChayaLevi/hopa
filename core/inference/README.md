# inference - where a model answer comes from

The adapters behind `ModelPort`. Today an OpenAI-compatible endpoint and a demo
client; tomorrow a local runtime behind the same port. Nothing here knows the
loop, and the loop does not know this module.

## Packages, bottom first

| package | what lives here | depends on |
|---|---|---|
| `choice` | `ProviderChoice`, `ProviderChoiceRules`, `ProviderState`, `ProviderStateResolver`: which provider the settings select and whether it is ready | - |
| `client` | `OpenAiClient`, `DemoModelClient`, `ModelFactory`: the things that answer, and the factory that builds one | contracts, choice |

## Rules

1. **One port, several clients.** Every adapter implements `ModelPort`; adding a
   local model means adding a class, not a branch in the loop.
2. **No key means demo.** `ModelFactory` returns `DemoModelClient` when nothing
   is configured, so a fresh install can answer before any setup.
3. **Capabilities belong to the adapter.** Context size and vision are properties
   of the model, not of the app; the prompt asks the adapter.
4. **No Android, no storage.** Settings are read by the client and passed in; this
   module only speaks HTTP.

## Tests

```
./gradlew :inference:test :inference:ktlintCheck
```

`OpenAiClientTest` runs against a local mock server; no key and no network.
