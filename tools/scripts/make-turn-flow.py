"""The turn flow: branches, the loop, every arrow labelled with what it carries.

Run from the repository root: py -3 -P tools/scripts/make-turn-flow.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1520, 2452

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
LABEL = "#3c4043"
BORDER = "#d8d1c4"
ARROW = "#4f5459"

BLUE_FILL = "#e7f0fb"
BLUE_LINE = "#b7cbe8"
LAV_FILL = "#ece9fb"
LAV_LINE = "#c8c0e8"
YEL_FILL = "#fbf3dc"
YEL_LINE = "#e4d5a8"
GRN_FILL = "#e5f3ea"
GRN_LINE = "#b9d9c4"
WHITE = "#ffffff"
GREY_FILL = "#f4f3f0"
GREY_LINE = "#c9c6bf"

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(path: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, s(size))


F_TITLE = font(BOLD, 29)
F_SUB = font(REG, 13)
F_BOX = font(SEMI, 12.5)
F_DIA = font(SEMI, 11.5)
F_SUB_TXT = font(REG, 9.5)
F_EDGE = font(REG, 9.5)
F_LEGEND = font(REG, 9.5)
F_SECTION = font(SEMI, 10.5)


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


def box(draw, x, y, w, h, title, subs=(), fill=WHITE, line=GREY_LINE, dashed=False):
    x, y, w, h = s(x), s(y), s(w), s(h)
    r = s(10)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=fill)
    if dashed:
        dashed_path(draw, rounded_path(x, y, w, h, r), line, width=1.3, dash=5, gap=4)
    else:
        draw.rounded_rectangle([x, y, x + w, y + h], radius=r, outline=line, width=max(1, s(1.1)))
    if not subs:
        draw.text((x + w / 2, y + h / 2), title, font=F_BOX, fill=INK, anchor="mm")
        return
    draw.text((x + s(13), y + s(18)), title, font=F_BOX, fill=INK, anchor="la")
    ty = y + s(35)
    for line_text in subs:
        draw.text((x + s(13), ty), line_text, font=F_SUB_TXT, fill=MUTED, anchor="la")
        ty += s(14)


def diamond(draw, cx, cy, w, h, lines, fill=YEL_FILL, line=YEL_LINE):
    pts = [(s(cx), s(cy - h / 2)), (s(cx + w / 2), s(cy)), (s(cx), s(cy + h / 2)), (s(cx - w / 2), s(cy))]
    draw.polygon(pts, fill=fill)
    draw.line(pts + [pts[0]], fill=line, width=max(1, s(1.2)), joint="curve")
    step = 15
    total = step * (len(lines) - 1)
    ty = s(cy) - total / 2 * SCALE
    for line_text in lines:
        draw.text((s(cx), ty), line_text, font=F_DIA, fill=INK, anchor="mm")
        ty += s(step)


def arrowhead(draw, tip, angle, color, size=6.5):
    size = s(size)
    left = (tip[0] - size * math.cos(angle - 0.5), tip[1] - size * math.sin(angle - 0.5))
    right = (tip[0] - size * math.cos(angle + 0.5), tip[1] - size * math.sin(angle + 0.5))
    draw.polygon([tip, left, right], fill=color)


def arrow(draw, pts, color=ARROW, width=1.5, dashed=False, head=6.5):
    p = [(s(x), s(y)) for x, y in pts]
    if dashed:
        dashed_path(draw, p, color, width=width, dash=5, gap=4)
    else:
        draw.line(p, fill=color, width=max(1, s(width)), joint="curve")
    if len(p) >= 2:
        a = math.atan2(p[-1][1] - p[-2][1], p[-1][0] - p[-2][0])
        arrowhead(draw, p[-1], a, color, head)


def edge(draw, lines, x, y, anchor="lm"):
    f = F_EDGE
    widths = [draw.textlength(t, font=f) / SCALE for t in lines]
    w = max(widths)
    h = 12 * len(lines)
    if anchor == "lm":
        x0, y0 = x, y - h / 2
    elif anchor == "rm":
        x0, y0 = x - w, y - h / 2
    elif anchor == "mb":
        x0, y0 = x - w / 2, y - h
    else:
        x0, y0 = x - w / 2, y - h / 2
    draw.rounded_rectangle([s(x0 - 4), s(y0 - 1.5), s(x0 + w + 4), s(y0 + h + 1.5)], radius=s(3), fill=BG)
    ty = y0
    for t in lines:
        draw.text((s(x0), s(ty + 6)), t, font=f, fill=LABEL, anchor="lm")
        ty += 12


def terminal(draw, x, y):
    draw.ellipse([s(x - 4), s(y - 4), s(x + 4), s(y + 4)], fill=ARROW)


def chip(draw, x, y, text="MODEL"):
    f = font(SEMI, 8)
    w = draw.textlength(text, font=f) / SCALE + 16
    draw.rounded_rectangle([s(x), s(y), s(x + w), s(y + 17)], radius=s(8), fill="#fdecc8", outline="#d9b06a", width=max(1, s(0.9)))
    draw.text((s(x + w / 2), s(y + 8.5)), text, font=f, fill="#7a5a1c", anchor="mm")


def note(draw, text, x, y, size=9.5, color=HINT):
    draw.text((s(x), s(y)), text, font=font(REG, size), fill=color, anchor="la")


img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(24)), "How one HOPA turn flows now", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(66)),
    "Android app, main b1de46e. Read the label on an arrow: it says what travels, or why that branch was picked.",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)

# ── entry and the optional voice call ────────────────────────────────────────
box(d, 540, 96, 440, 64, "A person sends a goal", ["text, voice, a shared file, a quick ask"], fill=BLUE_FILL, line=BLUE_LINE)
box(d, 60, 96, 390, 66, "voice, before the turn", ["the PC turns the WAV into text"], fill=YEL_FILL, line=YEL_LINE, dashed=True)
chip(d, 378, 103)
arrow(d, [(450, 129), (540, 129)], dashed=True)
edge(d, ["the text"], 495, 117, "mb")

diamond(d, 760, 232, 380, 80, ["a turn is already running?"])
arrow(d, [(760, 160), (760, 192)])
edge(d, ["the goal, as typed"], 772, 174)

arrow(d, [(950, 232), (1100, 232)])
edge(d, ["another turn is running"], 1025, 215, "mb")
box(d, 1100, 192, 340, 82, "a second goal, in parallel", [
    "its own hidden screen, already up",
    "max two at once",
    "a third goal is dropped",
], fill=BLUE_FILL, line=BLUE_LINE, dashed=True)
terminal(d, 1440, 232)

box(d, 540, 340, 440, 58, "the message is saved", ["in the phone's local database"])
arrow(d, [(760, 272), (760, 340)])
edge(d, ["nothing else is running"], 772, 306)

diamond(d, 760, 478, 380, 80, ["plan first is on?"])
arrow(d, [(760, 398), (760, 438)])
edge(d, ["the saved message"], 772, 418)

arrow(d, [(950, 478), (1020, 478), (1020, 471), (1100, 471)])
edge(d, ["plan first is on"], 1025, 457, "mb")
box(d, 1100, 438, 340, 66, "the model writes a plan", ["at most six lines, no action"], fill=YEL_FILL, line=YEL_LINE)
chip(d, 1368, 445)
arrow(d, [(1270, 504), (1270, 586)])
edge(d, ["six lines of text"], 1282, 545)
box(d, 1100, 586, 340, 66, "the person reads it", ["the plan is never used again"], fill=BLUE_FILL, line=BLUE_LINE)
arrow(d, [(1100, 619), (980, 619)])
edge(d, ["run now"], 1040, 606, "mb")
arrow(d, [(1270, 652), (1270, 672)])
box(d, 1100, 672, 340, 38, "cancel: nothing runs", [], fill=GREY_FILL, line=GREY_LINE, dashed=True)

box(d, 540, 588, 440, 66, "check the screen can be read", ["picks one source: hidden, then yours", "the tree is kept only as yes or no"])
arrow(d, [(760, 518), (760, 588)])
edge(d, ["plan first is off"], 772, 554)

diamond(d, 760, 728, 380, 80, ["did a source answer?"])
arrow(d, [(760, 654), (760, 688)])
edge(d, ["a tree came back"], 772, 670)

arrow(d, [(570, 728), (450, 728)])
edge(d, ["nothing answered"], 510, 714, "mb")
box(d, 60, 690, 390, 76, "say what is missing, stop", ["the error, named in the message"])
terminal(d, 60, 728)

arrow(d, [(760, 768), (760, 838)])
edge(d, ["that source is kept"], 772, 803)

diamond(d, 760, 880, 400, 84, ["a saved recipe for", "these exact words?"])
arrow(d, [(960, 880), (1100, 873)])
edge(d, ["same words, replay on"], 1030, 856, "mb")
box(d, 1100, 840, 340, 62, "replay the saved steps", ["no model, no tokens"], fill=GRN_FILL, line=GRN_LINE)
arrow(d, [(1270, 902), (1270, 920)])
edge(d, ["every step ran"], 1282, 911)
box(d, 1100, 920, 340, 58, "the replay finished", ["its own receipt, 0 tokens"], fill=GRN_FILL, line=GRN_LINE)
terminal(d, 1440, 949)
arrow(d, [(1100, 890), (1060, 890), (1060, 1000)], dashed=True)
edge(d, ["a step failed"], 1054, 950, "rm")

# ── the loop ─────────────────────────────────────────────────────────────────
dashed_path(d, rounded_path(s(470), s(1000), s(990), s(875), s(18)), BORDER, width=1.3, dash=10, gap=7)
d.text((s(492), s(1012)), "LOOP   one step per model call   ·   14 steps   ·   120 s for the whole turn", font=F_SECTION, fill=HINT, anchor="la")

box(d, 60, 1030, 390, 140, "collect the context", [
    "memory and tone, saved skills",
    "installed apps, typed app calls",
    "history, catalog and router hint",
    "all local, nothing is sent yet",
], fill=YEL_FILL, line=YEL_LINE)
box(d, 540, 1030, 440, 80, "model call", [
    "rules, history, the goal, the tree",
    "SCREEN TREE + ELEMENTS, never a photo",
    "one action, plain text, or a question",
], fill=YEL_FILL, line=YEL_LINE, )
chip(d, 908, 1037)
arrow(d, [(450, 1085), (540, 1085)])
edge(d, ["context + prompt"], 495, 1071, "mb")
arrow(d, [(760, 922), (760, 1030)])
edge(d, ["no recipe"], 772, 976)

diamond(d, 760, 1210, 380, 84, ["what came back?"])
arrow(d, [(760, 1110), (760, 1168)])
edge(d, ["the reply, parsed"], 772, 1139)

arrow(d, [(950, 1196), (1020, 1196), (1020, 1198), (1100, 1198)])
edge(d, ["plain text, or done"], 1025, 1180, "mb")
box(d, 1100, 1168, 340, 60, "an answer, no action", ["plain text, or done: true"], fill=LAV_FILL, line=LAV_LINE)
terminal(d, 1440, 1198)

arrow(d, [(950, 1224), (1020, 1224), (1020, 1274), (1100, 1274)])
edge(d, ["it asks a question"], 1025, 1258, "mb")
box(d, 1100, 1244, 340, 60, "a question with options", ["a tap starts a new turn"], fill=LAV_FILL, line=LAV_LINE)
terminal(d, 1440, 1274)

box(d, 540, 1315, 440, 52, "gate: how risky is it?", ["safe / sensitive / payment"])
arrow(d, [(760, 1252), (760, 1315)])
edge(d, ["an action"], 772, 1284)

diamond(d, 760, 1465, 380, 80, ["may it run right now?"])
arrow(d, [(760, 1367), (760, 1425)])
edge(d, ["a risk level"], 772, 1396)

arrow(d, [(950, 1465), (1100, 1465)])
edge(d, ["needs approval"], 1025, 1449, "mb")
box(d, 1100, 1430, 340, 70, "approval sheet", ["one question, 120 s, saved locally"], fill=BLUE_FILL, line=BLUE_LINE)
arrow(d, [(1270, 1500), (1270, 1580), (980, 1580)])
edge(d, ["a yes"], 1120, 1573, "mb")
arrow(d, [(1345, 1500), (1345, 1810), (980, 1810)])
edge(d, ["a no, or 120 s", "nothing is tapped"], 1357, 1660)

arrow(d, [(760, 1505), (760, 1570)])
edge(d, ["a safe action"], 772, 1538)

box(d, 540, 1570, 440, 72, "execute on that source", ["launch, tap, type, swipe, a typed call"], fill=GRN_FILL, line=GRN_LINE)
box(d, 540, 1690, 440, 48, "read screen tree", ["then compare hashes with the last read"])
arrow(d, [(760, 1642), (760, 1690)])
edge(d, ["the device's answer"], 772, 1666)
box(d, 540, 1786, 440, 48, "the step is recorded", ["locally, hash-chained"])
arrow(d, [(760, 1738), (760, 1786)])
edge(d, ["the new screen + hashes"], 772, 1762)

arrow(d, [(540, 1810), (500, 1810), (500, 1070), (540, 1070)])
edge(d, ["the record,", "the new screen"], 512, 1400)

# ── the action map ───────────────────────────────────────────────────────────
AX, AY, AW = 60, 1196, 390


def action_row(y, name, tag, colour, sub):
    d.text((s(AX + 16), s(y)), name, font=font(SEMI, 9.5), fill=INK, anchor="la")
    tw = d.textlength(tag, font=font(REG, 8.5)) / SCALE
    d.text((s(AX + AW - 16 - tw), s(y + 1)), tag, font=font(REG, 8.5), fill=colour, anchor="la")
    d.text((s(AX + 16), s(y + 13)), sub, font=font(REG, 8.5), fill=HINT, anchor="la")


d.rounded_rectangle([s(AX), s(AY), s(AX + AW), s(AY + 352)], radius=s(12), fill="#ffffff", outline=BORDER, width=max(1, s(1.2)))
d.text((s(AX + 16), s(AY + 14)), "what an action can be", font=font(SEMI, 11.5), fill=INK, anchor="la")
action_row(AY + 40, "ui.*", "runs", "#3f7a52", "observe, tap, type, swipe, scroll, back, home, launch, wait")
action_row(AY + 74, "ui.stop", "asks", "#2b6cb0", "closing an app")
action_row(AY + 108, "app.function", "asks", "#2b6cb0", "one typed call; a trusted app skips the question")
action_row(AY + 142, "system.openLink, setAlarm", "runs", "#3f7a52", "a link, an alarm")
action_row(AY + 176, "system.share, sendMessage, call, uninstall", "asks", "#2b6cb0", "outward actions")
action_row(AY + 210, "payment.*", "always asks", "#2b6cb0", "pay, confirm, checkout, enterCard, authorize")
action_row(AY + 244, "web.*", "not on Android", "#8a9096", "navigate, read, click, type")
action_row(AY + 278, "sandbox.run", "not on Android", "#8a9096", "a snippet in a sandbox")
action_row(AY + 312, "system.addEvent, delete, reset", "no handler", "#b4571f", "declared, but the executor refuses them")

# ── after the loop ───────────────────────────────────────────────────────────
arrow(d, [(600, 1875), (600, 1900), (300, 1900), (300, 1950)], dashed=True)
edge(d, ["14 steps reached"], 606, 1886)
box(d, 60, 1950, 390, 80, "one more model call", ["it reads every message so far"], fill=YEL_FILL, line=YEL_LINE)
chip(d, 378, 1957)
arrow(d, [(450, 1990), (540, 1990)])
edge(d, ["the summary text"], 495, 1977, "mb")

arrow(d, [(760, 1875), (760, 1945)])
edge(d, ["the answer + records"], 772, 1910)
box(d, 540, 1945, 440, 70, "answer, with a receipt", ["what ran, how long, how many tokens"], fill=LAV_FILL, line=LAV_LINE)

diamond(d, 760, 2085, 400, 80, ["every recorded step executed?"])
arrow(d, [(760, 2015), (760, 2045)])
edge(d, ["some steps ran"], 772, 2030)

arrow(d, [(960, 2085), (1100, 2085)])
edge(d, ["not every step ran"], 1030, 2069, "mb")
box(d, 1100, 2050, 340, 70, "nothing is saved", ["the next run asks the model"], fill=GREY_FILL, line=GREY_LINE, dashed=True)

arrow(d, [(760, 2125), (760, 2190)])
edge(d, ["every step ran"], 772, 2158)
box(d, 540, 2190, 440, 62, "the recipe is saved", ["under exactly these words"], fill=GRN_FILL, line=GRN_LINE)

note(d, "Up to the loop, everything is local code: reads, name matching, hashes, string comparison.", 40, 2266)
note(d, "The first model call is the first loop step, and that same call is the understanding.", 40, 2284)
note(d, "The check reads HOPA's own chat window and keeps only the source; the loop then reads for real, and again after every action.", 40, 2298)
note(d, "Reading it: a solid arrow is the next step, a dashed arrow is a side path or an early end, a dot stops the turn.", 40, 2330)

# ── footer ───────────────────────────────────────────────────────────────────
note(d, "Not in the code yet, so no arrow leads here:", 40, 2358, size=9.5)
chips = [("an incoming notification", 40), ("a scheduled goal", 330), ("a family approval", 560), ("a LAIX decision", 790)]
for text, x in chips:
    d.rounded_rectangle([s(x), s(2376), s(x + 250), s(2410)], radius=s(9), fill=GREY_FILL)
    dashed_path(d, rounded_path(s(x), s(2376), s(250), s(34), s(9)), GREY_LINE, width=1.2, dash=5, gap=4)
    d.text((s(x + 125), s(2393)), text, font=font(REG, 9), fill=MUTED, anchor="mm")

legend = [("input and answer", LAV_FILL, LAV_LINE), ("understanding and choice", YEL_FILL, YEL_LINE),
          ("approval", BLUE_FILL, BLUE_LINE), ("execution and knowledge", GRN_FILL, GRN_LINE)]
lx = 1060
for text, fill, line in legend:
    d.rounded_rectangle([s(lx), s(2384), s(lx + 12), s(2396)], radius=s(3), fill=fill, outline=line, width=max(1, s(0.8)))
    d.text((s(lx + 18), s(2390)), text, font=F_LEGEND, fill=MUTED, anchor="lm")
    lx += 18 + int(d.textlength(text, font=F_LEGEND) / SCALE) + 14

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-turn-flow.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
