"""The person-to-target map: from an ask to the target app, with the LAIX cases beside it.

Run from the repository root: py -3 -P tools/scripts/make-person-to-target-map.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1400 * SCALE, 1740 * SCALE

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"
ARROW = "#55595e"

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

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(path: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, s(size))


F_TITLE = font(BOLD, 34)
F_SUB = font(REG, 16)
F_SECTION = font(SEMI, 12)
F_BOX = font(SEMI, 15)
F_BOX_SUB = font(REG, 10.5)
F_LABEL = font(REG, 10.5)
F_NOTE = font(REG, 10)
F_LEGEND = font(REG, 10.5)


def rounded_path(x, y, w, h, r, steps=10):
    pts = []
    r = min(r, w / 2, h / 2)
    for cx, cy, a0, a1 in [
        (x + r, y + r, 180, 270),
        (x + w - r, y + r, 270, 360),
        (x + w - r, y + h - r, 0, 90),
        (x + r, y + h - r, 90, 180),
    ]:
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    pts.append(pts[0])
    return pts


def dashed_path(draw, pts, fill, width=1.5, dash=9, gap=6):
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


def box(draw, x, y, w, h, title, sub="", fill=BLUE_FILL, outline=BLUE_LINE, dashed=False):
    x, y, w, h = s(x), s(y), s(w), s(h)
    r = s(13)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=fill)
    if dashed:
        dashed_rounded_rect(draw, x, y, w, h, s(13), outline, width=1.5, dash=6, gap=4)
    else:
        draw.rounded_rectangle([x, y, x + w, y + h], radius=r, outline=outline, width=max(1, s(1.2)))
    cx = x + w / 2
    if sub:
        draw.text((cx, y + h * 0.40), title, font=F_BOX, fill=INK, anchor="mm")
        draw.text((cx, y + h * 0.66), sub, font=F_BOX_SUB, fill=MUTED, anchor="mm")
    else:
        draw.text((cx, y + h / 2), title, font=F_BOX, fill=INK, anchor="mm")


def arrowhead(draw, tip, angle, color, size=8):
    size = s(size)
    left = (tip[0] - size * math.cos(angle - 0.48), tip[1] - size * math.sin(angle - 0.48))
    right = (tip[0] - size * math.cos(angle + 0.48), tip[1] - size * math.sin(angle + 0.48))
    draw.polygon([tip, left, right], fill=color)


def arrow(draw, pts, color=ARROW, width=1.6, dashed=False, both=False):
    p = [(s(x), s(y)) for x, y in pts]
    if dashed:
        dashed_path(draw, p, color, width=width, dash=7, gap=5)
    else:
        draw.line(p, fill=color, width=max(1, s(width)), joint="curve")
    if len(p) >= 2:
        a = math.atan2(p[-1][1] - p[-2][1], p[-1][0] - p[-2][0])
        arrowhead(draw, p[-1], a, color)
        if both:
            a0 = math.atan2(p[0][1] - p[1][1], p[0][0] - p[1][0])
            arrowhead(draw, p[0], a0, color)


def label(draw, text, x, y, anchor="mm", size=10.5, color=HINT):
    f = font(REG, size)
    b = draw.textbbox((s(x), s(y)), text, font=f, anchor=anchor)
    pad = s(3)
    draw.rounded_rectangle([b[0] - pad, b[1] - pad, b[2] + pad, b[3] + pad], radius=s(3), fill=BG)
    draw.text((s(x), s(y)), text, font=f, fill=color, anchor=anchor)


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(30)), "HOPA - from person to target app", font=F_TITLE, fill=INK, anchor="la")
d.text((s(40), s(78)), "the whole journey: understand, decide, approve, execute", font=F_SUB, fill=MUTED, anchor="la")

# Lane A
dashed_rounded_rect(d, s(30), s(110), s(1340), s(670), s(22), BORDER, width=1.4, dash=10, gap=7)
d.text((s(52), s(126)), "PERSON -> UNDERSTAND -> DECIDE -> APPROVE", font=F_SECTION, fill=HINT, anchor="la")

box(d, 600, 150, 200, 65, "Person", fill=WHITE, outline=GREY_LINE)
box(d, 360, 250, 230, 65, "Typed goal", fill=WHITE, outline=GREY_LINE)
box(d, 810, 250, 230, 65, "Notification", fill=LAV_FILL, outline=LAV_LINE)
arrow(d, [(650, 215), (500, 250)])
# A notification does not come from the person: it is a sibling incoming event.
d.text((s(925), s(234)), "incoming event, not from the person", font=font(REG, 9.5), fill=HINT, anchor="mm")

box(d, 500, 340, 400, 100, "Understanding", "intent / app / time", fill=YEL_FILL, outline=YEL_LINE)
arrow(d, [(475, 315), (600, 340)])
arrow(d, [(925, 315), (800, 340)])
box(d, 960, 340, 300, 80, "Context", "catalogue / family / memory", fill=GRN_FILL, outline=GRN_LINE)
arrow(d, [(960, 380), (900, 380)])

# Real LAIX cases, not an abstract verb list.
d.rounded_rectangle([s(50), s(430), s(440), s(730)], radius=s(16), fill="#fffdf7", outline=YEL_LINE, width=max(1, s(1.2)))
d.text((s(70), s(448)), "Real cases in HOPA", font=font(SEMI, 12.5), fill=INK, anchor="la")
cases = [
    ("Which app?", "ask user"),
    ("Child wants to buy", "ask mom"),
    ("Screen unclear", "observe"),
    ("Later / schedule", "wait"),
    ("Did it work?", "verify"),
    ("Run it", "act"),
]
cy = 486
for case, verb in cases:
    d.text((s(70), s(cy)), case, font=font(SEMI, 10.5), fill=INK, anchor="lm")
    d.text((s(265), s(cy)), verb, font=font(REG, 10), fill=MUTED, anchor="lm")
    cy += 32
d.text((s(70), s(684)), "act goes to Approved task; others return to LAIX", font=font(REG, 9.5), fill=HINT, anchor="la")
arrow(d, [(440, 540), (500, 540)], width=1.2)
label(d, "one case", 470, 521, size=9.5)

box(d, 500, 490, 400, 100, "Decision (LAIX)", "one case, one verb", fill=YEL_FILL, outline=YEL_LINE)
arrow(d, [(700, 440), (700, 490)])
box(d, 960, 490, 300, 80, "Ask user / family", "clarify or approve", fill=BLUE_FILL, outline=BLUE_LINE)
arrow(d, [(900, 540), (960, 530)])
arrow(d, [(960, 550), (900, 565)], dashed=True)
label(d, "answer", 930, 575, size=9.5)

box(d, 500, 650, 400, 90, "Approved task", "run now or when due", fill=WHITE, outline=GREY_LINE)
arrow(d, [(700, 590), (700, 650)])
label(d, "act + allowed", 715, 625, anchor="lm", size=9.5)
arrow(d, [(1110, 570), (1110, 620), (900, 620), (900, 690)], dashed=True)
label(d, "approval", 1005, 610, size=9.5)
d.text((s(700), s(754)), "host scheduler holds a scheduled task until due", font=F_NOTE, fill=HINT, anchor="mm")

# Lane B
dashed_rounded_rect(d, s(30), s(810), s(1340), s(800), s(22), BORDER, width=1.4, dash=10, gap=7)
d.text((s(52), s(826)), "EXECUTE AN APPROVED TASK -> TARGET APP", font=F_SECTION, fill=HINT, anchor="la")

box(d, 500, 830, 400, 80, "Launch app", "target app starts here", fill=BLUE_FILL, outline=BLUE_LINE)
arrow(d, [(700, 740), (700, 830)])
box(d, 500, 960, 400, 80, "Saved recipe?", "RecipeStore", fill=GRN_FILL, outline=GRN_LINE)
arrow(d, [(700, 910), (700, 960)])

box(d, 220, 1110, 360, 80, "Replay recipe", "launch / tap / type - no model", fill=GRN_FILL, outline=GRN_LINE)
arrow(d, [(500, 1040), (400, 1110)])
label(d, "yes", 440, 1065, size=9.5)

gx, gy, gw, gh = 800, 1110, 470, 220
d.rounded_rectangle([s(gx), s(gy), s(gx + gw), s(gy + gh)], radius=s(16), fill="#f4faf6", outline=GRN_LINE, width=max(1, s(1.2)))
d.text((s(gx + 18), s(gy + 16)), "Adaptive loop - no recipe", font=font(SEMI, 12.5), fill=INK, anchor="la")
box(d, 820, 1150, 130, 60, "Observe", "tree / optional shot", fill=WHITE, outline=GREY_LINE)
box(d, 970, 1150, 140, 60, "Planner", "API model / local VLM?", fill=YEL_FILL, outline=YEL_LINE)
box(d, 1130, 1150, 120, 60, "Host executes", "launch / tap / type", fill=BLUE_FILL, outline=BLUE_LINE)
arrow(d, [(950, 1180), (970, 1180)])
arrow(d, [(1110, 1180), (1130, 1180)])
arrow(d, [(1190, 1210), (1190, 1260), (885, 1260), (885, 1210)], dashed=True)
label(d, "repeat", 1035, 1274, size=9.5)
arrow(d, [(900, 1040), (1035, 1110)])
label(d, "no", 965, 1065, size=9.5)

# Hints around execution
box(d, 220, 1240, 220, 70, "Saved skill", "hint, not executable", fill=LAV_FILL, outline=LAV_LINE)
arrow(d, [(440, 1275), (700, 1275), (700, 1080), (970, 1080), (970, 1150)], dashed=True)

box(d, 960, 1360, 300, 90, "LAIX gates", "risk / approval / verify / stop", fill=BLUE_FILL, outline=BLUE_LINE)
arrow(d, [(1110, 1360), (1190, 1210)], dashed=True)
label(d, "not per tap", 1155, 1315, size=9.5)

# Target app and receipt
box(d, 500, 1400, 400, 90, "Target app", "actions land here", fill=WHITE, outline=GREY_LINE)
arrow(d, [(400, 1190), (500, 1445)], width=1.2)
arrow(d, [(1035, 1330), (900, 1445)], width=1.2)
box(d, 500, 1540, 400, 80, "Verify + receipt", "what happened", fill=YEL_FILL, outline=YEL_LINE)
arrow(d, [(700, 1490), (700, 1540)])
box(d, 960, 1540, 300, 80, "Recipe candidate", "curate into app.md", fill=GRN_FILL, outline=GRN_LINE)
arrow(d, [(900, 1580), (960, 1580)])

# Notes and legend
d.text((s(52), s(1650)), "Saved skill = hint for the planner, not executable. A recipe runs without a model.", font=F_NOTE, fill=HINT, anchor="la")
d.text((s(52), s(1668)), "Local VLM is an option for the planner; device actions are local through accessibility, helper or bridge.", font=F_NOTE, fill=HINT, anchor="la")
d.text((s(52), s(1686)), "LAIX is called again only at gates: risk, approval, verify or stop - not for every tap.", font=F_NOTE, fill=HINT, anchor="la")


legend_y = 1710
d.text((s(52), s(legend_y)), "Colour = owning layer", font=F_LEGEND, fill=HINT, anchor="la")
items = [
    ("Clients / approval", BLUE_FILL, BLUE_LINE),
    ("Tasks / input", LAV_FILL, LAV_LINE),
    ("Understanding / decision", YEL_FILL, YEL_LINE),
    ("Execution / knowledge", GRN_FILL, GRN_LINE),
]
lx = 230
for text, fill, line in items:
    d.rounded_rectangle([s(lx), s(legend_y - 5), s(lx + 12), s(legend_y + 7)], radius=s(3), fill=fill, outline=line, width=max(1, s(0.8)))
    d.text((s(lx + 18), s(legend_y + 1)), text, font=F_LEGEND, fill=MUTED, anchor="lm")
    lx += 18 + int(d.textlength(text, font=F_LEGEND) / SCALE) + 24

img = img.resize((1400, 1740), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-person-to-target-map.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
