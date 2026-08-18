#!/usr/bin/env python3
"""
Generates the raster launcher icons and the app logo for
Seabuckthorn Field Survey - Ladakh.

Everything is drawn at 4x and downsampled, which is what gives the small
densities clean edges. Re-run after changing the artwork:

    python3 tools/generate_icons.py
"""

import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")

SS = 4  # supersampling factor

GREEN_DARK = (12, 74, 40)
GREEN = (27, 107, 58)
GREEN_LIGHT = (46, 138, 74)
LEAF = (206, 229, 212)
LEAF_EDGE = (150, 190, 162)
TWIG = (176, 205, 184)
BERRY_CORE = (255, 197, 77)
BERRY_MID = (246, 162, 29)
BERRY_EDGE = (214, 110, 18)
WHITE = (255, 255, 255)


def _radial_background(size):
    """Green field with a soft highlight up and to the left."""
    image = Image.new("RGB", (size, size), GREEN)
    draw = ImageDraw.Draw(image)
    steps = 130
    for index in range(steps, 0, -1):
        t = index / steps
        radius = size * 0.85 * t
        colour = tuple(
            int(GREEN_LIGHT[c] * (1 - t) + GREEN_DARK[c] * t) for c in range(3)
        )
        centre = (size * 0.36, size * 0.30)
        draw.ellipse(
            [centre[0] - radius, centre[1] - radius, centre[0] + radius, centre[1] + radius],
            fill=colour,
        )
    return image


def _berry(layer, cx, cy, r):
    """A glossy seabuckthorn berry."""
    draw = ImageDraw.Draw(layer)
    steps = 26
    for index in range(steps, 0, -1):
        t = index / steps
        rr = r * t
        colour = tuple(
            int(BERRY_CORE[c] * (1 - t) + BERRY_EDGE[c] * t) for c in range(3)
        )
        # Shade from a point up-left of centre so every berry catches the same light.
        ox = cx - r * 0.20 * (1 - t)
        oy = cy - r * 0.22 * (1 - t)
        draw.ellipse([ox - rr, oy - rr, ox + rr, oy + rr], fill=colour + (255,))

    highlight = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(highlight)
    hr = r * 0.30
    hx, hy = cx - r * 0.34, cy - r * 0.38
    hd.ellipse([hx - hr, hy - hr * 0.78, hx + hr, hy + hr * 0.78], fill=WHITE + (150,))
    highlight = highlight.filter(ImageFilter.GaussianBlur(r * 0.10))
    layer.alpha_composite(highlight)


