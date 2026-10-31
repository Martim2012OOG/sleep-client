"""Pets for Kubo: turns pet models into rigs the game animates like a real mob (walk cycle, tail wag, wing flaps...).

Called by import_cosmetics.py. Output per pet:
  pets/<id>.json            bones (role, pivot, parent) with their quads; pixels, y up, feet on y = 0, front = -z
  textures/cosmetics/pet_<id>.png
  cosmetics/pet3d_<id>.bin  a still copy next to the shoulder (only for the old 2D menu preview)

Roles the game knows: body, head, leg_fl, leg_fr, leg_bl, leg_br, arm_l, arm_r, tail, ear_l, ear_r, wing_l, wing_r, top.
Left = -x, right = +x (the pet looks towards -z).
"""
import json
import math
import os
import random
import struct

from PIL import Image

import import_cosmetics as ic

PARENT = {"head": "body", "leg_fl": "body", "leg_fr": "body", "leg_bl": "body", "leg_br": "body", "arm_l": "body",
          "arm_r": "body", "tail": "body", "wing_l": "body", "wing_r": "body", "ear_l": "head", "ear_r": "head", "top": "head"}
REHIGA = ic.REHIGA


# ---------------------------------------------------------------------------------------------------- rig helpers
class Rig:
    def __init__(self, pid, name, kind, scale, credit, sounds, **extra):
        self.id, self.name, self.kind, self.scale, self.credit, self.sounds = pid, name, kind, scale, credit, sounds
        self.extra = extra
        self.bones = {}  # role -> dict(pivot, quads)
        self.textures = []

    def add(self, role, quads, pivot=None):
        b = self.bones.setdefault(role, {"pivot": pivot, "quads": []})
        b["quads"] += quads
        if pivot is not None:
            b["pivot"] = pivot


def bbox(quads):
    pts = [p for q in quads for p in q.v]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def normalise(rig):
    """Feet on y = 0, centred on x / z, every bone gets a pivot."""
    allq = [q for b in rig.bones.values() for q in b["quads"]]
    (x0, y0, z0), (x1, y1, z1) = bbox(allq)
    dx, dy, dz = -(x0 + x1) / 2, -y0, -(z0 + z1) / 2
    for b in rig.bones.values():
        for q in b["quads"]:
            q.v = [(p[0] + dx, p[1] + dy, p[2] + dz, p[3], p[4]) for p in q.v]
        if b["pivot"] is not None:
            b["pivot"] = (b["pivot"][0] + dx, b["pivot"][1] + dy, b["pivot"][2] + dz)
    for role, b in rig.bones.items():
        if b["pivot"] is None:
            (a0, b0, c0), (a1, b1, c1) = bbox(b["quads"])
            cx, cz = (a0 + a1) / 2, (c0 + c1) / 2
            if role.startswith("leg") or role.startswith("arm"):
                b["pivot"] = (cx, b1, cz)  # hip / shoulder: top of the limb
            elif role == "head":
                b["pivot"] = (cx, b0 + (b1 - b0) * 0.15, c1 - (c1 - c0) * 0.25)  # neck: low, at the back of the head
            elif role == "tail":
                b["pivot"] = (cx, (b0 + b1) / 2, c0)  # root: the end touching the body
            elif role.startswith("wing"):
                b["pivot"] = (a1 if role == "wing_l" else a0, (b0 + b1) / 2, cz)  # shoulder: the inner edge
            elif role.startswith("ear") or role == "top":
                b["pivot"] = (cx, b0, cz)
            else:
                b["pivot"] = (cx, b0, cz)


