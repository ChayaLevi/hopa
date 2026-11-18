import importlib.util
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECKER = ROOT / "tools" / "checks" / "check-wire-types.py"

BOX = {
    "type": "object",
    "required": ["id", "v"],
    "properties": {
        "id": {"type": "string"},
        "v": {"const": 1},
        "note": {"type": "string"},
        "body": {"type": "object", "default": {}},
    },
}


def _load():
    spec = importlib.util.spec_from_file_location("check_wire_types", CHECKER)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def _compare(props):
    checker = _load()
    return checker.compare("Box", props, {"box": BOX}, "box", ())


def _agreeing():
    return {
        "id": ("String", None),
        "v": ("Int", "1"),
        "note": ("String?", None),
        "body": ("JsonObject", "JsonObject(emptyMap())"),
    }


def test_the_repository_agrees_with_its_schemas():
    checker = _load()
    assert checker.check() == []


def test_the_agreeing_shape_is_quiet():
    assert _compare(_agreeing()) == []


def test_a_default_the_schema_does_not_have_is_reported():
    props = _agreeing()
    props["v"] = ("Int", "2")
    problems = _compare(props)
    assert problems == ["Box.v: Kotlin default 2, schema default 1"]


def test_a_missing_default_is_reported():
    props = _agreeing()
    props["v"] = ("Int", None)
    problems = _compare(props)
    assert problems == ["Box.v: schema default 1, Kotlin has none"]


def test_a_wrong_type_is_reported():
    props = _agreeing()
    props["id"] = ("Int", None)
    problems = _compare(props)
    assert problems == ["Box.id: Kotlin Int, schema says String"]


def test_an_optional_field_must_be_nullable():
    props = _agreeing()
    props["note"] = ("String", None)
    problems = _compare(props)
    assert problems == ["Box.note: Kotlin non-null, schema nullable"]


def test_a_field_the_schema_lacks_is_reported():
    props = _agreeing()
    props["extra"] = ("String", None)
    problems = _compare(props)
    assert problems == ["Box.extra: not in box.schema.json"]


def test_a_field_the_class_lacks_is_reported():
    props = _agreeing()
    del props["id"]
    problems = _compare(props)
    assert problems == ["Box.id: in the schema, missing from the Kotlin class"]


def test_an_annotation_before_val_is_parsed():
    checker = _load()
    text = (
        "data class Box(\n"
        '    @SerialName("from") val from: String? = null,\n'
        "    val v: Int = 1,\n"
        ")"
    )
    assert checker.parse(text) == {"Box": {"from": ("String?", "null"), "v": ("Int", "1")}}
