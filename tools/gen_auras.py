#!/usr/bin/env python3
"""
Makes the Sleep client's auras: the sprite sheet (textures/cosmetics/aura_atlas.png, original pixel art drawn here), one
thumbnail per aura, the minimes (a small copy of the player, drawn by dev.solclient.cosmetic.Minimes: only a thumbnail and an
entry are needed), and their entries in cosmetics/index.json and cosmetics-catalogue.json. The motion of each aura is code
(dev.solclient.cosmetic.Auras); the row of the sheet and the motion are picked by "anim": "aura:<style>".

    python3 tools/gen_auras.py
"""
import json
import math
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "sol_client")

# 8x8 sprites: '#' main colour, '+' light, '-' dark, '.' nothing
SHAPES = {
    "flame": ["...#....", "...##...", "..###...", "..#+##..", ".##++#..", ".#+++##.", ".##++#..", "..####.."],
    "frost": ["...#....", ".#.#.#..", "..###...", "###+###.", "..###...", ".#.#.#..", "...#....", "........"],
    "heart": ["........", ".##.##..", "#+####-.", "#+####-.", ".####-..", "..##-...", "...-....", "........"],
    "star": ["...#....", "...#....", "..#+#...", "##+++##.", "..#+#...", "...#....", "...#....", "........"],
    "note": ["...###..", "...#-#..", "...#.#..", "...#.#..", ".###.#..", "#++####.", "###.#++.", "....###."],
    "cash": ["........", "-------.", "-#####-.", "-#+-+#-.", "-#+-+#-.", "-#####-.", "-------.", "........"],
    "bolt": ["....##..", "...##...", "..##....", ".#####..", "...##...", "..##....", ".##.....", ".#......"],
    "leaf": ["......#.", "...###-.", "..##+#-.", ".##+##..", ".#+##-..", ".###-...", "#--.....", "........"],
    "bubble": ["..###...", ".#...#..", "#.+...#.", "#.+...#.", "#.....#.", ".#...#..", "..###...", "........"],
    "rune": ["...#....", "..#+#...", ".#.+.#..", "#++#++#.", ".#.+.#..", "..#+#...", "...#....", "........"],
    "zzz": ["........", "######..", "....#-..", "...#-...", "..#-....", ".#-.....", "######..", "........"],
    "skull": [".####...", "######..", "#-##-#..", "#-##-#..", "######..", ".#--#...", ".#.#.#..", "........"],
}
# style: (name, price, rarity, four colours)
STYLES = [
    ("flame", "Inferno Aura", 900, "epic", ["#ff7a1a", "#ffb02e", "#ff4d2e", "#ffd23f"]),
    ("frost", "Frost Aura", 900, "epic", ["#8fd8ff", "#c9f1ff", "#5fb8f0", "#e8fbff"]),
    ("heart", "Love Aura", 700, "rare", ["#ff5c8a", "#ff8fb0", "#e83e6c", "#ffb3c8"]),
    ("star", "Starlight Aura", 800, "rare", ["#ffe066", "#fff3b0", "#ffc93c", "#ffffff"]),
    ("note", "Melody Aura", 700, "rare", ["#b388ff", "#7fd1ff", "#ff8fd0", "#8affc1"]),
    ("cash", "Cash Aura", 1200, "legendary", ["#4cc96a", "#63dd82", "#3aa957", "#7be898"]),
    ("bolt", "Storm Aura", 1000, "epic", ["#ffe94a", "#fff59c", "#8fd8ff", "#ffffff"]),
    ("leaf", "Autumn Aura", 600, "rare", ["#e8792b", "#d9a521", "#c2452d", "#8fae3a"]),
    ("bubble", "Bubble Aura", 600, "rare", ["#9be7ff", "#c8f4ff", "#7fd1ff", "#e3fbff"]),
    ("rune", "Arcane Aura", 1200, "legendary", ["#c77dff", "#9d4edd", "#e0aaff", "#7b2cbf"]),
    ("zzz", "Sleepy Aura", 500, "rare", ["#a9b8ff", "#d6deff", "#8496f0", "#ffffff"]),
    ("skull", "Soul Aura", 1000, "epic", ["#7ff5e0", "#b8fff2", "#52d6c0", "#e6fffb"]),
]


# where, name, price, rarity, (x, y, size) of the small player on the thumbnail
MINIMES = [
    ("shoulder", "Shoulder Minime", 1000, "epic", (56, 12, 0.34)),
    ("head", "Head Minime", 1000, "epic", (42, 0, 0.3)),
    ("buddy", "Buddy Minime", 1400, "legendary", (8, 30, 0.46)),
    ("backpack", "Backpack Minime", 1200, "legendary", (60, 34, 0.4)),
]


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def shade(col, k):
    return tuple(max(0, min(255, int(v * k + (255 - v) * max(0, k - 1)))) for v in col)


