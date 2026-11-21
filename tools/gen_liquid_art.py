"""Original textures for Realistic Liquids and Custom Rain (particles + mod icons).

Run:  python3 tools/gen_liquid_art.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

T = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sleep_client", "textures")
random.seed(7)


def save(im, name, sub="particle"):
    p = os.path.join(T, sub, name)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p, optimize=True)


def puddle():
    n = 64
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = (x - 31.5) / 31.5, (y - 31.5) / 31.5
            # an uneven blob: radius wobbles with the angle
            a = math.atan2(dy, dx)
            r = math.hypot(dx, dy) / (0.82 + 0.08 * math.sin(a * 3 + 1.0) + 0.06 * math.sin(a * 5 + 2.0))
            if r >= 1:
                continue
            edge = min(1.0, (1 - r) / 0.28)          # soft shoreline
            a8 = edge ** 0.8
            shade = 150 + 60 * (1 - r) + 30 * math.sin(dx * 4) * math.sin(dy * 3)  # sky reflection: lighter in the middle
            px[x, y] = (int(shade * 0.80), int(shade * 0.90), int(min(255, shade * 1.0)), int(255 * a8))
    # a thin bright streak like a window of sky on the water
    d = ImageDraw.Draw(im)
    d.ellipse((18, 22, 40, 28), fill=(235, 245, 255, 60))
    return im.filter(ImageFilter.GaussianBlur(0.6))


def ripple():
    n = 64
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 31.5, y - 31.5) / 31.5
            ring = math.exp(-((r - 0.78) / 0.07) ** 2)        # the bright ring
            inner = 0.45 * math.exp(-((r - 0.52) / 0.05) ** 2)  # a faint second ring
            a = min(1.0, ring + inner) * (1 - max(0, (r - 0.9) / 0.1))
            if a > 0.01:
                px[x, y] = (235, 245, 255, int(255 * a))
    return im


def glint():
    n = 16
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            v = max(math.exp(-dx * 1.4) * math.exp(-dy * 0.35 * dy * 0.6) * 0, 0)
            star = math.exp(-(dx * dy) * 0.9) * math.exp(-(dx + dy) * 0.18)  # 4-point star
            core = math.exp(-(dx * dx + dy * dy) / 3.0)
            a = min(1.0, star * 1.2 + core)
            if a > 0.03:
                px[x, y] = (255, 255, 255, int(255 * a))
    return im


def foam():
    n = 16
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _ in range(9):
        cx, cy, r = random.uniform(4.5, 11.5), random.uniform(4.5, 11.5), random.uniform(1.2, 2.4)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(245, 250, 255, 210), outline=(255, 255, 255, 255))
    return im.filter(ImageFilter.GaussianBlur(0.35))


def icon_drop():
    S = 4
    im = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W = (255, 255, 255, 255)
    d.polygon([(32 * S, 6 * S), (50 * S, 34 * S), (14 * S, 34 * S)], fill=W)
    d.ellipse((14 * S, 24 * S, 50 * S, 58 * S), fill=W)
    d.ellipse((22 * S, 38 * S, 30 * S, 50 * S), fill=(0, 0, 0, 0))   # highlight cut-out
    return im.resize((64, 64), Image.LANCZOS)


def icon_rain():
    S = 4
    im = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W = (255, 255, 255, 255)
    d.ellipse((8 * S, 18 * S, 30 * S, 38 * S), fill=W)
    d.ellipse((20 * S, 10 * S, 44 * S, 38 * S), fill=W)
    d.ellipse((34 * S, 20 * S, 56 * S, 38 * S), fill=W)
    d.rectangle((16 * S, 26 * S, 48 * S, 38 * S), fill=W)
    for x in (18, 30, 42):
        d.line((x * S, 44 * S, (x - 4) * S, 56 * S), fill=W, width=3 * S)
    return im.resize((64, 64), Image.LANCZOS)


save(puddle(), "liquid_puddle.png")
save(ripple(), "liquid_ripple.png")
save(glint(), "liquid_glint.png")
save(foam(), "liquid_foam.png")
for name, im in (("realistic_liquids", icon_drop()), ("custom_rain", icon_rain())):
    save(im, name + ".png", "gui/icon")
    open(os.path.join(T, "gui", "icon", name + ".png.mcmeta"), "w").write('{"texture":{"blur":true}}')
print("ok")
