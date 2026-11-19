# observation - the screen as data

Turns whatever a client can see into one `Observation`: a tree with stable refs,
anchors, limits and a hash. No Android here: the accessibility tree, the hidden
display and the bridge all deliver the same shape.

## Packages, bottom first

| package | what lives here | depends on |
|---|---|---|
| `tree` | `NodeAnchor`, `ObservationLimits`: a node addressed by what it says, not where it sits; the size caps | contracts |
| `produce` | `ObservationProducer`, `ScreenPath`, `ScreenPathOrder`: walk the tree, assign refs, redact, cap, hash | contracts, tree |
| `source` | `ScreenSource`, `ScreenSourceDevice`: ask each surface for one observation, then use the same one for the whole turn | contracts |

## Rules

1. **Anchors over positions.** A node is found by text, description and id, so a
   recipe survives a redraw; a node with none of those has no anchor and a step on
   it stays positional.
2. **One source per turn.** `ScreenSourceDevice` picks the first surface that
   answers and every action in the turn goes through it; a source that cannot act
   throws instead of pretending.
3. **The hash is canonical.** `ObservationHasher` in `contracts` seals what this
   module produces; records and recipes compare those digests.
4. **No Android, no network, no policy.** What is visible is this module's job;
   what to do about it is `agent`'s.

## Tests

```
./gradlew :observation:test :observation:check
```

`ObservationProducerTest` replays `spec/fixtures/observation-hash.json`, so the
same tree gives the same hash on every client.
