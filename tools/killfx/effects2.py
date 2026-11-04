"""
Kill effects 9-20 (built with the same tools as make_killfx.py). Units: pixels, the victim stands at the origin,
the viewer looks from +Z, creatures face +X unless said otherwise. Every effect: anticipation before the attack,
squash & stretch, overshoot, the victim reacts and is gone at the bite.
"""
import math
from make_killfx import (Model, Anim, add_victim, hexc, mul, mix, h3, ease, eye_mat, m_tooth, m_black, hops, write_events_trail, solid)


# ----------------------------------------------------------------------------------------------- helpers
def grad(col, top=1.1, bot=0.84, spot=None, spot_col=None, spot_cell=3.0, belly=None, belly_y=None, seed=1):
    """Smooth toy-like shading: lighter on top of each block, darker below, soft blotches, optional belly colour."""
    base = hexc(col)
    sc = hexc(spot_col) if spot_col else None
    bc = hexc(belly) if belly else None

    def m(p, f, c, t):
        y0, y1 = c["from"][1], c["to"][1]
        u = (p[1] - y0) / max(1e-6, y1 - y0)
        v = bot + (top - bot) * u
        if f == "up":
            v = top + 0.03
        elif f == "down":
            v = bot - 0.04
        v *= 0.97 + h3(p[0], p[1], p[2], seed) * 0.06
        colr = base
        if bc is not None and (f == "down" or (belly_y is not None and p[1] < belly_y)):
            colr = bc
        elif sc is not None and spot and h3(p[0], p[1], p[2], seed + 9, spot_cell) > spot:
            colr = sc
        return mul(colr, v)
    return m


def gum_mat(tongue_x=3, tongue_w=3.5):
    def m(p, f, c, t):
        x, y, z = p
        if abs(z) < tongue_w and x > tongue_x:
            return mul(hexc("ff6a9a"), 0.96 + h3(x, y, z) * 0.06)
        return mul(hexc("b31445"), 0.92 + h3(x, y, z, 5) * 0.1)
    return m


def teeth(m, g, x0, x1, step, y, zs, size=1.8):
    x = x0
    while x <= x1 + 1e-6:
        for z in zs:
            m.cube(g, [x - size / 2, y - size / 2, z - 0.5], [x + size / 2, y + size / 2, z + 0.5], m_tooth, rot=[0, 0, 45], origin=[x, y, z], name="tooth")
        x += step


def head(m, parent, hinge, L, W, Hu, Hl, up_mat, lo_mat, name="", step=3.0, tooth=1.8, gum=None):
    """Upper and lower jaw turning around 'hinge', looking towards +X. Returns (upper, lower)."""
    hx, hy, hz = hinge
    up = m.group(name + "jaw_upper", hinge, parent)
    lo = m.group(name + "jaw_lower", hinge, parent)
    g = gum or gum_mat(hx + 3)
    m.cube(up, [hx, hy, hz - W], [hx + L, hy + Hu, hz + W], up_mat, faces={"down": g}, name="upper")
    m.cube(lo, [hx, hy - Hl, hz - W * 0.92], [hx + L * 0.95, hy, hz + W * 0.92], lo_mat, faces={"up": g}, name="lower")
    if tooth:
        teeth(m, up, hx + 3, hx + L - 2, step, hy, [hz - W + 1.3, hz + W - 1.3], tooth)
        teeth(m, lo, hx + 3 + step / 2, hx + L * 0.95 - 2, step, hy, [hz - W * 0.92 + 1.5, hz + W * 0.92 - 1.5], tooth)
    return up, lo


def jaw(an, up, lo, keys, lo_k=0.8):
    an.key(up, "rotation", *[(k[0], (0, 0, k[1])) + tuple(k[2:]) for k in keys])
    an.key(lo, "rotation", *[(k[0], (0, 0, -k[1] * lo_k)) + tuple(k[2:]) for k in keys])


def eyes(m, g, x0, x1, y0, y1, z, mat=None):
    for s in (1, -1):
        za, zb = (z - 0.4, z + 0.6) if s > 0 else (-z - 0.6, -z + 0.4)
        m.cube(g, [x0, y0, za], [x1, y1, zb], mat or eye_mat("east"), name="eye")


def chomp_fx(an, t, x=0, y=18):
    an.ev(t, "s entity.player.attack.crit 1 0.6")
    an.ev(t, "s entity.generic.eat 1 0.6")
    an.ev(t, "p confetti 32 %d %d 0 7 0.35" % (x, y))
    an.ev(t, "p sparkle 12 %d %d 0 6 0.2" % (x, y))


# ================================================================================================ 9 WHALE
def make_whale():
    m = Model("whale")
    an = Anim(2.5)
    gone = 1.05
    add_victim(m, an, gone, "down", scared=0.4)
    BL = "2f7dff"
    body = grad(BL, belly="eef6ff", belly_y=-6)
    root = m.group("whale", [0, 0, 0])
    tail = m.group("tail", [-50, 0, 0], root)
    fluke = m.group("fluke", [-80, 0, 0], tail)

    def pleats(p, f, c, t):
        x, y, z = p
        if f in ("down", "south", "north") and int(math.floor(z * 0.7 if f == "down" else y * 0.9)) % 2 == 0:
            return mul(hexc("bcd7ff"), 0.98)
        return mul(hexc("eef6ff"), 1.0 - (0.06 if f == "down" else 0))
    m.cube(root, [-50, -14, -16], [0, 16, 16], body, name="body")
    m.cube(tail, [-80, -8, -10], [-50, 10, 10], body, name="tail")
    m.cube(fluke, [-96, -1, -24], [-80, 2, 24], grad("1f5fd6"), name="fluke")
    up, lo = head(m, root, [0, 0, 0], 30, 14, 14, 14, grad(BL), pleats, tooth=0, gum=gum_mat(2, 6))
    # baleen instead of teeth
    for z in range(-12, 13, 2):
        m.cube(up, [4, -1.6, z - 0.3], [26, -0.2, z + 0.3], solid("ffe9b8", edge=0), name="baleen")
    eyes(m, up, 6, 10, 2, 6, 14.2, m_black)
    m.cube(root, [-36, 16, -6], [-24, 22, 6], grad("1f5fd6"), name="dorsal")
    for s in (1, -1):
        za, zb = (16, 30) if s > 0 else (-30, -16)
        m.cube(root, [-20, -12, za], [-6, -9, zb], grad("1f5fd6"), rot=[s * 20, 0, 0], origin=[-13, -10, 16 * s], name="flipper")
    an.key(root, "position", (0, (0, -170, 0)), (0.45, (0, -90, 0), "out"), (0.95, (0, 6, 0), "out"), (1.35, (-6, 40, 0), "out"),
           (1.6, (-30, 22, 0), "in"), (2.05, (-60, -150, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 90)), (1.3, (0, 0, 95)), (1.7, (0, 0, 140), "io"), (2.05, (0, 0, 175), "in"))
    an.key(root, "scale", (0, 1), (0.85, (0.92, 1.12, 1.12), "out"), (1.05, (1.1, 0.92, 0.92), "in"), (1.2, 1.0, "back"))
    jaw(an, up, lo, [(0, 0), (0.45, 4), (0.75, 46, "out"), (0.98, 48), (1.05, 0, "in"), (1.12, 6, "out"), (1.2, 0, "in")], lo_k=0.7)
    an.key(tail, "rotation", *[(round(i * 0.22, 2), (0, 0, 14 if i % 2 else -14), "io") for i in range(12)])
    an.key(fluke, "rotation", *[(round(i * 0.22, 2), (0, 0, 18 if i % 2 else -18), "io") for i in range(12)])
    write_events_trail(an, 0.05, 0.4, 0.12, lambda t: an.ev(t, "ring splash 20 0 1 0 %d 0.2" % int(10 + 20 * t)))
    an.ev(0.15, "s entity.elder_guardian.ambient 1 0.5")
    an.ev(0.55, "ring splash 64 0 1 0 22 0.5")
    an.ev(0.55, "p water 50 0 4 0 14 0.75")
    an.ev(0.55, "p chunks 30 0 4 0 14 0.8")
    an.ev(0.55, "s entity.player.splash.high_speed 1 0.6")
    chomp_fx(an, gone, 0, 30)
    an.ev(1.35, "p water 40 -4 70 0 4 0.55")
    an.ev(1.35, "s entity.dolphin.jump 1 0.5")
    an.ev(1.95, "ring splash 64 -60 1 0 22 0.5")
    an.ev(1.95, "p water 40 -60 4 0 14 0.7")
    an.ev(1.95, "s entity.player.splash 1 0.5")
    return m, an


