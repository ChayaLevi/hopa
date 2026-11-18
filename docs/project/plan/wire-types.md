# Wire types from the schemas

Status: **the spike is done and generation is dropped; C1 and C2 landed.** The
schemas now carry the defaults and formats the Kotlin always assumed, and
`tools/checks/check-wire-types.py` fails when a `data class` and its schema
disagree.

## What the spike did

A throwaway generator (`spike.py`, 266 lines, kept out of the repo) read the
five documents in `contracts/schemas` and emitted `@Serializable` Kotlin in two
passes:

- **raw** - the schemas exactly as they are today;
- **patched** - the same schemas plus the keywords the hand-written types
  already assume (`default`, `format`, `title`).

The patched output was compiled inside `:contracts` and run through ktlint
before anything was decided.

## Result

The patched output is **7 classes, 131 lines, compiles and passes ktlint with
no manual edits**. The mechanical part is real: cross-file `$ref`, `$defs`,
`const`, `type: [x, null]`, `oneOf` with null, arrays, recursion and KDoc from
`description` all map cleanly.

The as-is pass does not. Without help the schemas produce:

| drift | because |
|---|---|
| 39 optional properties with no default | JSON Schema says "may be absent"; Kotlin needs a value |
| `timestamp: Int` | schema says `integer`; the phone counts milliseconds |
| `density: Double` | schema says `number`; the code uses `Float` |
| `data class HOPA gateway envelope v1(` | the title is prose, not an identifier |
| `Observation.kt` holds `ObservationNode` | a `$defs` entry has no name of its own |

Matching the hand-written types took **32 schema keys** in the spike: 26
`default`, 2 `format`, 3 `title`, 1 name. C1 landed 24, 3 and 2 - the `= null`
defaults needed no schema change, and `bounds` stayed an inline list.

## What a generator cannot reproduce

| hand-written thing | why the schema cannot hold it |
|---|---|
| `RunRecordSerializer` | the contract wants `approved` and `error` present as `null`, while `HopaJson` sets `explicitNulls = false`; the record writes its own object |
| `ActionKind` + `kindOrNull()` | 32 wire values that must **decode** when unknown and be refused by the executor; a schema `enum` rejects instead |
| per-class strictness | `additionalProperties: false` is per class; kotlinx strictness is one global `Json` flag, and the gateway frame must stay permissive |
| one shared frame | a generated `GatewayEnvelope` has to live in a module both `net` and `backend` see; that is a boundary decision, not code generation |

## The duplicate the spike found

`net.BackendEnvelope` and `backend.Envelope` are the same frame: same fields in
the same order, same optionality. They differ in `v` (default `1` vs none) and
in a redundant `@SerialName("from")`. Together with the schema that is **three
definitions of one shape**.

This is the only place where generation would delete real duplication - and it
is also the place with ~200 references in 33 files across two modules. The
churn is the cost, not the generator.

## Decision

**Do not generate the wire types.** The hand-written classes are ~145 lines in
total, and every one of them carries a decision the schema cannot express
(table above). Generating them would move code out of a language the compiler
checks and into a Python program that then needs its own tests, its own
ktlint-clean emitter, output file naming and a CI `--check`. The serializer,
the enum helper and the strictness would stay hand-written, so there would still
be two sources of truth - with one more program to maintain.

Instead, in this order:

1. **C1, done.** The schemas carry the 24 `default` values the Kotlin had by
   hand, `format: int64` on both millisecond clocks, `format: float` on density,
   and a `title` on the two nested objects that are classes (`ScreenSize`,
   `ObservationNode`). No Kotlin behaviour changed.
2. **C2, done.** `tools/checks/check-wire-types.py` reads the eight `data class`
   declarations and the five documents, and compares names, types, nullability
   and defaults in both directions. CI runs it next to the gateway fixtures; its
   own tests live in `tools/scripts/test_check_wire_types.py`.
3. Revisit generation only if C2 keeps reporting drift a generator would have
   prevented. It has not so far.

## What C2 found on the first run

One real drift, in the duplicate frame: `backend.Envelope` had no default for
`v` while `net.BackendEnvelope` and the schema's `const: 1` both say `1`. One
line fixed it. Everything else agreed, which is the answer we wanted: the
hand-written types were already correct, they just had nothing checking them.

C2 also fails when a mapped class is declared twice, so the duplicate frame
cannot quietly become three copies.

## Numbers

| file | lines |
|---|---|
| `Action.kt` (Action + `ActionKind`) | 62 |
| `Observation.kt` (3 classes) | 46 |
| `RunRecord.kt` | 29 |
| `Decision.kt` | 15 |
| `BackendEnvelope.kt` | 20 |
| `Envelope.kt` (frame only) | 20 |
| generated, all seven classes | 131 |
| generator | 266 |

The generator is not smaller than what it replaces; it is a different place to
keep the same knowledge.
