"""Generate the team map: one diagram of how the bricks and teams connect.

Deterministic layout, no external tooling: the output is
`docs/media/hopa-team-map.svg`. Three bands are three clocks (device, build,
learning); box colour is the owning team; arrows carry the artifact.
"""

from __future__ import annotations

import html
from pathlib import Path

W, H = 1280, 1210

INK = "#1C1B18"
MUTED = "#6B665C"
LINE = "#8C8579"
BORDER = "#C9C2B5"
BAND = "#D9D3C7"
PAPER = "#FFFFFF"
CELL = "#F1EDE4"

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
    first = y + h // 2 - (len(label) - 1) * 8 + 5
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
    add(f"<g{transform}>")
    add(f'<rect x="{x - width / 2:.1f}" y="{y - 11}" width="{width:.1f}" height="16" '
        f'rx="3" fill="#F7F4EE" stroke="#E4DED2" stroke-width="0.8"/>')
    text(int(x), y + 1, [value], size=11, colour=MUTED)
    add("</g>")


def band(x: int, y: int, w: int, h: int, title: str) -> None:
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="none" '
        f'stroke="{BAND}" stroke-width="1.2" stroke-dasharray="7 5"/>')
    text(x + 16, y + 22, [title], size=12, weight="600", colour=MUTED, anchor="start")


def rail(y: int, fill: str, title: str, cells: list[str]) -> None:
    rect(24, y, 1232, 44, fill)
    text(40, y + 28, [title], size=13, anchor="start")
    left, right, gap = 320, 1232, 14
    width = (right - left - gap * (len(cells) - 1)) / len(cells)
    x = left
    for cell in cells:
        rect(int(x), y + 8, int(width), 28, CELL, radius=4)
        text(int(x + width / 2), y + 27, [cell], size=12)
        x += width + gap


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

# 12 Architecture: what the shared role actually does.
rail(8, TEAM["shared"][0], "Architecture and contracts -",
     ["review every contract change", "keep both builds passing their checks", "build, sign and publish each release"])

# --- Band A: the device -----------------------------------------------------
band(24, 60, 1232, 330, "ON THE DEVICE - seconds - the loop repeats until the task closes")

add(f'<ellipse cx="100" cy="204" rx="42" ry="24" fill="{PAPER}" stroke="{BORDER}" stroke-width="1.2"/>')
text(100, 209, ["Person"], size=13)

box(160, 180, 130, 48, ["Clients"], "clients")
box(330, 180, 130, 48, ["Tasks"], "platform")
box(500, 180, 110, 48, ["router", "goal to apps"], "clients")
box(650, 180, 140, 48, ["Decision", "rules: LAIX"], "algorithms")
box(830, 180, 130, 48, ["Family rules"], "clients")
box(990, 180, 130, 48, ["Execution"], "clients")
box(1140, 180, 100, 48, ["target app"], "external", dashed=True)

box(340, 100, 140, 38, ["Notifications"], "platform")
box(620, 96, 180, 40, ["model provider (network)"], "external", dashed=True)
box(480, 280, 140, 48, ["catalogue (installed)", "ids, aliases, cases"], "shared")
box(630, 280, 160, 48, ["recipes (installed)"], "knowledge")

arrow([(142, 204), (158, 204)])
arrow([(290, 204), (328, 204)])
label(309, 170, "create task")
arrow([(460, 204), (498, 204)])
label(479, 166, "goal, state")
arrow([(610, 204), (648, 204)])
label(629, 188, "apps")
arrow([(790, 204), (828, 204)])
label(809, 194, "Action")
arrow([(960, 204), (988, 204)])
label(974, 194, "allow")
arrow([(1120, 204), (1138, 204)])

arrow([(410, 138), (410, 178)])
label(410, 158, "event")

arrow([(690, 178), (690, 136)], dashed=True)
arrow([(730, 136), (730, 178)], dashed=True)
label(668, 157, "prompt")
label(786, 157, "one action")
label(700, 86, "only if no recipe step fits")

arrow([(550, 280), (550, 230)])
label(550, 252, "which apps match")
arrow([(700, 280), (700, 230)])
label(700, 252, "steps")

arrow([(1040, 228), (1040, 264), (735, 264), (735, 230)])
label(885, 258, "Observation + record")

arrow([(880, 228), (880, 356), (225, 356), (225, 230)], dashed=True, tail=True)
label(480, 352, "ask, answer")

# --- Band B: the build ------------------------------------------------------
band(24, 404, 1232, 232, "BUILD AND DELIVERY - days - starts when a target app changes, not when someone asks")