# ================================================================================================ 10 DRAGON
def make_dragon():
    m = Model("dragon")
    an = Anim(2.6)
    gone = 1.15
    add_victim(m, an, gone, "up", scared=0.5)
    PU, BE, WI = "8a3bff", "ffb02e", "ff4fd8"
    root = m.group("dragon", [0, 0, 0])
    neck = m.group("neck", [-12, 1, 0], root)
    tail = m.group("tail", [-44, 0, 0], root)
    tip = m.group("tail_tip", [-70, 0, 0], tail)
    wl = m.group("wing_left", [-30, 8, 10], root)
    wr = m.group("wing_right", [-30, 8, -10], root)
    skin = grad(PU, belly=BE, belly_y=-4, spot=0.78, spot_col="6a24d6")
    m.cube(neck, [-16, -4, -5], [0, 8, 5], skin, name="neck")
    m.cube(root, [-44, -10, -11], [-14, 10, 11], skin, name="body")
    m.cube(tail, [-70, -4, -4], [-44, 4, 4], skin, name="tail")
    m.cube(tip, [-80, -6, -0.8], [-70, 6, 0.8], grad(WI), rot=[0, 0, 0], name="spade")
    up, lo = head(m, root, [0, 0, 0], 20, 6, 7, 5, grad(PU), grad(BE), step=3, tooth=1.5)
    eyes(m, up, 8, 12, 3, 6, 6.2, eye_mat("east"))
    for s in (1, -1):
        za, zb = (3, 5) if s > 0 else (-5, -3)
        m.cube(up, [-4, 6, za], [3, 8, zb], grad("ffe35c"), rot=[0, 0, 30], origin=[0, 7, 4 * s], name="horn")
    for x in range(-40, -14, 6):
        m.cube(root, [x, 10, -1], [x + 3, 14, 1], grad(WI), rot=[0, 0, 45], origin=[x + 1.5, 10, 0], name="spike")
    for g, s in ((wl, 1), (wr, -1)):
        z0, z1 = (10, 44) if s > 0 else (-44, -10)
        m.cube(g, [-34, 8, z0], [-28, 11, z1], grad(PU), name="wing_arm")
        m.cube(g, [-50, 8.5, z0 + (2 if s > 0 else 0)], [-34, 10.5, z1 - (0 if s > 0 else 2)], grad(WI, top=1.05, bot=0.95), name="membrane")
    for s in (1, -1):
        za, zb = (5, 9) if s > 0 else (-9, -5)
        m.cube(root, [-24, -18, za], [-18, -10, zb], skin, name="leg")
    pos = [(0, (-170, 130, 0)), (0.85, (-30, 26, 0), "out"), (1.05, (-16, 16, 0), "in"), (1.15, (-14, 14, 0)), (1.35, (-20, 30, 0), "out"),
           (2.6, (170, 150, 0), "in")]
    an.key(root, "position", *pos)
    an.key(root, "rotation", (0, (0, 0, -32)), (0.85, (0, 0, -18)), (1.05, (0, 0, -6)), (1.2, (0, 0, 10), "out"), (1.5, (0, 0, 30)),
           (2.6, (0, 0, 28)))
    an.key(root, "scale", (0, 1), (1.1, (1.08, 0.94, 1.0)), (1.2, (0.94, 1.08, 1.0), "out"), (1.32, 1.0))
    an.key(neck, "rotation", (0, (0, 0, 0)), (0.9, (0, 0, 8)), (1.1, (0, 0, -6)), (1.25, (0, 0, 0)))
    jaw(an, up, lo, [(0, 0), (0.6, 0), (0.85, 55, "out"), (1.08, 58), (1.15, 0, "in"), (1.22, 7, "out"), (1.3, 0, "in")])
    flap = []
    t, i = 0.0, 0
    while t <= 2.6:
        flap.append((round(t, 3), 42 if i % 2 == 0 else -38, "io"))
        t += 0.17 if (t < 0.7 or t > 1.25) else 0.3
        i += 1
    an.key(wl, "rotation", *[(k[0], (k[1], 0, 0), k[2]) for k in flap])
    an.key(wr, "rotation", *[(k[0], (-k[1], 0, 0), k[2]) for k in flap])
    an.key(tail, "rotation", *[(round(i * 0.25, 2), (0, 0, 12 if i % 2 else -12), "io") for i in range(11)])
    an.key(tip, "rotation", *[(round(i * 0.25, 2), (0, 0, 16 if i % 2 else -16), "io") for i in range(11)])
    an.ev(0.1, "s entity.ender_dragon.growl 0.7 1.5")
    for tt in (0.3, 0.64, 1.45, 1.8, 2.15):
        an.ev(tt, "s entity.ender_dragon.flap 0.8 1.3")
    an.ev(1.0, "ring cloud 16 0 1 0 14 0.2")
    chomp_fx(an, gone, 0, 18)
    an.ev(1.3, "p flame 14 14 22 0 4 0.2")
    return m, an


