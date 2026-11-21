#!/usr/bin/env python3
"""Original character portraits for the Personas voices (round avatars like voice-changer apps).
Everything here is drawn from scratch (SVG -> PNG with rsvg-convert): no third-party art.
Usage: python3 tools/personas/art.py [out_dir]   (default: the client's textures/persona folder)"""
import math, os, random, subprocess, sys, tempfile

S = 256
OUTLINE = "#14121f"


class Svg:
    def __init__(self, cat):
        self.defs = []
        self.body = []
        self.n = 0
        self.cat = cat

    def id(self, p="g"):
        self.n += 1
        return "%s%d" % (p, self.n)

    def radial(self, stops, cx=0.35, cy=0.3, r=0.8):
        i = self.id("r")
        s = "".join('<stop offset="%s" stop-color="%s"%s/>' % (o, c, (' stop-opacity="%s"' % a) if a is not None else "") for o, c, a in [(t[0], t[1], t[2] if len(t) > 2 else None) for t in stops])
        self.defs.append('<radialGradient id="%s" cx="%s" cy="%s" r="%s">%s</radialGradient>' % (i, cx, cy, r, s))
        return "url(#%s)" % i

    def linear(self, stops, x1=0, y1=0, x2=0, y2=1):
        i = self.id("l")
        s = "".join('<stop offset="%s" stop-color="%s"%s/>' % (t[0], t[1], (' stop-opacity="%s"' % t[2]) if len(t) > 2 else "") for t in stops)
        self.defs.append('<linearGradient id="%s" x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>' % (i, x1, y1, x2, y2, s))
        return "url(#%s)" % i

    def add(self, s):
        self.body.append(s)

    def svg(self):
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d"><defs>%s'
                '<clipPath id="clip"><circle cx="128" cy="128" r="128"/></clipPath>'
                '<filter id="soft" x="-20%%" y="-20%%" width="140%%" height="140%%"><feGaussianBlur stdDeviation="6"/></filter>'
                '<filter id="glow" x="-50%%" y="-50%%" width="200%%" height="200%%"><feGaussianBlur stdDeviation="3"/></filter>'
                '</defs><g clip-path="url(#clip)">%s</g></svg>') % (S, S, S, S, "".join(self.defs), "".join(self.body))


def shade(c, k):
    """lighter (k>0) or darker (k<0) version of a #rrggbb colour"""
    c = c.lstrip("#")
    if len(c) == 3:
        c = "".join(ch * 2 for ch in c)
    r, g, b = int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    if k >= 0:
        r, g, b = [int(v + (255 - v) * k) for v in (r, g, b)]
    else:
        r, g, b = [int(v * (1 + k)) for v in (r, g, b)]
    return "#%02x%02x%02x" % (r, g, b)


# ------------------------------------------------------------------ backgrounds (one mood per category)
BG = {
    "all": ("#3a3f5c", "#151726"), "funny": ("#ffb347", "#c2366a"), "scary": ("#5b2a86", "#0d0717"),
    "fantasy": ("#7b5cff", "#1b1145"), "animals": ("#6fd18a", "#1d5f4a"), "scifi": ("#1fb6ff", "#0a1238"),
    "places": ("#ff8a5c", "#3a1c5c"), "effects": ("#ff4fa3", "#26103f"), "custom": ("#3a3f5c", "#151726"),
}


def background(v, seed):
    a, b = BG.get(v.cat, BG["all"])
    v.add('<rect width="256" height="256" fill="%s"/>' % v.radial([(0, a), (1, b)], 0.5, 0.35, 0.75))
    rnd = random.Random(seed)
    if v.cat == "scary":
        v.add('<circle cx="42" cy="44" r="18" fill="#f4e7c5" opacity="0.8"/>')
        for i in range(5):
            v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="16" fill="#c9b6ff" opacity="0.10" filter="url(#soft)"/>' % (rnd.randint(0, 256), rnd.randint(170, 250), rnd.randint(50, 90)))
    elif v.cat == "scifi":
        for i in range(40):
            v.add('<circle cx="%d" cy="%d" r="%.1f" fill="#ffffff" opacity="%.2f"/>' % (rnd.randint(0, 256), rnd.randint(0, 256), rnd.uniform(0.5, 1.8), rnd.uniform(0.3, 0.9)))
        for k in range(6):
            y = 200 + k * 12
            v.add('<line x1="0" y1="%d" x2="256" y2="%d" stroke="#5be3ff" stroke-opacity="%.2f" stroke-width="1"/>' % (y, y, 0.25 - k * 0.03))
    elif v.cat == "fantasy":
        for i in range(18):
            x, y, r = rnd.randint(0, 256), rnd.randint(0, 200), rnd.uniform(1.5, 4)
            v.add('<path d="M%.1f %.1f l%.1f %.1f l%.1f %.1f l%.1f %.1f z" fill="#fff6c4" opacity="0.8"/>' % (x, y - r * 2, r * .6, r * 1.4, r * 1.4, r * .6, -r * 1.4, r * .6))
    elif v.cat == "funny":
        cols = ["#fff36b", "#7cf7ff", "#ff6bd6", "#ffffff", "#8bff7c"]
        for i in range(26):
            v.add('<rect x="%d" y="%d" width="6" height="10" rx="2" fill="%s" opacity="0.7" transform="rotate(%d %d %d)"/>' % (
                rnd.randint(0, 250), rnd.randint(0, 250), rnd.choice(cols), rnd.randint(0, 180), rnd.randint(0, 250), rnd.randint(0, 250)))
    elif v.cat == "animals":
        for i in range(7):
            x, y = rnd.randint(-20, 270), rnd.randint(150, 260)
            v.add('<ellipse cx="%d" cy="%d" rx="34" ry="22" fill="#2f8f5a" opacity="0.55"/>' % (x, y))
    elif v.cat == "places":
        for k in range(7):
            ang = -60 + k * 20
            v.add('<path d="M128 -20 L%.0f 280 L%.0f 280 z" fill="#ffffff" opacity="0.05"/>' % (128 + 300 * math.tan(math.radians(ang - 4)), 128 + 300 * math.tan(math.radians(ang + 4))))
    elif v.cat == "effects":
        for k in range(5):
            v.add('<circle cx="128" cy="128" r="%d" fill="none" stroke="#ffffff" stroke-opacity="%.2f" stroke-width="3"/>' % (60 + k * 22, 0.18 - k * 0.03))
    # soft spotlight behind the character
    v.add('<circle cx="128" cy="120" r="96" fill="%s"/>' % v.radial([(0, "#ffffff", 0.22), (1, "#ffffff", 0)], 0.5, 0.5, 0.5))


# ------------------------------------------------------------------ building blocks
def shoulders(v, col, collar=None):
    v.add('<path d="M28 262 C34 196 80 184 128 184 C176 184 222 196 228 262 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (
        v.radial([(0, shade(col, .25)), (1, shade(col, -.35))], .35, .2, .9), OUTLINE))
    if collar:
        v.add('<path d="M96 188 L128 222 L160 188" fill="none" stroke="%s" stroke-width="6" stroke-linecap="round"/>' % collar)


