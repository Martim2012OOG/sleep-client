"""Kill effects 25-33: short "finishers" (thunder, anvil, gravestone, balloons, ice, boxing glove, portal, sword rain, bed)."""
import math
from make_killfx import Model, Anim, add_victim, hexc, mul, h3, write_events_trail
from effects2 import grad


# ================================================================================================ 25 THUNDER STRIKE
def make_thunder():
    m = Model("thunder")
    an = Anim(2.0)
    gone = 0.92
    add_victim(m, an, gone, "up", scared=0.3, keys={"scale": [(0, 1), (0.7, 1), (0.76, (1.3, 1.08, 1.3), "out"), (0.84, (0.9, 1, 0.9)),
                                                             (0.9, (1.25, 1.05, 1.25)), (0.92, 0, "step")]})
    cloud = m.group("cloud", [0, 66, 0])
    dark = grad("4a4f66", top=1.25, bot=0.75)
    m.cube(cloud, [-18, 58, -8], [18, 68, 8], dark, name="cloud")
    m.cube(cloud, [-11, 66, -6], [9, 75, 6], grad("5b6180", top=1.25, bot=0.8), name="cloud_top")
    m.cube(cloud, [-26, 60, -5], [-15, 66, 5], dark, name="cloud_l")
    m.cube(cloud, [13, 60, -5.5], [25, 67, 5.5], dark, name="cloud_r")
    an.key(cloud, "scale", (0, 0), (0.1, 0), (0.5, 1, "back"), (1.5, 1), (1.95, 0, "in"))
    an.key(cloud, "position", (0, (0, 6, 0)), (0.5, (0, 0, 0), "out"), (0.7, (0, 0, 0)), (0.74, (0, -3, 0)), (0.9, (0, 0, 0), "back"))
    bolt = m.group("bolt", [0, 58, 0])
    yel = grad("ffe94a", top=1.1, bot=1.0)
    for i, (x0, x1, y0, y1) in enumerate(((-1, 4, 44, 58), (-6, 1, 32, 46), (-2, 5, 20, 34), (-7, 0, 8, 22), (-3, 3, 0, 10))):
        m.cube(bolt, [x0, y0, -1.5 + i * 0.05], [x1, y1, 1.5 - i * 0.05], yel, name="bolt")
    an.key(bolt, "scale", (0, 0), (0.699, 0, "step"), (0.7, 1, "step"), (0.78, (1.5, 1, 1.5), "out"), (0.84, 0, "step"), (0.87, 1, "step"),
           (0.97, (1.3, 1, 1.3), "out"), (1.0, 0, "step"), (1.04, (0.7, 1, 0.7), "step"), (1.1, 0, "step"))
    scorch = m.group("scorch", [0, 0, 0])
    m.cube(scorch, [-10, 0.1, -10], [10, 0.7, 10], grad("23202a", top=1.0, bot=0.9), name="scorch")
    m.cube(scorch, [-6, 0.75, -6], [6, 1.1, 6], grad("3b3340", top=1.0, bot=0.9), rot=[0, 45, 0], origin=[0, 0.9, 0], name="scorch_in")
    an.key(scorch, "scale", (0, 0), (0.91, 0, "step"), (0.92, (1.3, 1, 1.3), "step"), (1.05, 1, "out"), (1.5, 1), (1.95, 0, "in"))
    an.ev(0.15, "s entity.lightning_bolt.thunder 0.5 1.6")
    an.ev(0.7, "s entity.lightning_bolt.impact 1 1.0")
    an.ev(0.7, "s entity.lightning_bolt.thunder 1 1.0")
    an.ev(0.72, "p sparkle 26 0 20 0 8 0.3")
    an.ev(0.92, "p flame 22 0 6 0 8 0.12")
    an.ev(0.92, "ring smoke 14 0 1 0 8 0.1")
    write_events_trail(an, 1.0, 1.6, 0.15, lambda t: an.ev(t, "p smoke 2 0 3 0 6 0.03"))
    return m, an


