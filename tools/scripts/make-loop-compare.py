from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1640, 1128

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"
ARROW = "#55595e"

BLUE_FILL = "#e7f0fb"
BLUE_LINE = "#b7cbe8"
YEL_FILL = "#fbf3dc"
YEL_LINE = "#e4d5a8"
GRN_FILL = "#e5f3ea"
GRN_LINE = "#b9d9c4"
LAV_FILL = "#ece9fb"
LAV_LINE = "#c8c0e8"
WHITE = "#ffffff"
GREY_FILL = "#f2f1ed"
GREY_LINE = "#cfcdc6"

GREEN = "#3f7a52"
AMBER = "#b4571f"
BLUE = "#2b6cb0"

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(p: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(p, s(size))


F_TITLE = font(BOLD, 29)
F_SUB = font(REG, 13.5)
F_COL = font(SEMI, 13)
F_COL_SUB = font(REG, 9.5)
F_BOX = font(SEMI, 11)
F_BOX_MONO = font(MONO, 10.5)
F_SUB_TXT = font(REG, 9)
F_TAG = font(SEMI, 8.5)
F_ROW = font(MONO, 9)
F_NOTE = font(REG, 9.5)
F_CHIP = font(SEMI, 8)


def rounded(draw, x, y, w, h, r, fill=None, outline=None, width=1.2):
    draw.rounded_rectangle([s(x), s(y), s(x + w), s(y + h)], radius=s(r), fill=fill, outline=outline, width=max(1, s(width)))


def box(draw, x, y, w, h, title, sub="", fill=WHITE, line=GREY_LINE, mono=False):
    rounded(draw, x, y, w, h, 10, fill=fill, outline=line)
    if sub:
        draw.text((s(x + 14), s(y + 14)), title, font=(F_BOX_MONO if mono else F_BOX), fill=INK, anchor="la")
        draw.text((s(x + 14), s(y + 34)), sub, font=F_SUB_TXT, fill=MUTED, anchor="la")
    else:
        draw.text((s(x + w / 2), s(y + h / 2)), title, font=(F_BOX_MONO if mono else F_BOX), fill=INK, anchor="mm")


def diamond(draw, cx, cy, w, h, text, fill=YEL_FILL, line=YEL_LINE):
    pts = [(s(cx), s(cy - h / 2)), (s(cx + w / 2), s(cy)), (s(cx), s(cy + h / 2)), (s(cx - w / 2), s(cy))]
    draw.polygon(pts, fill=fill)
    draw.line(pts + [pts[0]], fill=line, width=max(1, s(1.1)), joint="curve")
    draw.text((s(cx), s(cy)), text, font=F_BOX, fill=INK, anchor="mm")


def arrow(draw, x1, y1, x2, y2, dashed=False):
    if dashed:
        step = 6
        n = max(1, int(math.hypot(x2 - x1, y2 - y1) // step))
        for i in range(0, n, 2):
            a = i / n
            b = min(1, (i + 1) / n)
            draw.line([s(x1 + (x2 - x1) * a), s(y1 + (y2 - y1) * a), s(x1 + (x2 - x1) * b), s(y1 + (y2 - y1) * b)], fill=ARROW, width=max(1, s(1.4)))
    else:
        draw.line([s(x1), s(y1), s(x2), s(y2)], fill=ARROW, width=max(1, s(1.5)))
    ang = math.atan2(y2 - y1, x2 - x1)
    size = s(6)
    left = (s(x2) - size * math.cos(ang - 0.5), s(y2) - size * math.sin(ang - 0.5))
    right = (s(x2) - size * math.cos(ang + 0.5), s(y2) - size * math.sin(ang + 0.5))
    draw.polygon([(s(x2), s(y2)), left, right], fill=ARROW)


def chip(draw, x, y, text, fill, colour):
    f = F_CHIP
    w = draw.textlength(text, font=f) / SCALE + 14
    rounded(draw, x, y, w, 16, 8, fill=fill)
    draw.text((s(x + w / 2), s(y + 8)), text, font=f, fill=colour, anchor="mm")
    return w


img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(24)), "The agentic loop today, and the same loop with no model", font=F_TITLE, fill=INK, anchor="la")
d.text((s(40), s(66)), "Nine rows, lined up. Four are unchanged: the input, the gate, the executor, the records. Five change.", font=F_SUB, fill=MUTED, anchor="la")

# column headers
d.text((s(60), s(104)), "TODAY: the model decides every step", font=F_COL, fill=INK, anchor="la")
d.text((s(60), s(124)), "one remote call per action; a new screen is handled by improvising", font=F_COL_SUB, fill=HINT, anchor="la")
d.text((s(920), s(104)), "WITHOUT A MODEL: the playbook decides", font=F_COL, fill=INK, anchor="la")
d.text((s(920), s(124)), "no tokens; a new screen is a stop with a question, not a guess", font=F_COL_SUB, fill=HINT, anchor="la")
d.text((s(740), s(104)), "WHAT CHANGES", font=F_COL, fill=HINT, anchor="la")

LEFT_X, RIGHT_X, COL_W = 60, 920, 620
MID_X = 800
TOP = 152
PITCH = 96

ROWS = [
    ("goal", "A person's goal", "", WHITE, GREY_LINE,
     "A person's goal", "", WHITE, GREY_LINE, "same"),
    ("plan", "Collect context, read the screen tree", "", BLUE_FILL, BLUE_LINE,
     "Parse the goal locally: app, intent, parameters", "", GRN_FILL, GRN_LINE, "differs"),
    ("decide", "MODEL CALL", "one per step, tokens, remote", YEL_FILL, YEL_LINE,
     "Playbook lookup: (app, intent) -> next step", "anchors, no reasoning", GRN_FILL, GRN_LINE, "differs"),
    ("fallback", "Model improvises on an unfamiliar screen", "", WHITE, GREY_LINE,
     "No playbook -> screen memory -> else ASK", "", GREY_FILL, GREY_LINE, "differs"),
    ("gate", "Gate: how risky is it? approval", "", BLUE_FILL, BLUE_LINE,
     "Gate: how risky is it? approval", "", BLUE_FILL, BLUE_LINE, "same"),
    ("exec", "Execute on the device: launch, tap, type", "", GRN_FILL, GRN_LINE,
     "Execute on the device: launch, tap, type", "", GRN_FILL, GRN_LINE, "same"),
    ("verify", "Verify: did the screen change? (hash)", "", WHITE, GREY_LINE,
     "Verify: the step's own expectation", "e.g. the display shows 84", WHITE, GREY_LINE, "differs"),
    ("record", "Record: RunRecord, hash-chained", "", LAV_FILL, LAV_LINE,
     "Record: RunRecord, hash-chained", "", LAV_FILL, LAV_LINE, "same"),
    ("loop", "Loop, up to 14 steps; then answer + receipt", "", WHITE, GREY_LINE,
     "Next step, or ask; then answer + receipt", "", WHITE, GREY_LINE, "differs"),
]

y = TOP
CENTRES = []
for i, row in enumerate(ROWS):
    tag = row[9]
    left_title, left_sub = row[1], row[2]
    left_fill, left_line = row[3], row[4]
    right_title, right_sub = row[5], row[6]
    right_fill, right_line = row[7], row[8]

    h = 62 if (left_sub or right_sub) else 54
    box(d, LEFT_X, y, COL_W, h, left_title, left_sub, left_fill, left_line)
    box(d, RIGHT_X, y, COL_W, h, right_title, right_sub, right_fill, right_line)

    colour = GREEN if tag == "same" else AMBER
    fill = "#e5f3ea" if tag == "same" else "#fbf3dc"
    tw = d.textlength(tag.upper(), font=F_TAG) / SCALE + 18
    rounded(d, MID_X - tw / 2, y + h / 2 - 10, tw, 20, 10, fill=fill)
    d.text((s(MID_X), s(y + h / 2 + 1)), tag.upper(), font=F_TAG, fill=colour, anchor="mm")

    d.text((s(40), s(y + h / 2 + 1)), str(i + 1), font=F_ROW, fill=HINT, anchor="rm")

    CENTRES.append(y + h / 2)

    if i < len(ROWS) - 1:
        arrow(d, LEFT_X + COL_W / 2, y + h, LEFT_X + COL_W / 2, y + PITCH)
        arrow(d, RIGHT_X + COL_W / 2, y + h, RIGHT_X + COL_W / 2, y + PITCH)

    y += PITCH

# chips on the two decisive nodes
chip(d, LEFT_X + COL_W - 84, TOP + PITCH * 2 + 6, "MODEL", "#fdecc8", "#7a5a1c")
chip(d, RIGHT_X + COL_W - 96, TOP + PITCH * 2 + 6, "PLAYBOOK", "#dff0e5", GREEN)

# footer: the trade, in three lines
fy = TOP + PITCH * len(ROWS) + 6
rounded(d, 40, fy, 480, 62, 10, fill="#fdf3e6", outline="#e8d3b6")
d.text((s(56), s(fy + 10)), "With the model", font=F_CHIP and font(SEMI, 9.5), fill=AMBER, anchor="la")
d.text((s(56), s(fy + 28)), "tokens on every step, a remote call, a new screen", font=F_NOTE, fill=MUTED, anchor="la")
d.text((s(56), s(fy + 44)), "covered by improvising; the same goal can run differently.", font=F_NOTE, fill=MUTED, anchor="la")

rounded(d, 540, fy, 500, 62, 10, fill="#eaf5ee", outline="#c4ddcd")
d.text((s(556), s(fy + 10)), "Without the model", font=font(SEMI, 9.5), fill=GREEN, anchor="la")
d.text((s(556), s(fy + 28)), "no tokens, works offline, the same goal runs the same way", font=F_NOTE, fill=MUTED, anchor="la")
d.text((s(556), s(fy + 44)), "every time; anything not written down stops and asks.", font=F_NOTE, fill=MUTED, anchor="la")

rounded(d, 1060, fy, 540, 62, 10, fill="#f7f5ef", outline=BORDER)
d.text((s(1076), s(fy + 10)), "Unchanged either way", font=font(SEMI, 9.5), fill=HINT, anchor="la")
d.text((s(1076), s(fy + 28)), "the gate, the executor, the records and the receipt are the same code;", font=F_NOTE, fill=MUTED, anchor="la")
d.text((s(1076), s(fy + 44)), "only the rows that understand, decide and verify have to be replaced.", font=F_NOTE, fill=MUTED, anchor="la")

def polyline(pts, colour="#9aa0a6", width=1.3):
    draw = [(s(x), s(y)) for x, y in pts]
    d.line(draw, fill=colour, width=max(1, s(width)), joint="curve")
    (x1, y1), (x2, y2) = draw[-2], draw[-1]
    ang = math.atan2(y2 - y1, x2 - x1)
    size = s(6)
    d.polygon([
        (x2, y2),
        (x2 - size * math.cos(ang - 0.5), y2 - size * math.sin(ang - 0.5)),
        (x2 - size * math.cos(ang + 0.5), y2 - size * math.sin(ang + 0.5)),
    ], fill=colour)

top = CENTRES[2]
bottom = CENTRES[-1]
polyline([(LEFT_X, bottom), (22, bottom), (22, top), (LEFT_X, top)])
polyline([(RIGHT_X + COL_W, bottom), (W - 22, bottom), (W - 22, top), (RIGHT_X + COL_W, top)])

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-loop-vs-no-model.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
