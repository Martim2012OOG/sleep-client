"""Kill effects 21-24: things, not animals (piano, tornado, gift box, cannon)."""
import math
from make_killfx import Model, Anim, add_victim, hexc, mul, h3, write_events_trail, solid
from effects2 import grad


# ================================================================================================ 21 PIANO DROP
def make_piano():
    m = Model("piano")
    an = Anim(2.4)
    gone = 1.0
    add_victim(m, an, gone, "up", scared=0.35)
    lacquer = grad("1c1630", top=1.35, bot=0.8)

    def keys(p, f, c, t):
        x, y, z = p
        if f == "up":
            kx = (x + 18) / 2.2
            if int(kx) % 7 not in (2, 6) and (kx % 1) > 0.6 and z < 13.5:
                return (24, 22, 30)  # black keys
            return (250, 250, 246) if (kx % 1) > 0.08 else (180, 180, 190)
        return mul(hexc("1c1630"), 1.2)
    root = m.group("piano", [0, 0, 0])
    m.cube(root, [-20, 6, -10], [20, 40, 10], lacquer, name="body")
    m.cube(root, [-21, 40, -11], [21, 43, 11], grad("2d2450", top=1.3, bot=0.9), name="lid")
    m.cube(root, [-18, 16, 10], [18, 19, 16], lacquer, faces={"up": keys}, name="keys")
    m.cube(root, [-14, 26, 10], [14, 34, 11], grad("ffd21a"), name="music_stand")
    for x in (-18, 15):
        m.cube(root, [x, 0, -8], [x + 3, 6, 14], grad("ffd21a"), name="leg")
    m.cube(root, [-6, 2, 8], [6, 4, 12], grad("ffd21a"), name="pedals")
    an.key(root, "position", (0, (0, 190, 0)), (0.25, (0, 190, 0)), (1.0, (0, 0, 0), "in"), (1.12, (0, 6, 0), "out"), (1.24, (0, 0, 0), "in"),
           (1.95, (0, 0, 0)))
    an.key(root, "rotation", (0, (0, 0, 12)), (1.0, (0, 0, 3), "lin"), (1.12, (0, 0, -4)), (1.24, (0, 0, 0)))
    an.key(root, "scale", (0, 1), (0.9, (0.92, 1.12, 0.92)), (1.0, (0.95, 1.05, 0.95)), (1.05, (1.3, 0.7, 1.3), "out"), (1.18, (0.95, 1.08, 0.95), "out"),
           (1.3, 1.0, "back"), (1.95, 1.0), (2.05, (1.1, 0.9, 1.1)), (2.3, 0.0, "in"))
    an.ev(0.25, "s entity.phantom.swoop 1 0.5")
    an.ev(1.0, "s block.anvil.land 1 0.6")
    an.ev(1.0, "ring poof 26 0 1 0 22 0.18")
    an.ev(1.0, "p confetti 30 0 20 0 14 0.4")
    for i, (tt, pitch) in enumerate(((1.02, 0.5), (1.04, 0.63), (1.06, 0.75), (1.35, 1.0), (1.5, 1.26), (1.65, 1.5), (1.8, 2.0))):
        an.ev(tt, "s block.note_block.harp 1 %s" % pitch)
    write_events_trail(an, 1.3, 1.9, 0.15, lambda t: an.ev(t, "p note 2 0 46 0 10 0.05"))
    an.ev(2.3, "p sparkle 18 0 20 0 14 0.15")
    return m, an


