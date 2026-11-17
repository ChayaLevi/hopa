"""One screen in three views: the visible text, the tree, and the flat element list.

Run from the repository root: py -3 -P tools/scripts/make-tree-example.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

import math
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1660, 1180

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"
RED = "#b4571f"
MONO_INK = "#2f3438"

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"

TOKEN = re.compile(r"(\[[a-z]+\])")


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(p: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(p, s(size))


F_TITLE = font(BOLD, 28)
F_SUB = font(REG, 13)
F_UI = font(REG, 10.5)
F_UI_SEMI = font(SEMI, 11)
F_UI_TITLE = font(SEMI, 12)
F_UI_BIG = font(SEMI, 13)
F_CODE = font(MONO, 11)
F_CHIP = font(MONO, 9.5)
F_NOTE = font(REG, 9)


def rounded(draw, x, y, w, h, r, fill=None, outline=None, width=1.2, dash=None):
    if dash:
        rr = min(r, w / 2, h / 2)
        pts = []
        for cx, cy, a0, a1 in [
            (x + rr, y + rr, 180, 270),
            (x + w - rr, y + rr, 270, 360),
            (x + w - rr, y + h - rr, 0, 90),
            (x + rr, y + h - rr, 90, 180),
        ]:
            for i in range(11):
                a = math.radians(a0 + (a1 - a0) * i / 10)
                pts.append((s(cx + rr * math.cos(a)), s(cy + rr * math.sin(a))))
        pts.append(pts[0])
        on = True
        carry = 0.0
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            dx, dy = x2 - x1, y2 - y1
            ln = math.hypot(dx, dy)
            if ln == 0:
                continue
            ux, uy = dx / ln, dy / ln
            pos = 0.0
            while pos < ln:
                span = s(dash[0]) if on else s(dash[1])
                take = min(span - carry, ln - pos)
                if on:
                    draw.line([(x1 + ux * pos, y1 + uy * pos), (x1 + ux * (pos + take), y1 + uy * (pos + take))], fill=outline, width=max(1, s(width)))
                pos += take
                carry += take
                if carry >= span - 0.01:
                    carry = 0.0
                    on = not on
        return
    draw.rounded_rectangle([s(x), s(y), s(x + w), s(y + h)], radius=s(r), fill=fill, outline=outline, width=max(1, s(width)))


def code_line(draw, x, y, text, base=MONO_INK, size=11):
    f = font(MONO, size)
    cx = x
    for part in TOKEN.split(text):
        if not part:
            continue
        colour = RED if TOKEN.fullmatch(part) else base
        draw.text((s(cx), s(y)), part, font=f, fill=colour, anchor="lm")
        cx += draw.textlength(part, font=f) / SCALE
    return cx


img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(26)), "One screen, three views: you, the tree, the model", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(68)),
    "Left: the app as you see it. Right: the visible text, the same nodes as a tree, and the flat element list the model acts on.",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)


def section(num, y, title, note=""):
    d.ellipse([s(578), s(y - 6), s(596), s(y + 12)], fill="#e9e5da")
    d.text((s(587), s(y + 3)), str(num), font=font(SEMI, 8), fill=INK, anchor="mm")
    d.text((s(606), s(y - 3)), title, font=font(SEMI, 10), fill=INK, anchor="la")
    if note:
        tw = d.textlength(title, font=font(SEMI, 10)) / SCALE
        d.text((s(606 + tw + 8), s(y - 2)), note, font=font(REG, 9), fill=HINT, anchor="la")


def divider(y):
    d.line([s(572), s(y), s(1604), s(y)], fill="#e7e3d8", width=max(1, s(1)))

# â”€â”€ phone mock â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
rounded(d, 60, 138, 420, 880, 40, fill="#202124")
rounded(d, 74, 156, 392, 844, 30, fill="#ffffff")

d.text((s(100), s(180)), "9:41", font=F_UI, fill="#3c4043", anchor="la")
for i in range(4):
    d.rectangle([s(410 + i * 9), s(186 - i * 2), s(414 + i * 9), s(192)], fill="#3c4043")
rounded(d, 452, 182, 16, 9, 2, fill=None, outline="#3c4043", width=1)

d.line([s(104), s(222), s(112), s(230)], fill="#1d1d1f", width=s(1.6))
d.line([s(112), s(230), s(104), s(238)], fill="#1d1d1f", width=s(1.6))
d.text((s(130), s(218)), "Transfer money", font=F_UI_TITLE, fill=INK, anchor="la")

size = s(52)
av = Image.new("RGBA", (size, size), (0, 0, 0, 0))
ad = ImageDraw.Draw(av)
ad.ellipse([0, 0, size - 1, size - 1], fill=(219, 231, 243, 255))
ad.ellipse([size * 0.08, size * 0.60, size * 0.92, size * 1.30], fill=(111, 143, 181, 255))
ad.ellipse([size * 0.30, size * 0.16, size * 0.70, size * 0.56], fill=(232, 195, 158, 255))
ad.chord([size * 0.26, size * 0.10, size * 0.74, size * 0.52], 180, 360, fill=(74, 58, 47, 255))
mask = Image.new("L", (size, size), 0)
ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
img.paste(av, (s(100), s(266)), mask)
d.ellipse([s(96), s(262), s(156), s(322)], outline="#b7cbe8", width=max(1, s(2)))
d.text((s(126), s(332)), "e6", font=font(MONO, 8), fill="#2b6cb0", anchor="mm")
d.text((s(166), s(268)), "Anna Petrova", font=F_UI_SEMI, fill=INK, anchor="la")
d.text((s(166), s(292)), "Sberbank", font=F_UI, fill=HINT, anchor="la")

rows = [
    ("Card number", "4123 4567 8901 2345"),
    ("Amount", "12 500.00"),
    ("Phone", "+7 916 123-45-67"),
    ("Email", "anna.k@example.com"),
]
y = 352
for label, value in rows:
    d.text((s(100), s(y)), label, font=F_UI, fill=HINT, anchor="la")
    rounded(d, 98, y + 20, 348, 40, 8, fill="#f6f7f9", outline="#e6e8ec", width=1)
    d.text((s(112), s(y + 32)), value, font=F_UI, fill="#1d1d1f", anchor="la")
    y += 76

d.rounded_rectangle([s(96), s(370), s(448), s(414)], radius=s(10), outline="#e4d5a8", width=max(1, s(2)))
d.text((s(438), s(392)), "e10", font=font(MONO, 8), fill="#b4571f", anchor="rm")
d.text((s(100), s(y)), "Verification code", font=F_UI, fill=HINT, anchor="la")
rounded(d, 98, y + 20, 348, 40, 8, fill="#f6f7f9", outline="#8a9096", width=1, dash=(5, 4))
d.text((s(112), s(y + 32)), "482913", font=F_UI, fill="#1d1d1f", anchor="la")

d.text((s(438), s(y + 40)), "dropped", font=font(MONO, 8), fill="#8a9096", anchor="rm")
rounded(d, 98, 878, 348, 52, 26, fill="#2b6cb0")
d.text((s(272), s(904)), "Confirm payment", font=F_UI_SEMI, fill="#ffffff", anchor="mm")
rounded(d, 228, 972, 84, 5, 3, fill="#c8c9cc")

# â”€â”€ arrow â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
d.line([s(492), s(578), s(536), s(578)], fill="#55595e", width=max(1, s(1.6)))
d.polygon([(s(544), s(578)), (s(534), s(572)), (s(534), s(584))], fill="#55595e")
d.text((s(516), s(558)), "read", font=font(REG, 9), fill=HINT, anchor="mm")
d.text((s(516), s(598)), "screen", font=font(REG, 9), fill=HINT, anchor="mm")

# â”€â”€ payload â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
rounded(d, 556, 138, 1064, 914, 14, fill="#fcfbf7", outline=BORDER, width=1.2)

section(1, 162, "SCREEN TREE", "every visible text, already redacted")
screen_lines = [
    "Transfer money",
    "Photo of Anna Petrova",
    "Anna Petrova",
    "Sberbank",
    "Card number",
    "[card]",
    "Amount",
    "12 500.00",
    "Phone",
    "[phone]",
    "Email",
    "[email]",
    "Verification code",
    "[otp]",
    "Confirm payment",
]
y = 186
for line in screen_lines:
    code_line(d, 580, y, line)
    y += 17

divider(432)
section(2, 448, "the tree on the phone", "same nodes, nested - the model never sees this shape")
tree_lines = [
    (0, "e1 FrameLayout"),
    (1, "e2 LinearLayout"),
    (2, "e3 ViewGroup"),
    (3, "e4 ImageButton Back"),
    (3, "e5 TextView Transfer money"),
    (2, "e6 ImageView Photo of Anna Petrova"),
    (2, "e9 TextView Card number"),
    (3, "e10 TextView [card]"),
]
y = 470
for depth, line_text in tree_lines:
    if line_text.startswith("e10"):
        d.rounded_rectangle([s(574), s(y - 9), s(900), s(y + 9)], radius=s(4), fill="#fbf3dc")
    elif line_text.startswith("e6 "):
        d.rounded_rectangle([s(574), s(y - 9), s(900), s(y + 9)], radius=s(4), fill="#e7f0fb")
    code_line(d, 580 + depth * 16, y, line_text)
    y += 17

divider(604)
section(3, 620, "ELEMENTS", "the same nodes, flat: one line per element, with its ref and rectangle")
elements = [
    ("e1 FrameLayout", "[0,0,1080,2340]"),
    ("e2 LinearLayout", "[0,0,1080,2340]"),
    ("e3 ViewGroup", "[0,0,1080,220]"),
    ("e4 ImageButton Back", "[24,60,120,156]"),
    ("e5 TextView Transfer money", "[150,84,760,132]"),
    ("e6 ImageView Photo of Anna Petrova", "[60,250,190,380]"),
    ("e7 TextView Anna Petrova", "[210,262,760,310]"),
    ("e8 TextView Sberbank", "[210,320,760,364]"),
    ("e9 TextView Card number", "[60,430,500,476]"),
    ("e10 TextView [card]", "[60,490,940,546]"),
    ("e11 TextView Amount", "[60,610,500,656]"),
    ("e12 TextView 12 500.00", "[60,670,940,726]"),
    ("e13 TextView Phone", "[60,790,500,836]"),
    ("e14 TextView [phone]", "[60,850,940,906]"),
    ("e15 TextView Email", "[60,970,500,1016]"),
    ("e16 TextView [email]", "[60,1030,940,1086]"),
    ("e19 Button Confirm payment", "[60,1980,1020,2090]"),
]
y = 642
for left, right in elements:
    if left.startswith("e10"):
        d.rounded_rectangle([s(574), s(y - 9), s(1600), s(y + 9)], radius=s(4), fill="#fbf3dc")
    elif left.startswith("e6 "):
        d.rounded_rectangle([s(574), s(y - 9), s(1600), s(y + 9)], radius=s(4), fill="#e7f0fb")
    code_line(d, 580, y, left)
    d.text((s(1290), s(y)), right, font=F_CODE, fill="#8a9096", anchor="lm")
    y += 17

rounded(d, 580, 924, 1016, 34, 8, outline="#d9d5cc", width=1.2, dash=(5, 4))
d.text((s(596), s(941)), "no e17 or e18: the code field was dropped, with its label and its children", font=font(REG, 9), fill=HINT, anchor="lm")
d.text((s(580), s(972)), "the label still shows in the text list, but there is no element left to tap", font=font(REG, 9), fill=HINT, anchor="la")
d.text((s(580), s(990)), "the model gets the flat list; the nesting stays on the phone", font=font(REG, 9), fill=HINT, anchor="la")
d.text((s(580), s(1004)), "a picture is one node, and the pixels never travel:", font=font(REG, 9), fill=HINT, anchor="la")
d.text((s(592), s(1020)), "with a description:  ImageView Photo of Anna Petrova [60,250,190,380]", font=font(MONO, 8), fill="#8a9096", anchor="la")
d.text((s(592), s(1034)), "without one:         ImageView [60,250,190,380]", font=font(MONO, 8), fill="#8a9096", anchor="la")

# â”€â”€ chips â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
chips = [
    ("4123 4567 8901 2345", "[card]", "13-19 digits"),
    ("+7 916 123-45-67", "[phone]", "a phone number"),
    ("anna.k@example.com", "[email]", "an address"),
    ("482913", "[otp]", "a code on screen"),
]
x = 60
for raw, token, why in chips:
    w = 300
    rounded(d, x, 1074, w, 56, 10, fill="#ffffff", outline=BORDER, width=1.1)
    d.text((s(x + 14), s(1090)), raw, font=F_CHIP, fill="#1d1d1f", anchor="la")
    d.text((s(x + 14), s(1110)), "\u2192", font=font(MONO, 9.5), fill=HINT, anchor="la")
    d.text((s(x + 32), s(1110)), token, font=F_CHIP, fill=RED, anchor="la")
    d.text((s(x + 98), s(1110)), why, font=F_NOTE, fill=HINT, anchor="la")
    x += w + 16

rounded(d, x, 1074, 1584 - x, 56, 10, fill="#ffffff", outline=BORDER, width=1.1)
d.text((s(x + 14), s(1090)), "the code field", font=font(SEMI, 9.5), fill="#1d1d1f", anchor="la")
d.text((s(x + 14), s(1110)), "dropped, with its label", font=F_NOTE, fill=HINT, anchor="la")

d.text((s(40), s(1146)), "The tree is the structure on the phone. The text list is for reading. The elements are for acting: one line, one ref, one rectangle.", font=font(REG, 9.5), fill=MUTED, anchor="la")
d.text((s(40), s(1164)), "Everything here is text - no image, no colour, no fonts.", font=font(REG, 9.5), fill=HINT, anchor="la")

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-tree-example.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
