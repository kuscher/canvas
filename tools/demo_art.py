#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Writes the demo artwork for Canvas's screenshots: "Evening Lake.psd", a
layered landscape (stars, sky, sun, far and near hills, lake) drawn from
code, so the README shows no one's photos.

    ~/.cache/canvas/venv/bin/python tools/demo_art.py [OUT.psd]

Needs psd-tools, numpy and Pillow (the venv `./cv` notes in CLAUDE.md).
"""
import pathlib
import sys

import numpy as np
from PIL import Image, ImageFilter
from psd_tools import PSDImage
from psd_tools.api.layers import PixelLayer

W, H = 2400, 1600
HORIZON = int(H * 0.66)
rng = np.random.default_rng(7)


def gradient(stops):
    """A vertical RGB gradient from (position, colour) stops."""
    ys = np.linspace(0.0, 1.0, H)
    out = np.zeros((H, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, [p for p, _ in stops], [col[c] for _, col in stops])
    return np.repeat(out[:, None, :], W, axis=1)


def ridge(base, roughness, amplitude, seed):
    """A hill line: midpoint displacement, smoothed."""
    r = np.random.default_rng(seed)
    n = 1025
    line = np.zeros(n)
    step, scale = n - 1, amplitude
    while step > 1:
        half = step // 2
        for i in range(half, n, step):
            line[i] = (line[i - half] + line[min(i + half, n - 1)]) / 2 + r.uniform(-scale, scale)
        step, scale = half, scale * roughness
    xs = np.linspace(0, n - 1, W)
    line = np.interp(xs, np.arange(n), line)
    line = np.convolve(line, np.ones(15) / 15, mode="same")
    return base + line


def rgba(rgb, alpha):
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 255)]).astype(np.uint8), "RGBA")


def hills(line, colour, haze_top):
    ys = np.arange(H)[:, None]
    inside = (ys >= line[None, :]) & (ys < HORIZON)
    shade = np.clip((ys - line[None, :]) / 220.0, 0, 1)[..., None]
    rgb = np.array(colour)[None, None, :] * (1 - 0.18 * shade) + np.array(haze_top)[None, None, :] * 0.18 * (1 - shade)
    alpha = np.where(inside, 255, 0).astype(float)
    # Soft edge on the ridge.
    edge = np.clip((ys - line[None, :]) + 1.0, 0, 1)
    return rgba(np.broadcast_to(rgb, (H, W, 3)), alpha * edge)


def main():
    out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/canvas/demo/Evening Lake.psd")
    out.parent.mkdir(parents=True, exist_ok=True)

    sky_rgb = gradient([(0.0, (22, 27, 74)), (0.28, (74, 46, 120)), (0.48, (182, 72, 132)),
                        (0.6, (246, 142, 102)), (0.66, (252, 196, 128)), (1.0, (252, 196, 128))])
    sky = rgba(sky_rgb, np.full((H, W), 255.0))

    ys, xs = np.mgrid[0:H, 0:W]
    stars_alpha = np.zeros((H, W))
    count = 420
    sx, sy = rng.integers(0, W, count), (rng.random(count) ** 1.6 * H * 0.42).astype(int)
    for x, y, b in zip(sx, sy, rng.uniform(90, 255, count)):
        stars_alpha[y, x] = b * (1 - y / (H * 0.45))
    stars_img = Image.fromarray(stars_alpha.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.1))
    stars_alpha = np.array(stars_img).astype(float) * 2.2
    stars = rgba(np.full((H, W, 3), 255.0), stars_alpha)

    cx, cy, radius = W * 0.74, HORIZON - 330, 105
    dist = np.hypot(xs - cx, ys - cy)
    disc = np.clip(radius + 1.5 - dist, 0, 1)
    glow = np.exp(-np.maximum(dist - radius, 0) / 170.0) * 0.75
    sun_alpha = np.clip(disc + glow * (1 - disc), 0, 1) * 255
    sun_rgb = np.dstack([np.full((H, W), 255.0), 236 - 60 * (1 - disc), 190 - 110 * (1 - disc)])
    sun = rgba(sun_rgb, sun_alpha * (ys < HORIZON))

    far = hills(ridge(HORIZON - 230, 0.6, 170, 3), (132, 70, 136), (250, 150, 120))
    near = hills(ridge(HORIZON - 110, 0.58, 130, 11), (38, 24, 66), (110, 50, 100))

    # The lake mirrors everything above the horizon, rippled and darkened.
    above = Image.new("RGBA", (W, H))
    for layer in (sky, sun, far, near):
        above = Image.alpha_composite(above, layer)
    above = np.array(above).astype(float)
    lake = np.zeros((H, W, 4))
    depth = np.arange(HORIZON, H) - HORIZON
    src_rows = np.clip(HORIZON - 1 - depth, 0, H - 1)
    ripple = (np.sin(depth[:, None] * 0.35 + xs[HORIZON:, :] * 0.012) * (2 + depth[:, None] * 0.02)).astype(int)
    cols = np.clip(xs[HORIZON:, :] + ripple, 0, W - 1)
    lake[HORIZON:, :, :3] = above[src_rows[:, None], cols, :3]
    fade = (0.62 - 0.32 * (depth / (H - HORIZON)))[:, None, None]
    lake[HORIZON:, :, :3] = lake[HORIZON:, :, :3] * fade + np.array([20, 24, 70]) * (1 - fade)
    lake[HORIZON:, :, 3] = 255
    lake = Image.fromarray(np.clip(lake, 0, 255).astype(np.uint8), "RGBA")

    psd = PSDImage.new(mode="RGB", size=(W, H))
    for name, image in (("Sky", sky), ("Stars", stars), ("Sun", sun), ("Far hills", far),
                        ("Near hills", near), ("Lake", lake)):
        psd.append(PixelLayer.frompil(image, psd, name))
    psd.save(out)
    flat = Image.new("RGBA", (W, H))
    for layer in (sky, stars, sun, far, near, lake):
        flat = Image.alpha_composite(flat, layer)
    flat.convert("RGB").resize((W // 4, H // 4)).save(out.with_suffix(".preview.png"))
    print(out)


if __name__ == "__main__":
    main()