def write(rig, assets, previews):
    normalise(rig)
    atlas, rects = ic.build_atlas(rig.textures)
    W, H = atlas.size
    tex = "pet_" + rig.id
    atlas.save(os.path.join(assets, "textures", "cosmetics", tex + ".png"), optimize=True)
    order = ["body"] + [r for r in rig.bones if r != "body"]
    bones = []
    still = []
    for role in order:
        if role not in rig.bones:
            continue
        b = rig.bones[role]
        verts = []
        for q in b["quads"]:
            for p in ic.atlas_uv(q, rects, W, H):
                verts += [round(p[0], 4), round(p[1], 4), round(p[2], 4), round(p[3], 6), round(p[4], 6)]
            still.append(q)
        parent = PARENT.get(role, None) if role != "body" else None
        if parent not in rig.bones:
            parent = "body" if role != "body" and "body" in rig.bones else None
        bones.append({"role": role, "pivot": [round(v, 4) for v in b["pivot"]], "parent": parent, "quads": verts})
    (x0, y0, z0), (x1, y1, z1) = bbox(still)
    out = {"id": rig.id, "name": rig.name, "kind": rig.kind, "scale": rig.scale, "texture": tex,
           "size": [round(x1 - x0, 3), round(y1 - y0, 3), round(z1 - z0, 3)], "sounds": rig.sounds, "bones": bones}
    out.update(rig.extra)
    os.makedirs(os.path.join(assets, "pets"), exist_ok=True)
    json.dump(out, open(os.path.join(assets, "pets", rig.id + ".json"), "w"), separators=(",", ":"))
    # still copy for the 2D menu fallback: next to the shoulder like the old pets (player space, y down, pixels)
    s = min(1.0, 10.0 / max(1e-3, y1 - y0)) * 0.9
    m = ic.chain(ic.mat_s(s, -s, s))
    quads = [[ic.apply(m, p[:3]) + (p[3], p[4]) for p in ic.atlas_uv(q, rects, W, H)] for q in still]
    ic.write_bin(os.path.join(assets, "cosmetics", "pet3d_" + rig.id + ".bin"), [(ic.PET, quads)])
    if previews is not None:
        prev = [[(p[0] * 0.6 - 12, -p[1] * 0.6 + 12, p[2] * 0.6, p[3], p[4]) for p in ic.atlas_uv(q, rects, W, H)] for q in still]
        previews.append(("pet " + rig.id, [prev], atlas))
    return {"id": rig.id, "name": rig.name, "category": "Pets", "geometry": "pet3d_" + rig.id, "texture": tex, "anim": "pet",
            "pet": rig.id, "credit": rig.credit, "textureSize": [W, H], "source": "pack"}


def tag_elements(mesh_quads, _unused=None):
    """Groups the quads of a Java model by element index (stored in q.bone as 'e<i>')."""
    by = {}
    for q in mesh_quads:
        by.setdefault(q.bone, []).append(q)
    return by


def load_java_by_element(path):
    """The model with every quad tagged by its element ('e<i>' in q.bone) and the raw JSON."""
    return ic.load_java(path, ic.pack_roots(path)), ic.read_json(path)


# ---------------------------------------------------------------------------------------------------- pack pets
def corgi(Z):
    p = ic.find(Z, "corgi_stand", ".bbmodel")
    m = ic.load_bbmodel(p, groups_as_bones=True)
    rig = Rig("corgi", "Corgi", "walk", 1.15, "Corgi by its Blockbench author (corgi_stand)",
              {"ambient": "entity.wolf.ambient", "happy": "entity.wolf.pant", "pitch": 1.5})
    rig.textures = m.textures
    legs = [q for q in m.quads if q.bone == "legs"]
    for q in m.quads:
        if q.bone == "body":
            rig.add("body", [q])
        elif q.bone == "head":
            rig.add("head", [q])
        elif q.bone == "tail":
            rig.add("tail", [q])
    # the 4 leg cubes: front = small z (the head is at -z), left = small x
    cubes = {}
    for q in legs:
        cx = sum(p[0] for p in q.v) / 4
        cz = sum(p[2] for p in q.v) / 4
        cubes.setdefault((cx < 8, cz < 8), []).append(q)
    for (left, front), qs in cubes.items():
        rig.add("leg_" + ("f" if front else "b") + ("l" if left else "r"), qs)
    rig.bones["head"]["pivot"] = (8, 5, 4.5)
    rig.bones["tail"]["pivot"] = (8, 5, 12)
    return rig


