"""The current code map: one turn from the sentence to the tapped app, band by band.

Run from the repository root: py -3 -P tools/scripts/make-current-code-map.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1500, 1902

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


F_TITLE = font(BOLD, 30)
F_SUB = font(REG, 13.5)
F_SECTION = font(SEMI, 11.5)
F_BOX = font(SEMI, 12.5)
F_BOX_MONO = font(MONO, 12)
F_SUB_TXT = font(REG, 9.5)
F_LEGEND = font(REG, 10)
F_TINY = font(REG, 9)


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


def band(draw, x, y, w, h, label, extra=""):
    dashed_path(draw, rounded_path(s(x), s(y), s(w), s(h), s(20)), BORDER, width=1.3, dash=10, gap=7)
    draw.text((s(x + 22), s(y + 13)), label, font=F_SECTION, fill=HINT, anchor="la")
    if extra:
        lw = draw.textlength(label, font=F_SECTION) / SCALE
        draw.text((s(x + 22 + lw + 20), s(y + 14)), extra, font=F_TINY, fill=HINT, anchor="la")


def card(draw, x, y, w, h, title, subs=(), fill=WHITE, line=GREY_LINE, dashed=False,
         mono_title=False, title_color=INK, sub_color=MUTED, title_size=12.5, left=False):
    x, y, w, h = s(x), s(y), s(w), s(h)
    r = s(11)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=fill)
    if dashed:
        dashed_path(draw, rounded_path(x, y, w, h, r), line, width=1.3, dash=5, gap=4)
    else:
        draw.rounded_rectangle([x, y, x + w, y + h], radius=r, outline=line, width=max(1, s(1.1)))
    tf = F_BOX_MONO if mono_title else font(SEMI, title_size)
    if not subs and not left:
        draw.text((x + w / 2, y + h / 2), title, font=tf, fill=title_color, anchor="mm")
        return
    draw.text((x + s(13), y + s(19)), title, font=tf, fill=title_color, anchor="la")
    ty = y + s(38)
    for line in subs:
        draw.text((x + s(13), ty), line, font=F_SUB_TXT, fill=sub_color, anchor="la")
        ty += s(15)


def arrowhead(draw, tip, angle, color, size=7):
    size = s(size)
    left = (tip[0] - size * math.cos(angle - 0.5), tip[1] - size * math.sin(angle - 0.5))
    right = (tip[0] - size * math.cos(angle + 0.5), tip[1] - size * math.sin(angle + 0.5))
    draw.polygon([tip, left, right], fill=color)


def arrow(draw, pts, color=ARROW, width=1.5, dashed=False, head=7):
    p = [(s(x), s(y)) for x, y in pts]
    if dashed:
        dashed_path(draw, p, color, width=width, dash=6, gap=5)
    else:
        draw.line(p, fill=color, width=max(1, s(width)), joint="curve")
    if len(p) >= 2:
        a = math.atan2(p[-1][1] - p[-2][1], p[-1][0] - p[-2][0])
        arrowhead(draw, p[-1], a, color, head)


def note(draw, text, x, y, anchor="la", size=9.5, color=HINT):
    draw.text((s(x), s(y)), text, font=font(REG, size), fill=color, anchor=anchor)


img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(26)), "HOPA as it is now - from one sentence to a tapped app", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(72)),
    "Android app, current code (main b1de46e). Real calls and real limits; grey dashes mean designed, not written.",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)

# ── surfaces ─────────────────────────────────────────────────────────────────
note(d, "WHERE A TAP CAN LAND", 40, 108)
card(d, 40, 126, 440, 68, "Your screen",
     ["the phone you are holding", "seen and tapped through accessibility"])
card(d, 500, 126, 440, 68, "Hidden screen",
     ["a helper process and its own display", "nothing of it appears on your screen"])
card(d, 960, 126, 500, 68, "My computer",
     ["the PC bridge, driving this phone over adb", "only used when you pick it"])

# ── 1 input ──────────────────────────────────────────────────────────────────
band(d, 30, 214, 1440, 158, "1  INPUT")
card(d, 40, 252, 200, 76, "Chat text", ["the main way in"])
card(d, 256, 252, 200, 76, "Voice", ["dictated, then sent as text", "needs the PC bridge"])
card(d, 472, 252, 200, 76, "Shared file", ["lands in the composer,", "not sent by itself"])
card(d, 688, 252, 210, 76, "Deep link / quick ask", ["prefilled, never sent"])
card(d, 914, 252, 250, 76, "Second goal while busy", ["needs the hidden screen;", "two can run at once"])
card(d, 1180, 252, 280, 76, "Notification, schedule", ["no incoming listener,", "no scheduler yet"], fill=GREY_FILL, line=GREY_LINE, dashed=True)
note(d, "Notifications exist only outward: HOPA tells you when it is done or waiting for you.", 40, 348)
arrow(d, [(750, 372), (750, 394)])

# ── 2 understand ─────────────────────────────────────────────────────────────
band(d, 30, 394, 1440, 258, "2  UNDERSTAND", "one system prompt, then one screen per step")
card(d, 40, 432, 540, 204, "Collected before each step", [
    "SCREEN + ELEMENTS - refs e1..., passwords dropped, cards and codes redacted",
    "Installed apps - what this phone can open right now",
    "Catalog - 54 apps: name, launch target, purpose, rules",
    "Router hint - the goal text scored against names and aliases",
    "Memory facts, plus tone and length",
    "Saved skills - text hints from earlier runs",
    "Typed app calls - when the foreground app publishes them",
    "Screenshot - only when Vision is switched on",
], fill=YEL_FILL, line=YEL_LINE, title_size=13)
card(d, 610, 432, 430, 54, "SystemPromptBuilder", [], fill=WHITE, line=GREY_LINE, mono_title=True, left=True)
card(d, 610, 498, 430, 78, "One model call per step", [
    "OpenAI-compatible, key in the phone Keystore",
    "cloud, or a LAN endpoint; no on-device model",
])
card(d, 1070, 432, 390, 150, "How a turn is built", [
    "rules once, then history, the goal, SCREEN",
    "after every action the new SCREEN is added",
    "14 steps and 120 s are the hard caps",
    "no recipe match? this loop is the only path",
], fill=WHITE, line=GREY_LINE)
arrow(d, [(580, 459), (610, 459)])
arrow(d, [(825, 486), (825, 498)])
note(d, "An app the model cannot place becomes a question to the person, not a guess.", 1070, 598)
note(d, "The catalog is words, not steps: name, target, purpose, rules. Nothing there replays.", 40, 646)

# ── 3 decide ─────────────────────────────────────────────────────────────────
band(d, 30, 676, 1440, 204, "3  DECIDE", "the model chooses; the code only checks the shape")
card(d, 520, 706, 460, 68, "DecisionParser", [
    "wants one JSON action; also reads fenced JSON",
    "and tool markup such as DSML",
], fill=WHITE, line=GREY_LINE, mono_title=True, left=True)
arrow(d, [(825, 576), (825, 706)])
card(d, 40, 792, 330, 76, "One action", ["ui.tap, ui.type, ui.launch, app.function,", "system.sendMessage, payment.pay ..."])
card(d, 390, 792, 330, 76, "Plain text", ["the answer; the turn ends here"])
card(d, 740, 792, 330, 76, "needUser + options", ["a question with buttons to tap"])
card(d, 1090, 792, 370, 76, "LAIX verb: ask / observe / act", ["not wired: the layer is a spike only"], fill=GREY_FILL, line=GREY_LINE, dashed=True)
arrow(d, [(750, 774), (750, 780)])
d.line([s(205), s(780), s(1260), s(780)], fill=ARROW, width=max(1, s(1.5)))
for x in (205, 555, 905):
    arrow(d, [(x, 780), (x, 792)])
arrow(d, [(1260, 780), (1260, 792)], dashed=True)
arrow(d, [(750, 880), (750, 904)])

# ── 4 approve ────────────────────────────────────────────────────────────────
band(d, 30, 904, 1440, 190, "4  APPROVE", "every action passes the gate, before any tap")
card(d, 40, 936, 230, 66, "ActionExecutor gate", ["classify(action, screen)"], fill=WHITE, line=GREY_LINE)
card(d, 300, 936, 340, 66, "READ: runs", ["tap, type, launch, back, home, scroll, wait"], fill=GRN_FILL, line=GRN_LINE)
card(d, 660, 936, 380, 66, "SENSITIVE: asks", ["send, call, delete, share, close, typed call"], fill=BLUE_FILL, line=BLUE_LINE)
card(d, 1060, 936, 400, 66, "PAYMENT: always asks", ["amount and merchant shown, never remembered"], fill=BLUE_FILL, line=BLUE_LINE)
arrow(d, [(270, 969), (300, 969)])
arrow(d, [(1015, 1002), (1015, 1010)])
card(d, 600, 1010, 840, 72, "Approval sheet", [
    "one question at a time, saved in Room; 120 s of silence is a no",
    "plan first: the same sheet, up to six lines; a trusted app skips typed calls",
], fill=BLUE_FILL, line=BLUE_LINE)
note(d, "READ actions never open the sheet.", 56, 1040)
note(d, "A no is recorded as refused; nothing is tapped.", 56, 1062)
arrow(d, [(750, 1082), (750, 1112)])
note(d, "a yes continues", 766, 1096, size=8.5)

# ── 5 execute ────────────────────────────────────────────────────────────────
band(d, 30, 1112, 1440, 310, "5  EXECUTE", "one source per turn, chosen by what answers first")
card(d, 40, 1148, 1420, 58, "ScreenSourceDevice", [
    "AUTO tries the hidden screen, then your screen. The first source that answers keeps the whole turn;",
    "when none answers the turn ends at once with a plain reason instead of guessing.",
], fill=WHITE, line=GREY_LINE, mono_title=True, left=True)
card(d, 40, 1220, 460, 96, "Your screen", [
    "accessibility service, same phone",
    "tap a node, or a gesture at its bounds",
    "type: SET_TEXT, else clipboard paste",
    "launch: startActivity, then settle",
])
card(d, 520, 1220, 460, 96, "Hidden screen", [
    "helper runs as shell, owns a virtual display",
    "started by wireless pairing or the PC bridge",
    "input goes into that display, sound muted",
    "opened apps are force-stopped at the end",
])
card(d, 1000, 1220, 460, 96, "My computer", [
    "HTTP to the PC bridge, which drives adb",
    "observe, tap, type, swipe, launch, keys",
    "screenshots and the app list too",
    "it plans nothing; it only drives adb",
])
card(d, 40, 1332, 480, 56, "ui.launch: the app starts here", [], fill=GRN_FILL, line=GRN_LINE, mono_title=True)
card(d, 540, 1332, 440, 56, "the same app is never launched twice", [], fill=GREY_FILL, line=GREY_LINE)
card(d, 1000, 1332, 460, 56, "the same action three times stops the run", [], fill=GREY_FILL, line=GREY_LINE)
note(d, "Each action comes back to step 2 with the new screen, until the model says done or a guard stops it.", 40, 1394)
note(d, "RunService holds a notification with Stop while a run is under way; the whole turn is capped at two minutes.", 40, 1408)
arrow(d, [(750, 1422), (750, 1440)])

# ── 6 verify and record ──────────────────────────────────────────────────────
band(d, 30, 1440, 1440, 248, "6  VERIFY, RECORD, REUSE", "what makes the next attempt cheaper")
card(d, 40, 1464, 340, 86, "Verify", [
    "observe again after the action",
    "changed screen, returned text, or a read",
    "action counts as verified",
])
card(d, 410, 1464, 340, 86, "RunRecord chain", [
    "Room, hash-chained per step",
    "action, class, approved, executed,",
    "verified, error, before and after hash",
])
card(d, 780, 1464, 340, 86, "Receipt in chat", [
    "path, steps, tokens, time taken",
    "a fallback is named as a fallback",
])
card(d, 1150, 1464, 310, 86, "History screens", [
    "Runs, Usage, Activity",
    "read from Room and the turn log",
])
card(d, 40, 1570, 340, 86, "Recipe, saved by itself", [
    "only when every step executed",
    "same goal again: replay, no model, 0 tokens",
    "first failed step falls back to the model",
], fill=GRN_FILL, line=GRN_LINE)
card(d, 410, 1570, 340, 86, "Skill, saved by hand", [
    "the person keeps the step text",
    "a hint in the prompt,",
    "not a plan the phone can replay",
], fill=LAV_FILL, line=LAV_LINE)
card(d, 780, 1570, 340, 86, "What reuse buys", [
    "a repeated goal skips the model",
    "a changed screen falls back,",
    "so a recipe never taps blind",
], fill=YEL_FILL, line=YEL_LINE)
card(d, 1150, 1570, 310, 86, "Recipe matching is exact", [
    "the same words, lowercased",
    "no synonyms, no fuzzy match",
], fill=GREY_FILL, line=GREY_LINE, dashed=True)

# ── 7 not in the code ────────────────────────────────────────────────────────
band(d, 30, 1710, 1440, 146, "7  NOT IN THE CODE TODAY", "so the rest of the plan stays honest")
items = [
    (40, "Incoming notifications", "nothing listens for them"),
    (410, "Scheduled / later", "Activity says: not available"),
    (780, "Family approval", "one person, one phone, one yes"),
    (1150, "LAIX decisions", "docs and a Kotlin spike"),
    (40, "app.md use cases", "the catalog carries no steps"),
    (410, "Local VLM", "every model call is remote"),
    (780, "Autonomous switch", "the policy has it, nothing sets it"),
    (1150, "Backend and relay", "Android never calls them"),
]
for i, (x, title, sub) in enumerate(items):
    y = 1736 if i < 4 else 1792
    w = 340 if x < 1150 else 310
    card(d, x, y, w, 52, title, [], fill=GREY_FILL, line=GREY_LINE, dashed=True, title_size=11.5, left=True)
    note(d, sub, x + 13, y + 35, size=9)

# ── legend ───────────────────────────────────────────────────────────────────
legend_y = 1876
note(d, "Colour = owning layer", 40, legend_y, size=10)
items = [
    ("client and approval", BLUE_FILL, BLUE_LINE),
    ("input and receipt", LAV_FILL, LAV_LINE),
    ("understanding and choice", YEL_FILL, YEL_LINE),
    ("execution and knowledge", GRN_FILL, GRN_LINE),
    ("not in the code", GREY_FILL, GREY_LINE),
]
lx = 190
for text, fill, line in items:
    d.rounded_rectangle([s(lx), s(legend_y - 5), s(lx + 12), s(legend_y + 7)], radius=s(3), fill=fill, outline=line, width=max(1, s(0.8)))
    d.text((s(lx + 18), s(legend_y + 1)), text, font=F_LEGEND, fill=MUTED, anchor="lm")
    lx += 18 + int(d.textlength(text, font=F_LEGEND) / SCALE) + 22

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-current-code-map.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