box(240, 478, 130, 60, ["Recipe", "factory"], "knowledge")
box(440, 478, 130, 60, ["Target-app", "knowledge"], "knowledge")
box(860, 478, 130, 60, ["Verification", "farm"], "integration")
box(1060, 478, 130, 60, ["Recipe", "delivery"], "knowledge")

box(40, 466, 150, 50, ["onboard a", "target app"], "knowledge")
box(40, 544, 150, 58, ["catalogue, authored", "ids, aliases, launch"], "knowledge")
arrow([(115, 516), (115, 542)])
label(215, 620, "build its knowledge")
arrow([(115, 602), (115, 620), (315, 620), (315, 540)], dashed=True)
arrow([(190, 560), (205, 560), (205, 398), (550, 398), (550, 330)])
label(310, 392, "compiled into the client")

arrow([(370, 508), (438, 508)])
label(404, 498, "new recipe")
box(640, 478, 150, 60, ["Replay", "on recorded runs"], "algorithms")
arrow([(570, 508), (638, 508)])
label(604, 498, "candidate change")
arrow([(790, 508), (858, 508)])
label(824, 498, "what passed")
arrow([(880, 540), (880, 570), (500, 570), (500, 540)], dashed=True)
label(690, 580, "failures")
arrow([(990, 508), (1058, 508)])
label(1024, 498, "pass")
arrow([(505, 478), (505, 452), (1125, 452), (1125, 476)])
label(815, 442, "package")

arrow([(1190, 508), (1215, 508), (1215, 304), (792, 304)])
label(1000, 296, "installs, signature checked")

# --- Band C: the learning loop ---------------------------------------------
band(24, 646, 1232, 354, "LEARNING - weeks - read in batches, change plans")
text(40, 684, ["Product lab - what it produces, and who gets it"],
     size=13, weight="600", anchor="start")
rect(40, 692, 900, 292, "#FBF7EE", dash="4 4", radius=10)

arrow([(705, 538), (705, 670), (470, 670), (470, 690)])
label(620, 640, "journals")
arrow([(1080, 228), (1100, 228), (1100, 670), (430, 670), (430, 690)])
label(1100, 600, "journals")

rows = [
    (712, ["list what broke, per app version"], "the failure list", "weekly",
     "to Target-app knowledge"),
    (772, ["run the 20-task test set"], "tasks passed", "every release",
     "to the next release: ship or block"),
    (832, ["read the pilot homes' runs"], "minutes per task, steps, repeats", "monthly",
     "to the next plan"),
    (892, ["goal: still works after an app update", "measured: steps that pass, before and after"],
     "method + data + report", "about 6 months",
     "to Target-app knowledge: adopt or drop"),
]
for y, left, artifact, cadence, consumer in rows:
    box(60, y, 300, 48, left, "algorithms")
    centre = y + 24
    arrow([(360, centre), (620, centre)])
    label(490, centre - 3, artifact)
    text(490, centre + 13, [cadence], size=11, colour=MUTED)
    text(634, centre + 4, [consumer], size=13, anchor="start")
text(60, 962, ["One researcher owns the bottom row: a goal today's recipes cannot reach."], size=11, colour=MUTED, anchor="start")
text(60, 978, ["It is measured as the share of steps still passing after an update; a method comes back, adopted or dropped."], size=11, colour=MUTED, anchor="start")

# 11 Platform: what the transport layer actually does.
rail(1012, TEAM["platform"][0], "Platform and services -",
     ["pair the devices once", "keep the link encrypted", "renew access codes", "message format both sides use"])

# --- Legend -----------------------------------------------------------------
add(f'<text x="24" y="1096" font-size="12" fill="{MUTED}">Box colour is the owning team</text>')
short = {
    "clients": "Clients",
    "platform": "Platform and services",
    "knowledge": "Target-app knowledge",
    "algorithms": "Algorithms and measurement",
    "integration": "Integration and quality",
    "shared": "Architecture and contracts",
}
columns = [(24, 1110), (24, 1138), (452, 1110), (452, 1138), (880, 1110), (880, 1138)]
order = ["clients", "platform", "knowledge", "algorithms", "integration", "shared"]
for key, (x, y) in zip(order, columns):
    rect(x, y, 16, 16, TEAM[key][0], radius=3)
    add(f'<text x="{x + 24}" y="{y + 12}" font-size="12" fill="{INK}">{esc(short[key])}</text>')

text(24, 1176, ["Decision holds the LAIX policy and its record. The client, not LAIX, calls the model provider."],
     size=12, colour=MUTED, anchor="start")
text(24, 1196, ["A step covered by a recipe runs without a model call; the call is the fallback for everything else."],
     size=12, colour=MUTED, anchor="start")

add("</svg>")

out = Path("docs/media/hopa-team-map.svg")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(parts), encoding="utf-8")
print(out, out.stat().st_size)