# ================================================================================================ 11 SANDWORM
def make_worm():
    m = Model("worm")
    an = Anim(2.5)
    gone = 1.0
    add_victim(m, an, gone, "down", scared=0.3,
               keys={"position": [(0, (0, 0, 0))] + [(round(0.05 * i, 2), ((0.6 if i % 2 else -0.6), 0, 0)) for i in range(1, 11)] + [(0.55, (0, 0, 0))]})
    root = m.group("worm", [0, 0, 0])
    SEG = 12
    segs = []
    prev = root
    for i in range(6):  # bottom (0) to top (5); each segment turns around its lower edge
        w = 9 + i * 0.6
        y0, y1 = i * SEG, (i + 1) * SEG
        g = m.group("seg%d" % i, [0, y0, 0], prev)
        col = "ff8f5a" if i % 2 == 0 else "ffad7a"
        m.cube(g, [-w, y0 + 0.15, -w], [w, y1 - 0.15, w], grad(col, top=1.12, bot=0.82), name="segment")
        m.cube(g, [-w - 0.8, y1 - 3, -w - 0.8], [w + 0.8, y1 - 1, w + 0.8], grad("e8613a"), name="ring")
        segs.append(g)
        prev = g
    top = segs[-1]
    petals = []
    for k, (axis, s_) in enumerate((("x", 1), ("x", -1), ("z", 1), ("z", -1))):
        s = s_
        if axis == "x":
            g = m.group("petal%d" % k, [12 * s, 72, 0], top)
            a, b = ([10, 72, -11], [12, 92, 11]) if s > 0 else ([-12, 72, -11], [-10, 92, 11])
        else:
            g = m.group("petal%d" % k, [0, 72, 12 * s], top)
            a, b = ([-11, 72.2, 10], [11, 91.8, 12]) if s > 0 else ([-11, 72.2, -12], [11, 91.8, -10])
        inner = {"x": ("west" if s > 0 else "east"), "z": ("north" if s > 0 else "south")}[axis]
        m.cube(g, a, b, grad("ff4f86"), faces={inner: gum_mat(99)}, name="petal")
        petals.append((g, axis, s))
    for k in range(10):  # a ring of teeth inside the mouth
        an_ = k / 10 * 2 * math.pi
        x, z = 8 * math.cos(an_), 8 * math.sin(an_)
        m.cube(top, [x - 1, 72, z - 1], [x + 1, 75, z + 1], m_tooth, rot=[0, k * 36, 0], origin=[x, 72, z], name="tooth")
    an.key(root, "position", (0, (0, -110, 0)), (0.5, (0, -110, 0)), (0.9, (0, -18, 0), "out"), (1.0, (0, -24, 0), "in"),
           (1.15, (0, -14, 0), "out"), (1.65, (0, -18, 0)), (2.25, (0, -130, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 0)), (1.0, (0, 0, 0)), (1.15, (0, 40, 6), "out"), (1.35, (0, -30, -6)), (1.55, (0, 20, 4)), (1.75, (0, 0, 0)))
    an.key(root, "scale", (0, 1), (0.8, (0.88, 1.12, 0.88)), (1.0, (1.12, 0.92, 1.12), "in"), (1.12, 1.0, "back"), (1.3, (1.08, 0.95, 1.08)),
           (1.45, 1.0))
    for i, g in enumerate(segs):
        an.key(g, "rotation", *[(round(0.05 + j * 0.2 + i * 0.04, 3), (3 if j % 2 else -3, 0, 3 if j % 2 else -3), "io") for j in range(12)])
    for g, axis, s in petals:
        closed, opened = 18, -95  # leaning in (a pointy tip) / bent all the way out
        keys = [(0, closed), (0.6, closed), (0.82, opened, "out"), (0.94, opened), (1.0, closed + 6, "in"), (1.08, closed, "out"), (1.8, closed),
                (1.9, -40, "out"), (2.02, closed, "in")]
        if axis == "x":
            an.key(g, "rotation", *[(k[0], (0, 0, k[1] * s)) + tuple(k[2:]) for k in keys])
        else:
            an.key(g, "rotation", *[(k[0], (-k[1] * s, 0, 0)) + tuple(k[2:]) for k in keys])
    for tt in (0.05, 0.22, 0.4):
        an.ev(tt, "ring dirt 18 0 1 0 12 0.25")
        an.ev(tt, "s block.sand.break 1 0.5")
    an.ev(0.6, "p dirt 50 0 2 0 14 0.6")
    an.ev(0.6, "ring dirt 32 0 1 0 18 0.5")
    an.ev(0.6, "s entity.ravager.roar 0.8 1.4")
    chomp_fx(an, gone, 0, 40)
    an.ev(1.9, "s entity.player.burp 1 0.6")
    an.ev(2.1, "ring dirt 26 0 1 0 14 0.35")
    an.ev(2.1, "s block.sand.break 1 0.5")
    return m, an


