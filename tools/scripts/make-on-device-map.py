"""The on-device map: what runs on the phone itself, without a PC.

Run from the repository root: py -3 -P tools/scripts/make-on-device-map.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1800 * SCALE, 800 * SCALE

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"
ARROW = "#55595e"
LABEL_BG = BG

BLUE_FILL = "#e7f0fb"
BLUE_LINE = "#b7cbe8"
LAV_FILL = "#ece9fb"
LAV_LINE = "#c8c0e8"
YEL_FILL = "#fbf3dc"
YEL_LINE = "#e4d5a8"
GRN_FILL = "#e5f3ea"
GRN_LINE = "#b9d9c4"
WHITE = "#ffffff"
GREY_LINE = "#cfcfcf"

FONT_DIR = Path("C:/Windows/Fonts")
REG = str(FONT_DIR / "segoeui.ttf")
SEMI = str(FONT_DIR / "seguisb.ttf")
BOLD = str(FONT_DIR / "segoeuib.ttf")


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(path: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, s(size))


F_TITLE = font(BOLD, 34)
F_SUB = font(REG, 16)
F_SECTION = font(SEMI, 12)
F_BOX = font(SEMI, 15)
F_BOX_SUB = font(REG, 10.5)
F_LABEL = font(REG, 11)
F_NOTE = font(REG, 10)
F_LEGEND = font(REG, 10.5)


def rounded_path(x, y, w, h, r, steps=10):
    """Points around a rounded rectangle, clockwise from top-left."""
    pts = []
    r = min(r, w / 2, h / 2)
    corners = [
        (x + r, y + r, 180, 270),
        (x + w - r, y + r, 270, 360),
        (x + w - r, y + h - r, 0, 90),
        (x + r, y + h - r, 90, 180),
    ]
    for cx, cy, a0, a1 in corners:
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    pts.append(pts[0])
    return pts


def dashed_path(draw, pts, fill, width=1.5, dash=9, gap=6):
    """Draw a dashed polyline inside the current 2x canvas."""
    dash = s(dash)
    gap = s(gap)
    width = max(1, s(width))
    carry = 0.0
    drawing = True
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        dx, dy = x2 - x1, y2 - y1
        length = math.hypot(dx, dy)
        if length == 0:
            continue
        ux, uy = dx / length, dy / length
        pos = 0.0
        while pos < length:
            span = dash if drawing else gap
            take = min(span - carry, length - pos)
            if drawing:
                draw.line(
                    [(x1 + ux * pos, y1 + uy * pos), (x1 + ux * (pos + take), y1 + uy * (pos + take))],
                    fill=fill,
                    width=width,
                )
            pos += take
            carry += take
            if carry >= span - 0.01:
                carry = 0.0
                drawing = not drawing


def dashed_rounded_rect(draw, x, y, w, h, r, fill, width=1.5, dash=9, gap=6):
    dashed_path(draw, rounded_path(x, y, w, h, r), fill, width, dash, gap)


def box(
    draw,
    x,
    y,
    w,
    h,
    title,
    sub="",
    fill=BLUE_FILL,
    outline=BLUE_LINE,
    dashed=False,
    title_size=15,
    sub_size=10.5,
):
    x, y, w, h = s(x), s(y), s(w), s(h)
    r = s(13)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=fill)
    if dashed:
        dashed_rounded_rect(draw, x, y, w, h, 13, outline, width=1.5, dash=6, gap=4)
    else:
        draw.rounded_rectangle([x, y, x + w, y + h], radius=r, outline=outline, width=max(1, s(1.2)))
    cx = x + w / 2
    if sub:
        draw.text((cx, y + h * 0.40), title, font=font(SEMI, title_size), fill=INK, anchor="mm")
        draw.text((cx, y + h * 0.66), sub, font=font(REG, sub_size), fill=MUTED, anchor="mm")
    else:
        draw.text((cx, y + h / 2), title, font=font(SEMI, title_size), fill=INK, anchor="mm")


def arrowhead(draw, tip, angle, color, size=8):
    size = s(size)
    left = (tip[0] - size * math.cos(angle - 0.48), tip[1] - size * math.sin(angle - 0.48))
    right = (tip[0] - size * math.cos(angle + 0.48), tip[1] - size * math.sin(angle + 0.48))
    draw.polygon([tip, left, right], fill=color)


def arrow(draw, pts, color=ARROW, width=1.6, dashed=False, both=False, head=8):
    p = [(s(x), s(y)) for x, y in pts]
    if dashed:
        dashed_path(draw, p, color, width=width, dash=7, gap=5)
    else:
        draw.line(p, fill=color, width=max(1, s(width)), joint="curve")
    if len(p) >= 2:
        a = math.atan2(p[-1][1] - p[-2][1], p[-1][0] - p[-2][0])
        arrowhead(draw, p[-1], a, color, head)
        if both:
            a0 = math.atan2(p[0][1] - p[1][1], p[0][0] - p[1][0])
            arrowhead(draw, p[0], a0, color, head)


def label(draw, text, x, y, anchor="mm", size=11, color=HINT, fill=True):
    f = font(REG, size)
    b = draw.textbbox((s(x), s(y)), text, font=f, anchor=anchor)
    if fill:
        pad = s(3)
        draw.rounded_rectangle(
            [b[0] - pad, b[1] - pad, b[2] + pad, b[3] + pad],
            radius=s(3),
            fill=LABEL_BG,
        )
    draw.text((s(x), s(y)), text, font=f, fill=color, anchor=anchor)


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

# Header
d.text((s(40), s(30)), "HOPA — on device", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(78)),
    "seconds: the loop repeats until the task closes",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)

# Outer dashed frame
ox, oy, ow, oh = s(30), s(112), s(1740), s(648)
dashed_rounded_rect(d, ox, oy, ow, oh, s(22), BORDER, width=1.4, dash=10, gap=7)
d.text((s(52), s(128)), "ON DEVICE", font=F_SECTION, fill=HINT, anchor="la")

# Main row
box(d, 55, 330, 120, 80, "Person", fill=WHITE, outline=GREY_LINE)
box(d, 215, 315, 155, 110, "Clients")
box(d, 450, 315, 200, 110, "Tasks", "waits, then resumes", fill=LAV_FILL, outline=LAV_LINE)
box(d, 685, 315, 180, 110, "Router", "one or many apps")
box(d, 915, 290, 230, 145, "Decision", "rules: LAIX stop or repeat", fill=YEL_FILL, outline=YEL_LINE)
box(d, 1195, 315, 170, 100, "Approval gate", "allow / ask / deny")
box(d, 1415, 315, 190, 100, "Execution", "drives apps, files")
box(d, 1645, 330, 115, 80, "Target app", fill=WHITE, outline=GREY_LINE)

# Lanes above and below
box(d, 445, 170, 200, 80, "Notifications", fill=LAV_FILL, outline=LAV_LINE)
box(d, 665, 165, 200, 85, "Family context", "who, role, rules")
box(d, 930, 145, 230, 85, "Model provider", "network", fill=WHITE, outline=GREY_LINE, dashed=True)
box(d, 665, 520, 220, 95, "Catalogue (installed)", "ids, aliases, cases", fill=GRN_FILL, outline=GRN_LINE)
box(d, 930, 520, 220, 95, "Recipes (installed)", "steps", fill=GRN_FILL, outline=GRN_LINE)
box(d, 1390, 520, 245, 100, "Observation + record", "what happened", fill=YEL_FILL, outline=YEL_LINE)

# Arrows
arrow(d, [(175, 370), (215, 370)])
arrow(d, [(370, 370), (450, 370)])
label(d, "create task", 410, 348, size=10.0)
arrow(d, [(545, 250), (545, 315)])
label(d, "event", 558, 282, anchor="lm", size=10.0)
arrow(d, [(620, 315), (700, 250)], dashed=True)
label(d, "who, role, rules", 660, 292, size=10.0)
arrow(d, [(765, 250), (765, 315)])
label(d, "allowed apps, risk", 780, 282, anchor="lm", size=10.0)
arrow(d, [(650, 370), (685, 370)])
arrow(d, [(865, 370), (915, 370)])
label(d, "apps", 890, 350, size=10.5)
arrow(d, [(1145, 362), (1195, 362)])
label(d, "Action", 1170, 342, size=10.5)
arrow(d, [(1365, 365), (1415, 365)])
label(d, "allow", 1390, 345, size=10.5)
arrow(d, [(1605, 365), (1645, 365)])

# Model provider: prompt up, one action down
arrow(d, [(1000, 290), (1000, 230)])
label(d, "prompt", 986, 260, anchor="rm", size=10.5)
arrow(d, [(1090, 230), (1090, 290)])
label(d, "one action", 1106, 260, anchor="lm", size=10.5)
label(d, "only if no recipe step fits", 1045, 124, size=10.5, color=HINT)

# Catalogue and recipes
arrow(d, [(820, 520), (820, 425)])
label(d, "which apps match", 835, 472, anchor="lm", size=10.5)
arrow(d, [(1040, 520), (1040, 435)])
label(d, "steps", 1055, 478, anchor="lm", size=10.5)

# Observation: execution -> record, record -> recipes, report -> decision
arrow(d, [(1510, 415), (1510, 520)])
label(d, "Observation + record", 1525, 468, anchor="lm", size=10.5)
arrow(d, [(1390, 570), (1150, 570)], dashed=True)
label(d, "correction", 1270, 550, size=10.5)
arrow(d, [(1400, 520), (1400, 475), (1140, 475), (1140, 435)], dashed=True)
label(d, "record", 1260, 455, size=10.5)

# Ask / answer back to person
arrow(d, [(550, 425), (550, 460), (292, 460), (292, 425)], dashed=True, both=True)
label(d, "ask / answer", 420, 478, size=10.0)

# Legend
legend_y = 688
d.text((s(52), s(legend_y)), "Colour = owning team", font=F_LEGEND, fill=HINT, anchor="la")
items = [
    ("Clients", BLUE_FILL, BLUE_LINE),
    ("Tasks / notifications", LAV_FILL, LAV_LINE),
    ("Algorithms & measurement", YEL_FILL, YEL_LINE),
    ("Target-app knowledge", GRN_FILL, GRN_LINE),
]
lx = 230
for text, fill, line in items:
    d.rounded_rectangle(
        [s(lx), s(legend_y - 5), s(lx + 12), s(legend_y + 7)],
        radius=s(3),
        fill=fill,
        outline=line,
        width=max(1, s(0.8)),
    )
    d.text((s(lx + 18), s(legend_y + 1)), text, font=F_LEGEND, fill=MUTED, anchor="lm")
    lx += 18 + int(d.textlength(text, font=F_LEGEND) / SCALE) + 24

# Footer note
d.text(
    (s(52), s(724)),
    "The client calls the model only when the recipe path does not fit. "
    "A recipe step runs without a model call.",
    font=F_LEGEND,
    fill=HINT,
    anchor="la",
)

img = img.resize((1800, 800), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-on-device-map.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