# ================================================================================================ 26 ANVIL DROP
def make_anvil():
    m = Model("anvil")
    an = Anim(2.3)
    add_victim(m, an, 1.75, "up", scared=0.3, keys={"scale": [(0, 1), (0.9, 1), (0.96, (1.55, 0.07, 1.55), "out"), (1.7, (1.55, 0.07, 1.55)),
                                                             (1.75, 0, "step")]})
    root = m.group("anvil", [0, 0, 0])
    iron = grad("3d3f4c", top=1.4, bot=0.78)
    m.cube(root, [-10, 0, -7], [10, 4, 7], iron, name="base")
    m.cube(root, [-6, 4, -4], [6, 10, 4], grad("32343f", top=1.2, bot=0.8), name="neck")
    m.cube(root, [-13, 10, -8], [13, 18, 8], iron, name="top")
    m.cube(root, [13, 12, -5], [20, 17, 5], iron, name="horn")
    an.key(root, "position", (0, (0, 200, 0)), (0.3, (0, 200, 0)), (0.9, (0, 3, 0), "in"), (1.0, (0, 9, 0), "out"), (1.1, (0, 3, 0), "in"),
           (1.7, (0, 3, 0)), (1.75, (0, 0, 0)))
    an.key(root, "rotation", (0, (0, 0, -14)), (0.9, (0, 0, 0), "lin"), (1.0, (0, 0, 4)), (1.1, (0, 0, 0)))
    an.key(root, "scale", (0, 1), (0.9, (0.94, 1.08, 0.94)), (0.95, (1.2, 0.8, 1.2), "out"), (1.1, 1, "back"), (1.9, 1), (2.25, 0, "in"))
    an.ev(0.3, "s entity.phantom.swoop 1 0.5")
    an.ev(0.9, "s block.anvil.land 1 0.8")
    an.ev(0.9, "ring poof 22 0 1 0 16 0.16")
    an.ev(0.92, "p sparkle 14 0 6 0 12 0.25")
    an.ev(1.75, "p poof 10 0 2 0 8 0.06")
    an.ev(2.2, "s entity.item.pickup 0.6 0.6")
    return m, an


# ================================================================================================ 27 REST IN PEACE
def make_grave():
    m = Model("grave")
    an = Anim(2.8)
    gone = 1.3
    add_victim(m, an, gone, "down", scared=0.25,
               keys={"position": [(0, (0, 0, 0)), (0.45, (0, 0, 0)), (0.55, (0, 3, 0), "out"), (1.25, (0, -36, 0), "in")],
                     "rotation": [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.7, (0, 0, 5)), (0.9, (0, 0, -5)), (1.1, (0, 0, 4)), (1.25, (0, 0, 0))]})

    def stone(p, f, c, t):
        x, y, z = p
        v = 0.86 + h3(x, y, z, 3, 2.5) * 0.24 + (y / 26.0) * 0.12
        if f == "south" and ((abs(x) < 1.3 and 9 < y < 20) or (abs(x) < 4.5 and 14 < y < 16.6)):
            return mul(hexc("4d5160"), 0.9)  # engraved cross
        return mul(hexc("9aa0b0"), v)
    tomb = m.group("tomb", [0, 0, 0])
    m.cube(tomb, [-9, 0, -3], [9, 20, 3], stone, name="slab")
    m.cube(tomb, [-7, 20, -3], [7, 23, 3], stone, name="slab_top")
    m.cube(tomb, [-4, 23, -3], [4, 25, 3], stone, name="slab_tip")
    an.key(tomb, "position", (0, (0, -30, -8)), (1.2, (0, -30, -8)), (1.6, (0, 0, -8), "back"))
    an.key(tomb, "scale", (0, 1), (1.6, 1), (1.66, (1.1, 0.92, 1.1)), (1.76, 1, "back"), (2.45, 1), (2.75, 0, "in"))
    mound = m.group("mound", [0, 0, 6])
    m.cube(mound, [-9, 0, -4], [9, 2.4, 18], grad("6b4a2f", top=1.15, bot=0.85, spot=0.62, spot_col="55391f"), name="mound")
    m.cube(mound, [-7, 2.4, -2], [7, 3.6, 15], grad("7a5636", top=1.15, bot=0.9), name="mound_top")
    an.key(mound, "scale", (0, 0), (1.0, 0), (1.35, (1, 1, 1), "back"), (2.45, 1), (2.75, 0, "in"))
    flower = m.group("flower", [5, 3.6, 10], mound)
    m.cube(flower, [4.6, 3.6, 9.6], [5.4, 10, 10.4], grad("3fae4a"), name="stem")
    m.cube(flower, [3, 10, 8], [7, 13, 12], grad("ff5c8a", top=1.15, bot=0.9), name="petals")
    m.cube(flower, [4.2, 13, 9.2], [5.8, 13.8, 10.8], grad("ffe066"), name="centre")
    an.key(flower, "scale", (0, 0), (1.85, 0), (2.1, 1, "elastic"), (2.45, 1))
    write_events_trail(an, 0.55, 1.25, 0.1, lambda t: an.ev(t, "p dirt 6 0 2 0 7 0.12"))
    an.ev(0.55, "s block.gravel.break 1 0.7")
    an.ev(0.9, "s block.gravel.break 1 0.6")
    an.ev(1.25, "s block.rooted_dirt.place 1 0.6")
    an.ev(1.6, "s block.stone.place 1 0.6")
    an.ev(1.6, "ring poof 14 0 1 -8 10 0.08")
    an.ev(2.0, "s block.note_block.bell 0.7 0.6")
    an.ev(2.05, "p heart 1 5 16 10 1 0.02")
    return m, an


