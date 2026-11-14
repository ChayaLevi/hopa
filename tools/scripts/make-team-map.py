"""Generate the team map: one diagram of how the bricks and teams connect.

Deterministic layout, no external tooling: the output is
`docs/media/hopa-team-map.svg`. Three bands are three clocks (device, build,
learning); box colour is the owning team; arrows carry the artifact.
"""

from __future__ import annotations

import html
from pathlib import Path

W, H = 1280, 1000

INK = "#1C1B18"
MUTED = "#6B665C"
LINE = "#8C8579"
BORDER = "#C9C2B5"
BAND = "#D9D3C7"
PAPER = "#FFFFFF"

TEAM = {
    "clients": ("#DCE9F5", "Clients"),
    "platform": ("#E8E1F4", "Platform and services"),
    "knowledge": ("#DFEDE1", "Target-app knowledge"),
    "algorithms": ("#F7E8CE", "Algorithms and measurement"),
    "integration": ("#F3DEDE", "Integration, release and quality"),
    "shared": ("#E7E3DA", "Architecture and contracts"),
}

parts: list[str] = []


def add(text: str) -> None:
    parts.append(text)


def esc(value: str) -> str:
    return html.escape(value, quote=False)


def rect(x: int, y: int, w: int, h: int, fill: str, stroke: str = BORDER,
         dash: str | None = None, radius: int = 6) -> None:
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    add(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="1.2"{dash_attr}/>'
    )


def text(x: int, y: int, lines: list[str], size: int = 13, colour: str = INK,
         weight: str = "400", anchor: str = "middle") -> None:
    add(f'<text x="{x}" y="{y}" font-size="{size}" fill="{colour}" '
        f'font-weight="{weight}" text-anchor="{anchor}">')
    for index, line in enumerate(lines):
        dy = 0 if index == 0 else size + 3
        add(f'<tspan x="{x}" dy="{dy}">{esc(line)}</tspan>')
    add("</text>")


def box(x: int, y: int, w: int, h: int, label: list[str], team: str,
        dashed: bool = False) -> None:
    fill = TEAM[team][0] if team in TEAM else PAPER
    rect(x, y, w, h, fill, dash="5 4" if dashed else None)
    first = y + h // 2 - (len(label) - 1) * 7 + 5
    text(x + w // 2, first, label)


def arrow(points: list[tuple[int, int]], dashed: bool = False,
          head: bool = True, tail: bool = False) -> None:
    path = " ".join(
        f"{'M' if index == 0 else 'L'}{x},{y}" for index, (x, y) in enumerate(points)
    )
    dash = ' stroke-dasharray="6 4"' if dashed else ""
    marker = ' marker-end="url(#head)"' if head else ""
    start = ' marker-start="url(#tail)"' if tail else ""
    add(f'<path d="{path}" fill="none" stroke="{LINE}" stroke-width="1.4"{dash}{marker}{start}/>')


def label(x: int, y: int, value: str, rotate: int = 0) -> None:
    width = len(value) * 6.2 + 10
    transform = f' transform="rotate({rotate} {x} {y})"' if rotate else ""
    add(f'<g{transform}>')
    add(f'<rect x="{x - width / 2:.1f}" y="{y - 11}" width="{width:.1f}" height="16" '
        f'rx="3" fill="#F7F4EE" stroke="#E4DED2" stroke-width="0.8"/>')
    text(int(x), y + 1, [value], size=11, colour=MUTED)
    add("</g>")


def band(x: int, y: int, w: int, h: int, title: str) -> None:
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="none" '
        f'stroke="{BAND}" stroke-width="1.2" stroke-dasharray="7 5"/>')
    text(x + 16, y + 22, [title], size=12, weight="600", colour=MUTED, anchor="start")


add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
    f'viewBox="0 0 {W} {H}" font-family="Segoe UI, Arial, sans-serif">')
add("<defs>"
    f'<marker id="head" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
    f'markerHeight="7" orient="auto-start-reverse">'
    f'<path d="M0,0 L10,5 L0,10 z" fill="{LINE}"/></marker>'
    f'<marker id="tail" viewBox="0 0 10 10" refX="1" refY="5" markerWidth="7" '
    f'markerHeight="7" orient="auto-start-reverse">'
    f'<path d="M10,0 L0,5 L10,10 z" fill="{LINE}"/></marker></defs>')
rect(0, 0, W, H, "#F7F4EE", stroke="none", radius=0)

# 12 Architecture: a strip above everything.
rect(24, 16, 1232, 30, TEAM["shared"][0])
text(40, 36, ["12 Architecture and contracts - contracts, CI, release cut, signing - applies to every box below"],
     size=13, anchor="start")

# --- Band A: the device -----------------------------------------------------
band(24, 60, 1232, 322, "ON THE DEVICE - seconds - the loop repeats until the task closes")