# ================================================================================================ 22 TORNADO
def make_tornado():
    m = Model("tornado")
    an = Anim(2.6)
    gone = 1.6
    root = m.group("tornado", [0, 0, 0])
    rings = []
    for i in range(9):
        r = 5 + i * 3.4
        y0 = i * 9
        g = m.group("ring%d" % i, [0, y0, 0], root)
        col = ["c9e9ff", "b4dcff", "a2d2ff", "d6f0ff"][i % 4]
        for a in (0, 45):
            m.cube(g, [-r, y0 + 0.3 + (0.2 if a else 0), -r * 0.5], [r, y0 + 8.7 - (0.2 if a else 0), r * 0.5], grad(col, top=1.08, bot=0.86),
                   rot=[0, a, 0], origin=[0, y0, 0], name="swirl")
        for k in range(2):  # debris flying around
            a = k * 180 + i * 40
            x, z = (r + 3) * math.cos(math.radians(a)), (r + 3) * math.sin(math.radians(a))
            m.cube(g, [x - 1.2, y0 + 3, z - 1.2], [x + 1.2, y0 + 5.4, z + 1.2], grad(["7cc950", "8a5a2b", "ffd21a"][(i + k) % 3]),
                   rot=[30, a, 20], origin=[x, y0 + 4, z], name="debris")
        rings.append(g)
    an.key(root, "position", (0, (-140, 0, 0)), (0.9, (0, 0, 0), "out"), (1.9, (0, 0, 0)), (2.6, (160, 0, 0), "in"))
    an.key(root, "scale", (0, (0.3, 0.2, 0.3)), (0.5, (1, 1, 1), "out"), (2.2, 1.0), (2.6, (0.4, 0.3, 0.4), "in"))
    for i, g in enumerate(rings):
        an.key(g, "rotation", (0, (0, 0, 0)), (2.6, (0, -2600 - i * 180, 0), "lin"))
        an.key(g, "position", *[(round(j * 0.2 + i * 0.03, 3), (3 * math.sin(j + i * 0.7), 0, 2 * math.cos(j + i)), "io") for j in range(14)])
    vk = [(0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.0, (0, 8, 0), "out"), (1.45, (0, 62, 0), "io"), (1.6, (-30, 88, 0), "out")]
    add_victim(m, an, gone, "left", scared=0.45, keys={"position": vk, "rotation": [(0, (0, 0, 0)), (0.85, (0, 0, 0)), (1.6, (0, 1800, 25), "in")],
                                                       "scale": [(0, 1), (1.45, 1), (1.59, 0.6, "in"), (1.6, 0, "step")]})
    an.ev(0.05, "s item.elytra.flying 0.8 1.4")
    write_events_trail(an, 0.1, 2.3, 0.15, lambda t: an.ev(t, "p cloud 3 %d 2 0 12 0.08" % int(-140 + 140 * min(1, t / 0.9))))
    an.ev(1.6, "p confetti 30 -30 88 0 10 0.35")
    an.ev(1.6, "s entity.player.attack.sweep 1 0.6")
    return m, an


# ================================================================================================ 23 GIFT BOX
def make_gift():
    m = Model("gift")
    an = Anim(2.4)
    gone = 1.0

    def paper(p, f, c, t):
        x, y, z = p
        if abs(x) < 2.2 or abs(z) < 2.2:
            return mul(hexc("ffd21a"), 1.0 + (0.06 if f == "up" else 0))  # ribbon
        if int(math.floor((x + y + z) / 4)) % 2 == 0:
            return mul(hexc("ff2b6a"), 0.98)
        return mul(hexc("ff4f86"), 1.0)
    root = m.group("gift", [0, 0, 0])
    lid = m.group("lid", [0, 36, 0], root)
    bow = m.group("bow", [0, 42, 0], lid)
    m.cube(root, [-16, 0, -16], [16, 36, 16], paper, name="box")
    m.cube(lid, [-18, 36, -18], [18, 42, 18], paper, name="lid")
    for s in (1, -1):
        m.cube(bow, [-9 if s < 0 else 1, 42, -3], [-1 if s < 0 else 9, 50, 3], grad("ffd21a"), rot=[0, 0, -25 * s], origin=[0, 42, 0], name="bow")
    m.cube(bow, [-2, 42, -2], [2, 46, 2], grad("ffb000"), name="knot")
    an.key(root, "position", (0, (0, 170, 0)), (0.2, (0, 170, 0)), (1.0, (0, 0, 0), "in"), (1.1, (0, 5, 0), "out"), (1.2, (0, 0, 0), "in"))
    an.key(root, "rotation", (0, (0, 30, 0)), (1.0, (0, 0, 0), "lin"), (1.3, (0, 0, 0)), (1.36, (0, 0, 6)), (1.42, (0, 0, -6)), (1.48, (0, 0, 6)),
           (1.54, (0, 0, -6)), (1.6, (0, 0, 0)))
    an.key(root, "scale", (0, 1), (1.0, (0.95, 1.05, 0.95)), (1.05, (1.2, 0.8, 1.2), "out"), (1.18, (0.95, 1.06, 0.95), "out"), (1.28, 1.0),
           (1.55, 1.0), (1.65, (1.15, 1.15, 1.15), "out"), (1.72, (0.9, 1.1, 0.9)), (2.05, 1.0), (2.35, 0.0, "in"))
    an.key(lid, "position", (0, (0, 0, 0)), (1.65, (0, 0, 0)), (1.95, (8, 70, 0), "out"), (2.3, (14, 30, 0), "in"))
    an.key(lid, "rotation", (0, (0, 0, 0)), (1.65, (0, 0, 0)), (2.3, (0, 120, -260), "lin"))
    an.key(bow, "scale", *[(round(j * 0.25, 2), (1.1 if j % 2 else 0.95, 1.0, 1.0)) for j in range(10)])
    add_victim(m, an, gone, "up", scared=0.3)
    an.ev(0.2, "s entity.phantom.swoop 1 0.7")
    an.ev(1.0, "s block.wool.place 1 0.5")
    an.ev(1.0, "ring poof 24 0 1 0 20 0.15")
    for tt in (1.36, 1.48):
        an.ev(tt, "s block.chest.locked 0.8 1.4")
    an.ev(1.65, "s entity.firework_rocket.blast 1 1.2")
    an.ev(1.65, "s entity.player.levelup 0.8 1.2")
    an.ev(1.65, "p confetti 90 0 40 0 10 0.5")
    an.ev(1.65, "p firework 30 0 44 0 10 0.3")
    an.ev(1.65, "p sparkle 20 0 44 0 10 0.25")
    return m, an


