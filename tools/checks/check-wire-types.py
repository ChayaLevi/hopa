#!/usr/bin/env python3
"""Fail when a hand-written Kotlin wire class and its JSON Schema disagree.

The schemas describe what crosses the wire; the `data class` declarations are
what the clients actually encode and decode. Nothing compared the two, so they
drifted: the two gateway frame copies disagreed on the default of `v`.

This check reads both sides. It is deliberately about the wire shape only -
names, types, nullability and defaults - because everything else (the custom
record serializer, `ActionKind`, per-class strictness) is a Kotlin decision the
schema cannot express. See `docs/project/plan/wire-types.md`.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ("apps", "packages", "services")

# class -> (schema document, path inside it)
CLASSES = {
    "Action": ("action", ()),
    "Decision": ("decision", ()),
    "RunRecord": ("run-record", ()),
    "Observation": ("observation", ()),
    "ObservationNode": ("observation", ("$defs", "node")),
    "ScreenSize": ("observation", ("properties", "screenSize")),
    "BackendEnvelope": ("gateway.v1", ()),
    "Envelope": ("gateway.v1", ()),
}

CLASS_HEAD = re.compile(r"\bdata class (\w+)\(\n(.*?)\n\)", re.S)
PROPERTY = re.compile(r"^ {4}(?:@\w+(?:\([^)]*\))? )*val (\w+): ([^=]+?)(?: = (.*))?,$")
MISSING = object()


def at(document: dict, path: tuple):
    node = document
    for key in path:
        node = node[key]
    return node


def resolve(file: str, ref: str) -> tuple[str, tuple]:
    if ref.startswith("#/"):
        return file, tuple(ref[2:].split("/"))
    return ref.replace(".schema.json", ""), ()


def literal(raw: str | None):
    """Read a Kotlin default as the value the schema would hold."""
    if raw is None:
        return MISSING
    if raw == "null":
        return None
    if raw in ("true", "false"):
        return raw == "true"
    if re.fullmatch(r"-?\d+", raw):
        return int(raw)
    if re.fullmatch(r"-?\d+\.\d+f", raw):
        return float(raw[:-1])
    if re.fullmatch(r"-?\d+\.\d+", raw):
        return float(raw)
    if raw.startswith('"') and raw.endswith('"'):
        return json.loads(raw)
    if raw == "emptyList()":
        return []
    if raw == "JsonObject(emptyMap())":
        return {}
    return ("raw", raw)


def parse(text: str) -> dict[str, dict[str, tuple[str, str | None]]]:
    """Every `data class Name(...)` in one file, with its properties."""
    found = {}
    for match in CLASS_HEAD.finditer(text):
        props = {}
        for line in match.group(2).splitlines():
            prop = PROPERTY.match(line)
            if prop:
                props[prop.group(1)] = (prop.group(2).strip(), prop.group(3))
        found[match.group(1)] = props
    return found


def declarations(root: Path) -> dict[str, list[tuple[Path, dict]]]:
    found: dict[str, list[tuple[Path, dict]]] = {}
    for area in SOURCES:
        for path in (root / area).rglob("*.kt"):
            if "build" in path.parts or "generated" in path.parts:
                continue
            for name, props in parse(path.read_text(encoding="utf-8", errors="replace")).items():
                found.setdefault(name, []).append((path, props))
    return found


def class_name(docs: dict, file: str, path: tuple) -> str | None:
    for name, (owner, where) in CLASSES.items():
        if owner == file and where == path:
            return name
    return at(docs[file], path).get("title")


def expected(docs: dict, file: str, key: str, prop: dict) -> tuple[str | None, bool]:
    """The Kotlin type and nullability the schema asks for."""
    ref = prop.get("$ref")
    if ref:
        target_file, target_path = resolve(file, ref)
        target = at(docs[target_file], target_path)
        if target.get("type") == "object" and target.get("properties"):
            return class_name(docs, target_file, target_path), False
        return expected(docs, target_file, key, target)
    if "oneOf" in prop:
        real = [option for option in prop["oneOf"] if option.get("type") != "null"]
        inner, _ = expected(docs, file, key, real[0])
        return inner, len(real) != len(prop["oneOf"])
    kind = prop.get("type")
    nullable = False
    if isinstance(kind, list):
        nullable = "null" in kind
        kind = next(item for item in kind if item != "null")
    elif kind is None:
        sample = prop["enum"][0] if "enum" in prop else prop.get("const")
        kind = "string" if isinstance(sample, str) else "integer"
    if kind == "string":
        return "String", nullable
    if kind == "integer":
        return ("Long" if prop.get("format") == "int64" else "Int"), nullable
    if kind == "number":
        return ("Float" if prop.get("format") == "float" else "Double"), nullable
    if kind == "boolean":
        return "Boolean", nullable
    if kind == "array":
        inner, _ = expected(docs, file, key, prop["items"])
        return f"List<{inner}>", nullable
    if kind == "object":
        if not prop.get("properties"):
            return "JsonObject", nullable
        # An inline object is a class only if the schema names it.
        return prop.get("title"), nullable
    return None, nullable


def compare(name: str, props: dict, docs: dict, file: str, path: tuple) -> list[str]:
    schema = at(docs[file], path)
    required = set(schema.get("required", []))
    json_props = schema.get("properties", {})
    problems = []

    for key in props:
        if key not in json_props:
            problems.append(f"{name}.{key}: not in {file}.schema.json")
    for key, prop in json_props.items():
        if key not in props:
            problems.append(f"{name}.{key}: in the schema, missing from the Kotlin class")
            continue
        kotlin, kotlin_default = props[key]
        want, nullable = expected(docs, file, key, prop)
        fixed = "default" in prop or "const" in prop
        # An optional property without a default has nowhere else to go: the
        # class has to accept its absence, which in Kotlin means `?`.
        want_nullable = nullable or (key not in required and not fixed)
        is_nullable = kotlin.endswith("?")
        base = kotlin[:-1] if is_nullable else kotlin

        if want is None:
            problems.append(f"{name}.{key}: nested object has no Kotlin class (add a title)")
        elif base != want:
            want_text = want + ("?" if want_nullable else "")
            problems.append(f"{name}.{key}: Kotlin {kotlin}, schema says {want_text}")
        elif is_nullable != want_nullable:
            problems.append(
                f"{name}.{key}: Kotlin {'nullable' if is_nullable else 'non-null'}, "
                f"schema {'nullable' if want_nullable else 'non-null'}"
            )

        declared = prop["default"] if "default" in prop else prop.get("const", MISSING)
        value = literal(kotlin_default)
        if declared is MISSING and value not in (MISSING, None):
            problems.append(f"{name}.{key}: Kotlin default {kotlin_default}, schema has none")
        elif declared is not MISSING and value is MISSING:
            problems.append(f"{name}.{key}: schema default {declared!r}, Kotlin has none")
        elif declared is not MISSING and value is not MISSING and value != declared:
            # `= null` on a nullable property says nothing; it is how "absent"
            # becomes a value, not a promise about the wire.
            if not (value is None and want_nullable):
                problems.append(
                    f"{name}.{key}: Kotlin default {kotlin_default}, schema default {declared!r}"
                )
    return problems


def check(root: Path = ROOT) -> list[str]:
    files = {owner for owner, _ in CLASSES.values()}
    docs = {
        name: json.loads(
            (root / "contracts" / "schemas" / f"{name}.schema.json").read_text(encoding="utf-8")
        )
        for name in files
    }
    declared = declarations(root)
    problems: list[str] = []
    for name, (file, path) in CLASSES.items():
        found = declared.get(name, [])
        if not found:
            problems.append(f"{name}: no data class declaration found")
            continue
        if len(found) > 1:
            places = ", ".join(str(where.relative_to(root)) for where, _ in found)
            problems.append(f"{name}: declared {len(found)} times ({places}); keep one")
            continue
        where, props = found[0]
        problems += compare(name, props, docs, file, path)
    return problems


def main() -> int:
    problems = check()
    for problem in problems:
        print(problem)
    print(f"{len(problems)} wire type problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