# ================================================================================================ 12 SNAKE
def make_snake():
    m = Model("snake")
    an = Anim(2.8)
    gone = 1.1

    def scales(p, f, c, t):
        x, y, z = p
        if f == "down" or (y < c["from"][1] + 1.2 and f not in ("up",)):
            return mul(hexc("fff2a8"), 0.98)
        d = abs(((x * 0.35) % 2) - 1) + abs(z) * 0.18
        col = hexc("ffd21a") if d < 0.45 and f == "up" else hexc("22c55e")
        y0, y1 = c["from"][1], c["to"][1]
        return mul(col, 0.88 + 0.2 * (p[1] - y0) / max(1e-6, y1 - y0) + (0.06 if f == "up" else 0))
    root = m.group("snake", [0, 0, 0])
    body, prev = [], root
    for i in range(7):  # the body trails behind (-X)
        g = m.group("body%d" % i, [-i * 10, 3, 0], prev)
        w = 3.4 - i * 0.25
        m.cube(g, [-(i + 1) * 10 + 0.15, 3 - w, -w], [-i * 10 - 0.15, 3 + w, w], scales, name="body")
        body.append(g)
        prev = g
    neck, prev = [], root
    for i in range(3):  # the neck rises in front (+X)
        g = m.group("neck%d" % i, [i * 10, 3, 0], prev)
        m.cube(g, [i * 10 + 0.15, -0.4, -3.6], [(i + 1) * 10 - 0.15, 6.4, 3.6], scales, name="neck")
        neck.append(g)
        prev = g
    hg = m.group("head", [30, 3, 0], prev)
    up, lo = head(m, hg, [30, 3, 0], 13, 4.5, 5.5, 3.5, scales, grad("fff2a8"), step=0, tooth=0)
    for s in (1, -1):
        m.cube(up, [39.5, -1.5, 2.2 * s - 0.5], [40.7, 3.2, 2.2 * s + 0.5], m_tooth, name="fang")
        za, zb = (4.3, 4.9) if s > 0 else (-4.9, -4.3)
        m.cube(up, [34, 5.5, za], [37, 7.5, zb], eye_mat("east"), name="eye")
    tongue = m.group("tongue", [41, 3, 0], lo)
    m.cube(tongue, [41, 2.6, -0.6], [46, 3.4, 0.6], solid("ff2b4f", edge=0), name="tongue")
    for s in (1, -1):
        m.cube(tongue, [46, 2.6, s * 0.4 - 0.4], [48, 3.4, s * 0.4 + 0.4], solid("ff2b4f", edge=0), rot=[0, 25 * s, 0], origin=[46, 3, 0], name="fork")
    an.key(root, "position", (0, (-170, 0, 0)), (0.65, (-62, 0, 0), "out"), (1.0, (-64, 0, 0)), (1.1, (-50, 0, 0), "in"), (1.6, (-56, 0, 0)),
           (2.8, (-190, 0, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 0)), (1.6, (0, 0, 0)), (1.8, (0, 180, 0), "io"), (2.8, (0, 180, 0)))
    for i, g in enumerate(body):
        an.key(g, "rotation", *[(round(j * 0.18 + i * 0.05, 3), (0, 22 if (j + i) % 2 else -22, 0), "io") for j in range(16)])
    rear = [(neck[0], [(0, 0), (0.6, 0), (0.85, 34, "out"), (1.0, 38), (1.1, 26, "in"), (1.6, 24), (1.8, 0)]),
            (neck[1], [(0, 0), (0.6, 0), (0.85, 22, "out"), (1.0, 24), (1.1, 8, "in"), (1.6, 8), (1.8, 0)]),
            (neck[2], [(0, 0), (0.6, 0), (0.85, -34, "out"), (1.0, -36), (1.1, -22, "in"), (1.6, -20), (1.8, 0)]),
            (hg, [(0, 0), (0.6, 0), (0.85, -18, "out"), (1.0, -20), (1.1, -14, "in"), (1.6, -8), (1.8, 0)])]
    for g, ks in rear:
        extra = [(round(0.62 + j * 0.06, 3), (0, 3 if j % 2 else -3, 0)) for j in range(0)]
        an.key(g, "rotation", *[(k[0], (0, 0, k[1])) + tuple(k[2:]) for k in ks])
    jaw(an, up, lo, [(0, 0), (0.85, 0), (0.98, 72, "out"), (1.05, 74), (1.1, 0, "in"), (1.16, 8, "out"), (1.24, 0, "in")], lo_k=0.9)
    an.key(tongue, "position", *[(round(j * 0.15, 3), (2.5 if j % 2 else 0, 0, 0), "io") for j in range(6)] + [(0.9, (0, 0, 0))])
    S = 1.7
    an.key(root, "scale", (0, S), (1.3, S), (1.4, (1.06 * S, 1.18 * S, 1.18 * S), "out"), (1.6, S, "back"))
    add_victim(m, an, gone, "left", scared=0.7)
    an.ev(0.1, "s entity.cat.hiss 1 0.6")
    an.ev(0.85, "s entity.cat.hiss 1 0.5")
    chomp_fx(an, gone, 0, 20)
    an.ev(1.45, "s entity.player.burp 1 0.7")
    return m, an


# ================================================================================================ 13 CROCODILE
def make_croc():
    m = Model("croc")
    an = Anim(2.6)
    gone = 1.05
    add_victim(m, an, gone, "left", scared=0.55)
    skin = grad("2fbf4a", belly="e9f59a", belly_y=-3, spot=0.8, spot_col="1f9a35")
    root = m.group("croc", [0, 0, 0])
    tail = m.group("tail", [-40, 0, 0], root)
    tail2 = m.group("tail2", [-62, 0, 0], tail)
    m.cube(root, [-40, -6, -10], [0, 5, 10], skin, name="body")
    m.cube(tail, [-62, -4, -6], [-40, 3, 6], skin, name="tail")
    m.cube(tail2, [-82, -2.5, -3.5], [-62, 2, 3.5], skin, name="tail2")
    for x in range(-38, -2, 5):
        for z in (-4, 4):
            m.cube(root, [x, 5, z - 1.2], [x + 3, 7, z + 1.2], grad("1f9a35"), rot=[0, 0, 45], origin=[x + 1.5, 5, z], name="scute")
    for x, z in ((-34, 1), (-34, -1), (-8, 1), (-8, -1)):
        za, zb = (10, 15) if z > 0 else (-15, -10)
        m.cube(root, [x, -8, za], [x + 6, -2, zb], skin, name="leg")
    up, lo = head(m, root, [0, 0, 0], 28, 6, 5, 4, skin, grad("e9f59a"), step=2.6, tooth=1.7)
    for s in (1, -1):
        za, zb = (2, 5) if s > 0 else (-5, -2)
        m.cube(up, [4, 5, za], [8, 8, zb], grad("2fbf4a"), name="eye_bump")
        m.cube(up, [5, 6.5, 5.1 if s > 0 else -5.6], [7.5, 8, 5.6 if s > 0 else -5.1], eye_mat("east"), name="eye")
    an.key(root, "position", (0, (-130, -10, 0)), (0.7, (-52, -9, 0), "out"), (0.85, (-60, -8, 0), "out"), (1.05, (-24, 8, 0), "in"),
           (1.2, (-28, 2, 0), "out"), (1.85, (-28, 2, 0)), (2.4, (-44, -40, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 0)), (0.85, (0, 0, 8)), (1.05, (0, 0, 20), "in"), (1.15, (0, 0, 4), "out"), (1.2, (0, 0, 0)),
           (1.75, (360, 0, 0), "io"), (1.85, (360, 0, 0)), (2.4, (360, 0, -20)))
    S = 1.55
    an.key(root, "scale", (0, S), (1.05, (1.1 * S, 0.92 * S, S), "in"), (1.18, S, "back"))
    jaw(an, up, lo, [(0, 0), (0.7, 2), (0.9, 55, "out"), (1.0, 58), (1.05, 0, "in"), (1.12, 8, "out"), (1.2, 0, "in")], lo_k=0.5)
    an.key(tail, "rotation", *[(round(j * 0.2, 2), (0, 18 if j % 2 else -18, 0), "io") for j in range(14)])
    an.key(tail2, "rotation", *[(round(j * 0.2 + 0.05, 2), (0, 24 if j % 2 else -24, 0), "io") for j in range(14)])
    write_events_trail(an, 0.05, 0.7, 0.12, lambda t: an.ev(t, "p splash 6 %d 1 0 6 0.2" % int(-110 + 66 * t / 0.7 + 10)))
    an.ev(0.9, "p splash 30 -20 1 0 10 0.45")
    an.ev(0.9, "s entity.player.splash 1 0.7")
    chomp_fx(an, gone, 0, 16)
    an.ev(1.25, "s entity.generic.splash 1 0.6")
    an.ev(1.5, "ring splash 40 -28 1 0 18 0.4")
    an.ev(2.2, "p splash 30 -40 1 0 14 0.35")
    return m, an