def plushie(Z, pid, name, file):
    p = ic.find(Z, "fnafplushiesfreeversion", "Nexo/pack/assets/fnafpack/models/plush/" + file + ".json")
    m, d = load_java_by_element(p)
    rig = Rig(pid, name, "hop", 0.55, "FNAF Plushies (free version) resource pack",
              {"ambient": "entity.allay.ambient_without_item", "happy": "entity.allay.item_given", "pitch": 0.9})
    rig.textures = m.textures
    els = d["elements"]
    for key, qs in tag_elements(m.quads, None).items():
        e = els[int(key[1:])]
        f, t = e["from"], e["to"]
        cy = (f[1] + t[1]) / 2
        cx = (f[0] + t[0]) / 2
        if t[1] <= 4.6 and f[2] < 2:
            role = "leg_fl" if cx < 8 else "leg_fr"
        elif cy < 10.2 and (t[0] <= 4.2 or f[0] >= 11.8) and (t[0] - f[0]) <= 6:
            role = "arm_l" if cx < 8 else "arm_r"
        elif cy >= 10.2:
            role = "head"
        else:
            role = "body"
        rig.add(role, qs)
    rig.bones["head"]["pivot"] = (8, 10, 8)
    return rig


def mini_sus(Z, colour):
    p = ic.find(Z, "[Rehiga]mini_sus", "Oraxen/pack/models", "mini_sus_" + colour + ".json")
    if p is None:
        return None
    m, d = load_java_by_element(p)
    rig = Rig("mini_sus_" + colour, "Mini Crewmate (" + colour.capitalize() + ")", "waddle", 1.6, REHIGA,
              {"ambient": "block.note_block.bit", "happy": "entity.player.levelup", "pitch": 1.8})
    rig.textures = m.textures
    els = d["elements"]
    for key, qs in tag_elements(m.quads, None).items():
        e = els[int(key[1:])]
        f, t = e["from"], e["to"]
        if t[1] <= 2.01:
            rig.add("leg_fl" if (f[0] + t[0]) / 2 < 8 else "leg_fr", qs)
        else:
            rig.add("body", qs)
    # the visor is at z 5..6, the backpack at z 8..9: the front is -z, like every other pet
    return rig


def from_bin(assets, pid, name, kind, scale, credit, sounds, classify):
    """Old still pets (.bin, one piece): split into cubes and give each a role."""
    b = open(os.path.join(assets, "cosmetics", "pet_" + pid + ".bin"), "rb").read()
    o = 12
    o += 1
    tris, = struct.unpack(">i", b[o:o + 4])
    o += 4
    fl = struct.unpack(">%df" % (tris * 15), b[o:o + tris * 60])
    T = [fl[i * 15:(i + 1) * 15] for i in range(tris)]
    par = list(range(tris))

    def f(x):
        while par[x] != x:
            par[x] = par[par[x]]
            x = par[x]
        return x
    key = {}
    for i, t in enumerate(T):
        for k in range(3):
            p = tuple(round(t[k * 5 + j], 3) for j in range(3))
            if p in key:
                par[f(i)] = f(key[p])
            else:
                key[p] = i
    clusters = {}
    for i in range(tris):
        clusters.setdefault(f(i), []).append(i)
    tex = Image.open(os.path.join(assets, "textures", "cosmetics", {"pug": "pug", "mowo": "mobloom"}[pid] + ".png")).convert("RGBA")
    rig = Rig(pid, name, kind, scale, credit, sounds)
    rig.textures = [tex]
    for ids in clusters.values():
        quads = []
        for i in ids:
            t = T[i]
            # back to y up; one triangle = a quad with its last corner repeated (the game draws quads)
            v = [(t[k * 5], -t[k * 5 + 1], t[k * 5 + 2], t[k * 5 + 3], t[k * 5 + 4]) for k in range(3)]
            quads.append(ic.Quad(v + [v[2]], 0))
        (x0, y0, z0), (x1, y1, z1) = bbox(quads)
        rig.add(classify(x0, y0, z0, x1, y1, z1), quads)
    return rig


def pug_role(x0, y0, z0, x1, y1, z1):
    if y1 <= 3.7:
        return ("leg_f" if z1 < 0 else "leg_b") + ("l" if x1 < 0.1 else "r")
    if z0 > 5:
        return "tail"
    if z1 < -1.2 or (z1 <= -4.7 and y0 > 7):
        return "head"
    return "body"


def mowo_role(x0, y0, z0, x1, y1, z1):
    if y1 <= 2.8:
        return "leg_fl" if x1 < 0.1 else "leg_fr"
    if y0 >= 8.9:
        return "top"
    if (x1 - x0) <= 1.2 and z0 < -4:
        return "arm_l" if x1 < 0 else "arm_r"
    return "body"