# ================================================================================================ 28 BALLOON RIDE
def make_balloons():
    m = Model("balloons")
    an = Anim(2.7)
    gone = 2.2
    sway = [(0, (0, 0, 0)), (0.75, (0, 0, 0)), (0.9, (0, 4, 0), "out")]
    rot = [(0, (0, 0, 0)), (0.8, (0, 0, 0))]
    for i in range(1, 9):
        t = 0.9 + i * 0.165
        sway.append((round(t, 3), (8 * math.sin(i * 1.3), 4 + (i / 8.0) ** 1.6 * 170, 0), "io"))
        rot.append((round(t, 3), (0, 0, 9 * math.sin(i * 1.3 + 1.2)), "io"))
    v = add_victim(m, an, gone, "up", scared=0.5, keys={"position": sway, "rotation": rot})
    bal = m.group("balloons", [0, 30, 0], v)
    for x0, y0, col, hi in ((-10, 52, "ff3d5a", "ff8fa0"), (-3, 58, "ffd21a", "fff0a0"), (4, 50, "3d9bff", "9ccbff")):
        base = hexc(col)

        def shiny(p, f, c, t, base=base, hi=hexc(hi)):
            k, j, w, h = t
            if f in ("south", "up") and 0.15 < k / float(w) < 0.4 and 0.15 < j / float(h) < 0.4:
                return hi
            return mul(base, 0.86 + ((p[1] - c["from"][1]) / 11.0) * 0.26)
        m.cube(bal, [x0, y0, -3.5], [x0 + 7, y0 + 10, 3.5], shiny, name="balloon")
        m.cube(bal, [x0 + 2.5, y0 - 1.2, -1], [x0 + 4.5, y0, 1], grad(col, top=0.85, bot=0.7), name="knot")
        m.cube(bal, [x0 + 3.2, 30, -0.3], [x0 + 3.8, y0 - 1.2, 0.3], grad("f2f2f2"), name="string")
    an.key(bal, "scale", (0, 0), (0.3, 0), (0.7, 1, "elastic"), (2.2, 1))
    an.key(bal, "rotation", *[(round(j * 0.3, 2), (0, 0, 5 if j % 2 else -5), "io") for j in range(9)])
    an.ev(0.3, "s entity.chicken.egg 1 1.6")
    an.ev(0.42, "s entity.chicken.egg 1 1.9")
    an.ev(0.54, "s entity.chicken.egg 1 2.0")
    an.ev(0.9, "s entity.bat.takeoff 0.7 1.4")
    an.ev(gone, "s entity.firework_rocket.blast 1 1.5")
    an.ev(gone, "p confetti 70 5 190 0 12 0.45")
    an.ev(gone, "p sparkle 16 5 190 0 10 0.2")
    return m, an