# ================================================================================================ 14 T-REX
def make_trex():
    m = Model("trex")
    an = Anim(2.6)
    gone = 1.1
    add_victim(m, an, gone, "up", scared=0.45)
    skin = grad("ff8a2a", belly="ffe2a8", belly_y=-4, spot=0.74, spot_col="d9531a", spot_cell=4.0)
    root = m.group("trex", [0, 0, 0])
    up, lo = head(m, root, [0, 0, 0], 30, 9, 13, 8, skin, grad("ffe2a8"), step=3, tooth=2.2)
    m.cube(root, [-34, -10, -10], [0, 13, 10], skin, name="neck")
    m.cube(root, [-90, -16, -12], [-34, 16, 12], skin, name="neck_far")
    for s in (1, -1):
        za, zb = (7, 9.6) if s > 0 else (-9.6, -7)
        m.cube(up, [6, 9, za], [12, 14, zb], grad("d9531a"), name="brow")
        m.cube(up, [7.5, 6, 9.0 if s > 0 else -9.6], [11, 9, 9.6 if s > 0 else -9.0], eye_mat("east"), name="eye")
        m.cube(root, [-28, -22, 8 * s - 1.5], [-22, -10, 8 * s + 1.5], skin, rot=[0, 0, -30], origin=[-25, -10, 8 * s], name="tiny_arm")
    for x in (24, 27):
        for s in (1, -1):
            m.cube(up, [x, 12.6, 2 * s - 0.6], [x + 1.4, 13.6, 2 * s + 0.6], grad("7a2b0c"), name="nostril")
    an.key(root, "position", (0, (-150, 95, 0)), (0.5, (-42, 40, 0), "out"), (0.85, (-44, 42, 0)), (1.1, (-26, 12, 0), "in"),
           (1.2, (-28, 18, 0), "out"), (1.65, (-30, 34, 0)), (2.5, (-170, 110, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, -12)), (0.5, (0, 0, 8), "out"), (0.55, (0, 6, 12)), (0.62, (0, -6, 14)), (0.69, (0, 6, 14)),
           (0.76, (0, -6, 12)), (0.85, (0, 0, 10)), (1.1, (0, 0, -24), "in"), (1.2, (0, 0, -10), "out"), (1.3, (16, 0, -6)), (1.4, (-16, 0, -6)),
           (1.5, (12, 0, -4)), (1.6, (0, 0, 0)), (2.5, (0, 0, 10)))
    an.key(root, "scale", (0, 1), (1.1, (1.06, 0.95, 1.0), "in"), (1.2, 1.0, "back"))
    jaw(an, up, lo, [(0, 6), (0.45, 10), (0.55, 62, "out"), (0.82, 58), (0.92, 64, "out"), (1.04, 66), (1.1, 0, "in"), (1.17, 8, "out"),
                     (1.25, 0, "in"), (1.6, 0), (1.75, 30, "out"), (1.9, 0, "in")], lo_k=0.75)
    an.ev(0.05, "s entity.ravager.step 1 0.6")
    an.ev(0.55, "s entity.ravager.roar 1 0.8")
    an.ev(0.55, "p cloud 16 14 38 0 6 0.25")
    an.ev(0.55, "ring poof 16 0 1 0 18 0.1")
    chomp_fx(an, gone, 0, 18)
    an.ev(1.1, "s entity.ravager.attack 1 0.8")
    an.ev(1.8, "s entity.ravager.roar 0.7 1.0")
    return m, an


# ================================================================================================ 15 TOILET
def make_toilet():
    m = Model("toilet")
    an = Anim(2.6)
    gone = 1.25
    porcelain = grad("ffffff", top=1.0, bot=0.86)
    root = m.group("toilet", [0, 0, 0])
    lid = m.group("lid", [0, 18, -11], root)

    def water(p, f, c, t):
        x, y, z = p
        a = math.atan2(z, x) / (2 * math.pi)
        r = math.hypot(x, z)
        v = (a * 3 + r / 5) % 1.0
        return hexc("2fb8ff") if v < 0.5 else hexc("7ad6ff")
    m.cube(root, [-14, 0, -12], [14, 13.6, 12], porcelain, faces={"up": water}, name="bowl")
    m.cube(root, [-10, -6, -8], [10, 0, 8], porcelain, name="base")
    for a, b in (([-14, 13.6, -12], [14, 16, -9]), ([-14, 13.6, 9], [14, 16, 12]), ([-14, 13.6, -9], [-11, 16, 9]), ([11, 13.6, -9], [14, 16, 9])):
        m.cube(root, a, b, grad("ffe066", top=1.05, bot=0.9), name="seat")
    m.cube(root, [-13, 16, -17], [13, 40, -12], porcelain, name="tank")
    m.cube(root, [-14, 40, -18], [14, 42, -11], porcelain, name="tank_lid")
    m.cube(root, [9, 33, -11.6], [13, 35, -11], solid("ffd21a"), name="handle")
    m.cube(lid, [-14, 18, -11], [14, 20, 12], grad("ffe066", top=1.05, bot=0.9), rot=[0, 0, 0], name="lid")
    an.key(root, "position", (0, (0, -50, 0)), (0.4, (0, 4, 0), "out"), (0.5, (0, 0, 0), "io"), (1.9, (0, 0, 0)), (2.0, (0, 6, 0), "out"),
           (2.45, (0, -50, 0), "in"))
    an.key(root, "scale", (0, 1), (0.35, (0.9, 1.15, 0.9)), (0.5, (1.12, 0.88, 1.12), "in"), (0.62, 1.0, "back"), (1.4, 1.0),
           (1.48, (1.15, 0.85, 1.15), "in"), (1.6, 1.0, "back"))
    an.key(root, "rotation", (0, (0, 0, 0)), (1.6, (0, 0, 0)), (1.7, (0, 0, 5)), (1.8, (0, 0, -5)), (1.9, (0, 0, 0)))
    an.key(lid, "rotation", (0, (-100, 0, 0)), (1.3, (-100, 0, 0)), (1.42, (0, 0, 0), "in"), (1.48, (-12, 0, 0), "out"), (1.55, (0, 0, 0), "in"))
    add_victim(m, an, gone, "down", scared=0.45, keys={
        "position": [(0, (0, 0, 0)), (0.4, (0, 0, 0)), (0.5, (0, 14, 0), "out"), (0.7, (0, 14, 0)), (1.24, (0, -12, 0), "in")],
        "rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (1.24, (0, 1440, 0), "in")],
        "scale": [(0, 1), (0.8, 1), (1.24, (0.3, 0.6, 0.3), "in"), (1.25, 0, "step")]})
    an.ev(0.05, "s block.stone.break 1 0.7")
    an.ev(0.4, "s block.copper_bulb.turn_on 1 1.4")
    an.ev(0.65, "s block.bubble_column.whirlpool_inside 1 1.0")
    an.ev(0.65, "s item.bucket.empty 1 0.6")
    write_events_trail(an, 0.65, 1.25, 0.1, lambda t: an.ev(t, "ring splash 12 0 15 0 9 0.15"))
    an.ev(1.25, "p confetti 30 0 18 0 6 0.35")
    an.ev(1.25, "p sparkle 12 0 18 0 6 0.2")
    an.ev(1.42, "s block.wooden_trapdoor.close 1 0.7")
    an.ev(2.05, "s block.stone.break 1 0.7")
    return m, an


