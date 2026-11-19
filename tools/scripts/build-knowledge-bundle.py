#!/usr/bin/env python3
"""Render one knowledge bundle from the shared contracts.

Source of truth: ``spec/catalog/catalog.v1.json`` and
``spec/prompts/rules.v1.json``. The bundle has the same shape as the
Kotlin ``KnowledgeBundle``: schemaVersion, version, catalog, sites, promptRules.
It is written to ``spec/knowledge`` for review and to the backend resources
so ``GET /v1/knowledge`` serves exactly the reviewed document.

The version is a digest of the data, so an unchanged source keeps its ETag and
the app can answer with ``If-None-Match``.
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "spec" / "catalog" / "catalog.v1.json"
RULES = ROOT / "spec" / "prompts" / "rules.v1.json"
OUTPUTS = [
    ROOT / "spec" / "knowledge" / "knowledge.v1.json",
    ROOT / "services" / "backend" / "src" / "main" / "resources" / "knowledge.v1.json",
    ROOT / "packages" / "catalog" / "src" / "test" / "resources" / "knowledge.v1.json",
    ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "knowledge.v1.json",
    ROOT / "apps" / "windows" / "sidecar" / "src" / "main" / "resources" / "knowledge.v1.json",
]


def target(platform: dict) -> dict:
    return {
        "id": platform.get("id", ""),
        "detail": platform.get("detail", ""),
        "verified": bool(platform.get("verified", False)),
    }


def app(entry: dict) -> dict:
    platforms = entry.get("platforms", {})
    return {
        "id": entry["id"],
        "display": entry["display"],
        "aliases": entry.get("aliases", []),
        "purpose": entry.get("purpose", ""),
        "actions": entry.get("actions", []),
        "harmonyos": target(platforms.get("harmonyos", {})),
        "android": target(platforms.get("android", {})),
        "windows": target(platforms.get("windows", {})),
    }


def site(entry: dict) -> dict:
    return {
        "id": entry["id"],
        "display": entry["display"],
        "domains": entry.get("domains", []),
        "aliases": entry.get("aliases", []),
        "validated": entry.get("validated", ""),
        "purpose": entry.get("purpose", ""),
        "actions": entry.get("actions", []),
    }


def render() -> str:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    rules = json.loads(RULES.read_text(encoding="utf-8"))
    data = {
        "catalog": [app(entry) for entry in catalog["apps"]],
        "sites": [site(entry) for entry in catalog["sites"]],
        "promptRules": rules["rules"],
    }
    canonical = json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
    bundle = {
        "schemaVersion": 1,
        "version": f"sha256:{digest}",
        **data,
    }
    return json.dumps(bundle, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    text = render()
    if "--check" in sys.argv:
        stale = [path for path in OUTPUTS if not path.exists() or path.read_text(encoding="utf-8") != text]
        if stale:
            for path in stale:
                print(f"{path.relative_to(ROOT)} is stale; run tools/scripts/build-knowledge-bundle.py", file=sys.stderr)
            return 1
        print("knowledge bundle is current")
        return 0
    for path in OUTPUTS:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"wrote {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
