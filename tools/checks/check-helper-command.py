"""The helper start command exists twice: Kotlin (app) and Python (bridge).

Both copy the same shell one-liner. When one side changes the start or the token
hand-off, the other must change too, or a phone started through the bridge runs a
different helper than the app expects.

The command points CLASSPATH at the installed APK: a zip on the classpath goes
through a PathClassLoader, which loads every dex in it, so the class that lives
in classes2.dex is found without copying anything to /data/local/tmp. ADR-0023
has the evidence; these fragments are what both sides must keep.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = (
    ROOT
    / "apps/android/capabilities/src/main/kotlin/com/hopa/capabilities/display/client/DisplayStart.kt"
)
PYTHON = ROOT / "services/bridge/src/hopa_bridge/device_android.py"

# Every part a correct start needs; keep both sides carrying all of them.
FRAGMENTS = (
    "pm path",
    "CLASSPATH=",
    "setsid app_process",
    "com.hopa.capabilities.display.helper.ShellMain",
    "--token=",
    "echo started",
)

# The staging that ADR-0023 removed. Its return would be a quiet regression: the
# command would still work, just slower and with 50 MB in shared storage.
FORBIDDEN = (
    "/data/local/tmp/hopa-dex",
    "unzip -o -q",
    "classes*.dex",
)


def main() -> int:
    problems: list[str] = []
    for name, path in (("Kotlin", KOTLIN), ("Python", PYTHON)):
        text = path.read_text(encoding="utf-8")
        for fragment in FRAGMENTS:
            if fragment not in text:
                problems.append(f"{name} is missing {fragment!r}")
        for fragment in FORBIDDEN:
            if fragment in text:
                problems.append(f"{name} still stages the dex: {fragment!r}")
    for problem in problems:
        print("FAIL ", problem)
    print(f"{len(problems)} helper command problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