# ================================================================================================ 29 ICE SHATTER
def make_ice():
    m = Model("ice")
    an = Anim(2.2)
    gone = 0.72
    add_victim(m, an, gone, "down", scared=0.2)

    def ice(p, f, c, t):
        x, y, z = p
        v = 0.92 + h3(x, y, z, 7, 4.0) * 0.14
        col = hexc("9fdcff")
        if (int(x * 1.3 + y * 0.9 + 40) % 9) == 0:
            col = hexc("e6f7ff")  # frosty streaks
        if f == "south":  # the frozen player's shadow inside
            if (abs(x) < 4 and 24 < y < 32) or (abs(x) < 4 and 1 < y <= 24) or (abs(x) < 7.5 and 12 < y <= 23.5):
                col = hexc("5fa9e0")
        return mul(col, v)
    block = m.group("block", [0, 0, 0])
    m.cube(block, [-10, 0, -6], [10, 36, 6], ice, name="ice")
    an.key(block, "scale", (0, (1, 0, 1)), (0.3, (1, 0, 1)), (0.7, (1, 1, 1), "out"), (0.78, (1.06, 0.97, 1.06)), (0.9, 1, "back"), (1.5, 1), (1.5001, 0, "step"))
    an.key(block, "rotation", (0, (0, 0, 0)), (1.1, (0, 0, 0)), (1.16, (0, 0, 4)), (1.22, (0, 0, -4)), (1.28, (0, 0, 5)), (1.34, (0, 0, -5)), (1.4, (0, 0, 3)),
           (1.5, (0, 0, 0)))
    for i in range(10):
        a = i * 36 + 10
        cx, cy, cz = 6 * math.cos(math.radians(a)), 5 + (i * 7) % 30, 3 * math.sin(math.radians(a))
        s = 2.2 + (i % 3) * 1.1
        g = m.group("shard%d" % i, [cx, cy, cz])
        m.cube(g, [cx - s, cy - s, cz - s * 0.6], [cx + s, cy + s, cz + s * 0.6], grad(["9fdcff", "c9ecff", "7fc6f5"][i % 3], top=1.15, bot=0.85),
               rot=[20 * i, 35 * i, 15 * i], origin=[cx, cy, cz], name="shard")
        dx, dz, up = 34 * math.cos(math.radians(a)) + cx * 2, 24 * math.sin(math.radians(a)), 14 + (i % 4) * 7
        an.key(g, "scale", (0, 0), (1.4999, 0, "step"), (1.5, 1, "step"), (2.0, 1), (2.2, 0, "in"))
        an.key(g, "position", (0, (0, 0, 0)), (1.5, (0, 0, 0)), (1.75, (dx * 0.6, up, dz * 0.6), "out"), (2.1, (dx, -cy + s, dz), "in"))
        an.key(g, "rotation", (0, (0, 0, 0)), (1.5, (0, 0, 0)), (2.1, (300 + 40 * i, 200, 260), "lin"))
    an.ev(0.3, "s entity.player.hurt_freeze 1 0.8")
    an.ev(0.3, "s block.powder_snow.place 1 0.6")
    write_events_trail(an, 0.3, 0.7, 0.1, lambda t: an.ev(t, "p cloud 3 0 %d 0 8 0.03" % int((t - 0.3) * 80)))
    for tt in (1.16, 1.28, 1.4):
        an.ev(tt, "s block.glass.hit 1 1.3")
    an.ev(1.5, "s block.glass.break 1 0.8")
    an.ev(1.5, "p chunks 60 0 18 0 9 0.4")
    an.ev(1.5, "p sparkle 14 0 18 0 9 0.2")
    return m, an


