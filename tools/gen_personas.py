"""Original character pictures for the Personas mod (64x64) + the mod icon.

Run:  python3 tools/gen_personas.py
"""
import os
from PIL import Image, ImageDraw

A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sleep_client", "textures")
S = 4  # supersampling


def canvas(bg):
    im = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((2 * S, 2 * S, 62 * S, 62 * S), 10 * S, fill=bg)
    return im, d


def R(d, box, fill, r=0, outline=None, w=1):
    x0, y0, x1, y1 = [v * S for v in box]
    if r:
        d.rounded_rectangle((x0, y0, x1, y1), r * S, fill=fill, outline=outline, width=w * S)
    else:
        d.rectangle((x0, y0, x1, y1), fill=fill, outline=outline, width=w * S)


def E(d, box, fill, outline=None, w=1):
    d.ellipse([v * S for v in box], fill=fill, outline=outline, width=w * S)


def P(d, pts, fill):
    d.polygon([(x * S, y * S) for x, y in pts], fill=fill)


def eyes(d, y, dx=9, r=4, col=(255, 255, 255), pupil=(30, 30, 40), cx=32):
    for s in (-1, 1):
        E(d, (cx + s * dx - r, y - r, cx + s * dx + r, y + r), col)
        E(d, (cx + s * dx - r / 2, y - r / 2, cx + s * dx + r / 2, y + r / 2), pupil)


def smile(d, y=42, w=9, col=(40, 30, 30)):
    d.arc((32 * S - w * S, (y - 6) * S, 32 * S + w * S, (y + 6) * S), 20, 160, fill=col, width=2 * S)


def me():
    im, d = canvas((70, 74, 90, 255))
    E(d, (16, 12, 48, 46), (220, 190, 165, 255))
    R(d, (12, 44, 52, 60), (110, 120, 150, 255), 6)
    eyes(d, 28, 8, 3)
    smile(d, 38, 7)
    return im


def sponge():
    im, d = canvas((90, 190, 235, 255))
    R(d, (12, 10, 52, 50), (250, 220, 60, 255), 5)
    for x, y in ((18, 16), (44, 22), (22, 44), (46, 14)):
        E(d, (x - 2, y - 2, x + 2, y + 2), (225, 190, 40, 255))
    eyes(d, 26, 9, 6)
    E(d, (13, 33, 21, 40), (255, 140, 130, 255))
    E(d, (43, 33, 51, 40), (255, 140, 130, 255))
    d.arc((20 * S, 30 * S, 44 * S, 46 * S), 15, 165, fill=(150, 70, 40, 255), width=2 * S)
    R(d, (27, 41, 32, 47), (255, 255, 255, 255), 1, (150, 150, 150, 255))
    R(d, (32, 41, 37, 47), (255, 255, 255, 255), 1, (150, 150, 150, 255))
    R(d, (16, 50, 48, 58), (150, 100, 60, 255), 2)
    return im


def hacker():
    im, d = canvas((16, 20, 22, 255))
    for i in range(8):
        for j in range(5):
            if (i * 7 + j * 3) % 4 == 0:
                R(d, (5 + i * 7, 5 + j * 4, 8 + i * 7, 8 + j * 4), (20, 90, 50, 255))
    P(d, [(10, 58), (14, 24), (32, 8), (50, 24), (54, 58)], (38, 40, 48, 255))
    E(d, (18, 20, 46, 50), (10, 12, 14, 255))
    for s in (-1, 1):
        R(d, (32 + s * 8 - 5, 31, 32 + s * 8 + 5, 36), (60, 255, 140, 255), 2)
    R(d, (24, 42, 40, 44), (60, 255, 140, 120), 1)
    return im


def robot():
    im, d = canvas((60, 70, 85, 255))
    R(d, (31, 5, 33, 14), (200, 200, 210, 255))
    E(d, (29, 3, 35, 9), (255, 90, 90, 255))
    R(d, (14, 14, 50, 50), (160, 170, 185, 255), 6, (110, 120, 135, 255), 2)
    R(d, (19, 22, 29, 32), (60, 230, 255, 255), 2)
    R(d, (35, 22, 45, 32), (60, 230, 255, 255), 2)
    R(d, (22, 39, 42, 45), (70, 80, 95, 255), 2)
    for x in (26, 31, 36):
        R(d, (x, 39, x + 1, 45), (160, 170, 185, 255))
    R(d, (9, 26, 14, 38), (110, 120, 135, 255))
    R(d, (50, 26, 55, 38), (110, 120, 135, 255))
    return im