# ---------------------------------------------------------------------------------------------------- original pets
class Painter:
    """Builds a box-UV texture: every cube gets its own patch, every face is painted by a function."""

    def __init__(self, size=64):
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.x = 0
        self.y = 0
        self.row = 0
        self.size = size

    def patch(self, w, h):
        if self.x + w > self.size:
            self.x = 0
            self.y += self.row
            self.row = 0
        if self.y + h > self.img.height:
            grown = Image.new("RGBA", (self.size, self.img.height * 2), (0, 0, 0, 0))
            grown.paste(self.img, (0, 0))
            self.img = grown
        r = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return r


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def cube(painter, frm, to, base, faces=None, noise=0.07, rot=None, seed=0, quads_out=None):
    """A cube with a painted texture. faces: {name: fn(img, x0, y0, w, h)} draws details on top of the base colour."""
    rnd = random.Random(seed)
    w, h, d = [max(1, int(round(to[i] - frm[i]))) for i in range(3)]
    sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    light = {"up": 1.12, "north": 1.0, "south": 0.92, "east": 0.96, "west": 0.96, "down": 0.78}
    faces_uv = {}
    rects = {}
    for name, (fw, fh) in sizes.items():
        x, y = painter.patch(fw, fh)
        rects[name] = (x, y, fw, fh)
        px = painter.img.load()
        for yy in range(fh):
            for xx in range(fw):
                k = light[name] * (1 + rnd.uniform(-noise, noise))
                if name not in ("up", "down"):
                    k *= 1.04 - 0.08 * yy / max(1, fh - 1)  # a little darker at the bottom
                px[x + xx, y + yy] = shade(base, k)
        if faces and faces.get(name):
            faces[name](painter.img, x, y, fw, fh)
        faces_uv[name] = {"uv": [x, y, x + fw, y + fh], "texture": 0}
    rm = ic.mat_id() if rot is None else rot
    return faces_uv, rm, (frm, to)


def bake(painter, specs):
    """specs: list of (role, frm, to, faces_uv, rot). Turns the painted cubes into quads (uv in the final texture size)."""
    S = painter.img.size
    out = []
    for role, (frm, to), faces_uv, rm in specs:
        qs = ic.cube_quads(frm, to, faces_uv, rm, lambda t: (float(S[0]), float(S[1])), lambda r: 0)
        out.append((role, qs))
    return out


