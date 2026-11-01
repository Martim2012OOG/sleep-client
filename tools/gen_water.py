"""Original animated water textures for Realistic Liquids (clear, light and translucent; the biome colour tints them in game).

Run:  python3 tools/gen_water.py
"""
import math, os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "minecraft", "textures", "block")
FR = 32  # frames, the animation loops


def wave(x, y, t, n, flow):
    """Tileable in x and y (n px) and in time (FR frames): a sum of drifting sine ripples + sparkles."""
    u, v = 2 * math.pi * x / n, 2 * math.pi * y / n
    ph = 2 * math.pi * t / FR
    if flow:
        v2 = 2 * math.pi * (y + t * (n / FR)) / n  # streaks slide down one tile per loop
        a = math.sin(2 * u + 0.5) * math.sin(v2) * 0.5 + math.sin(u + 1.3) * math.sin(2 * v2 + 0.4) * 0.35
    else:
        a = (math.sin(u + ph) * 0.35 + math.sin(v - ph) * 0.35 + math.sin(2 * u + v + ph * 2) * 0.28 + math.sin(u - 2 * v - ph) * 0.22)
    return a


def make(name, n, flow):
    im = Image.new("RGBA", (n, n * FR))
    px = im.load()
    for f in range(FR):
        for y in range(n):
            for x in range(n):
                a = wave(x, y, f, n, flow)            # about -1..1
                g = 218 + 24 * a                       # light grey: the biome colour does the blue
                # bright crests (caustics) and a more see-through trough
                crest = max(0.0, a - 0.55) * 2.2
                g = min(255, g + 40 * crest)
                al = 150 + 38 * a + 60 * crest        # translucent: you see the bottom
                px[x, f * n + y] = (int(g * 0.90), int(g * 0.97), int(min(255, g * 1.0)), int(max(110, min(235, al))))
    im.save(os.path.join(OUT, name), optimize=True)
    with open(os.path.join(OUT, name + ".mcmeta"), "w") as fh:
        fh.write('{"animation":{"frametime":%d,"interpolate":true}}' % (2 if flow else 3))


import random
random.seed(11)


def make_caustic(name, n, cells=26):
    """Ultra realistic surface: a web of bright caustic lines (voronoi cell edges) that drift and re-form, over translucent water."""
    pts = [(random.random(), random.random(), random.random() * 6.283, 0.05 + random.random() * 0.07) for _ in range(cells)]
    im = Image.new("RGBA", (n, n * FR))
    px = im.load()
    for f in range(FR):
        ph = 2 * math.pi * f / FR
        P = [((x + math.cos(ph + a) * r) % 1.0, (y + math.sin(ph * 1 + a) * r) % 1.0) for x, y, a, r in pts]
        for y in range(n):
            for x in range(n):
                u, v = x / n, y / n
                d1 = d2 = 9.0
                for (cx, cy) in P:
                    for ox in (-1, 0, 1):
                        for oy in (-1, 0, 1):
                            d = math.hypot(u - (cx + ox), v - (cy + oy))
                            if d < d1:
                                d2, d1 = d1, d
                            elif d < d2:
                                d2 = d
                e = d2 - d1                      # 0 on the cell borders
                line = max(0.0, 1.0 - e / 0.05) ** 1.6
                swell = 0.5 + 0.5 * math.sin(6.283 * (u * 2 + v) + ph)   # slow big wave
                g = 205 + 30 * swell + 20 * line
                al = 118 + 28 * swell + 118 * line
                px[x, f * n + y] = (int(min(255, g * 0.88 + 30 * line)), int(min(255, g * 0.96 + 20 * line)), 255, int(min(240, al)))
    im.save(os.path.join(OUT, name), optimize=True)
    with open(os.path.join(OUT, name + ".mcmeta"), "w") as fh:
        fh.write('{"animation":{"frametime":3,"interpolate":true}}')


os.makedirs(OUT, exist_ok=True)
make_caustic("water_still.png", 64)
make("water_flow.png", 32, True)
print("ok")
