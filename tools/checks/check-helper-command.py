"""The helper start command exists twice: Kotlin (app) and Python (bridge).

Both copy the same shell one-liner. When one side changes the dex hunt or the
token hand-off, the other must change too, or a phone started through the
bridge runs a different helper than the app expects.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = (
    ROOT
    / "apps/android/capabilities/src/main/kotlin/com/hopa/capabilities/display/DisplayStart.kt"
)
PYTHON = ROOT / "services/bridge/src/hopa_bridge/device_android.py"

# Every part a correct start needs; keep both sides carrying all of them.
FRAGMENTS = (
    "pm path",
    "/data/local/tmp/hopa-dex",
    "classes*.dex",
    "com/hopa/capabilities/display/ShellMain",
    "setsid app_process",
    "--token=",
    "echo started",
)


def main() -> int:
    problems: list[str] = []
    for name, path in (("Kotlin", KOTLIN), ("Python", PYTHON)):
        text = path.read_text(encoding="utf-8")
        for fragment in FRAGMENTS:
            if fragment not in text:
                problems.append(f"{name} is missing {fragment!r}")
    for problem in problems:
        print("FAIL ", problem)
    print(f"{len(problems)} helper command problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