def eyes(col=(20, 20, 26, 255), shine=(255, 255, 255, 255), gap=1, size=1, y_from_top=1, white=None):
    def f(img, x, y, w, h):
        px = img.load()
        cy = y + y_from_top
        lx = x + max(0, w // 2 - gap - size)
        rx = x + min(w - size, w // 2 + gap - (1 if w % 2 == 0 else 0))
        for ex in (lx, rx):
            for yy in range(size + (1 if size == 1 else 0)):
                for xx in range(size):
                    px[ex + xx, cy + yy] = col
            if white:
                px[ex + (size if ex == lx else -1), cy] = white
            px[ex, cy] = shine if size > 1 else px[ex, cy]
    return f


def stripes(col, every=2, start=1):
    def f(img, x, y, w, h):
        px = img.load()
        for yy in range(h):
            for xx in range(w):
                if ((xx + start) // every) % 2 == 0:
                    px[x + xx, y + yy] = shade(col, 1 - 0.05 * yy / max(1, h))
    return f


def stripes_rows(col, every=2, start=1, horizontal_faces=False):
    def f(img, x, y, w, h):
        px = img.load()
        n = w if horizontal_faces else w
        for xx in range(n):
            if ((xx + start) // every) % 2 == 0:
                for yy in range(h):
                    px[x + xx, y + yy] = col
    return f


def combine(*fns):
    def f(img, x, y, w, h):
        for fn in fns:
            fn(img, x, y, w, h)
    return f


def membrane(edge, inner):
    def f(img, x, y, w, h):
        px = img.load()
        for yy in range(h):
            for xx in range(w):
                # a scalloped bottom edge, finger bones every 3 texels
                if yy == h - 1 and xx % 3 == 1:
                    px[x + xx, y + yy] = (0, 0, 0, 0)
                elif xx % 3 == 0 or yy == 0:
                    px[x + xx, y + yy] = edge
                else:
                    px[x + xx, y + yy] = shade(inner, 1 - 0.15 * yy / max(1, h))
    return f


def clear(alpha_col):
    def f(img, x, y, w, h):
        px = img.load()
        for yy in range(h):
            for xx in range(w):
                px[x + xx, y + yy] = alpha_col if (xx == 0 or yy == 0 or xx == w - 1 or (xx + yy) % 4 == 0) else shade(alpha_col, 1.15)
    return f


def rig_from(pid, name, kind, scale, credit, sounds, painter, specs, **extra):
    rig = Rig(pid, name, kind, scale, credit, sounds, **extra)
    rig.textures = [painter.img]
    for role, qs in bake(painter, specs):
        rig.add(role, qs)
    return rig


def bee():
    P = Painter(64)
    Y, B, W = (250, 200, 40, 255), (40, 32, 28, 255), (225, 240, 255, 255)
    specs = []

    def c(role, f, t, base, faces=None, rot=None, seed=0):
        fu, rm, ft = cube(P, f, t, base, faces, rot=rot, seed=seed)
        specs.append((role, ft, fu, rm))
    st = stripes(B, 2, 0)
    c("body", (-3.5, 2, -4), (3.5, 8, 4), Y, {"east": st, "west": st, "up": stripes_rows(B, 2, 0), "down": stripes_rows(B, 2, 0),
                                               "north": eyes((30, 25, 30, 255), (255, 255, 255, 255), 2, 2, 1), "south": None}, seed=1)
    c("tail", (-0.5, 4, 4), (0.5, 5, 6), (60, 50, 45, 255), seed=2)  # stinger
    c("top", (-2, 8, -4), (-1, 10.5, -3), B, seed=3)  # antennae
    c("top", (1, 8, -4), (2, 10.5, -3), B, seed=4)
    c("wing_l", (-8.5, 8, -2), (-1.5, 8.01, 3), W, {"up": clear((205, 230, 255, 255)), "down": clear((190, 215, 240, 255))}, seed=5)
    c("wing_r", (1.5, 8, -2), (8.5, 8.01, 3), W, {"up": clear((205, 230, 255, 255)), "down": clear((190, 215, 240, 255))}, seed=6)
    c("leg_fl", (-2.5, 0.5, -2), (-1.5, 2, -1), B, seed=7)
    c("leg_fr", (1.5, 0.5, -2), (2.5, 2, -1), B, seed=8)
    c("leg_bl", (-2.5, 0.5, 1), (-1.5, 2, 2), B, seed=9)
    c("leg_br", (1.5, 0.5, 1), (2.5, 2, 2), B, seed=10)
    return rig_from("bee", "Buzzy Bee", "fly", 0.9, "Original Kubo art", {"ambient": "entity.bee.loop", "happy": "entity.bee.pollinate", "pitch": 1.3},
                    P, specs, flap=2.6, hover=1.4)


def dragon():
    P = Painter(64)
    G, D, Bl, H, E = (80, 190, 120, 255), (40, 120, 80, 255), (250, 210, 120, 255), (245, 240, 220, 255), (255, 220, 70, 255)
    specs = []

    def c(role, f, t, base, faces=None, seed=0):
        fu, rm, ft = cube(P, f, t, base, faces, seed=seed)
        specs.append((role, ft, fu, rm))

    def belly(img, x, y, w, h):
        px = img.load()
        for yy in range(h):
            for xx in range(1, w - 1):
                px[x + xx, y + yy] = shade(Bl, 1 - 0.04 * (yy % 2))

    def face(img, x, y, w, h):
        px = img.load()
        for ex in (x + 1, x + w - 3):
            px[ex, y + 2] = (255, 255, 255, 255)
            px[ex + 1, y + 2] = (20, 20, 20, 255)
            px[ex, y + 3] = (20, 20, 20, 255)
            px[ex + 1, y + 3] = E
    c("body", (-3, 3, -3), (3, 8, 5), G, {"down": belly, "north": belly}, seed=11)
    c("head", (-2.5, 6, -8), (2.5, 10.5, -3), G, {"north": face}, seed=12)
    c("head", (-1.5, 6, -10), (1.5, 8.5, -8), G, {"north": eyes((40, 60, 40, 255), (40, 60, 40, 255), 0, 1, 0)}, seed=13)  # snout + nostrils
    c("ear_l", (-2.5, 10.5, -5), (-1.5, 13, -4), H, seed=14)  # horns
    c("ear_r", (1.5, 10.5, -5), (2.5, 13, -4), H, seed=15)
    c("tail", (-1.5, 4, 5), (1.5, 6.5, 9), G, seed=16)
    c("tail", (-1, 4.5, 9), (1, 6, 12), D, seed=17)
    c("tail", (-0.5, 5.5, 11), (0.5, 7.5, 12.5), (240, 90, 70, 255), seed=18)  # tail spike
    c("top", (-0.5, 8, -1), (0.5, 9.5, 3), (240, 90, 70, 255), seed=19)  # back spikes (bob with the body)
    mem = membrane(D, (110, 220, 150, 255))
    c("wing_l", (-11, 7.5, -1), (-3, 7.6, 5), D, {"up": mem, "down": mem}, seed=20)
    c("wing_r", (3, 7.5, -1), (11, 7.6, 5), D, {"up": mem, "down": mem}, seed=21)
    c("leg_fl", (-3, 0, -2.5), (-1, 3, -0.5), D, seed=22)
    c("leg_fr", (1, 0, -2.5), (3, 3, -0.5), D, seed=23)
    c("leg_bl", (-3, 0, 2), (-1, 3, 4), D, seed=24)
    c("leg_br", (1, 0, 2), (3, 3, 4), D, seed=25)
    rig = rig_from("baby_dragon", "Baby Dragon", "fly", 0.85, "Original Kubo art",
                   {"ambient": "entity.ender_dragon.ambient", "happy": "entity.blaze.shoot", "pitch": 2.0, "volume": 0.25},
                   P, specs, flap=0.9, hover=1.8, particles="flame")
    return rig


def bat():
    P = Painter(64)
    F, M, Ear = (70, 55, 50, 255), (50, 38, 36, 255), (220, 130, 140, 255)
    specs = []

    def c(role, f, t, base, faces=None, seed=0):
        fu, rm, ft = cube(P, f, t, base, faces, seed=seed)
        specs.append((role, ft, fu, rm))

    def face(img, x, y, w, h):
        px = img.load()
        px[x + 1, y + 1] = (255, 60, 60, 255)
        px[x + w - 2, y + 1] = (255, 60, 60, 255)
        px[x + w // 2 - 1, y + h - 2] = (255, 255, 255, 255)  # tiny fangs
        px[x + w // 2, y + h - 2] = (255, 255, 255, 255)
    c("body", (-2.5, 2, -2), (2.5, 7, 3), F, seed=31)
    c("head", (-2.5, 7, -2.5), (2.5, 11, 2), F, {"north": face}, seed=32)
    c("ear_l", (-2.5, 11, -1), (-0.5, 14, 0), M, {"north": lambda i, x, y, w, h: i.putpixel((x + 1, y + 1), Ear)}, seed=33)
    c("ear_r", (0.5, 11, -1), (2.5, 14, 0), M, {"north": lambda i, x, y, w, h: i.putpixel((x, y + 1), Ear)}, seed=34)
    mem = membrane(M, (95, 70, 70, 255))
    c("wing_l", (-10, 6.5, -1), (-2.5, 6.6, 4), M, {"up": mem, "down": mem}, seed=35)
    c("wing_r", (2.5, 6.5, -1), (10, 6.6, 4), M, {"up": mem, "down": mem}, seed=36)
    c("leg_bl", (-1.5, 0, 0), (-0.5, 2, 1), M, seed=37)
    c("leg_br", (0.5, 0, 0), (1.5, 2, 1), M, seed=38)
    return rig_from("bat", "Night Bat", "fly", 0.8, "Original Kubo art", {"ambient": "entity.bat.ambient", "happy": "entity.bat.takeoff", "pitch": 1.4, "volume": 0.2},
                    P, specs, flap=2.0, hover=2.2, erratic=1)


def ghost():
    P = Painter(64)
    Wc, S = (238, 242, 255, 255), (200, 210, 240, 255)
    specs = []

    def c(role, f, t, base, faces=None, seed=0):
        fu, rm, ft = cube(P, f, t, base, faces, noise=0.03, seed=seed)
        specs.append((role, ft, fu, rm))

    def face(img, x, y, w, h):
        px = img.load()
        for ex in (x + 2, x + w - 4):
            for yy in range(3):
                px[ex, y + 3 + yy] = (25, 25, 45, 255)
                px[ex + 1, y + 3 + yy] = (25, 25, 45, 255)
            px[ex, y + 3] = (255, 255, 255, 255)
        for xx in range(w // 2 - 1, w // 2 + 1):
            px[x + xx, y + 8] = (25, 25, 45, 255)  # little "o" mouth
            px[x + xx, y + 9] = (25, 25, 45, 255)
        px[x + 1, y + 7] = (255, 170, 190, 255)  # blush
        px[x + w - 2, y + 7] = (255, 170, 190, 255)
    c("body", (-4, 3, -4), (4, 13, 4), Wc, {"north": face}, seed=41)
    c("head", (-3, 13, -3), (3, 14, 3), Wc, seed=42)  # round top
    for i, (x, z) in enumerate([(-4, -4), (0, -4), (-2, 0), (2, 0), (-4, 2), (0, 2)]):
        c("tail", (x, 1 + (i % 2), z), (x + 4, 3, z + 2), S, seed=43 + i)  # wavy skirt
    c("arm_l", (-6, 6, -1), (-4, 8, 1), Wc, seed=50)
    c("arm_r", (4, 6, -1), (6, 8, 1), Wc, seed=51)
    return rig_from("ghost", "Boo the Ghost", "float", 0.8, "Original Kubo art",
                    {"ambient": "entity.allay.ambient_with_item", "happy": "entity.allay.item_thrown", "pitch": 0.7, "volume": 0.4},
                    P, specs, hover=1.6, glow=1)


# ---------------------------------------------------------------------------------------------------- run
def run(Z, assets, previews, only=None):
    rigs = []

    def take(r):
        if r is not None and (not only or __import__("re").search(only, r.id)):
            rigs.append(r)
    take(corgi(Z))
    for pid, name, file in [("plush_freddy", "Freddy Plush", "freddy_plush"), ("plush_bonnie", "Bonnie Plush", "bonnie_plush"),
                            ("plush_chica", "Chica Plush", "chica_plush"), ("plush_foxy", "Foxy Plush", "foxy_plush"),
                            ("plush_golden_freddy", "Golden Freddy Plush", "golden_freddy_plush"), ("plush_puppet", "Puppet Plush", "the_puppet_plush")]:
        take(plushie(Z, pid, name, file))
    for c in ["white", "black", "red", "blue", "green", "purple", "yellow"]:
        take(mini_sus(Z, c))
    take(from_bin(assets, "pug", "Pug", "walk", 1.0,
                  "Minecraft Pug by Th3CrazyPng (CC-BY-4.0, https://sketchfab.com/3d-models/minecraft-pug-3f54ca18dfad4ebeb6be1e6e1c82b354)",
                  {"ambient": "entity.wolf.ambient", "happy": "entity.wolf.pant", "pitch": 1.3}, pug_role))
    take(from_bin(assets, "mowo", "Mowo", "hop", 0.85,
                  "Mobloom Mowo by IMO (CC-BY-4.0, https://sketchfab.com/3d-models/mobloom-mowo-a662f916ba134c2bafdd6411eee9310f)",
                  {"ambient": "block.azalea_leaves.place", "happy": "entity.allay.item_given", "pitch": 1.4}, mowo_role))
    for f in (bee, dragon, bat, ghost):
        take(f())
    out = []
    for r in rigs:
        e = write(r, assets, previews)
        if r.id in ("pug", "mowo"):
            # keep the shop ids the site already sells: the old entries now point at the animated pet
            e["id"] = r.id
            e["geometry"] = "pet_" + r.id
            e["texture"] = {"pug": "pug", "mowo": "mobloom"}[r.id]
            e.pop("source")
        out.append(e)
        print("  pet", r.id, {k: len(v["quads"]) for k, v in r.bones.items()})
    return out
