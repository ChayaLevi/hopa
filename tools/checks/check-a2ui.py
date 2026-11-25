#!/usr/bin/env python3
"""Check HOPA's A2UI 1.0 contract (ADR-0031).

1. `spec/a2ui/VERSION` is the version the catalog and the schema name.
2. The HOPA catalog is a valid A2UI 1.0 catalog (upstream catalog_definition),
   its ids agree, and every component is a basic-catalog name or a HOPA one.
3. Every fixture message is accepted or refused by `messages.schema.json` as
   the fixture says, and every accepted one is also valid upstream A2UI 1.0
   with the HOPA catalog in the catalog's place.

Graph rules (root, references, tree, parents, bindings) are the Kotlin
session's job; their fixtures are replayed by core/contracts' tests.

Usage:
    python tools/checks/check-a2ui.py

Exit codes:
    0  everything matches
    1  a mismatch
    2  a file or jsonschema is missing
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
A2UI = ROOT / "spec" / "a2ui"
UPSTREAM = A2UI / "upstream"
FIXTURES = ROOT / "spec" / "fixtures" / "a2ui"
UPSTREAM_BASE = "https://a2ui.org/specification/v1_0/"
HOPA_ONLY = {"PaymentNotice"}
KNOWN_ERRORS = {
    "VALIDATION_FAILED",
    "UNALLOWED_PARENT",
    "UNALLOWED_CHILD",
    "INTEGRITY_ERROR",
    "UNSUPPORTED",
}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    needed = [
        A2UI / "VERSION",
        A2UI / "hopa-catalog.v1.json",
        A2UI / "messages.schema.json",
        UPSTREAM / "agent_to_renderer.json",
        UPSTREAM / "renderer_to_agent.json",
        UPSTREAM / "common_types.json",
        UPSTREAM / "catalog_definition.json",
        UPSTREAM / "basic_catalog.json",
        FIXTURES / "surfaces.json",
        FIXTURES / "replies.json",
        FIXTURES / "builders.json",
    ]
    for path in needed:
        if not path.exists():
            print(f"missing: {path}", file=sys.stderr)
            return 2
    try:
        from jsonschema import Draft202012Validator
        from jsonschema_specifications import REGISTRY as SPECS
        from referencing import Resource
        from referencing.jsonschema import DRAFT202012
    except ImportError:
        print("jsonschema is required: pip install jsonschema", file=sys.stderr)
        return 2

    version = (A2UI / "VERSION").read_text(encoding="utf-8").strip()
    catalog = load(A2UI / "hopa-catalog.v1.json")
    ours = load(A2UI / "messages.schema.json")
    basic = load(UPSTREAM / "basic_catalog.json")
    problems: list[str] = []

    if catalog.get("protocolVersion") != version:
        problems.append(f"catalog protocolVersion is not {version}")
    if catalog.get("$id") != catalog.get("catalogId"):
        problems.append("catalog $id and catalogId differ")
    if catalog.get("functions"):
        problems.append("the HOPA catalog defines no functions")
    for name in catalog["components"]:
        if name not in basic["components"] and name not in HOPA_ONLY:
            problems.append(f"component {name} is neither basic nor HOPA")

    def resource(doc):
        # The catalog's $schema names the A2UI catalog definition, not a dialect.
        return Resource.from_contents(doc, default_specification=DRAFT202012) if doc.get("$schema", "").startswith("https://json-schema.org/") else DRAFT202012.create_resource(doc)

    upstream_docs = {
        name: load(UPSTREAM / f"{name}.json")
        for name in (
            "agent_to_renderer",
            "renderer_to_agent",
            "common_types",
            "catalog_definition",
        )
    }
    base = SPECS.with_resources(
        [(doc["$id"], resource(doc)) for doc in upstream_docs.values()]
        + [(catalog["$id"], resource(catalog)), (ours["$id"], resource(ours))]
    )
    # Upstream messages name the catalog as `catalog.json` beside them; put ours there.
    upstream_registry = base.with_resource(UPSTREAM_BASE + "catalog.json", resource(catalog))

    catalog_errors = list(
        Draft202012Validator(upstream_docs["catalog_definition"], registry=base).iter_errors(
            catalog
        )
    )
    for error in catalog_errors[:5]:
        problems.append(f"catalog is not valid A2UI {version}: {error.message[:160]}")

    hopa = Draft202012Validator(ours, registry=base)
    up_in = Draft202012Validator(upstream_docs["agent_to_renderer"], registry=upstream_registry)
    up_out = Draft202012Validator(upstream_docs["renderer_to_agent"], registry=upstream_registry)

    def upstream_errors(message):
        validator = up_out if ("action" in message or "error" in message) else up_in
        return list(validator.iter_errors(message))

    count = 0

    def check_message(where: str, message, schema_ok: bool) -> None:
        nonlocal count
        count += 1
        errors = list(hopa.iter_errors(message))
        if schema_ok and errors:
            problems.append(f"{where}: HOPA schema should accept: {errors[0].message[:160]}")
        if not schema_ok and not errors:
            problems.append(f"{where}: HOPA schema should refuse")
        if schema_ok and not errors:
            upstream = upstream_errors(message)
            if upstream:
                problems.append(f"{where}: not valid upstream A2UI: {upstream[0].message[:160]}")

    for file_name, key in (("surfaces.json", "messages"), ("replies.json", "message")):
        document = load(FIXTURES / file_name)
        seen: set[str] = set()
        for case in document["cases"]:
            name = f"{file_name}:{case['name']}"
            if case["name"] in seen:
                problems.append(f"{name}: duplicate case")
            seen.add(case["name"])
            if not case["ok"] and case.get("error", {}).get("code") not in KNOWN_ERRORS:
                problems.append(f"{name}: refused case names no known error code")
            messages = case[key] if key == "messages" else [case[key]]
            schema = case.get("schema", True)
            for index, message in enumerate(messages):
                # Only the message a case refuses at can be schema-invalid.
                last = index == len(messages) - 1
                check_message(f"{name}[{index}]", message, schema or not last)

    # A received card that fails is refused by HOPA's approval rules, not by
    # A2UI: every message in it is still valid A2UI.
    for case in load(FIXTURES / "surfaces.json")["receivedCases"]:
        if isinstance(case["a2ui"], list):
            for index, message in enumerate(case["a2ui"]):
                check_message(f"surfaces.json:received:{case['name']}[{index}]", message, True)
    for case in load(FIXTURES / "replies.json")["wireAnswers"]:
        if case["allowed"] and case["action"] is not None:
            check_message(f"replies.json:wire:{case['name']}", case["action"], True)

    builders = load(FIXTURES / "builders.json")
    for group in ("approvals", "choices"):
        for case in builders[group]:
            for index, message in enumerate(case["messages"]):
                check_message(f"builders.json:{case['name']}[{index}]", message, True)

    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        print(f"{len(problems)} problem(s) in {count} message(s)", file=sys.stderr)
        return 1
    print(f"ok A2UI {version}: catalog valid, {count} message(s) checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
