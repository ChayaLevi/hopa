from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1600, 724

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"

GREEN = "#3f7a52"
BLUE = "#2b6cb0"
AMBER = "#b4571f"
VIOLET = "#4a3f6b"

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(p: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(p, s(size))


F_TITLE = font(BOLD, 30)
F_SUB = font(REG, 13.5)
F_SECTION = font(SEMI, 11)
F_PLACE = font(SEMI, 13)
F_PLACE_SUB = font(REG, 9.5)
F_NUM = font(MONO, 10)
F_MODE = font(SEMI, 11)
F_CELL = font(REG, 10)
F_CHIP = font(SEMI, 8.5)
F_HEAD = font(SEMI, 8.5)
F_NOTE = font(REG, 9.5)


def rounded(draw, x, y, w, h, r, fill=None, outline=None, width=1.2):
    draw.rounded_rectangle([s(x), s(y), s(x + w), s(y + h)], radius=s(r), fill=fill, outline=outline, width=max(1, s(width)))


def dashed_rect(draw, x, y, w, h, r, colour, width=1.2, dash=6, gap=5):
    rr = min(r, w / 2, h / 2)
    pts = []
    for cx, cy, a0, a1 in [
        (x + rr, y + rr, 180, 270),
        (x + w - rr, y + rr, 270, 360),
        (x + w - rr, y + h - rr, 0, 90),
        (x + rr, y + h - rr, 90, 180),
    ]:
        for i in range(9):
            a = math.radians(a0 + (a1 - a0) * i / 8)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    pts.append(pts[0])
    on, carry = True, 0.0
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        dx, dy = x2 - x1, y2 - y1
        ln = math.hypot(dx, dy)
        if ln == 0:
            continue
        ux, uy = dx / ln, dy / ln
        pos = 0.0
        while pos < ln:
            span = dash if on else gap
            take = min(span - carry, ln - pos)
            if on:
                draw.line([(s(x1 + ux * pos), s(y1 + uy * pos)), (s(x1 + ux * (pos + take)), s(y1 + uy * (pos + take)))], fill=colour, width=max(1, s(width)))
            pos += take
            carry += take
            if carry >= span - 0.01:
                carry = 0.0
                on = not on


def chip(draw, x, y, text, fill, colour, w=0):
    f = F_CHIP
    tw = draw.textlength(text, font=f) / SCALE
    if w == 0:
        w = tw + 18
    rounded(draw, x, y, w, 19, 9.5, fill=fill)
    draw.text((s(x + w / 2), s(y + 9.5)), text, font=f, fill=colour, anchor="mm")
    return w


img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(24)), "Seven ways HOPA can act on the phone", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(68)),
    "One phone, one pair of hands. What changes is where the app opens, and who does the tapping.",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)

# ── the three places ─────────────────────────────────────────────────────────
d.text((s(40), s(106)), "THREE PLACES AN APP CAN OPEN", font=F_SECTION, fill=HINT, anchor="la")
places = [
    (40, "Hidden display", "inside the phone; you do not see it", "#e4e1f0", "#d6d0e8", VIOLET),
    (560, "Your screen", "the app opens over HOPA; you watch", "#e5f3ea", "#c9e2d2", GREEN),
    (1080, "PC drives your screen", "the bridge taps over adb; you watch", "#e7f0fb", "#c5d9ee", BLUE),
]
for x, title, sub, fill, line, colour in places:
    rounded(d, x, 126, 480, 62, 10, fill=fill, outline=line)
    d.text((s(x + 16), s(136)), title, font=F_PLACE, fill=colour, anchor="la")
    d.text((s(x + 16), s(158)), sub, font=F_PLACE_SUB, fill=MUTED, anchor="la")

# ── the table ───────────────────────────────────────────────────────────────
COLS = [
    (40, 30, "#"),
    (80, 400, "WAY"),
    (490, 170, "APP OPENS"),
    (675, 300, "WHO TAPS"),
    (990, 370, "WHAT YOU NEED"),
    (1375, 185, "IS THE PC NEEDED?"),
]
d.text((s(40), s(206)), "THE SEVEN PATHS, IN THE ACT PAGE", font=F_SECTION, fill=HINT, anchor="la")
for x, _w, name in COLS:
    d.text((s(x), s(230)), name, font=F_HEAD, fill=HINT, anchor="la")
