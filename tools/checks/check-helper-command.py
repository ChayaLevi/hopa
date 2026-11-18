"""The helper start command exists three times: Kotlin (app), Python (bridge) and
the R8 keep rule.

All three name the same class. When one moves, the others must follow, or the
command points at a class the APK does not carry, or R8 strips a class nothing
references.

The command points CLASSPATH at the installed APK: a zip on the classpath goes
through a PathClassLoader, which loads every dex in it, so the class that lives
in classes2.dex is found without copying anything to /data/local/tmp. ADR-0023
has the evidence; these fragments are what all three sides must keep.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = (
    ROOT
    / "apps/android/capabilities/src/main/kotlin/com/hopa/capabilities/display/client/DisplayStart.kt"
)
PYTHON = ROOT / "services/bridge/src/hopa_bridge/device_android.py"
PROGUARD = ROOT / "apps/android/app/proguard-rules.pro"

SHELL_MAIN = "com.hopa.capabilities.display.helper.ShellMain"

# Every part a correct start needs; keep both sides carrying all of them.
FRAGMENTS = (
    "pm path",
    "CLASSPATH=",
    "setsid app_process",
    SHELL_MAIN,
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

    # The helper is reached by name, so nothing in the app references it: without
    # this rule R8 drops the class and the start fails with no message.
    keep = PROGUARD.read_text(encoding="utf-8")
    if f"-keep class {SHELL_MAIN}" not in keep:
        problems.append(f"proguard-rules.pro does not keep {SHELL_MAIN}")

    for problem in problems:
        print("FAIL ", problem)
    print(f"{len(problems)} helper command problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