# ================================================================================================ 16 GIANT HAND (a cartoon glove)
def make_hand():
    m = Model("hand")
    an = Anim(2.4)
    gone = 2.0
    glove = grad("ffffff", top=1.0, bot=0.84)
    root = m.group("hand", [0, 40, 0])
    m.cube(root, [-12, 40, -10], [12, 47, 10], glove, name="palm")
    m.cube(root, [-10, 47, -9], [10, 60, 9], glove, name="wrist")
    m.cube(root, [-11.5, 60, -10.5], [11.5, 66, 10.5], grad("ff3348"), name="cuff")
    m.cube(root, [-9, 66, -8], [9, 120, 8], grad("2f7bff"), name="sleeve")
    fingers = []
    for (x, z, s) in ((12, -5.5, 1), (12, 5.5, 1), (-12, -5.5, -1), (-12, 5.5, -1)):
        g = m.group("finger%d" % len(fingers), [x - 2 * s, 40, z], root)
        xa, xb = sorted([x - 4.5 * s, x + 0.5 * s])
        m.cube(g, [xa, 22, z - 2.6], [xb, 40, z + 2.6], glove, name="finger")
        m.cube(g, [xa + 0.4, 21.6, z - 2.2], [xb - 0.4, 22, z + 2.2], solid("d8d8e0", edge=0), name="tip")
        fingers.append((g, s))
    an.key(root, "position", (0, (0, 120, 0)), (0.75, (0, 6, 0), "out"), (0.95, (0, -6, 0), "io"), (1.15, (0, -8, 0)), (1.3, (0, 0, 0), "out"),
           (2.0, (0, 150, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 6)), (0.75, (0, 0, -3)), (0.95, (0, 0, 0)), (1.4, (0, 0, 0)), (2.0, (0, 0, 8)))
    an.key(root, "scale", (0, 1), (1.12, (1.06, 0.94, 1.06), "out"), (1.25, 1.0))
    for g, s in fingers:
        an.key(g, "rotation", (0, (0, 0, 10 * s)), (0.6, (0, 0, 38 * s), "out"), (0.95, (0, 0, 40 * s)), (1.12, (0, 0, -14 * s), "in"),
               (1.2, (0, 0, -10 * s), "out"))
    vpos = [(0, (0, 0, 0)), (1.15, (0, 0, 0)), (1.3, (0, 8, 0), "out"), (2.0, (0, 158, 0), "in")]
    add_victim(m, an, gone, "up", scared=0.4, keys={"position": vpos,
                                                   "rotation": [(0, (0, 0, 0)), (1.2, (0, 0, 0)), (1.35, (0, 0, 10)), (1.5, (0, 0, -10)),
                                                                (1.65, (0, 0, 8))]})
    an.ev(0.1, "s entity.phantom.swoop 1 0.6")
    an.ev(1.12, "s entity.item.pickup 1 0.5")
    an.ev(1.12, "p sparkle 14 0 30 0 10 0.15")
    an.ev(1.3, "s entity.phantom.swoop 1 0.8")
    an.ev(1.3, "ring poof 18 0 1 0 14 0.1")
    an.ev(1.9, "p confetti 30 0 120 0 14 0.35")
    return m, an


# ================================================================================================ 17 KRAKEN
def make_kraken():
    m = Model("kraken")
    an = Anim(2.4)
    gone = 1.3

    def tent(p, f, c, t):
        x, y, z = p
        y0, y1 = c["from"][1], c["to"][1]
        inner = f == "west"
        if inner and h3(x, y, z, 7, 1.6) > 0.55:
            return mul(hexc("ffc2ec"), 1.0)  # suckers
        return mul(hexc("c13bff") if not inner else hexc("ff7ad9"), 0.86 + 0.22 * (p[1] - y0) / max(1e-6, y1 - y0))
    groups = []
    N = 6
    for k in range(N):
        phi = k / N * 360 + 15
        R = 22
        cx, cz = R * math.cos(math.radians(phi)), R * math.sin(math.radians(phi))
        # local +X points at the victim: yaw so that +X -> (-cos, -sin)
        yaw = math.degrees(math.atan2(math.sin(math.radians(phi)), -math.cos(math.radians(phi))))
        base = m.group("t%d" % k, [cx, 0, cz])
        tw = m.group("t%d_turn" % k, [cx, 0, cz], base)
        an.key(tw, "rotation", (0, (0, yaw, 0)))
        prev = tw
        segs = []
        for i in range(5):
            w = 4.2 - i * 0.7
            g = m.group("t%d_s%d" % (k, i), [cx, i * 10, cz], prev)
            m.cube(g, [cx - w, i * 10 + 0.1, cz - w], [cx + w, (i + 1) * 10 - 0.1, cz + w], tent, name="segment")
            segs.append(g)
            prev = g
        d = k * 0.05
        an.key(base, "position", (0, (0, -60, 0)), (0.2 + d, (0, -60, 0)), (0.55 + d, (0, 3, 0), "out"), (0.65 + d, (0, 0, 0)),
               (1.45, (0, 0, 0)), (2.0, (0, -64, 0), "in"))
        for i, g in enumerate(segs):
            curl = [(0, 0), (0.3 + d, 0), (0.65 + d, -8 - i * 3, "out"), (0.9, -20 - i * 9, "io"), (1.1, -24 - i * 14, "in"), (1.3, -26 - i * 15),
                    (1.5, -10), (1.9, 0)]
            wig = 6 * (1 if (i + k) % 2 else -1)
            an.key(g, "rotation", *[(c[0], (0, 0, c[1] + (wig if 0.65 < c[0] < 0.9 else 0))) + tuple(c[2:]) for c in curl])
        groups.append(base)
    add_victim(m, an, gone, "down", scared=0.4, keys={
        "position": [(0, (0, 0, 0)), (1.05, (0, 2, 0)), (1.3, (0, -30, 0), "in")],
        "scale": [(0, 1), (1.0, 1), (1.25, (0.8, 1.1, 0.8)), (1.3, 0, "step")]})
    an.ev(0.15, "ring splash 30 0 1 0 22 0.35")
    an.ev(0.2, "s entity.squid.ambient 1 0.4")
    an.ev(0.55, "ring splash 50 0 1 0 22 0.45")
    an.ev(0.55, "p water 30 0 4 0 22 0.6")
    an.ev(0.55, "s entity.player.splash.high_speed 1 0.5")
    an.ev(1.1, "s entity.squid.squirt 1 0.5")
    an.ev(1.3, "p confetti 30 0 10 0 8 0.35")
    an.ev(1.3, "ring splash 40 0 1 0 10 0.4")
    an.ev(1.3, "s entity.generic.splash 1 0.5")
    an.ev(1.95, "ring splash 40 0 1 0 22 0.35")
    return m, an