def head(v, col, shape="round", cx=128, cy=118, r=60):
    fill = v.radial([(0, shade(col, .35)), (.6, col), (1, shade(col, -.35))], .38, .3, .8)
    if shape == "round":
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="%s" stroke="%s" stroke-width="4"/>' % (cx, cy, r, int(r * 1.04), fill, OUTLINE))
    elif shape == "square":
        v.add('<rect x="%d" y="%d" width="%d" height="%d" rx="%d" fill="%s" stroke="%s" stroke-width="4"/>' % (cx - r, cy - r, r * 2, int(r * 2.05), r // 3, fill, OUTLINE))
    elif shape == "egg":
        v.add('<path d="M%d %d C%d %d %d %d %d %d C%d %d %d %d %d %d Z" fill="%s" stroke="%s" stroke-width="4"/>' % (
            cx, cy - r * 1.15, cx + r * 1.15, cy - r * 1.1, cx + r, cy + r, cx, cy + r * 1.05, cx - r, cy + r, cx - r * 1.15, cy - r * 1.1, cx, cy - r * 1.15, fill, OUTLINE))
    elif shape == "wide":
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="%s" stroke="%s" stroke-width="4"/>' % (cx, cy, int(r * 1.22), int(r * .9), fill, OUTLINE))
    # rim light
    v.add('<path d="M%d %d A%d %d 0 0 1 %d %d" fill="none" stroke="#ffffff" stroke-opacity="0.35" stroke-width="4" stroke-linecap="round"/>' % (
        cx - r * .62, cy - r * .62, r, r, cx + r * .1, cy - r * .92))


def eyes(v, cx=128, cy=112, gap=24, size=13, iris="#3b82f6", mood="happy", pupil=0.5, white="#ffffff", lids=None):
    for s in (-1, 1):
        x = cx + s * gap
        if mood == "closed":
            v.add('<path d="M%d %d q%d %d %d 0" fill="none" stroke="%s" stroke-width="4.5" stroke-linecap="round"/>' % (x - size, cy, size, size * .8, size * 2, OUTLINE))
            continue
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="%s" stroke="%s" stroke-width="3.5"/>' % (x, cy, size, int(size * 1.15), white, OUTLINE))
        v.add('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s"/>' % (x + s * 1.5, cy + 2, size * .62, v.radial([(0, shade(iris, .4)), (1, shade(iris, -.3))], .4, .35, .7)))
        v.add('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="#0b0b12"/>' % (x + s * 1.5, cy + 2, size * .62 * pupil))
        v.add('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="#ffffff"/>' % (x - size * .2, cy - size * .25, size * .22))
        v.add('<circle cx="%.1f" cy="%.1f" r="%.1f" fill="#ffffff" opacity="0.8"/>' % (x + size * .25, cy + size * .35, size * .1))
        if mood == "angry":
            v.add('<path d="M%d %d L%d %d" stroke="%s" stroke-width="6" stroke-linecap="round"/>' % (x - s * size * 1.2, cy - size * 1.6, x + s * size * .6, cy - size * .7, OUTLINE))
        elif mood == "sleepy":
            v.add('<path d="M%d %d a%d %d 0 0 1 %d 0 z" fill="%s" stroke="%s" stroke-width="3"/>' % (x - size, cy, size, size * 1.1, size * 2, lids or "#d9b48a", OUTLINE))
        elif mood == "sly":
            v.add('<path d="M%d %d L%d %d" stroke="%s" stroke-width="5" stroke-linecap="round"/>' % (x - size * 1.1, cy - size * .9 - s * 2, x + size * 1.1, cy - size * .9 + s * 2, OUTLINE))
        elif mood == "happy":
            v.add('<path d="M%d %d q%d %d %d 0" fill="none" stroke="%s" stroke-width="4" stroke-linecap="round"/>' % (x - size * .9, cy - size * 1.6, size * .9, -size * .5, size * 1.8, OUTLINE))


def mouth(v, kind="smile", cx=128, cy=150, w=24, teeth=False, tongue=True):
    if kind == "smile":
        v.add('<path d="M%d %d Q%d %d %d %d" fill="none" stroke="%s" stroke-width="5" stroke-linecap="round"/>' % (cx - w, cy - 4, cx, cy + w * .6, cx + w, cy - 4, OUTLINE))
    elif kind == "grin":
        v.add('<path d="M%d %d Q%d %d %d %d Q%d %d %d %d Z" fill="#5a1730" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (
            cx - w, cy - 6, cx, cy - 2, cx + w, cy - 6, cx, cy + w * 1.1, cx - w, cy - 6, OUTLINE))
        if teeth:
            v.add('<path d="M%d %d Q%d %d %d %d L%d %d Q%d %d %d %d Z" fill="#ffffff"/>' % (cx - w + 4, cy - 4, cx, cy, cx + w - 4, cy - 4, cx + w - 8, cy + 3, cx, cy + 6, cx - w + 8, cy + 3))
        if tongue:
            v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="#ff6b8a"/>' % (cx, cy + w * .62, w * .5, w * .25))
    elif kind == "o":
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="#5a1730" stroke="%s" stroke-width="4"/>' % (cx, cy, w * .45, w * .55, OUTLINE))
    elif kind == "frown":
        v.add('<path d="M%d %d Q%d %d %d %d" fill="none" stroke="%s" stroke-width="5" stroke-linecap="round"/>' % (cx - w, cy + 6, cx, cy - w * .5, cx + w, cy + 6, OUTLINE))
    elif kind == "fangs":
        v.add('<path d="M%d %d Q%d %d %d %d Q%d %d %d %d Z" fill="#3a0d16" stroke="%s" stroke-width="4"/>' % (cx - w, cy - 6, cx, cy, cx + w, cy - 6, cx, cy + w * .9, cx - w, cy - 6, OUTLINE))
        for s in (-1, 1):
            v.add('<path d="M%d %d l%d %d l%d %d z" fill="#ffffff" stroke="%s" stroke-width="2"/>' % (cx + s * w * .5 - 5, cy - 4, 5, 14, 5, -14, OUTLINE))
    elif kind == "grille":
        v.add('<rect x="%d" y="%d" width="%d" height="%d" rx="6" fill="#1b2433" stroke="%s" stroke-width="4"/>' % (cx - w, cy - 10, w * 2, 20, OUTLINE))
        for k in range(5):
            x = cx - w + 6 + k * (w * 2 - 12) / 4
            v.add('<line x1="%.1f" y1="%d" x2="%.1f" y2="%d" stroke="#5be3ff" stroke-width="3"/>' % (x, cy - 6, x, cy + 6))
    elif kind == "zigzag":
        pts = " ".join("%d,%d" % (cx - w + k * w / 3, cy + (6 if k % 2 else -6)) for k in range(7))
        v.add('<polyline points="%s" fill="none" stroke="%s" stroke-width="5" stroke-linejoin="round" stroke-linecap="round"/>' % (pts, OUTLINE))
    elif kind == "flat":
        v.add('<path d="M%d %d L%d %d" stroke="%s" stroke-width="5" stroke-linecap="round"/>' % (cx - w * .7, cy, cx + w * .7, cy, OUTLINE))
    elif kind == "shh":
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="#5a1730" stroke="%s" stroke-width="4"/>' % (cx, cy, 8, 6, OUTLINE))


def cheeks(v, cx=128, cy=136, gap=40, col="#ff7a9c"):
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="%d" rx="11" ry="7" fill="%s" opacity="0.55"/>' % (cx + s * gap, cy, col))


def ears_round(v, col, cx=128, cy=118, dx=56, dy=-40, r=20, inner="#ffb3c7"):
    for s in (-1, 1):
        x, y = cx + s * dx, cy + dy
        v.add('<circle cx="%d" cy="%d" r="%d" fill="%s" stroke="%s" stroke-width="4"/>' % (x, y, r, v.radial([(0, shade(col, .3)), (1, shade(col, -.3))]), OUTLINE))
        v.add('<circle cx="%d" cy="%d" r="%d" fill="%s"/>' % (x, y, r * .55, inner))


def ears_pointy(v, col, cx=128, cy=118, inner="#ffb3c7", h=46, spread=44, base=70):
    for s in (-1, 1):
        v.add('<path d="M%d %d L%d %d L%d %d Z" fill="%s" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (
            cx + s * spread, cy - base + 30, cx + s * (spread + 18), cy - base - h + 30, cx + s * (spread - 30), cy - base + 12, v.radial([(0, shade(col, .3)), (1, shade(col, -.3))]), OUTLINE))
        v.add('<path d="M%d %d L%d %d L%d %d Z" fill="%s"/>' % (cx + s * (spread - 2), cy - base + 26, cx + s * (spread + 12), cy - base - h + 42, cx + s * (spread - 20), cy - base + 16, inner))


def nose(v, cx=128, cy=132, col="#e29b7a", r=7):
    v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="%s" stroke="%s" stroke-width="3"/>' % (cx, cy, r, r * .8, shade(col, -.15), OUTLINE))


