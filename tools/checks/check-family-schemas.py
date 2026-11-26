#!/usr/bin/env python3
"""Replay the family board schema cases against the op and state JSON Schemas.

Usage:
    python tools/checks/check-family-schemas.py

Checks that every case is accepted or refused as it says, that every op kind in
op.v1 has at least one accepted case, and that a refused case says why.

Then checks the generated board fixtures (board-*.json): every log entry and every
final state fits the schemas, an entry the schema refuses is one the reducer
rejects as `invalid` or stops at, the results are in seq order and stop at
`stoppedAt`, and the recorded state hashes match a canonical JSON hash computed
here, independently of the generator.

Exit codes:
    0  every case matches its expectation
    1  a schema or expectation mismatch
    2  the cases or the schemas are missing
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / "spec" / "schemas" / "family"
FIXTURES = ROOT / "spec" / "fixtures" / "family"
CASES = FIXTURES / "schema-cases.json"
OP_SCHEMA = SCHEMAS / "op.v1.schema.json"
STATE_SCHEMA = SCHEMAS / "state.v1.schema.json"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def state_hash(value: object) -> str:
    """SHA-256 of canonical JSON: sorted keys, no whitespace, raw UTF-8 (see README)."""
    text = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def check_board(validators: dict) -> tuple[int, int, int]:
    """Returns (problems, cases, files) for the board fixtures."""
    problems = 0
    count = 0
    names: set[str] = set()
    files = sorted(FIXTURES.glob("board-*.json"))
    for path in files:
        data = load(path)
        if not (FIXTURES / data["seed"]).exists():
            print(f"{path.name}: seed {data['seed']} is missing", file=sys.stderr)
            problems += 1
        for case in data["cases"]:
            count += 1
            name = f"{path.name}:{case['name']}"
            if case["name"] in names:
                print(f"{name}: duplicate case name", file=sys.stderr)
                problems += 1
            names.add(case["name"])
            stopped = case.get("stoppedAt")
            results = {r["seq"]: r for r in case["results"]}
            seqs = [e["seq"] for e in case["log"]]
            if seqs != sorted(set(seqs)):
                print(f"{name}: log seqs are not strictly increasing", file=sys.stderr)
                problems += 1
            if [r["seq"] for r in case["results"]] != sorted(results):
                print(f"{name}: results are not in seq order", file=sys.stderr)
                problems += 1
            for entry in case["log"]:
                seq = entry["seq"]
                result = results.get(seq)
                if stopped is not None and seq >= stopped:
                    if result:
                        print(
                            f"{name}: a result at seq {seq}, after the stop",
                            file=sys.stderr,
                        )
                        problems += 1
                elif not result:
                    print(f"{name}: no result for seq {seq}", file=sys.stderr)
                    problems += 1
                errors = list(validators["entry"].iter_errors(entry))
                rejected_invalid = bool(result) and result.get("code") == "invalid"
                if errors and not (rejected_invalid or seq == stopped):
                    best = min(errors, key=lambda e: len(e.path))
                    print(
                        f"{name}: seq {seq} does not fit the op schema ({best.message}) "
                        "but the reducer did not reject it as invalid",
                        file=sys.stderr,
                    )
                    problems += 1
            errors = list(validators["state"].iter_errors(case["after"]))
            if errors:
                best = min(errors, key=lambda e: len(e.path))
                print(
                    f"{name}: after does not fit the state schema: {best.message}",
                    file=sys.stderr,
                )
                problems += 1
            after = case["after"]
            expected = {
                "state": state_hash(after),
                "family": state_hash(after["scopes"]["family"]),
                "parents": state_hash(after["scopes"].get("parents")),
            }
            if case["hashes"] != expected:
                print(
                    f"{name}: recorded hashes differ from the canonical hash",
                    file=sys.stderr,
                )
                problems += 1
    return problems, count, len(files)


def main() -> int:
    for path in (CASES, OP_SCHEMA, STATE_SCHEMA):
        if not path.exists():
            print(f"missing: {path}", file=sys.stderr)
            return 2

    try:
        from jsonschema import Draft202012Validator
        from referencing import Registry, Resource
    except ImportError:
        print("jsonschema is required: pip install jsonschema", file=sys.stderr)
        return 2

    op_schema = load(OP_SCHEMA)
    state_schema = load(STATE_SCHEMA)
    for schema in (op_schema, state_schema):
        Draft202012Validator.check_schema(schema)
    registry = Registry().with_resources(
        (schema["$id"], Resource.from_contents(schema))
        for schema in (op_schema, state_schema)
    )
    validators = {
        "op": Draft202012Validator(op_schema, registry=registry),
        "entry": Draft202012Validator(
            {"$ref": op_schema["$id"] + "#/$defs/logEntry"}, registry=registry
        ),
        "state": Draft202012Validator(state_schema, registry=registry),
    }

    cases = load(CASES)["cases"]
    problems = 0
    seen: set[str] = set()
    accepted_kinds: set[str] = set()
    for case in cases:
        name = case["name"]
        if name in seen:
            print(f"duplicate case: {name}", file=sys.stderr)
            problems += 1
        seen.add(name)

        validator = validators.get(case["schema"])
        if validator is None:
            print(f"{name}: unknown schema {case['schema']!r}", file=sys.stderr)
            problems += 1
            continue
        value = case["value"]
        if isinstance(value, dict) and set(value) == {"file"}:
            value = load(FIXTURES / value["file"])

        errors = list(validator.iter_errors(value))
        if case["ok"] and errors:
            best = min(errors, key=lambda e: len(e.path))
            print(f"{name}: schema should accept, got {best.message}", file=sys.stderr)
            problems += 1
        if not case["ok"]:
            if not errors:
                print(f"{name}: schema should refuse", file=sys.stderr)
                problems += 1
            if not case.get("why"):
                print(f"{name}: a refused case must say why", file=sys.stderr)
                problems += 1
        if case["ok"] and case["schema"] == "op":
            accepted_kinds.add(value["kind"])

    missing = set(op_schema["properties"]["kind"]["enum"]) - accepted_kinds
    if missing:
        print(f"op kinds with no accepted case: {sorted(missing)}", file=sys.stderr)
        problems += 1

    if problems:
        print(f"{problems} problem(s) in {len(cases)} case(s)", file=sys.stderr)
        return 1

    board_problems, board_cases, board_files = check_board(validators)
    if board_problems:
        print(
            f"{board_problems} problem(s) in {board_cases} board case(s)",
            file=sys.stderr,
        )
        return 1

    print(
        f"ok {len(cases)} case(s), {len(accepted_kinds)} op kind(s); "
        f"{board_cases} board case(s) in {board_files} file(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
