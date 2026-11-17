"""The action kinds: what each does, an example payload, the gate, and whether it works here.

Run from the repository root: py -3 -P tools/scripts/make-actions-map.py
Needs Segoe UI and Consolas (Windows). Output: docs/media/*.png
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 2
W, H = 1620, 1442

BG = "#faf8f3"
INK = "#1d1d1f"
MUTED = "#5f6368"
HINT = "#777b80"
BORDER = "#d8d1c4"
MONO_INK = "#2f3438"

GREEN = "#3f7a52"
BLUE = "#2b6cb0"
GREY = "#8a9096"
RED = "#b4571f"

REG = "C:/Windows/Fonts/segoeui.ttf"
SEMI = "C:/Windows/Fonts/seguisb.ttf"
BOLD = "C:/Windows/Fonts/segoeuib.ttf"
MONO = "C:/Windows/Fonts/consola.ttf"


def s(v: float) -> int:
    return int(round(v * SCALE))


def font(p: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(p, s(size))


F_TITLE = font(BOLD, 27)
F_SUB = font(REG, 12.5)
F_COL = font(SEMI, 8.5)
F_GROUP = font(SEMI, 10)
F_KIND = font(MONO, 11)
F_DESC = font(REG, 9.5)
F_EX = font(MONO, 9)
F_BADGE = font(SEMI, 8)
F_SUP = font(REG, 9)
F_NOTE = font(REG, 9.5)

img = Image.new("RGB", (W * SCALE, H * SCALE), BG)
d = ImageDraw.Draw(img)

d.text((s(40), s(24)), "What the model can ask for: every action kind", font=F_TITLE, fill=INK, anchor="la")
d.text(
    (s(40), s(62)),
    "Wire name, what it really does, an example payload, the gate, and whether it works on this phone right now.",
    font=F_SUB,
    fill=MUTED,
    anchor="la",
)

COLS = [(60, "KIND"), (300, "WHAT IT REALLY DOES"), (760, "AN EXAMPLE THE MODEL SENDS"), (1200, "GATE"), (1330, "ON THIS PHONE")]
for x, name in COLS:
    d.text((s(x), s(96)), name, font=F_COL, fill=HINT, anchor="la")
d.line([s(40), s(110), s(1580), s(110)], fill=BORDER, width=max(1, s(1)))


def badge(x, y, text, colour, w=106):
    d.rounded_rectangle([s(x), s(y), s(x + w), s(y + 20)], radius=s(10), fill="#ffffff", outline=colour, width=max(1, s(1)))
    d.text((s(x + w / 2), s(y + 10)), text, font=F_BADGE, fill=colour, anchor="mm")


def group(y, name, note):
    d.rounded_rectangle([s(40), s(y), s(1580), s(y + 24)], radius=s(6), fill="#f2f1ed")
    d.text((s(60), s(y + 12)), name, font=F_GROUP, fill=INK, anchor="lm")
    d.text((s(300), s(y + 12)), note, font=F_NOTE, fill=HINT, anchor="lm")
    return y + 26


def row(y, kind, desc, example, gate, gate_colour, support, support_colour):
    d.text((s(60), s(y + 9)), kind, font=F_KIND, fill=MONO_INK, anchor="la")
    d.text((s(300), s(y + 9)), desc, font=F_DESC, fill=INK, anchor="la")
    if example:
        d.text((s(760), s(y + 10)), example, font=F_EX, fill="#8a9096", anchor="la")
    badge(1200, y + 3, gate, gate_colour)
    d.text((s(1330), s(y + 9)), support, font=F_SUP, fill=support_colour, anchor="la")
    d.line([s(60), s(y + 34), s(1580), s(y + 34)], fill="#eceae4", width=max(1, s(0.8)))
    return y + 34


y = 120
y = group(y, "UI — the screen itself", "one gesture or a key press, always by element ref, never by invented coordinates")
y = row(y, "ui.observe", "read the screen tree again, no gesture", "", "runs", GREEN, "yes", GREEN)
y = row(y, "ui.tap", "tap one element", '{"kind":"ui.tap","ref":"e12"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.doubleTap", "two taps in quick succession", '{"ref":"e12"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.longPress", "press and hold an element", '{"ref":"e12"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.type", "put text into the focused field", '{"ref":"e15","text":"котики"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.swipe", "drag from a point to a point", '{"x":540,"y":1800,"toX":540,"toY":600}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.scroll", "scroll one screen, direction only", '{"direction":"down"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.back", "the system back key", "", "runs", GREEN, "yes", GREEN)
y = row(y, "ui.home", "the system home key", "", "runs", GREEN, "yes", GREEN)
y = row(y, "ui.launch", "start an app by package", '{"app":"tv.danmaku.bili"}', "runs", GREEN, "yes", GREEN)
y = row(y, "ui.stop", "close an app", '{"app":"com.sina.weibo"}', "asks", BLUE, "hidden screen or bridge", BLUE)
y = row(y, "ui.wait", "sleep, then read again", '{"timeoutMs":2000}', "runs", GREEN, "yes", GREEN)

y = group(y, "APP — one typed call", "skip the taps when the app publishes a function for this")
y = row(y, "app.function", "call a function the app publishes; a trusted app skips the ask", '{"app":"…","ref":"send","text":"{…}"}', "asks", BLUE, "hidden screen only", BLUE)

y = group(y, "WEB — the built-in browser", "this client has no web, so the model is told these do not exist")
y = row(y, "web.navigate", "open a URL in the browser", '{"url":"https://weibo.com"}', "runs", GREEN, "no, web is off", GREY)
y = row(y, "web.read", "read the current page as text", "", "runs", GREEN, "no, web is off", GREY)
y = row(y, "web.click", "click a link on the page", '{"ref":"l3"}', "runs", GREEN, "no, web is off", GREY)
y = row(y, "web.type", "type into a field on the page", '{"ref":"f1","text":"…"}', "runs", GREEN, "no, web is off", GREY)

y = group(y, "SYSTEM — intents the phone already offers", "these open another app; they do not finish the job for you")
y = row(y, "system.openLink", "open a link in whatever handles it", '{"text":"https://…"}', "runs", GREEN, "yes", GREEN)
y = row(y, "system.setAlarm", "open the clock with an alarm set", '{"text":"07:30"}', "runs", GREEN, "yes, in the clock app", GREEN)
y = row(y, "system.share", "the system share sheet", '{"text":"…"}', "asks", BLUE, "yes", GREEN)
y = row(y, "system.sendMessage", "draft an SMS to the number in app", '{"app":"+79161234567","text":"буду в 8"}', "asks", BLUE, "SMS draft only", RED)
y = row(y, "system.call", "open the dialer with a number", '{"text":"+7…"}', "asks", BLUE, "opens, does not dial", RED)
y = row(y, "system.uninstall", "the system uninstall dialog", '{"app":"com.sina.weibo"}', "asks", BLUE, "yes", GREEN)
y = row(y, "system.addEvent", "open the calendar with an event", "", "runs", GREEN, "no handler", RED)
y = row(y, "system.delete", "delete by a query", "", "asks", BLUE, "no handler", RED)
y = row(y, "system.reset", "reset something", "", "asks", BLUE, "no handler", RED)

y = group(y, "SANDBOX — a snippet of code", "a separate machine if it existed; this client has none")
y = row(y, "sandbox.run", "run a snippet in a sandbox", '{"text":"…"}', "runs", GREEN, "no sandbox", GREY)

y = group(y, "PAYMENT — money", "these are gestures with a label: they still tap or type, but never without a yes")
y = row(y, "payment.pay", "tap the pay button", '{"ref":"e42"}', "always asks", BLUE, "yes, as a tap", GREEN)
y = row(y, "payment.confirm", "tap confirm", '{"ref":"e42"}', "always asks", BLUE, "yes, as a tap", GREEN)
y = row(y, "payment.checkout", "tap checkout", '{"ref":"e42"}', "always asks", BLUE, "yes, as a tap", GREEN)
y = row(y, "payment.enterCard", "type the card number", '{"ref":"e30","text":"…"}', "always asks", BLUE, "yes, as typing", GREEN)
y = row(y, "payment.authorize", "tap authorize", '{"ref":"e42"}', "always asks", BLUE, "yes, as a tap", GREEN)

# footer
d.text((s(40), s(y + 14)), "The gate looks at the kind, not at the app or at the words. A kind the executor does not handle still writes a record, and the loop carries on.", font=F_NOTE, fill=HINT, anchor="la")
d.text((s(40), s(y + 32)), "payment.* is not a separate transport: payment.pay is a tap on the pay button, payment.enterCard is typing into the card field.", font=F_NOTE, fill=HINT, anchor="la")

# legend
ly = y + 58
d.text((s(40), s(ly)), "Gate:", font=font(SEMI, 9), fill=HINT, anchor="la")
badge(96, ly - 10, "runs", GREEN)
badge(210, ly - 10, "asks", BLUE)
badge(324, ly - 10, "always asks", BLUE)
d.text((s(452), s(ly)), "On this phone:", font=font(SEMI, 9), fill=HINT, anchor="la")
d.text((s(556), s(ly)), "yes", font=F_SUP, fill=GREEN, anchor="la")
d.text((s(600), s(ly)), "via the hidden screen", font=F_SUP, fill=BLUE, anchor="la")
d.text((s(730), s(ly)), "not possible here", font=F_SUP, fill=GREY, anchor="la")
d.text((s(846), s(ly)), "declared, never executed", font=F_SUP, fill=RED, anchor="la")

img = img.resize((W, H), Image.Resampling.LANCZOS)
out = Path("docs/media/hopa-actions-map.png")
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out, "PNG")
print(out)