d.line([s(40), s(244), s(1560), s(244)], fill=BORDER, width=max(1, s(1)))

ROWS = [
    ("1", "Hidden screen - wireless debugging", "hidden", "the helper on the phone", "wireless debugging + screen access", "no", GREEN),
    ("2", "Hidden screen - started by the PC bridge", "hidden", "the helper on the phone", "a paired bridge + adb + screen access", "only to start", AMBER),
    ("3", "Hidden screen - manual adb (developer)", "hidden", "the helper on the phone", "adb on a developer PC + screen access", "only to start", AMBER),
    ("4", "Your screen", "visible", "HOPA, through accessibility", "screen access only", "no", GREEN),
    ("5", "Bridge - pair code", "visible", "the PC, over adb", "a bridge + adb + the same network", "always", BLUE),
    ("6", "Bridge - token", "visible", "the PC, over adb", "a bridge + adb + a valid token", "always", BLUE),
    ("7", "Auto: hidden first, then your screen", "hidden → visible", "whatever is ready", "screen access + a hidden start", "never - auto skips it", HINT),
]

CHIP = {
    "hidden": ("#e4e1f0", VIOLET),
    "visible": ("#e5f3ea", GREEN),
    "hidden → visible": ("#efece4", "#6b5f3f"),
}

y = 254
for num, mode, place, taps, need, pc, pc_colour in ROWS:
    if ROWS.index((num, mode, place, taps, need, pc, pc_colour)) % 2 == 1:
        d.rectangle([s(34), s(y - 4), s(1566), s(y + 40)], fill="#f7f5ef")
    d.text((s(40), s(y + 12)), num, font=F_NUM, fill=HINT, anchor="la")
    d.text((s(80), s(y + 12)), mode, font=F_MODE, fill=INK, anchor="la")
    fillx, colourx = CHIP[place]
    chip(d, 490, y + 2, place, fillx, colourx, 160)
    d.text((s(675), s(y + 12)), taps, font=F_CELL, fill=INK, anchor="la")
    d.text((s(990), s(y + 12)), need, font=F_CELL, fill=MUTED, anchor="la")
    d.text((s(1375), s(y + 12)), pc, font=F_CELL, fill=pc_colour, anchor="la")
    d.line([s(40), s(y + 44), s(1560), s(y + 44)], fill="#eceae4", width=max(1, s(0.8)))
    y += 50

# ── the two footnotes ───────────────────────────────────────────────────────
fy = y + 8
dashed_rect(d, 40, fy, 740, 62, 10, BORDER, width=1.2)
d.text((s(56), s(fy + 12)), "Not separate modes", font=F_SECTION, fill=INK, anchor="la")
d.text((s(56), s(fy + 30)), "Helper already running is the state after 1-3. A pair code or a token", font=F_NOTE, fill=MUTED, anchor="la")
d.text((s(56), s(fy + 46)), "only authorises the bridge; it does not change where the app opens.", font=F_NOTE, fill=MUTED, anchor="la")

dashed_rect(d, 800, fy, 760, 62, 10, BORDER, width=1.2)
d.text((s(816), s(fy + 12)), "Which one to pick", font=F_SECTION, fill=INK, anchor="la")
d.text((s(816), s(fy + 30)), "1-3 for work in the background   -   4 when someone should watch", font=F_NOTE, fill=MUTED, anchor="la")
d.text((s(816), s(fy + 46)), "5-6 when the phone has no screen access   -   7 when it should just work", font=F_NOTE, fill=MUTED, anchor="la")

d.text((s(40), s(H - 26)), "Step-by-step setup for each path: docs/guides/android/run-modes.md", font=F_NOTE, fill=HINT, anchor="la")

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-run-modes.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
