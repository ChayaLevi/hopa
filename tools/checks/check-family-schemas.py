#!/usr/bin/env python3
"""Replay the family board schema cases against the op and state JSON Schemas.

Usage:
    python tools/checks/check-family-schemas.py

Checks that every case is accepted or refused as it says, that every op kind in
op.v1 has at least one accepted case, and that a refused case says why.

Exit codes:
    0  every case matches its expectation
    1  a schema or expectation mismatch
    2  the cases or the schemas are missing
"""

from __future__ import annotations

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

    print(f"ok {len(cases)} case(s), {len(accepted_kinds)} op kind(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