# ================================================================================================ 18 HUNGRY PIG
def make_pig():
    m = Model("pig")
    an = Anim(2.9)
    gone = 1.05
    add_victim(m, an, gone, "left", scared=0.6)
    PK = "ff9ec4"
    skin = grad(PK, top=1.06, bot=0.88)
    root = m.group("pig", [0, 20, 0])
    up, lo = head(m, root, [0, 20, 0], 15, 9, 13, 6, skin, skin, step=0, tooth=0, gum=gum_mat(2, 5))
    m.cube(up, [15, 22, -4.5], [18.5, 29, 4.5], grad("ff7aa8"), name="snout")
    for s in (1, -1):
        m.cube(up, [18.5, 24, 2 * s - 0.9], [18.9, 27, 2 * s + 0.9], solid("b0446d", edge=0), name="nostril")
        za, zb = (8.6, 9.4) if s > 0 else (-9.4, -8.6)
        m.cube(up, [9, 27, za], [12, 30, zb], m_black, name="eye")
        m.cube(up, [2, 32.5, 5 * s - 3], [7, 34, 5 * s + 3], skin, rot=[0, 0, 25], origin=[4, 33, 5 * s], name="ear")
    m.cube(lo, [12, 19.6, -2], [14, 20.2, 2], m_tooth, name="tusk")
    m.cube(root, [-32, 10, -11], [0, 30, 11], skin, name="body")
    tail = m.group("tail", [-32, 26, 0], root)
    m.cube(tail, [-36, 26, -1], [-32, 28, 1], skin, rot=[0, 0, 30], origin=[-32, 27, 0], name="tail")
    legs = []
    for (x, z) in ((-27, 6), (-27, -6), (-7, 6), (-7, -6)):
        g = m.group("leg%d" % len(legs), [x, 12, z], root)
        m.cube(g, [x - 3.5, 0.2, z - 3.5], [x + 3.5, 12, z + 3.5], skin, name="leg")
        m.cube(g, [x - 3.6, 0, z - 3.6], [x + 3.6, 2, z + 3.6], grad("c95c86"), name="hoof")
        legs.append(g)
    an.key(root, "position", (0, (-150, 0, 0)), (0.7, (-34, 0, 0), "lin"), (0.85, (-40, 2, 0), "out"), (0.95, (-40, 2, 0)), (1.05, (-12, 0, 0), "in"),
           (1.15, (-14, 0, 0), "out"), (1.9, (-14, 0, 0)), (2.9, (160, 0, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, 0)), (0.85, (0, 0, 10), "out"), (0.95, (0, 0, 12)), (1.05, (0, 0, -8), "in"), (1.2, (0, 0, 0), "out"),
           (1.3, (0, -40, 0), "back"), (1.9, (0, -40, 0)), (2.05, (0, 0, 0)))
    bob = [(round(j * 0.12, 3), (1.0, 0.96 if j % 2 else 1.04, 1.0)) for j in range(7)]
    an.key(root, "scale", *bob, (1.05, (1.15, 0.85, 1.15), "in"), (1.18, 1.0, "back"), (1.35, (1.1, 1.12, 1.2)), (1.5, 1.0), (1.65, (1.1, 1.12, 1.2)),
           (1.8, 1.0), (2.0, (1.0, 1.0, 1.0)))
    for i, g in enumerate(legs):
        sw = []
        for j in range(25):
            t = j * 0.12
            run = t < 0.75 or t > 1.95
            sw.append((round(t, 3), (0, 0, (35 if (j + i) % 2 else -35) if run else 0), "io"))
        an.key(g, "rotation", *sw)
    an.key(tail, "rotation", *[(round(j * 0.1, 2), (0, 30 if j % 2 else -30, 0)) for j in range(29)])
    jaw(an, up, lo, [(0, 6), (0.7, 6), (0.85, 48, "out"), (0.98, 52), (1.05, 0, "in"), (1.12, 8, "out"), (1.2, 0, "in"), (1.35, 14, "out"),
                     (1.5, 0, "in"), (1.65, 14, "out"), (1.8, 0, "in")], lo_k=0.9)
    write_events_trail(an, 0.0, 0.7, 0.12, lambda t: an.ev(t, "p poof 2 %d 1 0 4 0.02" % int(-150 + 116 * t / 0.7 - 20)))
    an.ev(0.1, "s entity.pig.ambient 1 0.8")
    an.ev(0.85, "s entity.pig.ambient 1 1.2")
    chomp_fx(an, gone, 0, 16)
    for tt in (1.35, 1.65):
        an.ev(tt, "s entity.generic.eat 0.8 0.8")
    an.ev(1.85, "s entity.player.burp 1 0.7")
    an.ev(1.85, "p heart 8 0 40 0 8 0.12")
    an.ev(2.0, "s entity.pig.ambient 1 1.4")
    return m, an


# ================================================================================================ 19 GHOST
def make_ghost():
    m = Model("ghost")
    an = Anim(2.6)
    gone = 1.15
    add_victim(m, an, gone, "left", scared=0.5)
    sheet = grad("f4f7ff", top=1.0, bot=0.86)

    def face(p, f, c, t):
        x, y, z = p
        if f == "south":
            for ex in (-6, 6):
                if abs(x - ex) < 2.6 and abs(y - 36) < 3.6:
                    return (24, 18, 40) if not (abs(x - ex + 1) < 0.8 and abs(y - 37.5) < 0.8) else (255, 255, 255)
            if abs(x) < 4 and abs(y - 27) < 3.2:
                return (60, 10, 40)  # "O" mouth
            if abs(x) < 9 and 29 < y < 31 and abs(abs(x) - 10) < 2:
                return (255, 150, 190)
            if 30 < y < 33 and 9 < abs(x) < 13:
                return (255, 160, 200)  # blush
        return sheet(p, f, c, t)
    root = m.group("ghost", [0, 26, 0])
    m.cube(root, [-14, 12, -12], [14, 44, 12], face, name="body")
    m.cube(root, [-11, 44, -10], [11, 48, 10], sheet, name="top")
    skirt = []
    for i, x in enumerate((-10.5, -3.5, 3.5, 10.5)):
        g = m.group("skirt%d" % i, [x, 12, 0], root)
        m.cube(g, [x - 3.4, 5, -11], [x + 3.4, 11.8, 11], sheet, name="skirt")
        skirt.append(g)
    arms = []
    for s in (1, -1):
        g = m.group("arm%s" % ("r" if s > 0 else "l"), [14 * s, 30, 0], root)
        xa, xb = sorted([14 * s, 19 * s])
        m.cube(g, [xa, 24, -3], [xb, 31, 3], sheet, name="arm")
        arms.append((g, s))
    pos = [(0, (-150, 40, 0))]
    for j in range(1, 7):
        t = j * 0.12
        pos.append((round(t, 3), (-150 + 112 * t / 0.72, 40 + (5 if j % 2 else -5), 0), "io"))
    pos += [(0.85, (-46, 6, 0), "out"), (0.95, (-52, 8, 0)), (1.15, (0, -14, 0), "in"), (1.3, (0, -10, 0), "out"), (1.7, (0, -8, 0)),
            (2.5, (40, 150, 0), "in")]
    an.key(root, "position", *pos)
    an.key(root, "rotation", (0, (0, 0, -10)), (0.85, (0, 0, 6)), (0.95, (0, 0, 12)), (1.15, (0, 0, -6), "in"), (1.3, (0, 0, 0)), (1.6, (0, 0, 0)),
           (2.5, (0, 720, 0), "in"))
    an.key(root, "scale", (0, 1), (0.95, (0.92, 1.1, 0.92), "out"), (1.15, (1.55, 1.25, 1.55), "in"), (1.3, (1.25, 1.18, 1.3), "out"),
           (1.45, (1.45, 1.05, 1.45)), (1.6, (1.3, 1.2, 1.3)), (2.2, (1.3, 1.2, 1.3)), (2.5, 0.0, "in"))
    for i, g in enumerate(skirt):
        an.key(g, "rotation", *[(round(j * 0.14 + i * 0.03, 3), (12 if (j + i) % 2 else -12, 0, 0), "io") for j in range(19)])
    for g, s in arms:
        an.key(g, "rotation", (0, (0, 0, 20 * s)), (0.85, (0, 0, 70 * s), "out"), (1.0, (0, 0, 80 * s)), (1.15, (0, 0, -10 * s), "in"),
               (1.4, (0, 0, 20 * s)))
    an.ev(0.1, "s entity.allay.ambient_without_item 1 0.6")
    an.ev(0.85, "s entity.vex.charge 1 0.8")
    an.ev(gone, "s entity.allay.item_taken 1 0.6")
    an.ev(gone, "p confetti 26 0 20 0 8 0.3")
    an.ev(gone, "p sparkle 20 0 20 0 8 0.2")
    an.ev(1.5, "s entity.player.burp 1 1.2")
    write_events_trail(an, 1.7, 2.45, 0.1, lambda t: an.ev(t, "p sparkle 3 %d %d 0 8 0.02" % (int(40 * (t - 1.7) / 0.8), int(30 + 140 * (t - 1.7) / 0.8))))
    return m, an