# ================================================================================================ 30 KNOCKOUT
def make_glove():
    m = Model("glove")
    an = Anim(2.2)
    gone = 1.35
    add_victim(m, an, gone, "left", scared=0.25,
               keys={"position": [(0, (0, 0, 0)), (0.62, (0, 0, 0)), (1.35, (190, 120, 0), "out")],
                     "rotation": [(0, (0, 0, 0)), (0.62, (0, 0, 0)), (1.35, (0, 0, -900), "lin")],
                     "scale": [(0, 1), (0.62, 1), (0.66, (0.7, 1.2, 1), "out"), (0.8, 1), (1.2, 0.5, "in"), (1.35, 0, "in")]})
    root = m.group("glove", [0, 18, 0])
    red = grad("e8262f", top=1.2, bot=0.8)
    m.cube(root, [-18, 10, -7], [-4, 26, 7], red, name="fist")
    m.cube(root, [-12, 24, -4], [-5, 30, 6], grad("ff4a52", top=1.15, bot=0.85), name="thumb")
    m.cube(root, [-23, 12, -5.5], [-18, 24, 5.5], grad("f4f4f4", top=1.05, bot=0.85), name="cuff")
    for i in range(9):
        x = -27 - i * 5
        m.cube(root, [x, 14 if i % 2 else 12, -3], [x + 4, 22 if i % 2 else 24, 3], grad("9aa0b0" if i % 2 else "c3c8d6", top=1.2, bot=0.75), name="spring")
    m.cube(root, [-80, 6, -8], [-72, 30, 8], grad("5b4a8a", top=1.2, bot=0.8), name="box")
    an.key(root, "position", (0, (-170, 0, 0)), (0.2, (-170, 0, 0)), (0.42, (-58, 0, 0), "out"), (0.52, (-66, 0, 0), "io"), (0.62, (0, 0, 0), "in"),
           (0.72, (-10, 0, 0), "out"), (1.3, (-10, 0, 0)), (1.9, (-190, 0, 0), "in"))
    an.key(root, "scale", (0, 1), (0.52, (0.85, 1.1, 1.1)), (0.62, (1.35, 0.85, 0.85), "in"), (0.8, 1, "back"))
    an.ev(0.2, "s block.piston.extend 0.8 1.2")
    an.ev(0.62, "s entity.player.attack.knockback 1 0.8")
    an.ev(0.62, "s entity.player.attack.crit 1 0.7")
    an.ev(0.62, "p sparkle 20 4 22 0 5 0.35")
    an.ev(0.62, "p confetti 20 4 22 0 5 0.4")
    an.ev(1.3, "s entity.firework_rocket.twinkle 0.8 1.4")
    an.ev(1.33, "p sparkle 12 185 138 0 4 0.12")
    an.ev(1.35, "s block.piston.contract 0.8 1.2")
    return m, an


# ================================================================================================ 31 VOID PORTAL
def make_portal():
    m = Model("portal")
    an = Anim(2.4)
    gone = 1.25
    add_victim(m, an, gone, "down", scared=0.3,
               keys={"position": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (0.7, (0, 4, 0), "out"), (1.25, (0, -40, 0), "in")],
                     "rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (1.25, (0, 720, 0), "in")],
                     "scale": [(0, 1), (0.7, 1), (1.24, (0.35, 1, 0.35), "in"), (1.25, 0, "step")]})
    root = m.group("portal", [0, 0, 0])

    def swirl(base, hi):
        b, h = hexc(base), hexc(hi)

        def mat(p, f, c, t):
            x, y, z = p
            r, a = math.hypot(x, z), math.atan2(z, x)
            return h if math.sin(a * 3 + r * 0.55) > 0.55 else mul(b, 0.85 + h3(x, y, z, 4) * 0.25)
        return mat
    rings = []
    for i, (r, base, hi) in enumerate(((15, "2a0f4d", "5a24a8"), (11, "4a1a8c", "8b3dff"), (7, "7b2cbf", "e0aaff"), (3.5, "0b0614", "1a0d2e"))):
        g = m.group("ring%d" % i, [0, 0, 0], root)
        y = 0.15 + i * 0.5
        m.cube(g, [-r, y, -r], [r, y + 0.45, r], swirl(base, hi), name="ring")
        m.cube(g, [-r * 0.92, y + 0.02, -r * 0.92], [r * 0.92, y + 0.43, r * 0.92], swirl(base, hi), rot=[0, 45, 0], origin=[0, y, 0], name="ring_b")
        rings.append(g)
        an.key(g, "rotation", (0, (0, 0, 0)), (2.4, (0, (500 + i * 260) * (1 if i % 2 else -1), 0), "lin"))
    an.key(root, "scale", (0, (0, 1, 0)), (0.15, (0, 1, 0)), (0.6, (1, 1, 1), "back"), (1.25, 1), (1.32, (1.25, 1, 1.25), "out"), (1.5, 1), (1.9, 1),
           (2.35, (0, 1, 0), "in"))
    an.ev(0.15, "s block.portal.trigger 0.5 1.8")
    write_events_trail(an, 0.3, 1.9, 0.1, lambda t: an.ev(t, "p portal 8 0 14 0 14 0.2"))
    an.ev(0.7, "s entity.enderman.teleport 1 0.6")
    an.ev(1.25, "s entity.illusioner.mirror_move 1 0.7")
    an.ev(1.25, "ring sparkle 16 0 2 0 12 0.1")
    an.ev(2.3, "s block.beacon.deactivate 0.7 1.6")
    return m, an


