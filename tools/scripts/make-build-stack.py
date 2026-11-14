#!/usr/bin/env python3
"""Draw the build stack for the development story (SVG, no dependencies).

Run from the repository root:

    python tools/scripts/make-build-stack.py

The output is docs/media/hopa-build-stack.svg, embedded by
docs/project/operations/development-story.md.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "docs" / "media" / "hopa-build-stack.svg"

WIDTH = 980
TOP = 40
LAYER_H = 58
GAP = 8
LEFT = 140
RIGHT = 840

CREAM = "#F7F4EE"
INK = "#1C1B18"
MUTED = "#6B665C"
LINE = "#E4DFD3"
ACCENT = "#E8703A"
BLUE = "#0A59F7"
WHITE = "#FFFFFF"

LAYERS = [
    ("0", "Where we are today", "chat on a phone and a computer", "grey"),
    ("1", "It answers", "App team + Rulebook team", "blue"),
    ("2", "It can do things", "Hands team", "blue"),
    ("3", "It stops and asks", "App team + Rulebook team", "blue"),
    ("4", "It remembers", "Memory team", "orange"),
    ("5", "It decides and asks", "Judgement team + Family team", "orange"),
    ("6", "It knows how apps work", "Know-how team", "green"),
    ("7", "It works across devices", "Connection team", "green"),
    ("8", "It proves it works", "Numbers team", "green"),
]

TINTS = {
    "grey": "#F1ECE3",
    "blue": "#E8F0FF",
    "orange": "#FDEFE7",
    "green": "#EAF3EC",
}


def escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    height = TOP * 2 + len(LAYERS) * (LAYER_H + GAP) + 70
    out: list[str] = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" '
        f'viewBox="0 0 {WIDTH} {height}" font-family="Segoe UI, Arial, sans-serif">',
        f'<rect width="{WIDTH}" height="{height}" fill="{CREAM}"/>',
        f'<text x="{LEFT}" y="30" font-size="20" font-weight="600" fill="{INK}">'
        "How HOPA gets built: one ability at a time</text>",
        f'<text x="{LEFT}" y="54" font-size="12" fill="{MUTED}">'
        "Each step is one team, one written agreement and one demonstration. "
        "It stands on the step below.</text>",
    ]

    top = TOP + 46
    positions: list[tuple[int, int]] = []
    for index, (number, ability, team, tint) in enumerate(reversed(LAYERS)):
        y = top + index * (LAYER_H + GAP)
        positions.append((y, index))
        dash = ' stroke-dasharray="6 5"' if number == "0" else ""
        out.append(
            f'<rect x="{LEFT}" y="{y}" width="{RIGHT - LEFT}" height="{LAYER_H}" rx="12" '
            f'fill="{TINTS[tint]}" stroke="{LINE}"{dash}/>'
        )
        out.append(
            f'<circle cx="{LEFT + 34}" cy="{y + LAYER_H / 2}" r="16" fill="{BLUE}" '
            f'opacity="{0.25 if number == "0" else 1}"/>'
        )
        out.append(
            f'<text x="{LEFT + 34}" y="{y + LAYER_H / 2 + 5}" font-size="14" font-weight="700" '
            f'fill="{WHITE if number != "0" else INK}" text-anchor="middle">{number}</text>'
        )
        out.append(
            f'<text x="{LEFT + 64}" y="{y + 24}" font-size="16" font-weight="600" fill="{INK}">'
            f"{escape(ability)}</text>"
        )
        out.append(
            f'<text x="{LEFT + 64}" y="{y + 44}" font-size="12" fill="{MUTED}">'
            f"{escape(team)}</text>"
        )
        out.append(
            f'<text x="{RIGHT - 18}" y="{y + LAYER_H / 2 + 4}" font-size="11" fill="{MUTED}" '
            f'text-anchor="end">{"starting point" if number == "0" else "one team"}</text>'
        )

    # The build arrow on the left.
    first = positions[0][0]
    last = positions[-1][0]
    out.append(
        f'<line x1="{LEFT - 34}" y1="{last + LAYER_H - 10}" x2="{LEFT - 34}" y2="{first + 12}" '
        f'stroke="{ACCENT}" stroke-width="3"/>'
    )
    out.append(
        f'<polygon points="{LEFT - 34},{first} {LEFT - 41},{first + 14} {LEFT - 27},{first + 14}" '
        f'fill="{ACCENT}"/>'
    )
    out.append(
        f'<text x="{LEFT - 48}" y="{(first + last) / 2:.0f}" font-size="11" fill="{ACCENT}" '
        f'text-anchor="middle" transform="rotate(-90 {LEFT - 48},{(first + last) / 2:.0f})">'
        "the product grows upward</text>"
    )

    out.append(
        f'<text x="{LEFT}" y="{height - 22}" font-size="11" fill="{MUTED}">'
        "Every arrow between two layers is one written agreement between two teams. "
        "A step starts when the agreement below it is stable.</text>"
    )
    out.append("</svg>")

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {TARGET.relative_to(ROOT)} ({len(out)} elements)")


if __name__ == "__main__":
    main()