# ================================================================================================ 20 EAGLE
def make_eagle():
    m = Model("eagle")
    an = Anim(2.6)
    gone = 2.15
    BR, WH, YE = "8a4b1e", "ffffff", "ffc21a"
    root = m.group("eagle", [0, 48, 0])
    m.cube(root, [-18, 42, -8], [6, 56, 8], grad(BR), name="body")
    m.cube(root, [6, 46, -6], [18, 60, 6], grad(WH, top=1.0, bot=0.88), name="head")
    m.cube(root, [18, 50, -2.5], [24, 56, 2.5], grad(YE), name="beak")
    m.cube(root, [22, 48, -1.6], [25, 52, 1.6], grad("e09a10"), rot=[0, 0, -30], origin=[22, 50, 0], name="hook")
    eyes(m, root, 12, 15, 54, 57, 6.1, eye_mat("east"))
    m.cube(root, [-32, 46, -7], [-18, 48, 7], grad(WH, top=1.0, bot=0.9), name="tail")
    wings = []
    for s in (1, -1):
        g = m.group("wing%s" % ("l" if s > 0 else "r"), [-6, 54, 8 * s], root)
        z0, z1 = (8, 44) if s > 0 else (-44, -8)
        m.cube(g, [-16, 53, z0], [4, 56, z1], grad(BR, top=1.08, bot=0.86), name="wing")
        z2, z3 = (44, 52) if s > 0 else (-52, -44)
        m.cube(g, [-18, 53.4, z2], [-2, 55.6, z3], grad("5a2e10"), name="tips")
        wings.append((g, s))
    talons = []
    for s in (1, -1):
        g = m.group("talon%s" % ("l" if s > 0 else "r"), [-2, 42, 4 * s], root)
        m.cube(g, [-4, 32, 4 * s - 1.5], [0, 42, 4 * s + 1.5], grad(YE), name="leg")
        for dx in (-3, 1):
            m.cube(g, [dx, 29, 4 * s - 1], [dx + 2, 33, 4 * s + 1], solid("2b2b33", edge=0), rot=[0, 0, 25], origin=[dx + 1, 32, 4 * s], name="claw")
        talons.append(g)
    pos = [(0, (-170, 100, 0)), (0.9, (-6, 10, 0), "out"), (1.05, (2, 2, 0), "io"), (1.2, (0, 6, 0), "out"), (2.2, (170, 140, 0), "in")]
    an.key(root, "position", *pos)
    an.key(root, "rotation", (0, (0, 0, -28)), (0.9, (0, 0, -6)), (1.05, (0, 0, 22), "out"), (1.25, (0, 0, 14)), (2.2, (0, 0, 22)))
    for g, s in wings:
        k = []
        t, i = 0.0, 0
        while t <= 2.6:
            dive = 0.45 < t < 0.95
            k.append((round(t, 3), (s * (-70 if dive else (45 if i % 2 == 0 else -40)), 0, 0), "io"))
            t += 0.16
            i += 1
        an.key(g, "rotation", *k)
    for g in talons:
        an.key(g, "rotation", (0, (0, 0, 30)), (0.9, (0, 0, -45), "out"), (1.05, (0, 0, -60)), (1.15, (0, 0, 10), "in"), (2.6, (0, 0, 10)))
    tpos = []
    t = 1.15
    while t <= gone + 1e-6:
        from make_killfx import ease as _e
        u = (t - 1.2) / 1.0
        hp = (0 + 170 * _e("in", u), 6 + 134 * _e("in", u)) if t > 1.2 else (0, 6)
        tpos.append((round(t, 3), (hp[0] - 2, hp[1] - 6 + 0, 0), "lin"))
        t += 0.05
    add_victim(m, an, gone, "up", scared=0.5, keys={"position": [(0, (0, 0, 0)), (1.1, (0, 0, 0))] + tpos,
                                                   "rotation": [(0, (0, 0, 0)), (1.2, (0, 0, 0)), (1.4, (0, 0, 15)), (1.6, (0, 0, -10)), (1.8, (0, 0, 12))]})
    an.ev(0.1, "s entity.parrot.fly 1 0.5")
    an.ev(0.3, "s entity.phantom.ambient 0.6 1.6")
    an.ev(1.05, "s entity.item.pickup 1 0.4")
    an.ev(1.05, "ring poof 20 0 1 0 18 0.15")
    an.ev(1.05, "p sparkle 12 0 26 0 8 0.15")
    for tt in (1.3, 1.62, 1.94):
        an.ev(tt, "s entity.ender_dragon.flap 0.6 1.8")
    an.ev(2.1, "p confetti 30 150 120 0 12 0.3")
    return m, an


EFFECTS = [("whale", "Whale", make_whale), ("dragon", "Dragon", make_dragon), ("worm", "Giant Worm", make_worm), ("snake", "Snake", make_snake),
           ("croc", "Crocodile", make_croc), ("trex", "T-Rex", make_trex), ("toilet", "Toilet Flush", make_toilet), ("hand", "Giant Hand", make_hand),
           ("kraken", "Kraken", make_kraken), ("pig", "Hungry Pig", make_pig), ("ghost", "Ghost", make_ghost), ("eagle", "Eagle", make_eagle)]