# ================================================================================================ 32 SWORD RAIN
def make_swords():
    m = Model("swords")
    an = Anim(2.5)
    gone = 1.15
    add_victim(m, an, gone, "up", scared=0.3)
    steel = grad("d9e2ee", top=1.1, bot=0.82)
    spots = ((-18, -6, 0.45, 1.0, 10), (15, 5, 0.58, 1.0, -8), (-9, 9, 0.7, 1.0, -6), (20, -8, 0.82, 1.0, 12), (-22, 6, 0.94, 1.0, 5), (0, 0, 1.15, 1.9, 0))
    for i, (x, z, hit, k, tilt) in enumerate(spots):
        g = m.group("sword%d" % i, [x, 0, z])
        m.cube(g, [x - 1.2, 0, z - 0.45], [x + 1.2, 18, z + 0.45], steel, name="blade")
        m.cube(g, [x - 0.4, 0.5, z - 0.5], [x + 0.4, 17, z + 0.5], grad("ffffff", top=1.0, bot=0.9), name="edge")
        m.cube(g, [x - 4, 18, z - 0.9], [x + 4, 20, z + 0.9], grad("ffc93c", top=1.15, bot=0.85), name="guard")
        m.cube(g, [x - 0.9, 20, z - 0.8], [x + 0.9, 26, z + 0.8], grad("6b3a1e", top=1.1, bot=0.85), name="grip")
        m.cube(g, [x - 1.5, 26, z - 1.2], [x + 1.5, 28.5, z + 1.2], grad("ffc93c", top=1.15, bot=0.85), name="pommel")
        an.key(g, "scale", (0, k), (2.0 + i * 0.03, k), (2.3 + i * 0.03, 0, "in"))
        an.key(g, "position", (0, (0, 210, 0)), (hit - 0.3, (0, 210, 0)), (hit, (0, -4 * k, 0), "in"), (hit + 0.06, (0, -2.5 * k, 0), "out"),
               (hit + 0.12, (0, -4 * k, 0)))
        an.key(g, "rotation", (0, (0, 0, tilt)), (hit, (0, 0, tilt)), (hit + 0.05, (0, 0, tilt + 5)), (hit + 0.1, (0, 0, tilt - 4)), (hit + 0.16, (0, 0, tilt + 2)),
               (hit + 0.22, (0, 0, tilt)))
        an.ev(round(hit - 0.28, 3), "s item.trident.throw 0.6 %s" % (1.3 + i * 0.08))
        an.ev(hit, "s item.trident.hit_ground 0.8 %s" % (1.5 - i * 0.05))
        an.ev(hit, "p dirt 8 %d 1 %d 3 0.12" % (x, z))
    an.ev(gone, "s entity.player.attack.crit 1 0.6")
    an.ev(gone, "s entity.generic.explode 0.5 1.6")
    an.ev(gone, "ring poof 20 0 1 0 12 0.14")
    an.ev(gone, "p sparkle 26 0 18 0 8 0.3")
    return m, an