def alien():
    im, d = canvas((40, 30, 80, 255))
    for x, y in ((10, 10), (52, 14), (46, 52), (12, 50)):
        E(d, (x - 1, y - 1, x + 1, y + 1), (255, 255, 255, 255))
    E(d, (12, 8, 52, 58), (120, 220, 110, 255))
    for s in (-1, 1):
        P(d, [(32 + s * 6, 28), (32 + s * 19, 24), (32 + s * 14, 40), (32 + s * 7, 38)], (15, 20, 25, 255))
        E(d, (32 + s * 12 - 2, 29, 32 + s * 12 + 1, 32), (255, 255, 255, 200))
    d.arc((24 * S, 40 * S, 40 * S, 50 * S), 20, 160, fill=(40, 90, 40, 255), width=2 * S)
    return im


def chipmunk():
    im, d = canvas((120, 170, 90, 255))
    E(d, (14, 14, 22, 24), (140, 90, 50, 255))
    E(d, (42, 14, 50, 24), (140, 90, 50, 255))
    E(d, (10, 16, 54, 58), (180, 120, 70, 255))
    E(d, (18, 36, 46, 58), (235, 205, 165, 255))
    eyes(d, 32, 8, 4)
    E(d, (29, 38, 35, 43), (60, 40, 30, 255))
    R(d, (28, 46, 32, 53), (255, 255, 255, 255), 1, (120, 120, 120, 255))
    R(d, (32, 46, 36, 53), (255, 255, 255, 255), 1, (120, 120, 120, 255))
    return im


def giant():
    im, d = canvas((90, 80, 70, 255))
    R(d, (8, 8, 56, 58), (140, 138, 132, 255), 4, (95, 93, 90, 255), 2)
    for x, y, w, h in ((12, 12, 10, 6), (40, 14, 10, 7), (14, 46, 12, 7), (42, 44, 10, 8)):
        R(d, (x, y, x + w, y + h), (118, 116, 110, 255), 2)
    R(d, (14, 24, 50, 29), (80, 78, 74, 255))
    for s in (-1, 1):
        E(d, (32 + s * 11 - 4, 28, 32 + s * 11 + 4, 36), (255, 210, 90, 255))
        E(d, (32 + s * 11 - 2, 30, 32 + s * 11 + 2, 34), (40, 30, 20, 255))
    R(d, (22, 44, 42, 48), (70, 68, 64, 255), 2)
    return im


def demon():
    im, d = canvas((70, 16, 24, 255))
    P(d, [(14, 28), (8, 6), (26, 18)], (230, 220, 200, 255))
    P(d, [(50, 28), (56, 6), (38, 18)], (230, 220, 200, 255))
    E(d, (12, 14, 52, 56), (210, 50, 50, 255))
    for s in (-1, 1):
        P(d, [(32 + s * 5, 30), (32 + s * 18, 26), (32 + s * 14, 36)], (255, 230, 60, 255))
        E(d, (32 + s * 11, 28, 32 + s * 11 + 2, 33), (20, 10, 10, 255))
    d.arc((20 * S, 38 * S, 44 * S, 54 * S), 200, 340, fill=(60, 10, 10, 255), width=2 * S)
    P(d, [(26, 44), (29, 50), (31, 44)], (255, 255, 255, 255))
    P(d, [(33, 44), (35, 50), (38, 44)], (255, 255, 255, 255))
    return im


def ghost():
    im, d = canvas((30, 36, 70, 255))
    E(d, (14, 8, 50, 40), (240, 244, 255, 255))
    R(d, (14, 24, 50, 54), (240, 244, 255, 255))
    for i in range(3):
        E(d, (14 + i * 12, 48, 26 + i * 12, 60), (240, 244, 255, 255))
    for i in range(3):
        E(d, (17 + i * 12, 53, 23 + i * 12, 62), (30, 36, 70, 255))
    eyes(d, 28, 8, 3, (30, 30, 60), (30, 30, 60))
    E(d, (29, 36, 35, 44), (50, 50, 90, 255))
    return im