box(160, 176, 130, 48, ["1 Clients"], "clients")
box(330, 176, 130, 48, ["3 Tasks"], "platform")
box(500, 176, 140, 48, ["13 Decision"], "algorithms")
box(680, 176, 130, 48, ["4 Family rules"], "clients")
box(850, 176, 130, 48, ["2 Execution"], "clients")
box(1020, 176, 130, 48, ["target app"], "external", dashed=True)

box(340, 100, 140, 38, ["9 Notifications"], "platform")
box(560, 84, 180, 40, ["model provider (network)"], "external", dashed=True)
box(500, 276, 160, 48, ["5 recipes (installed)"], "knowledge")
box(300, 276, 170, 48, ["catalogue", "apps, aliases, launch"], "shared")
arrow([(470, 288), (492, 288), (492, 234), (498, 230)])
label(430, 264, "candidates")

# Person
add(f'<ellipse cx="100" cy="200" rx="42" ry="24" fill="{PAPER}" stroke="{BORDER}" stroke-width="1.2"/>')
text(100, 205, ["Person"], size=13)

arrow([(142, 200), (158, 200)])
arrow([(290, 200), (328, 200)])
label(309, 190, "Task")
arrow([(460, 200), (498, 200)])
label(479, 190, "goal")
arrow([(640, 200), (678, 200)])
label(659, 190, "Action")
arrow([(810, 200), (848, 200)])
label(829, 190, "allow")
arrow([(980, 200), (1018, 200)])

arrow([(410, 138), (410, 174)])
label(410, 158, "event")

arrow([(600, 128), (600, 174)], dashed=True, tail=True)
label(668, 152, "prompt / Decision")

arrow([(560, 276), (560, 226)])
label(560, 252, "candidate steps")

arrow([(915, 224), (915, 250), (630, 250), (630, 226)])
label(770, 240, "next Observation")

arrow([(745, 226), (745, 340), (225, 340), (225, 226)], dashed=True, tail=True)
label(480, 336, "ask, answer")

# 11 Platform: a bracket under every band, because every cross-device arrow uses it.
rect(24, 822, 1232, 28, TEAM["platform"][0])
text(40, 841, ["11 Platform and services - every arrow that leaves a device travels over this layer"],
     size=12, anchor="start")

# --- Band B: the build ------------------------------------------------------
band(24, 396, 1232, 232, "BUILD AND DELIVERY - days - no person involved")

box(120, 470, 130, 60, ["6 Factory"], "knowledge")
box(320, 470, 130, 60, ["5 Knowledge"], "knowledge")
box(540, 470, 130, 60, ["8 Farm"], "integration")
box(760, 470, 130, 60, ["7 Delivery"], "knowledge")

arrow([(250, 500), (318, 500)])
label(284, 490, "new recipe")
arrow([(450, 500), (538, 500)])
label(494, 490, "version to test")
arrow([(560, 532), (560, 562), (400, 562), (400, 532)], dashed=True)
label(480, 572, "failures")
arrow([(670, 500), (758, 500)])
label(714, 490, "go or no-go")
arrow([(385, 470), (385, 444), (825, 444), (825, 468)])
label(600, 434, "package")

arrow([(890, 500), (920, 500), (920, 300), (662, 300)])
label(790, 290, "installs, signature checked")

# --- Band C: the learning loop ---------------------------------------------
band(24, 646, 1232, 160, "LEARNING - weeks - read in batches, change plans")

box(540, 700, 150, 60, ["10 Product lab"], "algorithms")
text(615, 786, ["failure list, bench score, pilot numbers"], size=11, colour=MUTED)

arrow([(605, 532), (605, 698)])
label(620, 600, "journals")
arrow([(900, 226), (900, 234), (1100, 234), (1100, 730), (692, 730)])
label(1100, 600, "journals")
arrow([(540, 730), (300, 730), (300, 500), (318, 500)])
label(288, 610, "failure list", rotate=-90)

# --- Legend -----------------------------------------------------------------
add(f'<text x="24" y="878" font-size="12" fill="{MUTED}">Box colour is the owning team</text>')
short = {
    "clients": "Clients",
    "platform": "Platform and services",
    "knowledge": "Target-app knowledge",
    "algorithms": "Algorithms and measurement",
    "integration": "Integration and quality",
    "shared": "Architecture and contracts",
}
columns = [(24, 892), (24, 920), (452, 892), (452, 920), (880, 892), (880, 920)]
order = ["clients", "platform", "knowledge", "algorithms", "integration", "shared"]
for key, (x, y) in zip(order, columns):
    rect(x, y, 16, 16, TEAM[key][0], radius=3)
    add(f'<text x="{x + 24}" y="{y + 12}" font-size="12" fill="{INK}">{esc(short[key])}</text>')

text(24, 960, ["Dashed arrow: feedback or a network call. Every other arrow is an artifact handed over."],
     size=12, colour=MUTED, anchor="start")
text(24, 982, ["White boxes are outside HOPA: the person, the model provider and the app HOPA operates."],
     size=12, colour=MUTED, anchor="start")

add("</svg>")

out = Path("docs/media/hopa-team-map.svg")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(parts), encoding="utf-8")
print(out, out.stat().st_size)