def hair(v, col, style="tuft", cx=128, cy=118):
    fill = v.radial([(0, shade(col, .3)), (1, shade(col, -.35))], .4, .2, .9)
    if style == "tuft":
        v.add('<path d="M80 92 C84 50 120 44 132 52 C146 40 178 56 176 92 C160 76 140 74 128 80 C112 72 94 78 80 92 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
    elif style == "curl":
        v.add('<path d="M120 60 c-4 -18 18 -24 22 -10 c3 10 -10 14 -12 6" fill="none" stroke="%s" stroke-width="5" stroke-linecap="round"/>' % OUTLINE)
    elif style == "bun":
        v.add('<circle cx="128" cy="54" r="20" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<path d="M70 110 C66 60 100 56 128 58 C156 56 190 60 186 110 C176 84 150 76 128 78 C106 76 80 84 70 110 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
    elif style == "side":
        v.add('<path d="M70 120 C62 70 96 52 128 54 C164 52 196 72 186 120 C178 96 170 86 160 82 C140 92 108 92 92 84 C84 92 76 104 70 120 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
    elif style == "long":
        v.add('<path d="M66 200 C50 120 70 54 128 52 C186 54 206 120 190 200 L172 200 C178 140 170 100 160 86 C140 96 112 96 96 86 C86 100 78 140 84 200 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
    elif style == "bald_sides":
        for s in (-1, 1):
            v.add('<path d="M%d 120 c%d -24 %d -36 %d -40" fill="none" stroke="%s" stroke-width="12" stroke-linecap="round"/>' % (128 + s * 58, s * 4, s * -2, s * -10, col))


def beard(v, col="#f2f2f2", cx=128, cy=150):
    v.add('<path d="M70 128 C72 210 112 226 128 226 C144 226 184 210 186 128 C170 158 150 164 128 164 C106 164 86 158 70 128 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (
        v.radial([(0, "#ffffff"), (1, shade(col, -.15))], .5, .3, .8), OUTLINE))


def hat(v, kind, col):
    fill = v.radial([(0, shade(col, .3)), (1, shade(col, -.35))], .35, .2, .9)
    if kind == "cowboy":
        v.add('<ellipse cx="128" cy="78" rx="96" ry="18" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<path d="M84 80 C84 30 104 26 128 38 C152 26 172 30 172 80 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<path d="M86 70 L170 70" stroke="#3b2a1a" stroke-width="7"/>')
    elif kind == "santa":
        v.add('<path d="M74 84 C82 30 150 20 186 60 C196 72 200 90 196 104 L178 92 C176 72 160 60 140 58 C120 58 96 66 86 86 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<circle cx="198" cy="106" r="13" fill="#ffffff" stroke="%s" stroke-width="3"/>' % OUTLINE)
        v.add('<rect x="66" y="78" width="128" height="22" rx="11" fill="#ffffff" stroke="%s" stroke-width="4"/>' % OUTLINE)
    elif kind == "wizard":
        v.add('<path d="M70 86 L132 -6 L186 86 Z" fill="%s" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (fill, OUTLINE))
        v.add('<ellipse cx="128" cy="86" rx="84" ry="14" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        for x, y in ((120, 40), (146, 62), (106, 70)):
            v.add('<path d="M%d %d l3 7 7 3 -7 3 -3 7 -3 -7 -7 -3 7 -3z" fill="#ffe066"/>' % (x, y))
    elif kind == "pirate":
        v.add('<path d="M58 86 C70 40 186 40 198 86 C170 76 86 76 58 86 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<circle cx="128" cy="66" r="9" fill="#ffffff"/><path d="M118 78 l20 -6 M118 72 l20 6" stroke="#ffffff" stroke-width="3"/>')
    elif kind == "crown":
        v.add('<path d="M84 76 L92 40 L110 62 L128 32 L146 62 L164 40 L172 76 Z" fill="%s" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (
            v.radial([(0, "#fff3a0"), (1, "#d99a00")]), OUTLINE))
    elif kind == "beanie":
        v.add('<path d="M72 100 C70 44 186 44 184 100 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<rect x="68" y="90" width="120" height="20" rx="8" fill="%s" stroke="%s" stroke-width="4"/>' % (shade(col, -.2), OUTLINE))
    elif kind == "tophat":
        v.add('<ellipse cx="128" cy="74" rx="64" ry="12" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<rect x="96" y="22" width="64" height="54" rx="6" fill="%s" stroke="%s" stroke-width="4"/>' % (fill, OUTLINE))
        v.add('<rect x="96" y="60" width="64" height="9" fill="#d33"/>')


def headphones(v, col="#2a2f45"):
    v.add('<path d="M62 120 C62 40 194 40 194 120" fill="none" stroke="%s" stroke-width="10"/>' % OUTLINE)
    v.add('<path d="M62 120 C62 40 194 40 194 120" fill="none" stroke="%s" stroke-width="5"/>' % col)
    for s in (-1, 1):
        v.add('<rect x="%d" y="104" width="22" height="40" rx="10" fill="%s" stroke="%s" stroke-width="4"/>' % (128 + s * 66 - 11, col, OUTLINE))


def sparkle(v, x, y, r=8, col="#ffffff"):
    v.add('<path d="M%d %d l%.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %.1fz" fill="%s"/>' % (
        x, y - r, r * .3, r * .7, r * .7, r * .3, -r * .7, r * .3, -r * .3, r * .7, -r * .3, -r * .7, -r * .7, -r * .3, r * .7, -r * .3, col))


SKIN = "#f2c29b"


# ------------------------------------------------------------------ the characters
def person(v, skin=SKIN, shirt="#4f6bd8", hair_col="#5a3a22", hair_style="tuft", mood="happy", mouth_kind="smile", iris="#5b3a1e", collar=None, cheek=True, r=58):
    shoulders(v, shirt, collar)
    head(v, skin, "round", r=r)
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="122" rx="9" ry="13" fill="%s" stroke="%s" stroke-width="4"/>' % (128 + s * r, skin, OUTLINE))
    if hair_style:
        hair(v, hair_col, hair_style)
    eyes(v, iris=iris, mood=mood, size=11, gap=22, lids=shade(skin, -.08))
    nose(v, col=skin, r=6)
    mouth(v, mouth_kind, w=20)
    if cheek:
        cheeks(v, gap=36, cy=140)


def draw(vid, cat):
    v = Svg(cat)
    background(v, hash(vid) & 0xFFFF)
    D = DRAW.get(vid)
    if D:
        D(v)
    return v.svg()


def d_me(v):
    person(v, shirt="#5b67f0", hair_col="#3b2a1e", hair_style="side", mouth_kind="grin")


def d_sponge(v):
    # an original kitchen-sponge buddy: turquoise sponge, green scrub layer on top, pores, googly eyes
    shoulders(v, "#2c8ea8")
    v.add('<rect x="58" y="56" width="140" height="150" rx="22" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#9ff0e8"), (1, "#1f9e9a")], .35, .25, .9), OUTLINE))
    v.add('<rect x="58" y="40" width="140" height="34" rx="14" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#b6f56b"), (1, "#3f9a2a")]), OUTLINE))
    rnd = random.Random(3)
    for i in range(16):
        x, y = rnd.randint(70, 186), rnd.randint(84, 196)
        if 90 < x < 166 and 96 < y < 168:
            continue
        v.add('<ellipse cx="%d" cy="%d" rx="%d" ry="%d" fill="#147a78" opacity="0.6"/>' % (x, y, rnd.randint(3, 7), rnd.randint(3, 6)))
    eyes(v, cy=116, gap=26, size=17, iris="#ff8a00", mood="none", pupil=.6)
    mouth(v, "grin", cy=160, w=30, teeth=False)
    cheeks(v, gap=48, cy=150, col="#ff6b8a")


def d_chipmunk(v):
    shoulders(v, "#b5733f")
    ears_round(v, "#b5733f", dx=50, dy=-50, r=16)
    head(v, "#c98b52", "round", r=58)
    v.add('<ellipse cx="128" cy="146" rx="42" ry="30" fill="#f5dcb8"/>')
    for x in (100, 156):
        v.add('<path d="M%d 66 l0 34" stroke="#6b3d1e" stroke-width="6" stroke-linecap="round"/>' % x)
    eyes(v, iris="#2b1b10", mood="none", size=12, gap=24, pupil=.8)
    nose(v, cy=134, col="#5a2a1a", r=6)
    mouth(v, "grin", cy=150, w=16)
    v.add('<rect x="120" y="148" width="16" height="12" rx="2" fill="#ffffff" stroke="%s" stroke-width="3"/>' % OUTLINE)


def d_helium(v):
    v.add('<path d="M128 196 C120 220 140 236 128 262" stroke="#ffffff" stroke-width="3" fill="none"/>')
    v.add('<ellipse cx="128" cy="112" rx="68" ry="80" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#ffb3c8"), (.5, "#ff3d71"), (1, "#a1123b")], .35, .25, .8), OUTLINE))
    v.add('<path d="M120 192 l8 10 l8 -10 z" fill="#c2184b" stroke="%s" stroke-width="3"/>' % OUTLINE)
    v.add('<ellipse cx="100" cy="70" rx="14" ry="22" fill="#ffffff" opacity="0.45" transform="rotate(-25 100 70)"/>')
    eyes(v, cy=104, gap=24, size=12, iris="#6b2bd9", mood="happy")
    mouth(v, "o", cy=146, w=24)


def d_clown(v):
    shoulders(v, "#ffd43b", "#ff3b6b")
    for s in (-1, 1):
        for k in range(3):
            v.add('<circle cx="%d" cy="%d" r="18" fill="%s" stroke="%s" stroke-width="3"/>' % (128 + s * (60 + k * 6), 84 + k * 22, ["#ff4d4d", "#3bc9ff", "#ffd43b"][k], OUTLINE))
    head(v, "#fff7ef", "round", r=56)
    eyes(v, iris="#2b6bff", mood="happy", size=11, gap=22)
    for s in (-1, 1):
        v.add('<path d="M%d 92 l0 10 M%d 128 l0 10" stroke="#3b5bff" stroke-width="4" stroke-linecap="round"/>' % (128 + s * 22, 128 + s * 22))
    v.add('<circle cx="128" cy="134" r="12" fill="%s" stroke="%s" stroke-width="3"/>' % (v.radial([(0, "#ff9a9a"), (1, "#d10f2f")]), OUTLINE))
    v.add('<path d="M96 150 Q128 186 160 150 Q128 166 96 150 Z" fill="#e8173f" stroke="%s" stroke-width="3"/>' % OUTLINE)
    hat(v, "tophat", "#5a2bd9")


def d_baby(v):
    person(v, skin="#ffd8bd", shirt="#8fd3ff", hair_col="#a0703a", hair_style="curl", mood="none", mouth_kind="o", iris="#3b82f6", r=60)
    v.add('<circle cx="128" cy="176" r="12" fill="#ff9ec4" stroke="%s" stroke-width="3"/><rect x="118" y="168" width="20" height="8" rx="4" fill="#fff"/>' % OUTLINE)


def d_kid(v):
    person(v, shirt="#ff8a3d", hair_col="#6b3e1f", hair_style="tuft", mouth_kind="grin", iris="#3a7d2b")
    hat(v, "beanie", "#3b82f6")


def d_grandpa(v):
    person(v, skin="#f0c3a0", shirt="#7a6a55", hair_col="#e6e6e6", hair_style="bald_sides", mood="sleepy", mouth_kind="smile", iris="#4a6b8a")
    v.add('<path d="M96 156 C108 146 148 146 160 156 C148 152 108 152 96 156 Z" fill="#ffffff" stroke="%s" stroke-width="3"/>' % OUTLINE)
    for s in (-1, 1):
        v.add('<circle cx="%d" cy="112" r="16" fill="none" stroke="#3b3b3b" stroke-width="4"/>' % (128 + s * 22))
    v.add('<path d="M122 112 L134 112" stroke="#3b3b3b" stroke-width="4"/>')


def d_grandma(v):
    person(v, skin="#f2c6a4", shirt="#c06bd8", hair_col="#eeeeee", hair_style="bun", mood="happy", mouth_kind="smile", iris="#5a7aa0")
    for s in (-1, 1):
        v.add('<circle cx="%d" cy="112" r="15" fill="none" stroke="#b07a2a" stroke-width="3.5"/>' % (128 + s * 22))
    v.add('<path d="M100 194 q28 16 56 0" stroke="#ffffff" stroke-width="5" stroke-dasharray="1 9" stroke-linecap="round" fill="none"/>')


def d_dizzy(v):
    head(v, "#ffcf4a", "round", r=64)
    for s in (-1, 1):
        x = 128 + s * 24
        v.add('<path d="M%d 112 m-12 0 a12 12 0 1 1 12 12 a8 8 0 1 1 -8 -8 a4 4 0 1 1 4 4" fill="none" stroke="%s" stroke-width="4" stroke-linecap="round"/>' % (x, OUTLINE))
    mouth(v, "zigzag", cy=156, w=20)
    for a in range(0, 360, 60):
        x, y = 128 + 84 * math.cos(math.radians(a)), 60 + 18 * math.sin(math.radians(a))
        sparkle(v, int(x), int(y), 9, "#fff36b")


def d_fast(v):
    for k in range(4):
        v.add('<path d="M10 %d L80 %d" stroke="#ffffff" stroke-opacity="%.2f" stroke-width="6" stroke-linecap="round"/>' % (90 + k * 26, 90 + k * 26, .5 - k * .1))
    v.add('<path d="M146 20 L80 136 L124 136 L100 236 L184 104 L138 104 L170 20 Z" fill="%s" stroke="%s" stroke-width="5" stroke-linejoin="round"/>' % (
        v.linear([(0, "#fff7a8"), (1, "#ffb100")]), OUTLINE))
    eyes(v, cx=134, cy=92, gap=14, size=8, iris="#ff3d00", mood="angry")
    mouth(v, "grin", cx=128, cy=128, w=12, tongue=False)


def d_slow(v):
    v.add('<ellipse cx="128" cy="232" rx="110" ry="30" fill="#2a6a3a"/>')
    v.add('<ellipse cx="150" cy="170" rx="72" ry="52" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#c9a35b"), (1, "#6b4a1e")]), OUTLINE))
    for x, y in ((130, 150), (170, 150), (150, 186), (110, 182), (190, 182)):
        v.add('<path d="M%d %d l14 8 v16 l-14 8 l-14 -8 v-16 z" fill="#8a6a2e" stroke="#4a3210" stroke-width="3"/>' % (x, y - 16))
    head(v, "#8fd16b", "round", cx=78, cy=128, r=36)
    eyes(v, cx=78, cy=120, gap=13, size=8, iris="#2b1b10", mood="sleepy", lids="#7bbd5a")
    mouth(v, "smile", cx=80, cy=140, w=12)


def d_cowboy(v):
    person(v, skin="#e8b48a", shirt="#c0392b", hair_col="#7a4a22", hair_style=None, mood="sly", mouth_kind="smile", collar="#f2d16b")
    hat(v, "cowboy", "#a5692e")
    v.add('<path d="M100 186 L128 206 L156 186 L128 196 Z" fill="#2a63c9" stroke="%s" stroke-width="3"/>' % OUTLINE)


def d_pirate(v):
    person(v, skin="#e3ad84", shirt="#f3f3f3", hair_col="#2a1a10", hair_style=None, mood="sly", mouth_kind="grin", iris="#2b5a7a")
    v.add('<path d="M84 102 L170 92" stroke="%s" stroke-width="4"/>' % OUTLINE)
    v.add('<ellipse cx="150" cy="112" rx="15" ry="13" fill="#141414" stroke="%s" stroke-width="3"/>' % OUTLINE)
    beard(v, "#3a2312")
    mouth(v, "grin", cy=150, w=18)
    hat(v, "pirate", "#2a2a35")
    v.add('<path d="M40 262 L80 200 L128 230 L176 200 L216 262 Z" fill="#c0392b" opacity="0.9"/>')


def d_santa(v):
    person(v, skin="#f5c7a5", shirt="#d62828", hair_col="#ffffff", hair_style=None, mood="happy", mouth_kind="smile", iris="#3b6bd8")
    beard(v)
    v.add('<path d="M96 150 C108 140 124 146 128 150 C132 146 148 140 160 150 C148 158 136 154 128 156 C120 154 108 158 96 150 Z" fill="#ffffff" stroke="%s" stroke-width="3"/>' % OUTLINE)
    nose(v, cy=136, col="#f08a7a", r=8)
    hat(v, "santa", "#d62828")


def d_snowman(v):
    v.add('<circle cx="128" cy="230" r="64" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#ffffff"), (1, "#b9d4ff")]), OUTLINE))
    head(v, "#f4f9ff", "round", cy=122, r=56)
    v.add('<path d="M74 168 Q128 196 182 168 L182 184 Q128 208 74 184 Z" fill="#e63946" stroke="%s" stroke-width="3"/>' % OUTLINE)
    eyes(v, cy=110, gap=22, size=8, iris="#111111", mood="none", pupil=1)
    v.add('<path d="M128 126 L176 134 L128 140 Z" fill="#ff8c1a" stroke="%s" stroke-width="3" stroke-linejoin="round"/>' % OUTLINE)
    for k in range(5):
        v.add('<circle cx="%d" cy="%d" r="3.5" fill="#222"/>' % (102 + k * 13, 156 + abs(k - 2) * -3 + 6))
    hat(v, "tophat", "#232336")


def d_hacker(v):
    shoulders(v, "#1f2230")
    v.add('<path d="M50 230 C40 120 70 36 128 34 C186 36 216 120 206 230 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#3a3f55"), (1, "#0e1018")], .4, .25, .9), OUTLINE))
    v.add('<ellipse cx="128" cy="130" rx="54" ry="66" fill="#05060a"/>')
    for s in (-1, 1):
        v.add('<rect x="%d" y="112" width="30" height="12" rx="6" fill="#39ff88" filter="url(#glow)"/>' % (128 + s * 26 - 15))
        v.add('<rect x="%d" y="114" width="26" height="8" rx="4" fill="#b6ffd2"/>' % (128 + s * 26 - 13))
    v.add('<path d="M108 158 L148 158" stroke="#39ff88" stroke-width="4" stroke-linecap="round" opacity="0.8"/>')
    rnd = random.Random(7)
    for i in range(24):
        v.add('<text x="%d" y="%d" fill="#39ff88" opacity="%.2f" font-family="monospace" font-size="12">%s</text>' % (rnd.randint(0, 240), rnd.randint(10, 250), rnd.uniform(.15, .45), rnd.choice("01")))


def d_demon(v):
    shoulders(v, "#3a0d1a")
    for s in (-1, 1):
        v.add('<path d="M%d 76 C%d 40 %d 24 %d 10 C%d 40 %d 62 %d 86 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (
            128 + s * 40, 128 + s * 56, 128 + s * 70, 128 + s * 86, 128 + s * 76, 128 + s * 64, 128 + s * 54, v.linear([(0, "#fff3d6"), (1, "#c9a36b")]), OUTLINE))
    head(v, "#e5383b", "round", r=60)
    eyes(v, iris="#ffd60a", mood="angry", size=12, gap=24, white="#fff3b0", pupil=.35)
    mouth(v, "fangs", cy=152, w=26)


def d_villain(v):
    shoulders(v, "#1a1a24")
    v.add('<path d="M20 262 L60 170 L128 200 L196 170 L236 262 Z" fill="#6a0f1f" stroke="%s" stroke-width="4"/>' % OUTLINE)
    head(v, "#2a2d3a", "egg", r=58)
    v.add('<path d="M78 104 C96 92 160 92 178 104 L170 126 C150 118 106 118 86 126 Z" fill="#0b0c12" stroke="#5a5f78" stroke-width="2"/>')
    for s in (-1, 1):
        v.add('<path d="M%d 110 l%d 0 l%d 8 z" fill="#ff2a3d" filter="url(#glow)"/>' % (128 + s * 12, s * 34, s * -6))
    for k in range(4):
        v.add('<rect x="%d" y="150" width="8" height="24" rx="3" fill="#5a5f78"/>' % (110 + k * 10))


def d_monster(v):
    shoulders(v, "#3b7a2a")
    head(v, "#5bbf3a", "wide", r=64)
    for s in (-1, 1):
        v.add('<path d="M%d 70 l%d -30 l%d 34 z" fill="#f4e3b5" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (128 + s * 40, s * 14, s * 6, OUTLINE))
    eyes(v, cx=128, cy=100, gap=0, size=22, iris="#ff5a1f", mood="none", pupil=.4)
    v.add('<path d="M74 140 Q128 210 182 140 Q128 160 74 140 Z" fill="#2a0d12" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for k in range(6):
        x = 86 + k * 17
        v.add('<path d="M%d %d l7 14 l7 -14 z" fill="#ffffff" stroke="%s" stroke-width="2"/>' % (x, 144 + abs(k - 2.5) * 2, OUTLINE))


def d_goblin(v):
    shoulders(v, "#6b4a2a")
    ears_pointy(v, "#7cc24a", inner="#b3e07a", h=20, spread=62, base=40)
    head(v, "#7cc24a", "round", r=54)
    eyes(v, iris="#ffcc00", mood="sly", size=11, gap=22, white="#fff9c4")
    v.add('<path d="M128 118 C150 128 156 146 128 146 Z" fill="#6aa83a" stroke="%s" stroke-width="3"/>' % OUTLINE)
    mouth(v, "grin", cy=158, w=22, teeth=True, tongue=False)


def d_zombie(v):
    shoulders(v, "#5a6b7a")
    head(v, "#9fc48a", "round", r=58)
    hair(v, "#3a2a20", "tuft")
    v.add('<path d="M150 80 l10 10 l-6 6 l10 10" stroke="#5a2a2a" stroke-width="3" fill="none"/>')
    eyes(v, iris="#c9ff4a", mood="sleepy", size=12, gap=22, lids="#88ad74", white="#f6ffe0", pupil=.3)
    mouth(v, "zigzag", cy=154, w=20)
    v.add('<rect x="70" y="130" width="22" height="8" rx="3" fill="#e6d3b3" transform="rotate(-20 81 134)"/>')


def d_vampire(v):
    shoulders(v, "#151520", "#d01f3a")
    v.add('<path d="M30 262 L54 150 L100 196 L128 186 L156 196 L202 150 L226 262 Z" fill="#7a0f24" stroke="%s" stroke-width="4"/>' % OUTLINE)
    head(v, "#e7e2f2", "egg", r=54)
    v.add('<path d="M76 92 C80 44 176 44 180 92 L128 74 Z" fill="#141420" stroke="%s" stroke-width="4"/>' % OUTLINE)
    eyes(v, iris="#d01f3a", mood="sly", size=11, gap=22)
    mouth(v, "fangs", cy=152, w=20)


def d_ghost(v):
    v.add('<path d="M58 250 L58 120 C58 50 198 50 198 120 L198 250 L176 232 L154 250 L128 232 L102 250 L80 232 Z" fill="%s" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (
        v.radial([(0, "#ffffff"), (.7, "#e3e6ff"), (1, "#a9b0e6")], .4, .3, .8), OUTLINE))
    eyes(v, cy=118, gap=24, size=14, iris="#2a2a5a", mood="none", pupil=.9, white="#20203a")
    mouth(v, "o", cy=160, w=26)
    cheeks(v, gap=46, cy=148, col="#a9b8ff")


def d_skeleton(v):
    shoulders(v, "#2a2a35")
    for k in range(4):
        v.add('<path d="M84 %d Q128 %d 172 %d" stroke="#e8e8e8" stroke-width="7" fill="none" stroke-linecap="round"/>' % (200 + k * 14, 210 + k * 14, 200 + k * 14))
    head(v, "#f2f0e6", "round", r=58)
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="112" rx="18" ry="20" fill="#14121f"/>' % (128 + s * 24))
        v.add('<circle cx="%d" cy="110" r="4" fill="#7af0ff"/>' % (128 + s * 24))
    v.add('<path d="M120 140 l8 -12 l8 12 z" fill="#14121f"/>')
    for k in range(6):
        v.add('<rect x="%d" y="152" width="9" height="14" rx="2" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (101 + k * 9, OUTLINE))


def d_doll(v):
    shoulders(v, "#e79ac4", "#ffffff")
    hair(v, "#6b2a1a", "long")
    head(v, "#fbe3d4", "round", r=54)
    v.add('<path d="M76 98 C78 52 178 52 180 98 C160 80 96 80 76 98 Z" fill="#6b2a1a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    eyes(v, iris="#2a9bd6", mood="none", size=14, gap=22, pupil=.25)
    v.add('<path d="M100 134 L104 150 M156 134 L152 150" stroke="#2a2a2a" stroke-width="2.5"/>')
    v.add('<path d="M116 150 Q128 156 140 150" stroke="#c2184b" stroke-width="4" fill="none" stroke-linecap="round"/>')
    cheeks(v, gap=38, cy=140, col="#ff6b9a")
    v.add('<path d="M140 60 l18 -10 l4 16 z M140 60 l-18 -10 l-4 16 z" fill="#ff4d8d" stroke="%s" stroke-width="3"/>' % OUTLINE)


def d_giant(v):
    shoulders(v, "#6b6f7a")
    v.add('<path d="M66 70 L104 42 L160 44 L194 78 L196 168 L162 196 L96 198 L62 162 Z" fill="%s" stroke="%s" stroke-width="5" stroke-linejoin="round"/>' % (
        v.radial([(0, "#c9ccd6"), (1, "#5a5e6b")], .35, .25, .9), OUTLINE))
    v.add('<path d="M100 60 l14 20 l-6 18 M170 120 l-16 12 l4 16 M84 140 l18 6" stroke="#4a4e5a" stroke-width="4" fill="none"/>')
    v.add('<path d="M88 100 L118 104 M138 104 L168 100" stroke="%s" stroke-width="8" stroke-linecap="round"/>' % OUTLINE)
    for s in (-1, 1):
        v.add('<circle cx="%d" cy="118" r="8" fill="#ffd166" filter="url(#glow)"/><circle cx="%d" cy="118" r="5" fill="#fff6c9"/>' % (128 + s * 24, 128 + s * 24))
    mouth(v, "flat", cy=160, w=28)


def d_wizard(v):
    shoulders(v, "#3a2a8a")
    head(v, SKIN, "round", r=54)
    beard(v, "#e8e8f0")
    eyes(v, iris="#3a7bd8", mood="happy", size=10, gap=22)
    nose(v, cy=134, col=SKIN, r=8)
    v.add('<path d="M100 150 C112 142 144 142 156 150 C144 148 112 148 100 150 Z" fill="#ffffff" stroke="%s" stroke-width="3"/>' % OUTLINE)
    hat(v, "wizard", "#4b33c9")


def d_elf(v):
    shoulders(v, "#2f8f5a", "#d4af37")
    ears_pointy(v, "#f6d2b4", inner="#f0b89a", h=30, spread=66, base=34)
    person(v, skin="#f6d2b4", shirt="#2f8f5a", hair_col="#f2dc8a", hair_style="long", mood="happy", mouth_kind="smile", iris="#2fa86b")
    v.add('<path d="M84 80 Q128 66 172 80" stroke="#d4af37" stroke-width="5" fill="none"/>')


def d_fairy(v):
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="140" rx="54" ry="34" fill="#bdf6ff" opacity="0.65" stroke="#ffffff" stroke-width="3" transform="rotate(%d %d 140)"/>' % (128 + s * 70, s * -30, 128 + s * 70))
    person(v, skin="#ffe0cc", shirt="#ff7ad9", hair_col="#ff9de2", hair_style="bun", mood="happy", mouth_kind="smile", iris="#a64dff")
    for x, y in ((70, 60), (190, 70), (200, 170), (56, 170)):
        sparkle(v, x, y, 9, "#fff6b0")


def d_unicorn(v):
    shoulders(v, "#f3e9ff")
    v.add('<path d="M70 70 C60 120 70 170 84 200 L100 196 C96 150 96 110 110 80 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.linear([(0, "#ff7ad9"), (.5, "#8a7bff"), (1, "#5be3ff")]), OUTLINE))
    head(v, "#ffffff", "egg", r=56)
    v.add('<path d="M120 62 L134 0 L144 62 Z" fill="%s" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (v.linear([(0, "#fff3a0"), (1, "#ffc93c")]), OUTLINE))
    ears_pointy(v, "#ffffff", inner="#ffc2e6", h=20, spread=44, base=48)
    eyes(v, iris="#a64dff", mood="happy", size=11, gap=22)
    mouth(v, "smile", cy=152, w=16)
    cheeks(v, gap=38, cy=140)


def d_dragon(v):
    shoulders(v, "#7a1fa2")
    for s in (-1, 1):
        v.add('<path d="M%d 80 L%d 30 L%d 84 Z" fill="#ffd166" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % (128 + s * 30, 128 + s * 50, 128 + s * 52, OUTLINE))
    head(v, "#9b4dff", "wide", r=60)
    v.add('<ellipse cx="128" cy="146" rx="44" ry="26" fill="#b98aff" stroke="%s" stroke-width="3"/>' % OUTLINE)
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="142" rx="4" ry="6" fill="%s"/>' % (128 + s * 14, OUTLINE))
    eyes(v, cy=104, gap=30, size=12, iris="#ffb000", mood="angry", pupil=.25)
    v.add('<path d="M170 156 C200 150 222 160 240 150 C230 170 210 176 186 172 Z" fill="#ff8a00" opacity="0.9"/>')
    v.add('<path d="M176 160 C196 158 210 162 224 158 C214 168 200 170 186 168 Z" fill="#fff06b"/>')


def d_knight(v):
    shoulders(v, "#8a93a6")
    v.add('<path d="M70 196 L70 96 C70 44 186 44 186 96 L186 196 C160 210 96 210 70 196 Z" fill="%s" stroke="%s" stroke-width="5"/>' % (
        v.linear([(0, "#e8edf5"), (.5, "#9aa4b8"), (1, "#5a6378")], 0, 0, 1, 0), OUTLINE))
    v.add('<rect x="80" y="104" width="96" height="16" rx="4" fill="#0b0c12"/>')
    for k in range(5):
        v.add('<rect x="%d" y="132" width="6" height="40" rx="2" fill="#4a5266"/>' % (100 + k * 13))
    v.add('<path d="M128 44 C160 10 196 20 210 40 C188 34 166 40 150 54 Z" fill="#d62839" stroke="%s" stroke-width="3"/>' % OUTLINE)


def d_cat(v):
    shoulders(v, "#f2a65a")
    ears_pointy(v, "#f2a65a", h=34, spread=40, base=52)
    head(v, "#f6b46b", "wide", r=56)
    for k in range(3):
        v.add('<path d="M%d 70 q6 14 0 26" stroke="#c9752e" stroke-width="5" fill="none" stroke-linecap="round"/>' % (114 + k * 14))
    eyes(v, iris="#3fcf6b", mood="none", size=13, gap=26, pupil=.45)
    v.add('<path d="M120 132 L136 132 L128 142 Z" fill="#ff7aa2" stroke="%s" stroke-width="3" stroke-linejoin="round"/>' % OUTLINE)
    v.add('<path d="M128 142 q-10 12 -20 4 M128 142 q10 12 20 4" fill="none" stroke="%s" stroke-width="4" stroke-linecap="round"/>' % OUTLINE)
    for s in (-1, 1):
        for k in range(3):
            v.add('<path d="M%d %d l%d %d" stroke="#ffffff" stroke-width="2.5" stroke-linecap="round"/>' % (128 + s * 30, 136 + k * 8, s * 40, k * 4 - 4))


def d_mouse(v):
    shoulders(v, "#9aa0ad")
    ears_round(v, "#a9afbb", dx=58, dy=-46, r=30, inner="#ffb3c7")
    head(v, "#b9bfcb", "egg", r=52)
    eyes(v, iris="#1a1a1a", mood="none", size=11, gap=20, pupil=.9)
    v.add('<circle cx="128" cy="144" r="8" fill="#ff7aa2" stroke="%s" stroke-width="3"/>' % OUTLINE)
    for s in (-1, 1):
        for k in range(3):
            v.add('<path d="M%d %d l%d %d" stroke="%s" stroke-width="2" stroke-linecap="round"/>' % (128 + s * 14, 146 + k * 5, s * 40, k * 5 - 5, OUTLINE))
    mouth(v, "smile", cy=160, w=10)


def d_bear(v):
    shoulders(v, "#7a4a2a")
    ears_round(v, "#8a5a32", dx=50, dy=-48, r=22, inner="#c99a6b")
    head(v, "#9a6a3e", "round", r=60)
    v.add('<ellipse cx="128" cy="146" rx="32" ry="24" fill="#e2c39a" stroke="%s" stroke-width="3"/>' % OUTLINE)
    eyes(v, iris="#2b1b10", mood="none", size=9, gap=26, pupil=.9)
    v.add('<ellipse cx="128" cy="136" rx="11" ry="8" fill="#2b1b10"/>')
    mouth(v, "smile", cy=152, w=12)


def d_lion(v):
    for a in range(0, 360, 20):
        x, y = 128 + 78 * math.cos(math.radians(a)), 120 + 78 * math.sin(math.radians(a))
        v.add('<circle cx="%.0f" cy="%.0f" r="26" fill="%s" stroke="%s" stroke-width="3"/>' % (x, y, v.radial([(0, "#e0782a"), (1, "#8a3a10")]), OUTLINE))
    head(v, "#f2b452", "round", r=58)
    eyes(v, iris="#c27a00", mood="angry", size=10, gap=24)
    v.add('<path d="M116 132 L140 132 L128 146 Z" fill="#5a2a1a" stroke="%s" stroke-width="3" stroke-linejoin="round"/>' % OUTLINE)
    mouth(v, "grin", cy=158, w=18, teeth=True, tongue=False)


def d_frog(v):
    shoulders(v, "#3f9a2a")
    for s in (-1, 1):
        v.add('<circle cx="%d" cy="74" r="28" fill="%s" stroke="%s" stroke-width="4"/>' % (128 + s * 36, v.radial([(0, "#a6f06b"), (1, "#3f9a2a")]), OUTLINE))
    head(v, "#6ccf3f", "wide", cy=128, r=62)
    eyes(v, cy=72, gap=36, size=16, iris="#ffbf00", mood="none", pupil=.5)
    v.add('<path d="M74 136 Q128 186 182 136" stroke="%s" stroke-width="5" fill="none" stroke-linecap="round"/>' % OUTLINE)
    cheeks(v, gap=56, cy=132)


def d_duck(v):
    shoulders(v, "#f2c94c")
    head(v, "#ffd84d", "round", r=60)
    v.add('<path d="M66 70 C80 50 96 54 100 68" stroke="%s" stroke-width="5" fill="none"/>' % OUTLINE)
    eyes(v, cy=104, gap=26, size=11, iris="#1a1a1a", mood="none", pupil=.9)
    v.add('<path d="M92 132 C110 118 146 118 164 132 C150 146 106 146 92 132 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.linear([(0, "#ffb347"), (1, "#ff7a00")]), OUTLINE))
    v.add('<path d="M96 134 C112 152 144 152 160 134" stroke="%s" stroke-width="3" fill="none"/>' % OUTLINE)


def d_pig(v):
    shoulders(v, "#f28fb0")
    ears_pointy(v, "#f7a8c4", inner="#e5789c", h=22, spread=44, base=46)
    head(v, "#f7b5cc", "wide", r=58)
    eyes(v, iris="#1a1a1a", mood="happy", size=9, gap=28, pupil=.9)
    v.add('<ellipse cx="128" cy="138" rx="24" ry="17" fill="#f28fb0" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="138" rx="4" ry="6" fill="#b5476e"/>' % (128 + s * 9))
    mouth(v, "smile", cy=166, w=14)


def d_bee(v):
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="70" rx="34" ry="22" fill="#e9fbff" opacity="0.8" stroke="%s" stroke-width="3" transform="rotate(%d %d 70)"/>' % (128 + s * 50, OUTLINE, s * 25, 128 + s * 50))
    v.add('<ellipse cx="128" cy="210" rx="70" ry="50" fill="#ffcf2a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for k in range(2):
        v.add('<path d="M70 %d Q128 %d 186 %d" stroke="#1a1a1a" stroke-width="14" fill="none"/>' % (200 + k * 26, 214 + k * 26, 200 + k * 26))
    head(v, "#ffd84d", "round", r=54)
    for s in (-1, 1):
        v.add('<path d="M%d 70 C%d 40 %d 30 %d 26" stroke="%s" stroke-width="4" fill="none"/><circle cx="%d" cy="26" r="6" fill="#1a1a1a"/>' % (128 + s * 16, 128 + s * 20, 128 + s * 30, 128 + s * 40, OUTLINE, 128 + s * 40))
    eyes(v, iris="#1a1a1a", mood="none", size=13, gap=22, pupil=.9)
    mouth(v, "smile", cy=150, w=14)


def d_bat(v):
    v.add('<path d="M128 140 C90 100 40 100 6 130 C30 136 40 150 44 168 C60 156 76 158 88 170 C96 158 110 154 128 160 Z" fill="#3a2a5a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    v.add('<path d="M128 140 C166 100 216 100 250 130 C226 136 216 150 212 168 C196 156 180 158 168 170 C160 158 146 154 128 160 Z" fill="#3a2a5a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    ears_pointy(v, "#4a3a72", inner="#ff8ab0", h=30, spread=30, base=56)
    head(v, "#4a3a72", "round", cy=128, r=46)
    eyes(v, cy=120, gap=18, size=10, iris="#ff3d6b", mood="none", pupil=.5)
    mouth(v, "fangs", cy=152, w=14)


def d_fish(v):
    rnd = random.Random(9)
    for i in range(10):
        v.add('<circle cx="%d" cy="%d" r="%d" fill="none" stroke="#ffffff" stroke-opacity="0.6" stroke-width="2"/>' % (rnd.randint(20, 236), rnd.randint(10, 120), rnd.randint(3, 9)))
    v.add('<path d="M200 128 L246 90 L238 128 L246 166 Z" fill="#ff8a00" stroke="%s" stroke-width="4" stroke-linejoin="round"/>' % OUTLINE)
    v.add('<ellipse cx="124" cy="132" rx="82" ry="62" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#ffd36b"), (1, "#ff6b1a")]), OUTLINE))
    for x in (110, 150):
        v.add('<path d="M%d 74 Q%d 132 %d 190" stroke="#ffffff" stroke-width="12" fill="none" opacity="0.9"/>' % (x, x + 14, x))
    eyes(v, cx=86, cy=120, gap=0, size=15, iris="#2a6bff", mood="none")
    mouth(v, "o", cx=56, cy=148, w=16)


def d_robot(v):
    shoulders(v, "#6b7a8f")
    v.add('<path d="M128 56 L128 30" stroke="%s" stroke-width="5"/><circle cx="128" cy="26" r="9" fill="#ff3d3d" filter="url(#glow)"/>' % OUTLINE)
    head(v, "#aab6c8", "square", r=58)
    for s in (-1, 1):
        v.add('<rect x="%d" y="104" width="14" height="30" rx="5" fill="#7a879b" stroke="%s" stroke-width="3"/>' % (128 + s * 64 - 7, OUTLINE))
        v.add('<circle cx="%d" cy="112" r="16" fill="#0b1626" stroke="%s" stroke-width="4"/><circle cx="%d" cy="112" r="9" fill="#35e0ff" filter="url(#glow)"/>' % (128 + s * 24, OUTLINE, 128 + s * 24))
    mouth(v, "grille", cy=152, w=30)


def d_cyborg(v):
    shoulders(v, "#3a4252")
    head(v, SKIN, "round", r=58)
    v.add('<path d="M128 60 L186 70 L186 172 L128 178 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.linear([(0, "#c9d2e0"), (1, "#5a6378")], 0, 0, 1, 0), OUTLINE))
    hair(v, "#2a2a30", "side")
    eyes(v, cx=104, gap=0, size=11, iris="#5a3a22", mood="none")
    v.add('<circle cx="154" cy="112" r="14" fill="#0b0b12" stroke="%s" stroke-width="3"/><circle cx="154" cy="112" r="7" fill="#ff2a3d" filter="url(#glow)"/>' % OUTLINE)
    for k in range(3):
        v.add('<path d="M140 %d L176 %d" stroke="#35e0ff" stroke-width="2" opacity="0.7"/>' % (140 + k * 10, 140 + k * 10))
    mouth(v, "flat", cy=152, w=18)


def d_alien(v):
    shoulders(v, "#3b2a6b")
    v.add('<path d="M60 96 C56 40 200 40 196 96 C192 150 150 196 128 196 C106 196 64 150 60 96 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#b6ff8a"), (1, "#3fae2a")], .4, .3, .8), OUTLINE))
    for s in (-1, 1):
        v.add('<ellipse cx="%d" cy="110" rx="26" ry="16" fill="%s" stroke="%s" stroke-width="3" transform="rotate(%d %d 110)"/>' % (128 + s * 30, v.radial([(0, "#3a3a4a"), (1, "#05050a")]), OUTLINE, s * 25, 128 + s * 30))
        v.add('<ellipse cx="%d" cy="104" rx="7" ry="4" fill="#ffffff" opacity="0.8" transform="rotate(%d %d 104)"/>' % (128 + s * 24, s * 25, 128 + s * 24))
    mouth(v, "smile", cy=164, w=12)


def d_ufo(v):
    v.add('<path d="M96 160 L40 262 L216 262 L160 160 Z" fill="#9dff6b" opacity="0.25"/>')
    v.add('<ellipse cx="128" cy="98" rx="46" ry="40" fill="#bdf2ff" opacity="0.85" stroke="%s" stroke-width="4"/>' % OUTLINE)
    head(v, "#8be36b", "round", cy=100, r=24)
    eyes(v, cy=98, gap=10, size=6, iris="#000", mood="none", pupil=1, white="#111")
    v.add('<ellipse cx="128" cy="140" rx="104" ry="30" fill="%s" stroke="%s" stroke-width="4"/>' % (v.linear([(0, "#e8edf5"), (1, "#7a8399")]), OUTLINE))
    for k in range(7):
        v.add('<circle cx="%d" cy="146" r="6" fill="#ffd60a" filter="url(#glow)"/>' % (50 + k * 26))


def d_astronaut(v):
    shoulders(v, "#e8edf5")
    v.add('<circle cx="128" cy="116" r="74" fill="%s" stroke="%s" stroke-width="5"/>' % (v.radial([(0, "#ffffff"), (1, "#a9b4c8")]), OUTLINE))
    v.add('<ellipse cx="128" cy="118" rx="54" ry="46" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#3a5aa8"), (1, "#0b1238")], .35, .3, .8), OUTLINE))
    v.add('<path d="M92 96 C104 82 120 78 134 80" stroke="#ffffff" stroke-width="6" stroke-linecap="round" opacity="0.6"/>')
    v.add('<rect x="108" y="196" width="40" height="22" rx="4" fill="#ff7a00" stroke="%s" stroke-width="3"/>' % OUTLINE)


def d_glitch(v):
    for k in range(9):
        y = 30 + k * 24
        v.add('<rect x="%d" y="%d" width="%d" height="8" fill="%s" opacity="0.5"/>' % ((k * 37) % 120, y, 60 + (k * 53) % 140, ["#ff2bd6", "#2bfff2", "#fff22b"][k % 3]))
    for dx, col in ((-6, "#ff2bd6"), (6, "#2bfff2"), (0, "#a64dff")):
        v.add('<g transform="translate(%d 0)" opacity="%s">' % (dx, "0.6" if dx else "1"))
        px = [[0, 0, 1, 0, 0, 0, 1, 0, 0], [0, 0, 0, 1, 0, 1, 0, 0, 0], [0, 0, 1, 1, 1, 1, 1, 0, 0], [0, 1, 1, 0, 1, 0, 1, 1, 0], [1, 1, 1, 1, 1, 1, 1, 1, 1], [1, 0, 1, 1, 1, 1, 1, 0, 1], [1, 0, 1, 0, 0, 0, 1, 0, 1], [0, 0, 0, 1, 0, 1, 0, 0, 0]]
        for y, row in enumerate(px):
            for x, p in enumerate(row):
                if p:
                    v.add('<rect x="%d" y="%d" width="18" height="18" fill="%s"/>' % (47 + x * 18, 56 + y * 18, col))
        v.add('</g>')


def d_8bit(v):
    v.add('<rect x="40" y="40" width="176" height="176" rx="18" fill="#1b1f2e" stroke="%s" stroke-width="5"/>' % OUTLINE)
    face = ["..XXXXXX..", ".XXXXXXXX.", "XXOOXXOOXX", "XXOOXXOOXX", "XXXXXXXXXX", "XXRXXXXRXX", ".XXRRRRXX.", "..XXXXXX.."]
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            col = {"X": "#ffcf4a", "O": "#14121f", "R": "#d62839"}.get(ch)
            if col:
                v.add('<rect x="%d" y="%d" width="15" height="15" fill="%s"/>' % (53 + x * 15, 68 + y * 15, col))
    for k in range(3):
        v.add('<rect x="%d" y="196" width="18" height="8" rx="3" fill="%s"/>' % (80 + k * 34, ["#ff3d6b", "#3bff8a", "#3bb6ff"][k]))


def d_phone(v):
    v.add('<rect x="54" y="120" width="148" height="100" rx="20" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#ff7a7a"), (1, "#a3122a")]), OUTLINE))
    v.add('<path d="M40 104 C40 56 216 56 216 104 L192 120 C176 100 80 100 64 120 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (v.linear([(0, "#ff8a8a"), (1, "#b5182f")]), OUTLINE))
    v.add('<circle cx="128" cy="168" r="34" fill="#fff4e6" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for a in range(0, 300, 30):
        v.add('<circle cx="%.0f" cy="%.0f" r="5" fill="#3a3a3a"/>' % (128 + 22 * math.cos(math.radians(a - 60)), 168 + 22 * math.sin(math.radians(a - 60))))
    eyes(v, cy=150, gap=16, size=7, iris="#000", mood="none", pupil=1)


def d_radio(v):
    v.add('<path d="M70 70 L170 30" stroke="%s" stroke-width="4"/><circle cx="170" cy="30" r="5" fill="#ffd60a"/>' % OUTLINE)
    v.add('<rect x="34" y="70" width="188" height="140" rx="24" fill="%s" stroke="%s" stroke-width="5"/>' % (v.radial([(0, "#ffd36b"), (1, "#c97a00")]), OUTLINE))
    v.add('<circle cx="92" cy="146" r="38" fill="#3a2a1a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for k in range(4):
        v.add('<circle cx="92" cy="146" r="%d" fill="none" stroke="#6b5030" stroke-width="3"/>' % (8 + k * 8))
    v.add('<rect x="146" y="104" width="56" height="24" rx="5" fill="#fff4d6" stroke="%s" stroke-width="3"/>' % OUTLINE)
    for k in range(2):
        v.add('<circle cx="%d" cy="168" r="11" fill="#7a4a1a" stroke="%s" stroke-width="3"/>' % (160 + k * 30, OUTLINE))
    eyes(v, cx=174, cy=116, gap=12, size=5, iris="#000", mood="none", pupil=1)


def d_megaphone(v):
    for k in range(3):
        v.add('<path d="M%d 80 Q%d 128 %d 176" stroke="#ffffff" stroke-width="6" fill="none" opacity="%.2f" stroke-linecap="round"/>' % (200 + k * 18, 222 + k * 18, 200 + k * 18, .7 - k * .2))
    v.add('<path d="M60 108 L180 56 L180 200 L60 148 Z" fill="%s" stroke="%s" stroke-width="5" stroke-linejoin="round"/>' % (v.linear([(0, "#ffffff"), (1, "#c9d2e0")]), OUTLINE))
    v.add('<ellipse cx="180" cy="128" rx="18" ry="72" fill="#ff3d3d" stroke="%s" stroke-width="5"/>' % OUTLINE)
    v.add('<rect x="38" y="104" width="26" height="48" rx="8" fill="#ff3d3d" stroke="%s" stroke-width="4"/>' % OUTLINE)
    v.add('<path d="M96 150 L86 210 L112 210 L118 156 Z" fill="#3a3a4a" stroke="%s" stroke-width="4"/>' % OUTLINE)
    eyes(v, cx=124, cy=112, gap=16, size=8, iris="#000", mood="angry", pupil=1)


def d_tvhost(v):
    v.add('<rect x="30" y="40" width="196" height="150" rx="20" fill="#1b1f2e" stroke="%s" stroke-width="5"/>' % OUTLINE)
    v.add('<rect x="44" y="54" width="168" height="122" rx="10" fill="%s"/>' % v.radial([(0, "#5bc0ff"), (1, "#1b3a8a")]))
    person(v, skin="#e8b48a", shirt="#2a2f45", hair_col="#2a1a10", hair_style="side", mouth_kind="grin", iris="#3b2a1e", collar="#ffffff", r=44)
    v.add('<path d="M120 196 L128 214 L136 196 Z" fill="#d62839"/>')


def d_trailer(v):
    v.add('<rect x="0" y="0" width="256" height="40" fill="#000" opacity="0.6"/><rect x="0" y="216" width="256" height="40" fill="#000" opacity="0.6"/>')
    person(v, skin="#d9a27a", shirt="#1a1a24", hair_col="#1a1a1a", hair_style="side", mood="sly", mouth_kind="flat", iris="#2a1a10", collar="#ffffff")
    v.add('<path d="M70 160 C80 196 176 196 186 160 C170 176 86 176 70 160 Z" fill="#2a1a10" opacity="0.8"/>')
    headphones(v)


def d_stadium(v):
    v.add('<ellipse cx="128" cy="200" rx="140" ry="60" fill="#2f8f3a"/>')
    v.add('<path d="M0 150 C40 120 216 120 256 150 L256 262 L0 262 Z" fill="#1f5f2a"/>')
    for k in range(4):
        v.add('<rect x="%d" y="20" width="12" height="40" fill="#8a93a6"/><rect x="%d" y="12" width="34" height="16" rx="3" fill="#fff6c9" filter="url(#glow)"/>' % (28 + k * 64, 17 + k * 64))
    person(v, skin="#f0c3a0", shirt="#2a63c9", hair_col="#3a2a1a", hair_style="tuft", mouth_kind="grin", iris="#2a3a5a", r=46)
    headphones(v, "#d62839")


def d_cathedral(v):
    v.add('<path d="M48 250 L48 120 L128 40 L208 120 L208 250 Z" fill="%s" stroke="%s" stroke-width="5"/>' % (v.linear([(0, "#e8dcc8"), (1, "#a8977a")]), OUTLINE))
    v.add('<path d="M120 40 L128 4 L136 40 Z M124 14 h8 M128 8 v16" stroke="%s" stroke-width="3" fill="#a8977a"/>' % OUTLINE)
    v.add('<circle cx="128" cy="104" r="26" fill="%s" stroke="%s" stroke-width="4"/>' % (v.radial([(0, "#ffe066"), (.5, "#ff6b9a"), (1, "#5b3ad8")]), OUTLINE))
    for a in range(0, 360, 45):
        v.add('<line x1="128" y1="104" x2="%.0f" y2="%.0f" stroke="%s" stroke-width="2"/>' % (128 + 26 * math.cos(math.radians(a)), 104 + 26 * math.sin(math.radians(a)), OUTLINE))
    v.add('<path d="M100 250 L100 176 C100 150 156 150 156 176 L156 250 Z" fill="#5a3a1a" stroke="%s" stroke-width="4"/>' % OUTLINE)


def d_cave(v):
    v.add('<rect width="256" height="256" fill="#1a1426" opacity="0.6"/>')
    v.add('<path d="M0 256 L0 60 C40 20 80 40 100 20 C140 0 180 30 210 20 C240 30 256 60 256 80 L256 256 L200 256 C200 190 170 150 128 150 C86 150 56 190 56 256 Z" fill="%s" stroke="%s" stroke-width="5"/>' % (
        v.linear([(0, "#8a7a6a"), (1, "#3a2f28")]), OUTLINE))
    for x in (40, 80, 160, 200, 230):
        v.add('<path d="M%d 50 l8 34 l8 -34 z" fill="#5a4a3a" stroke="%s" stroke-width="3"/>' % (x, OUTLINE))
    for k in range(3):
        v.add('<path d="M%d 200 q10 -20 20 0" stroke="#c9b6ff" stroke-width="3" fill="none" opacity="%.2f"/>' % (110 + k * 6 - 6, .9 - k * .25))
    eyes(v, cy=196, gap=16, size=7, iris="#ffd60a", mood="none", pupil=.6, white="#2a2a2a")


def d_bathroom(v):
    v.add('<rect width="256" height="256" fill="#bfe9ff" opacity="0.25"/>')
    for x in range(0, 256, 32):
        v.add('<line x1="%d" y1="0" x2="%d" y2="256" stroke="#ffffff" stroke-opacity="0.15" stroke-width="2"/>' % (x, x))
    v.add('<path d="M190 20 L190 60 L150 60" stroke="#c9d2e0" stroke-width="10" fill="none" stroke-linecap="round"/>')
    v.add('<path d="M120 52 L180 52 L170 72 L130 72 Z" fill="#e8edf5" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for k in range(9):
        v.add('<path d="M%d 80 l%d 34" stroke="#5bc0ff" stroke-width="3" stroke-linecap="round" opacity="0.8"/>' % (132 + k * 5, (k - 4) * 3))
    person(v, skin="#f2c29b", shirt="#ffffff", hair_col="#5a3a22", hair_style=None, mood="closed", mouth_kind="o", r=46)
    v.add('<path d="M84 96 C80 60 176 60 172 96 Z" fill="#ffd1e8" stroke="%s" stroke-width="4"/>' % OUTLINE)
    for x, y in ((90, 70), (160, 76), (140, 58)):
        v.add('<circle cx="%d" cy="%d" r="7" fill="#ffffff" stroke="#5bc0ff" stroke-width="2"/>' % (x, y))


def d_whisper(v):
    person(v, skin="#f2c29b", shirt="#7a5cff", hair_col="#2a1a10", hair_style="long", mood="sly", mouth_kind="shh", iris="#5a3a22")
    v.add('<rect x="122" y="128" width="12" height="44" rx="6" fill="#f2c29b" stroke="%s" stroke-width="3"/>' % OUTLINE)
    for k in range(3):
        v.add('<path d="M%d 120 q12 10 0 20" stroke="#ffffff" stroke-width="3" fill="none" opacity="%.2f"/>' % (196 + k * 12, .8 - k * .25))


def d_crowd(v):
    cols = [("#e8b48a", "#ff6b6b"), ("#c98b52", "#4dabf7"), ("#f2c29b", "#51cf66"), ("#8a5a32", "#fcc419"), ("#ffd8bd", "#cc5de8")]
    pos = [(56, 130, 34), (200, 130, 34), (92, 150, 38), (164, 150, 38), (128, 140, 44)]
    for (skin, shirt), (x, y, r) in zip(cols, pos):
        v.add('<path d="M%d 262 C%d %d %d %d %d %d C%d %d %d %d %d 262 Z" fill="%s" stroke="%s" stroke-width="4"/>' % (
            x - r * 1.5, x - r * 1.4, y + r * 1.6, x - r, y + r * 1.2, x, y + r * 1.2, x + r, y + r * 1.2, x + r * 1.4, y + r * 1.6, x + r * 1.5, shirt, OUTLINE))
        v.add('<circle cx="%d" cy="%d" r="%d" fill="%s" stroke="%s" stroke-width="4"/>' % (x, y, r, v.radial([(0, shade(skin, .3)), (1, shade(skin, -.25))]), OUTLINE))
        for s in (-1, 1):
            v.add('<circle cx="%.0f" cy="%.0f" r="%.1f" fill="%s"/>' % (x + s * r * .35, y - r * .1, r * .12, OUTLINE))
        v.add('<path d="M%.0f %.0f q%.0f %.0f %.0f 0" fill="#5a1730" stroke="%s" stroke-width="3"/>' % (x - r * .35, y + r * .3, r * .35, r * .4, r * .7, OUTLINE))


def d_bass(v):
    for k in range(3):
        v.add('<circle cx="128" cy="128" r="%d" fill="none" stroke="#ff4fa3" stroke-width="5" opacity="%.2f"/>' % (96 + k * 16, .6 - k * .18))
    v.add('<rect x="54" y="30" width="148" height="196" rx="22" fill="%s" stroke="%s" stroke-width="5"/>' % (v.linear([(0, "#3a3f55"), (1, "#14161f")]), OUTLINE))
    v.add('<circle cx="128" cy="78" r="24" fill="#0b0c12" stroke="#5a5f78" stroke-width="4"/><circle cx="128" cy="78" r="8" fill="#5a5f78"/>')
    v.add('<circle cx="128" cy="160" r="48" fill="%s" stroke="#5a5f78" stroke-width="5"/>' % v.radial([(0, "#3a3f55"), (1, "#05060a")]))
    v.add('<circle cx="128" cy="160" r="18" fill="#ff4fa3" filter="url(#glow)"/>')
    eyes(v, cx=128, cy=78, gap=0, size=0.1, mood="closed")


DRAW = {k[2:]: f for k, f in globals().items() if k.startswith("d_") and callable(f)}
DRAW["8bit"] = d_8bit


def render(svg_text, out_png, size=256):
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg_text)
        p = f.name
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), p, "-o", out_png], check=True)
    os.unlink(p)


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from voices import V
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "src/main/resources/assets/sleep_client/textures/persona")
    os.makedirs(out, exist_ok=True)
    missing = []
    for vid, name, tag, cat, emoji, vals in V:
        if vid not in DRAW:
            missing.append(vid)
            continue
        render(draw(vid, cat), os.path.join(out, vid + ".png"))
        open(os.path.join(out, vid + ".png.mcmeta"), "w").write('{"texture":{"blur":true}}')
    print("drew", len(V) - len(missing), "missing:", missing)
