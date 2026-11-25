#!/usr/bin/env python3
"""Replay every gateway fixture file against the JSON Schema.

Every `spec/fixtures/gateway-*.json` file is replayed (protocol v0, and the
files later protocol additions bring, such as the family board). Case names
are unique across all the files, because the Kotlin side names cases by them.

Usage:
    python tools/checks/check-gateway-fixtures.py

Exit codes:
    0  every case matches its expectation
    1  a schema or expectation mismatch
    2  the fixtures or the schema are missing
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES_DIR = ROOT / "spec" / "fixtures"
FIXTURES_GLOB = "gateway-*.json"
SCHEMA = ROOT / "spec" / "schemas" / "gateway.v1.schema.json"

ALLOWED_ERRORS = {
    "E_AUTH",
    "E_TENANT",
    "E_VERSION",
    "E_RATE",
    "E_QUEUE",
    "E_SCHEMA",
    "E_UNSUPPORTED",
    "E_SCOPE",
    "E_CONFLICT",
}


def check(fixtures_dir: Path, schema_path: Path) -> int:
    """Replay every gateway fixture file in `fixtures_dir`; return an exit code."""
    files = sorted(fixtures_dir.glob(FIXTURES_GLOB))
    if not files:
        print(f"missing: {fixtures_dir / FIXTURES_GLOB}", file=sys.stderr)
        return 2
    if not schema_path.exists():
        print(f"missing: {schema_path}", file=sys.stderr)
        return 2

    try:
        from jsonschema import Draft202012Validator
    except ImportError:
        print("jsonschema is required: pip install jsonschema", file=sys.stderr)
        return 2

    validator = Draft202012Validator(json.loads(schema_path.read_text(encoding="utf-8")))

    problems = 0
    total = 0
    seen: dict[str, str] = {}
    for path in files:
        cases = json.loads(path.read_text(encoding="utf-8"))["cases"]
        total += len(cases)
        for case in cases:
            name = case["name"]
            where = f"{path.name}: {name}"
            if name in seen:
                print(f"{where}: duplicate case (also in {seen[name]})", file=sys.stderr)
                problems += 1
            seen.setdefault(name, path.name)

            errors = list(validator.iter_errors(case["message"]))
            schema_valid = case.get("schema", case["ok"])
            if schema_valid and errors:
                print(f"{where}: schema should accept, got {errors[0].message}", file=sys.stderr)
                problems += 1
            if not schema_valid and not errors:
                print(f"{where}: schema should refuse", file=sys.stderr)
                problems += 1

            if case["ok"]:
                if case.get("error") is not None:
                    print(f"{where}: accepted case carries an error", file=sys.stderr)
                    problems += 1
            elif case.get("error") not in ALLOWED_ERRORS:
                print(f"{where}: refused case names no known gateway error", file=sys.stderr)
                problems += 1

    if problems:
        print(f"{problems} problem(s) in {total} case(s)", file=sys.stderr)
        return 1

    print(f"ok {total} case(s) in {len(files)} file(s)")
    return 0


def main() -> int:
    return check(FIXTURES_DIR, SCHEMA)


if __name__ == "__main__":
    raise SystemExit(main())
