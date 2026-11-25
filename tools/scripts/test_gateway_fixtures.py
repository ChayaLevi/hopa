"""Gateway fixtures: every vector carries its expected outcome.

The fixture files (`spec/fixtures/gateway-*.json`) are the executable part of
the gateway contract. The Kotlin codec and validator replay the same files, so
a change here breaks both sides.
"""

import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES_DIR = ROOT / "spec" / "fixtures"
V0 = FIXTURES_DIR / "gateway-v0.json"
SCHEMA = ROOT / "spec" / "schemas" / "gateway.v1.schema.json"
CHECKER = ROOT / "tools" / "checks" / "check-gateway-fixtures.py"


def load_checker():
    spec = importlib.util.spec_from_file_location("check_gateway_fixtures", CHECKER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


checker = load_checker()


def load():
    return json.loads(V0.read_text(encoding="utf-8"))


def write_fixture(directory: Path, name: str, cases: list[dict]) -> None:
    body = {"version": 1, "description": "test", "cases": cases}
    (directory / name).write_text(json.dumps(body), encoding="utf-8")


def v0_case(name: str) -> dict:
    """A copy of the first accepted v0 case under a new name."""
    case = next(c for c in load()["cases"] if c["ok"])
    return {**case, "name": name}


def refused_case(name: str, error: str | None) -> dict:
    """A schema-valid message that the gateway refuses with `error`."""
    return {**v0_case(name), "ok": False, "schema": True, "error": error}


def test_at_least_fourteen_unique_cases():
    names = [c["name"] for c in load()["cases"]]
    assert len(names) >= 14
    assert len(names) == len(set(names))


def test_every_case_matches_its_expectation():
    from jsonschema import Draft202012Validator

    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    for case in load()["cases"]:
        errors = list(validator.iter_errors(case["message"]))
        schema_valid = case.get("schema", case["ok"])
        if schema_valid:
            assert not errors, (case["name"], errors)
        else:
            assert errors, f"{case['name']} must be refused by the schema"


def test_refused_messages_name_a_gateway_error():
    for case in load()["cases"]:
        if case["ok"]:
            assert case.get("error") is None, case["name"]
        else:
            assert case.get("error") in checker.ALLOWED_ERRORS, case["name"]


def test_board_errors_are_allowed():
    assert {"E_SCOPE", "E_CONFLICT"} <= checker.ALLOWED_ERRORS


def test_checker_passes_on_the_repository_fixtures():
    assert checker.check(FIXTURES_DIR, SCHEMA) == 0


def test_checker_replays_every_gateway_file(tmp_path):
    write_fixture(tmp_path, "gateway-v0.json", [v0_case("a")])
    write_fixture(tmp_path, "gateway-board.json", [refused_case("b", None)])
    assert checker.check(tmp_path, SCHEMA) == 1


def test_checker_ignores_other_fixture_files(tmp_path):
    write_fixture(tmp_path, "gateway-v0.json", [v0_case("a")])
    write_fixture(tmp_path, "routing.json", [refused_case("b", None)])
    assert checker.check(tmp_path, SCHEMA) == 0


def test_checker_accepts_board_errors(tmp_path):
    write_fixture(tmp_path, "gateway-v0.json", [v0_case("a")])
    write_fixture(
        tmp_path,
        "gateway-board.json",
        [refused_case("scope", "E_SCOPE"), refused_case("conflict", "E_CONFLICT")],
    )
    assert checker.check(tmp_path, SCHEMA) == 0


def test_checker_refuses_an_unknown_error(tmp_path):
    write_fixture(tmp_path, "gateway-v0.json", [refused_case("a", "E_NOPE")])
    assert checker.check(tmp_path, SCHEMA) == 1


def test_case_names_are_unique_across_files(tmp_path):
    write_fixture(tmp_path, "gateway-v0.json", [v0_case("same")])
    write_fixture(tmp_path, "gateway-board.json", [v0_case("same")])
    assert checker.check(tmp_path, SCHEMA) == 1


def test_no_fixture_files_is_a_missing_input(tmp_path):
    assert checker.check(tmp_path, SCHEMA) == 2