# ================================================================================================ 33 SWEET DREAMS
def make_bed():
    m = Model("bed")
    an = Anim(3.0)
    gone = 2.5
    # the player hops up and falls back onto the bed (head on the pillow, away from you), then sleeps
    add_victim(m, an, gone, "right", scared=0.3,
               keys={"position": [(0, (0, 0, 0)), (0.75, (0, 0, 0)), (0.95, (0, 16, 0), "out"), (1.2, (0, 9.5, 0), "in"), (1.26, (0, 8.6, 0)),
                                  (1.34, (0, 9.5, 0), "out"), (2.5, (0, 9.5, 0))],
                     "rotation": [(0, (0, 0, 0)), (0.8, (0, 0, 0)), (1.2, (-90, 0, 0), "io"), (2.5, (-90, 0, 0))],
                     "scale": [(0, 1), (2.3, 1), (2.5, 0, "in")]})
    bed = m.group("bed", [0, 0, -16])
    wood = grad("8a5a2b", top=1.15, bot=0.8)
    for x in (-10, 8):
        for z in (-36, 2):
            m.cube(bed, [x, 0, z], [x + 2, 4, z + 2], wood, name="leg")
    m.cube(bed, [-10.5, 4, -36.5], [10.5, 6, 4.5], wood, name="frame")
    m.cube(bed, [-10.5, 6, -38], [10.5, 17, -36.5], grad("7a4a20", top=1.2, bot=0.8), name="headboard")
    m.cube(bed, [-9.5, 6, -36], [9.5, 9, 4], grad("f1f1f6", top=1.05, bot=0.88), name="mattress")
    m.cube(bed, [-6.5, 9, -35], [6.5, 11.4, -27], grad("ffffff", top=1.05, bot=0.9), name="pillow")
    blanket = m.group("blanket", [0, 9, 4], bed)
    m.cube(blanket, [-10, 9.02, -22], [10, 14.2, 4.3], grad("5b6ee8", top=1.15, bot=0.82), name="blanket")
    m.cube(blanket, [-10.2, 13, -24], [10.2, 14.6, -21.5], grad("dfe4ff", top=1.05, bot=0.9), name="fold")
    an.key(bed, "position", (0, (170, 0, 0)), (0.25, (170, 0, 0)), (0.75, (0, 0, 0), "out"), (2.5, (0, 0, 0)))
    an.key(bed, "scale", (0, 1), (1.2, 1), (1.26, (1.06, 0.9, 1.06)), (1.4, 1, "back"), (2.3, 1), (2.5, 0, "in"))
    an.key(blanket, "scale", (0, (1, 1, 0.02)), (1.3, (1, 1, 0.02)), (1.65, (1, 1, 1), "out"), (1.8, (1, 1.06, 1)), (2.05, (1, 1, 1)), (2.3, (1, 1.06, 1)))
    an.ev(0.25, "s entity.minecart.riding 0.4 1.4")
    an.ev(0.9, "s entity.player.attack.sweep 0.5 1.4")
    an.ev(1.2, "s block.wool.place 1 0.6")
    an.ev(1.2, "ring poof 12 0 6 -16 12 0.06")
    for i, (tt, pitch) in enumerate(((1.45, 1.0), (1.7, 0.84), (1.95, 0.75), (2.2, 0.63))):
        an.ev(tt, "s block.note_block.chime 0.8 %s" % pitch)
        an.ev(tt, "p cloud 1 %d %d -30 1 0.02" % (4 + i * 3, 16 + i * 5))
    an.ev(2.5, "p poof 26 0 8 -16 14 0.1")
    an.ev(2.5, "p sparkle 14 0 12 -16 12 0.15")
    an.ev(2.5, "s entity.item.pickup 0.7 0.7")
    return m, an


EFFECTS = [("thunder", "Thunder Strike", make_thunder), ("anvil", "Anvil Drop", make_anvil), ("grave", "Rest in Peace", make_grave),
           ("balloons", "Balloon Ride", make_balloons), ("ice", "Ice Shatter", make_ice), ("glove", "Knockout", make_glove),
           ("portal", "Void Portal", make_portal), ("swords", "Sword Rain", make_swords), ("bed", "Sweet Dreams", make_bed)]
