#!/usr/bin/env python3
"""Generates the textures of the new Mace Effects (slash arcs, star flares, energy rings, cracks, streaks, shards, dust, glow).
All are grey-scale + alpha, so the client can tint them with the chosen colour. Run: python3 tools/gen_mace_fx.py
Output: src/main/resources/assets/sol_client/textures/particle/mfx_*.png and particles/mfx_*.json"""
import json, math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

N = 256
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sol_client")
TEX = os.path.join(ROOT, "textures", "particle")
PART = os.path.join(ROOT, "particles")
os.makedirs(TEX, exist_ok=True)
os.makedirs(PART, exist_ok=True)

yy, xx = np.mgrid[0:N, 0:N]
X = (xx + 0.5) / N * 2 - 1
Y = 1 - (yy + 0.5) / N * 2
R = np.hypot(X, Y)
TH = np.arctan2(Y, X)


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype("uint8"))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r)), dtype=np.float32) / 255.0


def smooth(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def save(name, lum, alpha):
    lum = np.clip(lum, 0, 1)
    alpha = np.clip(alpha, 0, 1)
    # fade the border so nothing is cut by the square
    edge = smooth(1.0, 0.86, np.maximum(abs(X), abs(Y)))
    alpha = alpha * edge
    rgba = np.dstack([lum, lum, lum, alpha])
    Image.fromarray((rgba * 255).astype("uint8"), "RGBA").save(os.path.join(TEX, name + ".png"))


def particle(name, frames):
    with open(os.path.join(PART, name + ".json"), "w") as f:
        json.dump({"textures": ["sol_client:%s_%d" % (name, i) for i in range(frames)]}, f)


# ------------------------------------------------------------------ slash arc (crescent that is drawn, then dissolves)
def crescent(s, r2, soft):
    xs = X + 0.42  # centre the crescent in the square
    outer = smooth(soft, -soft, np.hypot(xs, Y) - 0.9)  # inside the big circle
    cut = smooth(-soft, soft, np.hypot(xs + s, Y) - r2)  # outside the cutter
    return outer * cut


def slash_frames(prefix, s, r2, frames=8, core=False):
    base = crescent(s, r2, 0.012 if core else 0.02)
    for f in range(frames):
        head = -1.15 + 2.3 * min(1.0, (f + 1) / 4.0)
        tail = -1.15 + 2.3 * max(0.0, (f - 2) / 5.0)
        mask = smooth(0.5, -0.04, Y - head) * smooth(0.0, 0.55, Y - tail)
        # brighter near the head, so it looks like a fast moving blade
        fade = np.clip(1.0 - (head - Y) / 2.0, 0.25, 1.0)
        a = base * mask * fade
        if core:
            lum = np.ones_like(a)
            alpha = np.clip(a * 1.4 + blur(a, 2.5) * 0.7, 0, 1)
        else:
            glow = blur(a, 5) * 1.1 + blur(a, 16) * 0.55
            streak = blur(a * smooth(0.2, 0.9, X), 2.0) * 0.0
            lum = 0.72 + 0.28 * np.clip(blur(a, 3) * 1.6, 0, 1)
            alpha = np.clip(a * 0.85 + glow, 0, 1)
        save("%s_%d" % (prefix, f), lum, alpha)
    particle(prefix, frames)


# ------------------------------------------------------------------ star flare
def star_frames(prefix, frames=3, core=False):
    for f in range(frames):
        k = 1.0 + 0.12 * math.sin(f * 2.1)
        h = np.exp(-np.abs(Y) / (0.016 if core else 0.045)) * np.clip(1 - np.abs(X) / (0.98 * k), 0, 1) ** 1.5
        v = np.exp(-np.abs(X) / (0.016 if core else 0.045)) * np.clip(1 - np.abs(Y) / (0.98 * k), 0, 1) ** 1.5
        u = (X + Y) / math.sqrt(2)
        w = (X - Y) / math.sqrt(2)
        d1 = np.exp(-np.abs(w) / 0.02) * np.clip(1 - np.abs(u) / 0.55, 0, 1) ** 2.4
        d2 = np.exp(-np.abs(u) / 0.02) * np.clip(1 - np.abs(w) / 0.55, 0, 1) ** 2.4
        centre = np.exp(-(R / (0.1 if core else 0.17)) ** 2)
        halo = np.exp(-((R - 0.5) / 0.018) ** 2) * 0.35
        if core:
            a = np.clip(h + v + d1 * 0.5 + d2 * 0.5 + centre * 1.6, 0, 1)
            save("%s_%d" % (prefix, f), np.ones_like(a), np.clip(a, 0, 1))
        else:
            bloom = np.exp(-(R / 0.4) ** 2) * 0.55
            a = np.clip(h * 1.2 + v * 1.2 + d1 * 0.7 + d2 * 0.7 + centre + bloom + halo * 0.6, 0, 1)
            save("%s_%d" % (prefix, f), 0.78 + 0.22 * centre, a)
    particle(prefix, frames)


# ------------------------------------------------------------------ energy ring that lies on the ground
def noise_angle(seed, octaves=5):
    rnd = random.Random(seed)
    n = np.zeros_like(TH)
    for o in range(octaves):
        fr = 3 * (2 ** o)
        n += (0.5 ** o) * np.cos(TH * fr + rnd.random() * 6.28)
    return n / 1.9


def ring_frames(prefix, frames=6):
    for f in range(frames):
        t = f / (frames - 1)
        n = noise_angle(11 + f)
        edge_r = 0.93 - 0.03 * t
        width = 0.018 + 0.03 * (1 - t)
        edge = np.exp(-((R - edge_r) / width) ** 2)
        body = smooth(edge_r - 0.45 + 0.25 * t, edge_r, R) * smooth(edge_r + 0.03, edge_r - 0.005, R) * (0.22 + 0.2 * n)
        lines = np.clip(np.cos(TH * 46 + f * 0.7) * 0.5 + 0.5, 0, 1) ** 6 * smooth(edge_r - 0.35, edge_r, R) * smooth(edge_r + 0.02, edge_r - 0.02, R)
        spikes = np.clip(n, 0, 1) * np.exp(-((R - edge_r - 0.02) / 0.05) ** 2) * 0.5
        a = np.clip(edge * 1.1 + body + lines * 0.5 + spikes, 0, 1) * (1 - 0.35 * t)
        a = np.clip(a + blur(a, 4) * 0.8, 0, 1)
        save("%s_%d" % (prefix, f), 0.85 + 0.15 * edge, a)
    particle(prefix, frames)


# ------------------------------------------------------------------ cracks in the ground (glowing veins, growing)
def crack_frames(prefix, frames=5):
    rnd = random.Random(7)
    segs = []  # (x0,y0,x1,y1,width,birth(0..1))

    def branch(x, y, ang, length, width, birth, depth):
        steps = 6
        px, py = x, y
        for i in range(steps):
            ang += rnd.uniform(-0.35, 0.35)
            l = length / steps
            nx, ny = px + math.cos(ang) * l, py + math.sin(ang) * l
            b = birth + (i + 1) / steps * length * 0.9
            segs.append((px, py, nx, ny, width * (1 - i / (steps * 1.4)), b))
            if depth > 0 and i in (2, 4) and rnd.random() < 0.8:
                branch(nx, ny, ang + rnd.choice([-1, 1]) * rnd.uniform(0.5, 0.9), length * 0.55, width * 0.6, b, depth - 1)
            px, py = nx, ny

    for k in range(9):
        branch(0, 0, math.tau * k / 9 + rnd.uniform(-0.2, 0.2), rnd.uniform(0.55, 0.9), 5.0, 0.0, 2)
    for f in range(frames):
        t = (f + 1) / frames
        im = Image.new("L", (N, N), 0)
        d = ImageDraw.Draw(im)
        for (x0, y0, x1, y1, w, b) in segs:
            if b <= t * 1.05:
                d.line([((x0 + 1) / 2 * N, (1 - y0) / 2 * N), ((x1 + 1) / 2 * N, (1 - y1) / 2 * N)], fill=255, width=max(1, int(w)))
        core = np.asarray(im, dtype=np.float32) / 255.0
        glow = blur(core, 3) * 1.2 + blur(core, 9) * 0.8
        centre = np.exp(-(R / 0.14) ** 2) * (0.6 + 0.4 * t)
        a = np.clip(core + glow * 0.8 + centre, 0, 1)
        save("%s_%d" % (prefix, f), 0.6 + 0.4 * core, a)
    particle(prefix, frames)


# ------------------------------------------------------------------ light streak, shard, glow, dust
def streak(prefix="mfx_streak"):
    a = np.exp(-(X / 0.05) ** 2) * np.clip(1 - np.abs(Y), 0, 1) ** 1.6
    a2 = np.exp(-(X / 0.016) ** 2) * np.clip(1 - np.abs(Y), 0, 1) ** 1.2
    save(prefix + "_0", 0.8 + 0.2 * a2, np.clip(a * 0.7 + a2, 0, 1))
    particle(prefix, 1)


def shard_frames(prefix="mfx_shard", frames=4):
    rnd = random.Random(3)
    for f in range(frames):
        im = Image.new("L", (N * 2, N * 2), 0)
        d = ImageDraw.Draw(im)
        w = rnd.uniform(0.12, 0.22)
        top = rnd.uniform(0.85, 0.98)
        pts = [(0, top), (w, rnd.uniform(0.1, 0.35)), (rnd.uniform(-0.05, 0.05), -rnd.uniform(0.6, 0.95)), (-w * rnd.uniform(0.6, 1.0), rnd.uniform(0.0, 0.3))]
        d.polygon([((px + 1) * N, (1 - py) * N) for px, py in pts], fill=255)
        m = np.asarray(im.resize((N, N), Image.LANCZOS), dtype=np.float32) / 255.0
        edge = np.clip(m - blur(m, 6), 0, 1) * 3
        a = np.clip(m * 0.9 + blur(m, 5) * 0.5, 0, 1)
        save("%s_%d" % (prefix, f), 0.45 + 0.55 * np.clip(edge + (Y + 1) / 2 * 0.3, 0, 1), a)
    particle(prefix, frames)


def glow(prefix="mfx_glow"):
    a = np.exp(-(R / 0.38) ** 2) * 0.9 + np.exp(-(R / 0.14) ** 2) * 0.5
    save(prefix + "_0", np.ones_like(a), a)
    particle(prefix, 1)


def dust_frames(prefix="mfx_dust", frames=4):
    for f in range(frames):
        rnd = np.random.RandomState(40 + f)
        n = np.zeros((N, N), dtype=np.float32)
        for o, s in enumerate((8, 16, 32, 64)):
            g = rnd.rand(s, s).astype("float32")
            im = Image.fromarray((g * 255).astype("uint8")).resize((N, N), Image.BICUBIC)
            n += np.asarray(im, dtype=np.float32) / 255.0 / (2 ** o)
        n = n / 1.9
        a = np.clip((n - 0.25) * 2.0, 0, 1) * smooth(0.95, 0.35, R)
        save("%s_%d" % (prefix, f), 0.55 + 0.35 * n, a * 0.9)
    particle(prefix, frames)


if __name__ == "__main__":
    slash_frames("mfx_slash", 0.55, 1.12)
    slash_frames("mfx_slashcore", 0.50, 1.30, core=True)
    star_frames("mfx_star")
    star_frames("mfx_starcore", core=True)
    ring_frames("mfx_ring")
    crack_frames("mfx_crack")
    streak()
    shard_frames()
    glow()
    dust_frames()
    print("ok")