def sprite(style, colour):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    main = rgb(colour)
    for y, row in enumerate(SHAPES[style]):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x, y), main + (255,))
            elif ch == "+":
                img.putpixel((x, y), tuple(min(255, int(v + (255 - v) * 0.6)) for v in main) + (255,))
            elif ch == "-":
                img.putpixel((x, y), tuple(int(v * 0.62) for v in main) + (255,))
    return img


def mannequin():
    """A small grey player seen from the front (drawn here), for the thumbnails."""
    img = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    def box(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                n = ((x * 7 + y * 13) % 5 - 2) * 2
                img.putpixel((x, y), tuple(max(0, min(255, v + n)) for v in col) + (255,))
    box(40, 18, 56, 34, (180, 180, 190))  # head
    box(40, 18, 56, 23, (74, 74, 84))  # hair
    box(43, 26, 46, 28, (40, 40, 48)); box(50, 26, 53, 28, (40, 40, 48))
    box(40, 34, 56, 58, (150, 152, 164))  # body
    box(32, 34, 40, 58, (168, 170, 180)); box(56, 34, 64, 58, (168, 170, 180))  # arms
    box(40, 58, 48, 82, (92, 94, 108)); box(48, 58, 56, 82, (84, 86, 100))  # legs
    return img


def frac(v):
    return v - math.floor(v)


def h(i, k):
    return frac(math.sin(i * 12.9898 + k * 78.233) * 43758.5453)


def thumb(style, colours):
    img = mannequin()
    back = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    for i in range(16):
        sp = sprite(style, colours[i % 4]).resize((16, 16), Image.NEAREST) if i % 3 else sprite(style, colours[i % 4]).resize((24, 24), Image.NEAREST)
        ang = h(i, 1) * math.tau
        x = int(48 + math.cos(ang) * (26 + 14 * h(i, 2)) - sp.width / 2)
        y = int(8 + 72 * h(i, 3))
        (back if math.sin(ang) < 0 else img).alpha_composite(sp, (max(0, min(96 - sp.width, x)), max(0, min(96 - sp.height, y))))
    back.alpha_composite(img)
    return back


def main():
    atlas = Image.new("RGBA", (64, 128), (0, 0, 0, 0))
    for row, (style, _n, _p, _r, colours) in enumerate(STYLES):
        for col in range(8):
            atlas.paste(sprite(style, colours[col % 4]), (col * 8, row * 8))
    tex = os.path.join(A, "textures", "cosmetics")
    atlas.save(os.path.join(tex, "aura_atlas.png"), optimize=True)
    os.makedirs(os.path.join(tex, "thumb"), exist_ok=True)

    ip = os.path.join(A, "cosmetics", "index.json")
    idx = [e for e in json.load(open(ip)) if e.get("category") != "Auras"]
    cp = os.path.join(ROOT, "cosmetics-catalogue.json")
    cat = json.load(open(cp))
    cat["cosmetics"] = [e for e in cat["cosmetics"] if e.get("category") != "Auras"]
    for row, (style, name, price, rarity, colours) in enumerate(STYLES):
        cid = "aura_" + style
        thumb(style, colours).save(os.path.join(tex, "thumb", cid + ".png"), optimize=True)
        idx.append({"id": cid, "name": name, "category": "Auras", "geometry": "aura", "texture": "aura_atlas", "anim": "aura:" + style,
                    "credit": "Sleep client (original)", "textureSize": [64, 128]})
        cat["cosmetics"].append({"id": cid, "name": name, "category": "Auras", "price": price, "rarity": rarity, "live_pet": False,
                                 "image": "images/cosmetics/" + cid + ".png"})
    idx = [e for e in idx if e.get("category") != "Minimes"]
    cat["cosmetics"] = [e for e in cat["cosmetics"] if e.get("category") != "Minimes"]
    for where, name, price, rarity, (mx, my, k) in MINIMES:
        cid = "minime_" + where
        img = mannequin()
        small = mannequin().crop((30, 16, 66, 84))
        small = small.resize((int(small.width * k), int(small.height * k)), Image.NEAREST)
        img.alpha_composite(small, (mx, my))
        img.save(os.path.join(tex, "thumb", cid + ".png"), optimize=True)
        idx.append({"id": cid, "name": name, "category": "Minimes", "geometry": "minime", "texture": "aura_atlas", "anim": "minime:" + where,
                    "credit": "Sleep client (original)", "textureSize": [64, 64]})
        cat["cosmetics"].append({"id": cid, "name": name, "category": "Minimes", "price": price, "rarity": rarity, "live_pet": False,
                                 "image": "images/cosmetics/" + cid + ".png"})
    cat["count"] = len(cat["cosmetics"])
    json.dump(idx, open(ip, "w"), indent=1)
    json.dump(cat, open(cp, "w"), indent=1)
    print("wrote", len(STYLES), "auras")


main()
