# bridge - the link to the user's PC

The PC companion link: a websocket to a helper on the user's own machine, which
drives the phone over adb. Local by design - a relay may carry the frames, but
commands run on that machine.

## Packages, bottom first

| package | what lives here | depends on |
|---|---|---|
| `session` | `BridgeClient`, `BridgeSession`, `BridgeException`: the socket, its frames and its failure | contracts |
| `chat` | `ChatTransport`, `BridgeChatTransport`: the chat channel over the session | contracts, session |
| `device` | `AdbDevice`, `BridgeObservation`: the phone as a `DevicePort` - actions out, screen back | contracts, session |

## Rules

1. **No Android.** `checkNoAndroidImports` enforces it; the sidecar uses the same
   module.
2. **`AdbDevice` is a `DevicePort`.** It lives here because it travels over the
   bridge, but it drives the phone: tap, type, observe, screenshot.
3. **Failures are typed.** `BridgeException` carries the reason, so the client can
   say "the PC is not connected" instead of a stack trace.
4. **No knowledge of the agent.** This module moves frames; what to do with a
   screen is `agent`'s business.

## Tests

```
./gradlew :bridge:test :bridge:ktlintCheck
```

The session tests run against a local mock websocket; nothing here needs a PC.