# ================================================================================================ 24 CANNON
def make_cannon():
    m = Model("cannon")
    an = Anim(2.6)
    gone = 1.15
    add_victim(m, an, gone, "left", scared=0.6)
    metal = grad("3a3f55", top=1.35, bot=0.8)
    root = m.group("cannon", [-56, 0, 0])
    barrel = m.group("barrel", [-56, 16, 0], root)
    m.cube(root, [-70, 4, -9], [-44, 12, 9], grad("b0582a"), name="carriage")
    wheels = []
    for s in (1, -1):
        g = m.group("wheel%s" % ("l" if s > 0 else "r"), [-58, 8, 10 * s], root)
        za, zb = (9.2, 11.8) if s > 0 else (-11.8, -9.2)
        for a in (0, 45):
            m.cube(g, [-66, 0, za], [-50, 16, zb], grad("7a3a14"), rot=[0, 0, a], origin=[-58, 8, 10 * s], name="wheel")
        wheels.append(g)
    m.cube(barrel, [-66, 11, -6], [-30, 21, 6], metal, name="barrel")
    m.cube(barrel, [-30, 10, -7], [-26, 22, 7], metal, name="muzzle")
    m.cube(barrel, [-48, 9.5, -6.5], [-44, 22.5, 6.5], grad("ffd21a"), name="band")
    m.cube(barrel, [-70, 13, -4], [-66, 19, 4], metal, name="breech")
    ball = m.group("ball", [-28, 16, 0])
    for a in (0, 45):
        m.cube(ball, [-33, 11, -5], [-23, 21, 5], grad("23232e", top=1.4, bot=0.9), rot=[a, a, 0], origin=[-28, 16, 0], name="ball")
    an.key(root, "position", (0, (-110, 0, 0)), (0.6, (0, 0, 0), "out"), (1.0, (0, 0, 0)), (1.03, (-10, 0, 0), "out"), (1.4, (0, 0, 0), "io"),
           (2.1, (0, 0, 0)), (2.6, (-140, 0, 0), "in"))
    for g in wheels:
        an.key(g, "rotation", (0, (0, 0, 0)), (0.6, (0, 0, -400), "out"), (1.03, (0, 0, -400)), (1.04, (0, 0, -330), "out"), (1.4, (0, 0, -400)),
               (2.1, (0, 0, -400)), (2.6, (0, 0, 0), "in"))
    an.key(barrel, "rotation", (0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.8, (0, 0, 10), "back"), (0.95, (0, 0, 8)), (1.03, (0, 0, 22), "out"),
           (1.4, (0, 0, 8), "io"), (2.1, (0, 0, 8)), (2.25, (0, 0, 0)))
    an.key(barrel, "scale", (0, 1), (0.85, 1.0), (0.98, (0.88, 1.2, 1.2), "out"), (1.03, (1.15, 0.85, 0.85), "in"), (1.2, 1.0, "back"))
    an.key(ball, "scale", (0, 0), (1.0, 0, "step"), (1.02, 1.0, "step"), (1.15, 1.0), (1.16, 0.0, "step"))
    an.key(ball, "position", (0, (0, 0, 0)), (1.02, (0, 0, 0)), (1.15, (30, 2, 0), "lin"))
    an.ev(0.05, "s entity.minecart.riding 0.6 0.8")
    an.ev(0.75, "s entity.creeper.primed 1 1.2")
    an.ev(0.75, "p flame 6 -70 20 0 2 0.05")
    an.ev(1.02, "s entity.generic.explode 1 1.4")
    an.ev(1.02, "p smoke 20 -26 18 0 6 0.12")
    an.ev(1.02, "p flame 12 -26 18 0 4 0.15")
    an.ev(gone, "s entity.firework_rocket.large_blast 1 1.0")
    an.ev(gone, "p confetti 70 0 16 0 10 0.5")
    an.ev(gone, "p firework 26 0 18 0 8 0.3")
    an.ev(gone, "p sparkle 18 0 18 0 8 0.25")
    return m, an


EFFECTS = [("piano", "Piano Drop", make_piano), ("tornado", "Tornado", make_tornado), ("gift", "Surprise Gift", make_gift),
           ("cannon", "Cannon", make_cannon)]