def _leaf(layer, cx, cy, length, width, angle_deg):
    """A narrow silvery seabuckthorn leaf."""
    pad = int(length)
    tile = Image.new("RGBA", (pad * 2, pad * 2), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    box = [pad - width / 2, pad - length / 2, pad + width / 2, pad + length / 2]
    td.ellipse(box, fill=LEAF + (255,), outline=LEAF_EDGE + (255,), width=max(1, int(width * 0.09)))
    td.line(
        [(pad, pad - length * 0.42), (pad, pad + length * 0.42)],
        fill=LEAF_EDGE + (190,),
        width=max(1, int(width * 0.08)),
    )
    tile = tile.rotate(angle_deg, resample=Image.BICUBIC)
    layer.alpha_composite(tile, (int(cx - pad), int(cy - pad)))


def draw_motif(size, inset=0.62):
    """
    A seabuckthorn sprig: berries packed densely along a woody twig, with the
    narrow silver leaves the species is recognised by.

    `inset` is the fraction of the canvas the artwork occupies, which is what
    keeps it inside the adaptive-icon safe zone.
    """
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    s = size * inset
    ox = size / 2 - s / 2
    oy = size / 2 - s / 2

    def at(u, v):
        """Artwork coordinates in 0..1 within the motif box."""
        return (ox + u * s, oy + v * s)

    twig_w = max(2, int(s * 0.050))

    # The twig runs bottom-left to top-right with a gentle arch.
    twig = []
    for i in range(49):
        t = i / 48
        u = 0.16 + 0.66 * t
        v = 0.92 - 0.80 * t + 0.06 * math.sin(t * math.pi)
        twig.append(at(u, v))
    draw.line(twig, fill=TWIG + (255,), width=twig_w, joint="curve")

    # Two short shoots that carry the upper leaves.
    draw.line([at(0.62, 0.30), at(0.40, 0.13)], fill=TWIG + (255,), width=int(twig_w * 0.75))
    draw.line([at(0.55, 0.39), at(0.79, 0.30)], fill=TWIG + (255,), width=int(twig_w * 0.75))

    # Leaves sit behind the berries, fanning off the top of the twig.
    _leaf(layer, *at(0.36, 0.10), s * 0.30, s * 0.093, 34)
    _leaf(layer, *at(0.82, 0.27), s * 0.27, s * 0.085, -30)
    _leaf(layer, *at(0.57, 0.06), s * 0.26, s * 0.082, 4)

    # Berries packed along the twig, largest and frontmost at the base.
    berries = [
        (0.20, 0.83, 0.148),
        (0.36, 0.74, 0.132),
        (0.28, 0.62, 0.120),
        (0.45, 0.60, 0.126),
        (0.38, 0.48, 0.104),
        (0.55, 0.50, 0.108),
        (0.49, 0.38, 0.090),
        (0.64, 0.41, 0.086),
        (0.60, 0.29, 0.070),
    ]
    for u, v, r in sorted(berries, key=lambda b: b[1]):
        _berry(layer, *at(u, v), s * r)

    return layer


def monochrome_foreground(size):
    """
    Themed-icon silhouette. Android tints this flat, so it has to read as a
    single shape rather than as shaded artwork.
    """
    big = size * SS
    source = draw_motif(big, inset=0.58)
    alpha = source.split()[3].point(lambda a: 255 if a > 90 else 0)
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    layer.putalpha(alpha)
    layer = Image.composite(
        Image.new("RGBA", (big, big), (0, 0, 0, 255)),
        Image.new("RGBA", (big, big), (0, 0, 0, 0)),
        alpha,
    )
    return layer.resize((size, size), Image.LANCZOS)


def adaptive_foreground(size):
    layer = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
    layer.alpha_composite(draw_motif(size * SS, inset=0.58))
    return layer.resize((size, size), Image.LANCZOS)


def legacy_icon(size, rounded=True, circular=False):
    big = size * SS
    background = _radial_background(big).convert("RGBA")

    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if circular:
        md.ellipse([0, 0, big - 1, big - 1], fill=255)
    elif rounded:
        md.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=255)
    else:
        md.rectangle([0, 0, big - 1, big - 1], fill=255)

    background.putalpha(mask)
    background.alpha_composite(draw_motif(big, inset=0.66))
    return background.resize((size, size), Image.LANCZOS)


def logo(size):
    """Circular badge used on the welcome, splash and about screens."""
    return legacy_icon(size, circular=True)


def write(image, relative_path):
    path = os.path.join(RES, relative_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"  {relative_path}  {image.size[0]}x{image.size[1]}  {os.path.getsize(path):,} bytes")


def main():
    # Legacy square launcher icons (API 24-25) and the round variant.
    launcher = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    print("Launcher icons:")
    for density, px in launcher.items():
        write(legacy_icon(px), f"mipmap-{density}/ic_launcher.png")
        write(logo(px), f"mipmap-{density}/ic_launcher_round.png")

    # Adaptive-icon foreground (108dp canvas).
    print("Adaptive foregrounds:")
    foreground = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for density, px in foreground.items():
        write(adaptive_foreground(px), f"mipmap-{density}/ic_launcher_foreground.png")
        write(monochrome_foreground(px), f"mipmap-{density}/ic_launcher_monochrome.png")

    # App logo for the welcome / splash / about screens.
    print("Logo:")
    for density, px in {"xhdpi": 192, "xxhdpi": 288, "xxxhdpi": 384}.items():
        write(logo(px), f"drawable-{density}/ic_logo.png")

    print("Store icon:")
    store = os.path.join(ROOT, "tools", "play-store-icon.png")
    legacy_icon(512).save(store, "PNG", optimize=True)
    print(f"  tools/play-store-icon.png  512x512  {os.path.getsize(store):,} bytes")


if __name__ == "__main__":
    main()
