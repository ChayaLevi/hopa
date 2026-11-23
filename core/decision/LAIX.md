# LAIX (vendored)

`src/main/kotlin/dev/laix/**` is a copy of the LAIX Kotlin action policy, not a
reimplementation. LAIX is Apache-2.0; the license text is in
`LAIX-LICENSE.txt`, and the root `NOTICE` names the project.

| Field | Value |
|---|---|
| Project | https://github.com/michaelatamuk/laix |
| Version | 0.18.0 (pre-1.0) |
| Commit | `3ccc8add90dbd019197a990393600bbe1c1647d7` (2026-11-21) |
| License | Apache-2.0 |

## Copied files

```
dev/laix/decision/contracts/Contracts.kt
dev/laix/decision/contracts/Decision.kt
dev/laix/decision/contracts/Observation.kt
dev/laix/decision/contracts/Payloads.kt
dev/laix/decision/policies/action/ActionPolicy.kt
dev/laix/decision/policies/action/ActionPolicyInput.kt
dev/laix/decision/support/Json.kt
dev/laix/decision/support/Time.kt
dev/laix/decision/support/Values.kt
```

Everything else in the LAIX package (the app kit, planners, signal attention,
bench, MCP server) is not copied: the adapter only needs the action policy and
the shapes it returns. The files are byte-identical to the pinned commit.

## Why a copy, not a dependency

LAIX is pre-1.0 and its API still moves. A pinned copy is reproducible offline,
builds in CI without a path outside the repository, and keeps a LAIX upgrade a
reviewed commit rather than a version range. The adapter
(`com.hopa.decision.laix.LaixDecisionLayer`) is the only place that imports
`dev.laix.*`; no LAIX type reaches the loop or the run log.

## Updating

1. Check out the new LAIX commit.
2. Copy the files above again, without edits.
3. Run `:decision:test` (conformance kit plus the comparison corpus) and
   `:decision:decisionReport`.
4. Update the commit and version in this file and the adapter's metadata.
5. A dump of the decision fields in the run log must stay the same shape.

Never patch a copied file locally: a change belongs in the adapter, or upstream
in LAIX. If the file must diverge, say why here and in the commit message.