def phone():
    im, d = canvas((70, 90, 70, 255))
    R(d, (8, 20, 56, 34), (200, 60, 50, 255), 7)
    R(d, (4, 14, 20, 40), (170, 45, 40, 255), 5)
    R(d, (44, 14, 60, 40), (170, 45, 40, 255), 5)
    R(d, (14, 38, 50, 58), (60, 50, 50, 255), 5)
    for i in range(3):
        for j in range(2):
            E(d, (21 + i * 10, 42 + j * 8, 27 + i * 10, 48 + j * 8), (220, 220, 220, 255))
    return im


def kid():
    im, d = canvas((255, 180, 100, 255))
    E(d, (14, 16, 50, 54), (245, 205, 170, 255))
    P(d, [(12, 28), (32, 6), (52, 28), (58, 30), (50, 32), (14, 32)], (60, 120, 220, 255))
    R(d, (12, 28, 56, 33), (40, 90, 190, 255), 2)
    eyes(d, 38, 8, 4)
    E(d, (16, 42, 22, 47), (255, 150, 140, 255))
    E(d, (42, 42, 48, 47), (255, 150, 140, 255))
    d.arc((24 * S, 40 * S, 40 * S, 52 * S), 15, 165, fill=(120, 50, 40, 255), width=2 * S)
    return im


def grandpa():
    im, d = canvas((110, 120, 150, 255))
    E(d, (12, 10, 52, 30), (235, 235, 240, 255))
    E(d, (14, 16, 50, 56), (235, 195, 160, 255))
    E(d, (10, 22, 20, 44), (235, 235, 240, 255))
    E(d, (44, 22, 54, 44), (235, 235, 240, 255))
    for s in (-1, 1):
        E(d, (32 + s * 9 - 6, 28, 32 + s * 9 + 6, 40), (255, 255, 255, 80), (60, 50, 40, 255), 2)
        E(d, (32 + s * 9 - 1, 33, 32 + s * 9 + 1, 35), (30, 30, 40, 255))
    R(d, (30, 34, 34, 36), (60, 50, 40, 255))
    E(d, (22, 44, 42, 56), (235, 235, 240, 255))
    d.arc((26 * S, 42 * S, 38 * S, 50 * S), 20, 160, fill=(130, 70, 60, 255), width=2 * S)
    return im


def cave():
    im, d = canvas((40, 38, 50, 255))
    E(d, (8, 10, 56, 70), (20, 20, 26, 255))
    P(d, [(8, 62), (10, 30), (32, 6), (54, 30), (56, 62)], (95, 90, 100, 255))
    E(d, (16, 18, 48, 70), (14, 14, 20, 255))
    for r in (6, 11, 16):
        d.arc((32 * S - r * S, 44 * S - r * S, 32 * S + r * S, 44 * S + r * S), 200, 340, fill=(150, 200, 255, 255 - r * 10), width=2 * S)
    return im


def icon():
    """White mask glyph like the other mod icons (64x64)."""
    im = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W = (255, 255, 255, 255)
    d.rounded_rectangle((10 * S, 8 * S, 54 * S, 40 * S), 14 * S, fill=W)
    P(d, [(12, 34), (52, 34), (46, 54), (32, 58), (18, 54)], W)
    for s in (-1, 1):
        d.ellipse(((32 + s * 11 - 5) * S, 22 * S, (32 + s * 11 + 5) * S, 31 * S), fill=(0, 0, 0, 0))
    clear = Image.new("RGBA", im.size, (0, 0, 0, 0))
    px = im.load()
    d2 = ImageDraw.Draw(im)
    # cut eyes and mouth out
    for s in (-1, 1):
        for y in range(22 * S, 31 * S):
            for x in range((32 + s * 11 - 5) * S, (32 + s * 11 + 5) * S):
                if ((x - (32 + s * 11) * S) / (5.0 * S)) ** 2 + ((y - 26.5 * S) / (4.5 * S)) ** 2 <= 1:
                    px[x, y] = (0, 0, 0, 0)
    for y in range(42 * S, 46 * S):
        for x in range(24 * S, 40 * S):
            px[x, y] = (0, 0, 0, 0)
    return im


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.resize((64, 64), Image.LANCZOS).save(path, optimize=True)


for fn in (me, sponge, hacker, robot, alien, chipmunk, giant, demon, ghost, phone, kid, grandpa, cave):
    save(fn(), os.path.join(A, "persona", fn.__name__ + ".png"))
icon_path = os.path.join(A, "gui", "icon", "personas.png")
save(icon(), icon_path)
open(icon_path + ".mcmeta", "w").write('{"texture":{"blur":true}}')
print("ok")
