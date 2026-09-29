#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Makes the README's images in docs/images from window captures.

    tools/readme_images.py [SHOTS_DIR]

SHOTS_DIR (default ~/.cache/canvas/shots) holds `./cv shot` captures of a
1536x960 Canvas window (`am task resize <task> 192 90 1728 1050`) showing the
demo artwork from tools/demo_art.py, never anyone's own files:

  hero.png    the document in the dark scheme
  menus.png   Image > Adjustments open
  dialog.png  Hue/Saturation with a live preview
  light.png   the light scheme with the Appearance menu open
  start.png   the start page (only its header is used: the recent files are private)

Each gets rounded corners and a soft shadow; icon.png is the logo from
android/brand/canvas-logo.svg (needs rsvg-convert).
"""
import pathlib
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"
SHOTS = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/canvas/shots")
# The start page's header (logo, name, tagline, buttons), above the recent files.
START_HEADER = (360, 220, 800, 410)


def rounded(im, radius):
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius, fill=255)
    im.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return im


def shadowed(im, blur=18, offset=(0, 10), alpha=110, pad=44):
    canvas = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    a = im.getchannel("A").point(lambda v: v * alpha // 255)
    shadow.paste(Image.new("RGBA", im.size, (10, 12, 30, 255)), (pad + offset[0], pad + offset[1]), a)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(im, (pad, pad))
    return canvas


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("hero", "menus", "dialog", "light"):
        im = Image.open(SHOTS / f"{name}.png")
        # The window's own corners are rounded, so the capture's corners show what
        # is behind it; a slightly larger radius hides them.
        out = shadowed(rounded(im, 22))
        out.save(OUT / f"{name}.png", optimize=True)
        print(OUT / f"{name}.png", out.size)
    start = Image.open(SHOTS / "start.png").crop(START_HEADER)
    out = shadowed(rounded(start, 18), blur=14, pad=32)
    out.save(OUT / "start.png", optimize=True)
    print(OUT / "start.png", out.size)
    subprocess.run(["rsvg-convert", "-w", "256", "-h", "256", str(ROOT / "android/brand/canvas-logo.svg"),
                    "-o", str(OUT / "icon.png")], check=True)
    print(OUT / "icon.png")


if __name__ == "__main__":
    main()
