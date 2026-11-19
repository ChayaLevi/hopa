# runlog - the journal of a run

Every executed step becomes one `RunRecord` while the turn runs, and the same
records are the history afterwards. This module builds them, chains them by hash
and hands them to a recorder. It never decides or taps.

## Packages, bottom first

| package | what lives here | depends on |
|---|---|---|
| `record` | `RunRecordFactory`, `RunRecordDraft`, `RunChain`: turn a step into a record and link it to the previous one | contracts |
| `store` | `RunRecorder`, `InMemoryRunRecorder`: the port a client implements, and the in-memory one tests and the sidecar use | contracts, record |

## Rules

1. **A journal, not a database.** The records are written through `RunRecorder`;
   storage is the client: Room in the app, files in the sidecar.
2. **Append-only and chained.** `RunChain` links every record to the previous hash,
   so a rewritten history is detectable.
3. **Written during the run.** The executor records each step as it happens; a
   crashed turn still leaves what it did.
4. **No policy, no Android, no network.**

## Tests

```
./gradlew :runlog:test :runlog:check
```

`RunChainTest` proves the chain survives a reload; `RunRecorderClearTest` pins what
clearing means.
