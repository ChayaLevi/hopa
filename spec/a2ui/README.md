# A2UI

HOPA's approval and choice cards are A2UI surfaces (ADR-0027).

| File | What it is |
|---|---|
| [`VERSION`](VERSION) | `1.0`, the exact wire value: every message carries `"version": "1.0"` |
| [`hopa-catalog.v1.json`](hopa-catalog.v1.json) | The components HOPA renders: Text, Row, Column, Card, Button, Divider, Icon and HOPA's `PaymentNotice`. It is a valid A2UI 1.0 catalog. |
| [`messages.schema.json`](messages.schema.json) | One message in either direction, the HOPA subset of 1.0 |
| [`upstream/`](upstream/UPSTREAM.md) | Unmodified A2UI 1.0 schemas and basic catalog (Apache-2.0) at commit `d4f2761b`, used only by the check |
| [`../fixtures/a2ui/`](../fixtures/a2ui/) | `surfaces.json` (accepted or refused, with code and path; received approval cards, shown or refused against their request), `replies.json` (taps and errors, and how an answer is read, typed and as `task.result` carries it), `builders.json` (golden cards) |

- The fixtures are generated. Edit
  [`tools/scripts/build-a2ui-fixtures.py`](../../tools/scripts/build-a2ui-fixtures.py)
  and run it; never edit the JSON by hand. CI runs it with `--check` to catch
  stale files.
- `python tools/checks/check-a2ui.py` checks the following:
  - the catalog is valid A2UI 1.0;
  - every fixture is accepted or refused by our schema as the fixture says;
  - every accepted message is also valid upstream 1.0.
- The graph rules can't be expressed in JSON Schema: one tree from `root`,
  every reference resolves, allowed parents and children, and bindings
  resolve to strings. `A2uiSession` in
  `core/contracts/.../a2ui/` enforces them, and its tests replay the same
  fixtures.
- Every HOPA renderer (Compose, ArkUI, WinUI) must pass the fixtures. A
  refusal reports the first problem in message order.

Changing `VERSION` or refreshing `upstream/` requires a new ADR, plus the
codec and fixture updates in the same pull request.
