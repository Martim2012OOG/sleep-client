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


os.makedirs(OUT, exist_ok=True)
make("water_still.png", 16, False)
make("water_flow.png", 32, True)
print("ok")
