#!/usr/bin/env python3
"""Cut the plush mascot out of its baked background.

The two drawables the app ships are RGBA but fully opaque: the studio backdrop
and the cast shadow are part of the picture. The cut-out look in the app draws
the mascot as an object on the page, so both have to go: the backdrop via a
flood fill from the border, and the shadow via a saturation rule, because the
photo's shadow sits under the feet as an island the fill cannot reach.

The eyes and mouth are also neutral, so they are spared by requiring a bright
pixel; the cream belly is spared because it is saturated (about 80), while the
shadow is not (about 30).

Writes next to the inputs:

    hopa_plush_cut.png
    hopa_plush_blink_cut.png

The originals stay, because the "Framed" mascot setting still uses them.
Running it twice is safe: an input that already has transparency is skipped.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
DRAWABLES = ROOT / "apps" / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
SOURCES = {
    "hopa_plush.png": "hopa_plush_cut.png",
    "hopa_plush_blink.png": "hopa_plush_blink_cut.png",
}

# How close to the backdrop colour still counts as backdrop, and how far the
# fill may travel. The plush is saturated orange against a near-white studio.
TOLERANCE = 70
FILL_STEPS = 600

# The cast shadow: neutral (low saturation) and brighter than the eyes.
SHADOW_SATURATION = 85
SHADOW_BRIGHTNESS = 120
SHADOW_FROM = 0.55  # of the height; the shadow only lives under the body

# The mascot fills the slot it is drawn in, with a hair of breathing room.
MARGIN = 1.04


def grow(start: np.ndarray, allowed: np.ndarray) -> np.ndarray:
    """Flood fill four-connected through ``allowed`` from ``start``."""
    current = start
    for _ in range(FILL_STEPS):
        nxt = current.copy()
        nxt[1:, :] |= current[:-1, :] & allowed[1:, :]
        nxt[:-1, :] |= current[1:, :] & allowed[:-1, :]
        nxt[:, 1:] |= current[:, :-1] & allowed[:, 1:]
        nxt[:, :-1] |= current[:, 1:] & allowed[:, :-1]
        if nxt.sum() == current.sum():
            break
        current = nxt
    return current


def matte(img: Image.Image) -> Image.Image:
    pixels = np.asarray(img.convert("RGB")).astype(np.int16)
    height, width, _ = pixels.shape

    border = np.concatenate([pixels[0], pixels[-1], pixels[:, 0], pixels[:, -1]])
    backdrop = np.median(border, axis=0)
    distance = np.sqrt(((pixels - backdrop) ** 2).sum(axis=2))
    close = distance < TOLERANCE

    outside = np.zeros((height, width), dtype=bool)
    outside[0, :] = True
    outside[-1, :] = True
    outside[:, 0] = True
    outside[:, -1] = True
    outside &= close
    outside = grow(outside, close)

    saturation = pixels.max(axis=2) - pixels.min(axis=2)
    brightness = pixels.mean(axis=2)
    shadow = (
        (saturation < SHADOW_SATURATION)
        & (brightness > SHADOW_BRIGHTNESS)
        & (np.arange(height)[:, None] >= height * SHADOW_FROM)
    )

    # The shadow is reached from the outside, not matched everywhere: the belly
    # is a similar cream and must survive, and it is enclosed by saturated fur.
    stray = grow(outside.copy(), close | shadow) & ~outside
    alpha = np.where(outside | stray, 0, 255).astype(np.uint8)
    # One pixel of erosion: the outermost ring still carries the backdrop hue.
    eroded = alpha.copy()
    eroded[1:, :] = np.minimum(eroded[1:, :], alpha[:-1, :])
    eroded[:-1, :] = np.minimum(eroded[:-1, :], alpha[1:, :])
    eroded[:, 1:] = np.minimum(eroded[:, 1:], alpha[:, :-1])
    eroded[:, :-1] = np.minimum(eroded[:, :-1], alpha[:, 1:])

    out = img.convert("RGBA")
    out.putalpha(Image.fromarray(eroded, "L").filter(ImageFilter.GaussianBlur(1.8)))
    return seat(square(out))


def square(img: Image.Image) -> Image.Image:
    """Crop to the plush and centre it in a square, so the feet sit on the edge.

    The app draws a contact shadow at the bottom of the slot; a wide empty band
    left by the removed backdrop would leave the shadow floating under nothing.
    """
    box = img.getchannel("A").getbbox()
    if box is None:
        return img
    tight = img.crop(box)
    side = int(max(tight.size) * MARGIN)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(tight, ((side - tight.width) // 2, (side - tight.height) // 2))
    return canvas.resize((512, 512), Image.LANCZOS)


SEAT_DEPTH = 0.18  # of the plush's height, measured up from its feet
SEAT_STRENGTH = 0.08  # how much the bottom edge darkens


def seat(img: Image.Image) -> Image.Image:
    """Darken the last band of the plush a little.

    The cream belly is almost the page colour, so the lower half of the mascot
    dissolves into it. A soft multiply towards the feet - the same occlusion a
    real object has where it meets the floor - keeps the shape without a border.
    """
    pixels = np.asarray(img).astype(np.float32)
    box = img.getchannel("A").getbbox()
    if box is None:
        return img
    bottom = box[3]
    depth = max(1, int((bottom - box[1]) * SEAT_DEPTH))
    for offset in range(depth):
        y = bottom - depth + offset
        if y < 0 or y >= pixels.shape[0]:
            continue
        strength = 1.0 - SEAT_STRENGTH * (offset + 1) / depth
        pixels[y, :, :3] *= strength
    return Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGBA")


def main() -> int:
    if not DRAWABLES.is_dir():
        print(f"no drawables at {DRAWABLES}", file=sys.stderr)
        return 2
    for name, target_name in SOURCES.items():
        source = DRAWABLES / name
        target = DRAWABLES / target_name
        if not source.exists():
            print(f"missing {source}", file=sys.stderr)
            return 2
        existing = Image.open(source)
        if existing.getchannel("A").getextrema()[0] < 255:
            print(f"skip {name}: already has transparency")
            continue
        matte(Image.open(source)).save(target, "PNG", optimize=True)
        print(f"wrote {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
