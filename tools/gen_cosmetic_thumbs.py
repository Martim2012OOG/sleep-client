"""Little 3D pictures of every bundled cosmetic for the Locker cards (textures/cosmetics/thumb/<id>.png, 96x96, transparent).

Reads each model (assets/sol_client/cosmetics/<geometry>.bin) with its texture and draws it from the front (hats, pets) or from behind
(wings, tails, body). Run:  python3 tools/gen_cosmetic_thumbs.py
"""
import json
import math
import os
import struct

from PIL import Image

A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sol_client")
SIZE = 96


def read_bin(path):
    b = open(path, "rb").read()
    magic, ver, parts = struct.unpack(">iii", b[:12])
    o = 12
    tris = []
    for _ in range(parts):
        o += 1
        n, = struct.unpack(">i", b[o:o + 4])
        o += 4
        fl = struct.unpack(">%df" % (n * 15), b[o:o + n * 60])
        o += n * 60
        for t in range(n):
            tris.append([fl[t * 15 + k * 5:t * 15 + k * 5 + 5] for k in range(3)])
    return tris


def render(tris, tex, yaw, pitch):
    px = tex.load()
    W, H = tex.size
    pts = [p for t in tris for p in t]
    xs, ys, zs = [p[0] for p in pts], [p[1] for p in pts], [p[2] for p in pts]
    cx, cy, cz = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2
    ext = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs), 1e-3)
    sc = SIZE * 0.86 / ext
    cyw, syw, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p[0] - cx, p[1] - cy, p[2] - cz
        x, z = x * cyw - z * syw, x * syw + z * cyw
        y, z = y * cp - z * sp, y * sp + z * cp
        return (SIZE / 2 + x * sc, SIZE / 2 + y * sc, z)

    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    op = out.load()
    zb = [[1e9] * SIZE for _ in range(SIZE)]
    for t in tris:
        P = [proj(p) + (p[3], p[4]) for p in t]
        a, b, c = P
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-9:
            continue
        # light: faces turned to the camera are brighter
        ux, uy, uz = [b[k] - a[k] for k in range(3)]
        vx, vy, vz = [c[k] - a[k] for k in range(3)]
        nz = abs(ux * vy - uy * vx) / (math.sqrt((uy * vz - uz * vy) ** 2 + (uz * vx - ux * vz) ** 2 + (ux * vy - uy * vx) ** 2) + 1e-9)
        shade = 0.68 + 0.32 * nz
        for yy in range(int(max(0, min(a[1], b[1], c[1]))), int(min(SIZE - 1, max(a[1], b[1], c[1]))) + 1):
            for xx in range(int(max(0, min(a[0], b[0], c[0]))), int(min(SIZE - 1, max(a[0], b[0], c[0]))) + 1):
                l1 = ((b[1] - c[1]) * (xx + .5 - c[0]) + (c[0] - b[0]) * (yy + .5 - c[1])) / den
                l2 = ((c[1] - a[1]) * (xx + .5 - c[0]) + (a[0] - c[0]) * (yy + .5 - c[1])) / den
                l3 = 1 - l1 - l2
                if l1 < -1e-4 or l2 < -1e-4 or l3 < -1e-4:
                    continue
                z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                if z >= zb[yy][xx]:
                    continue
                u = l1 * a[3] + l2 * b[3] + l3 * c[3]
                v = l1 * a[4] + l2 * b[4] + l3 * c[4]
                col = px[min(W - 1, max(0, int(u * W))), min(H - 1, max(0, int(v * H)))]
                if col[3] < 40:
                    continue
                zb[yy][xx] = z
                op[xx, yy] = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), 255)
    return out


def main():
    idx = json.load(open(os.path.join(A, "cosmetics", "index.json")))
    os.makedirs(os.path.join(A, "textures", "cosmetics", "thumb"), exist_ok=True)
    for e in idx:
        geo = os.path.join(A, "cosmetics", e["geometry"] + ".bin")
        texp = os.path.join(A, "textures", "cosmetics", e["texture"] + ".png")
        if not (os.path.exists(geo) and os.path.exists(texp)):
            continue
        tris = read_bin(geo)
        if not tris:
            continue
        tex = Image.open(texp).convert("RGBA")
        back = e["category"] in ("Wings", "Tails", "Bodywear")
        img = render(tris, tex, 160 if back else -30, -14)
        img.save(os.path.join(A, "textures", "cosmetics", "thumb", e["id"] + ".png"), optimize=True)
    print("done", len(idx))


main()
