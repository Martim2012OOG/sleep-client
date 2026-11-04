#!/usr/bin/env python3
"""
Builds the Kubo 3D kill effects as Blockbench projects (tools/killfx/models/<id>.bbmodel: open them in Blockbench,
Animate tab, play "animation.kill") and converts them for the client with bbmodel_to_killfx.py.

Units are Blockbench pixels (16 = 1 block). The victim stands at the origin, the viewer looks from +Z, so creatures
are built in profile along X. Animations are written with easing (anticipation, squash & stretch, overshoot) and
baked to 20 keyframes a second so Blockbench and the game play exactly the same curve.
"""
import base64, io, json, math, os, random, uuid as _uuid
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FPS = 20


def uid():
    return str(_uuid.uuid4())


def h3(x, y, z, s=0, cell=3.0):
    """Smooth noise in 0..1 (blurry blotches every few pixels, never per-texel grain)."""
    fx, fy, fz = x / cell, y / cell, z / cell
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = (v - math.floor(v) for v in (fx, fy, fz))
    tx, ty, tz = (t * t * (3 - 2 * t) for t in (tx, ty, tz))
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                acc += w * hraw(ix + dx, iy + dy, iz + dz, s)
    return acc


def hraw(x, y, z, s=0):
    n = (int(math.floor(x)) * 73856093) ^ (int(math.floor(y)) * 19349663) ^ (int(math.floor(z)) * 83492791) ^ (s * 2654435761)
    n &= 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def mul(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


# ----------------------------------------------------------------------------------------------- model builder
class Model:
    def __init__(self, name):
        self.name = name
        self.groups = {}  # name -> dict
        self.order = []
        self.cubes = []

    def group(self, name, origin, parent=None):
        g = {"name": name, "origin": list(origin), "parent": parent, "uuid": uid(), "children": []}
        self.groups[name] = g
        self.order.append(name)
        if parent:
            self.groups[parent]["children"].append(("g", name))
        return name

    def cube(self, group, a, b, mat, faces=None, rot=None, origin=None, name="cube", skin=None, inflate=0):
        c = {"name": name, "from": list(a), "to": list(b), "mat": mat, "faces": faces or {}, "rot": rot or [0, 0, 0],
             "origin": list(origin) if origin else [(a[i] + b[i]) / 2 for i in range(3)], "uuid": uid(), "group": group}
        if skin:
            c["skin"] = skin  # face -> [u0, v0, u1, v1] on the 64x64 player skin
        if inflate:
            c["inflate"] = inflate
        self.cubes.append(c)
        self.groups[group]["children"].append(("c", c["uuid"]))
        return c


FACES = ["north", "east", "south", "west", "up", "down"]


def corners(f, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[f]


DENSITY = 2  # texels per model pixel: smooth gradients instead of big pixels


def face_size(f, a, b):
    dx, dy, dz = (abs(b[i] - a[i]) * DENSITY for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[f]
    return max(1, int(math.ceil(w - 1e-6))), max(1, int(math.ceil(h - 1e-6)))


def paint_and_pack(model, width=512):
    """Gives every face its own texels (1 per pixel), painted by the cube's material at the texel's model position."""
    rects = []
    for c in model.cubes:
        if "skin" in c:
            continue
        for f in FACES:
            w, h = face_size(f, c["from"], c["to"])
            rects.append((c, f, w, h))
    # shelf packing, tallest first
    order = sorted(range(len(rects)), key=lambda i: -rects[i][3])
    x = y = shelf = 0
    pos = {}
    for i in order:
        c, f, w, h = rects[i]
        if x + w > width:
            x, y, shelf = 0, y + shelf, 0
        pos[i] = (x, y)
        x += w
        shelf = max(shelf, h)
    height = 16
    while height < y + shelf:
        height *= 2
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for i, (c, f, w, h) in enumerate(rects):
        ox, oy = pos[i]
        tl, tr, br, bl = corners(f, c["from"], c["to"])
        mat = c["faces"].get(f, c["mat"])
        for j in range(h):
            for k in range(w):
                u, v = (k + 0.5) / w, (j + 0.5) / h
                p = tuple(tl[n] + (tr[n] - tl[n]) * u + (bl[n] - tl[n]) * v for n in range(3))
                col = mat(p, f, c, (k, j, w, h))
                px[ox + k, oy + j] = col + (255,) if len(col) == 3 else col
        c.setdefault("uv", {})[f] = [ox, oy, ox + w, oy + h]
    return img


# ----------------------------------------------------------------------------------------------- animation
def ease(kind, u):
    u = max(0.0, min(1.0, u))
    if kind == "lin":
        return u
    if kind == "in":
        return u * u * u
    if kind == "out":
        return 1 - (1 - u) ** 3
    if kind == "io":
        return u * u * (3 - 2 * u)
    if kind == "back":  # overshoot and settle
        s = 2.2
        u -= 1
        return u * u * ((s + 1) * u + s) + 1
    if kind == "elastic":
        if u in (0, 1):
            return u
        return 2 ** (-10 * u) * math.sin((u * 10 - 0.75) * (2 * math.pi) / 3) + 1
    if kind == "step":
        return 1.0 if u >= 1 else 0.0
    return u


class Anim:
    def __init__(self, length):
        self.length = length
        self.tracks = {}  # (group, channel) -> [(t, (x,y,z), ease)]
        self.events = []  # (t, script)

    def key(self, group, ch, *keys):
        tr = self.tracks.setdefault((group, ch), [])
        for k in keys:
            t, v = k[0], k[1]
            e = k[2] if len(k) > 2 else "io"
            if not isinstance(v, (list, tuple)):
                v = (v, v, v) if ch == "scale" else (0, 0, v)
            tr.append((t, tuple(v), e))
        tr.sort(key=lambda k: k[0])

    def ev(self, t, script):
        self.events.append((round(t, 3), script))

    def sample(self, group, ch, t):
        tr = self.tracks.get((group, ch))
        if not tr:
            return None
        if t <= tr[0][0]:
            return tr[0][1]
        for i in range(len(tr) - 1):
            a, b = tr[i], tr[i + 1]
            if a[0] <= t <= b[0]:
                u = ease(b[2], (t - a[0]) / max(1e-9, b[0] - a[0]))
                return tuple(a[1][n] + (b[1][n] - a[1][n]) * u for n in range(3))
        return tr[-1][1]

    def baked(self, group, ch):
        tr = self.tracks[(group, ch)]
        times = set(round(k[0], 4) for k in tr)
        t = 0.0
        while t <= self.length + 1e-6:
            if tr[0][0] - 1e-6 <= t <= tr[-1][0] + 1e-6:
                times.add(round(t, 4))
            t += 1.0 / FPS
        out = []
        for t in sorted(times):
            v = self.sample(group, ch, t)
            out.append((t, v, "step" if False else "linear"))
        # drop keys that a straight line through their neighbours already gives
        keep = [out[0]]
        for i in range(1, len(out) - 1):
            p, c, n = keep[-1], out[i], out[i + 1]
            u = (c[0] - p[0]) / max(1e-9, n[0] - p[0])
            if any(abs(p[1][k] + (n[1][k] - p[1][k]) * u - c[1][k]) > 0.02 for k in range(3)):
                keep.append(c)
        if len(out) > 1:
            keep.append(out[-1])
        return keep


# ----------------------------------------------------------------------------------------------- .bbmodel writer
def png_uri(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def tex_entry(name, img, src):
    return {"path": "", "name": name, "folder": "", "namespace": "", "id": name, "group": "", "width": img.width, "height": img.height,
            "uv_width": img.width, "uv_height": img.height, "particle": False, "use_as_default": False, "layers_enabled": False,
            "sync_to_project": "", "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False, "uuid": uid(), "source": src}


def write_bbmodel(model, anim, img, path, tex_name, skin_img=None):
    src = png_uri(img)

    def outl(name):
        g = model.groups[name]
        ch = []
        for kind, ref in g["children"]:
            ch.append(outl(ref) if kind == "g" else ref)
        return {"name": name, "origin": g["origin"], "rotation": [0, 0, 0], "color": 0, "uuid": g["uuid"], "export": True, "isOpen": False,
                "locked": False, "visibility": True, "autouv": 0, "children": ch}

    elements = []
    for c in model.cubes:
        if "skin" in c:
            faces = {f: {"uv": c["skin"][f], "texture": 1} for f in FACES}
        else:
            faces = {f: {"uv": c["uv"][f], "texture": 0} for f in FACES}
        elements.append({"name": c["name"], "box_uv": False, "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                         "from": c["from"], "to": c["to"], "autouv": 0, "color": 0, "origin": c["origin"], "rotation": c["rot"],
                         "inflate": c.get("inflate", 0), "faces": faces, "type": "cube", "uuid": c["uuid"]})
    animators = {}
    for (gname, ch), _ in anim.tracks.items():
        g = model.groups[gname]
        a = animators.setdefault(g["uuid"], {"name": gname, "type": "bone", "keyframes": []})
        for t, v, interp in anim.baked(gname, ch):
            x, y, z = v
            if ch == "rotation":  # stored the Blockbench way: the bone turns by (-x, -y, +z)
                x, y, z = -x, -y, z
            elif ch == "position":
                x = -x
            a["keyframes"].append({"channel": ch, "data_points": [{"x": round(x, 3), "y": round(y, 3), "z": round(z, 3)}], "uuid": uid(),
                                   "time": round(t, 4), "color": -1, "interpolation": interp})
    by_t = {}
    for t, s in anim.events:
        by_t.setdefault(t, []).append(s)
    animators["effects"] = {"name": "Effects", "type": "effect",
                            "keyframes": [{"channel": "timeline", "data_points": [{"script": "\n".join(v)}], "uuid": uid(), "time": t, "color": -1,
                                           "interpolation": "linear"} for t, v in sorted(by_t.items())]}
    bb = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": model.name, "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": img.width, "height": img.height},
        "elements": elements,
        "outliner": [outl(n) for n in model.order if model.groups[n]["parent"] is None],
        "textures": [tex_entry(tex_name + ".png", img, src)] + ([tex_entry("skin.png", skin_img, png_uri(skin_img))] if skin_img else []),
        "animations": [{"uuid": uid(), "name": "animation.kill", "loop": "once", "override": False, "length": anim.length, "snapping": FPS,
                        "selected": True, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators}],
    }
    with open(path, "w") as f:
        json.dump(bb, f, indent=1)


# ----------------------------------------------------------------------------------------------- shared: the victim stand-in
SKIN, HAIR, SHIRT, PANTS, SHOE = hexc("c69c77"), hexc("4a3222"), hexc("2aa7b5"), hexc("3a46a3"), hexc("5d5d5d")
# ----------------------------------------------------------------------------------------------- shared helpers
def solid(col, var=0.07, edge=0.84, seed=1):
    base = hexc(col)

    def m(p, f, c, t):
        k, j, w, h = t
        v = 1 + (h3(p[0], p[1], p[2], seed) - 0.5) * var * 2
        if edge and w > 2 and h > 2 and (k == 0 or j == 0 or k == w - 1 or j == h - 1):
            v *= edge
        return mul(base, v)
    return m


def eye_mat(pupil_face="east"):
    """White eye with a black pupil (and a shine) on the face that looks forward."""
    def m(p, f, c, t):
        k, j, w, h = t
        if f == pupil_face or (f in ("south", "north") and k >= w - 3 if pupil_face == "east" else False):
            cx, cy = w / 2, h / 2
            if abs(k + 0.5 - cx) < max(1, w * 0.3) and abs(j + 0.5 - cy) < max(1, h * 0.35):
                return (255, 255, 255) if (k == int(cx) and j == int(cy) - 1) else (16, 14, 22)
        return mul((255, 255, 255), 0.95 + h3(*p) * 0.05)
    return m


def box_uv(u, v, w, h, d):
    """Minecraft box UV of a skin part, mapped so the player faces +Z (towards the viewer)."""
    return {"up": [u + d, v, u + d + w, v + d], "down": [u + d + w, v, u + d + 2 * w, v + d], "west": [u, v + d, u + d, v + d + h],
            "south": [u + d, v + d, u + d + w, v + d + h], "east": [u + d + w, v + d, u + 2 * d + w, v + d + h],
            "north": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h]}


def default_skin():
    """A bright stand-in skin for Blockbench and the preview (in game the dead player's real skin is used)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    SK, HA, SH, PA, SO = hexc("e8b48a"), hexc("6b3a1e"), hexc("13c3d6"), hexc("3b4ff0"), hexc("4a4a58")

    def fill(x0, y0, x1, y1, col):
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = mul(col, 0.94 + h3(x, y, 0, 5) * 0.1) + (255,)
    fill(0, 0, 32, 16, SK)
    fill(8, 0, 16, 8, HA)  # top of the head
    fill(24, 8, 32, 16, HA)  # back
    fill(0, 8, 8, 11, HA)
    fill(16, 8, 24, 11, HA)
    fill(8, 8, 16, 10, HA)  # fringe
    for (x, y, col) in ((9, 12, (255, 255, 255)), (10, 12, (40, 60, 200)), (13, 12, (40, 60, 200)), (14, 12, (255, 255, 255))):
        px[x, y] = col + (255,)
    fill(11, 14, 13, 15, hexc("b06a50"))
    fill(16, 16, 40, 32, SH)
    fill(40, 16, 56, 32, SK)
    fill(40, 20, 56, 23, SH)
    fill(32, 48, 48, 64, SK)
    fill(32, 52, 48, 55, SH)
    fill(0, 16, 16, 32, PA)
    fill(16, 48, 32, 64, PA)
    fill(0, 29, 16, 32, SO)
    fill(16, 61, 32, 64, SO)
    return img


def add_victim(m, an, gone, look="left", scared=None, keys=None):
    """The dead player standing where they died (drawn with their own skin in game). It looks at the creature, gets
    scared, and is gone (eaten) at 'gone' seconds. keys: extra tracks for the whole player {"position": [...], ...}."""
    v = m.group("victim", [0, 0, 0])
    hd = m.group("victim_head", [0, 24, 0], v)
    ar = m.group("victim_arm_right", [-6, 22, 0], v)
    al = m.group("victim_arm_left", [6, 22, 0], v)
    m.cube(hd, [-4, 24, -4], [4, 32, 4], None, name="head", skin=box_uv(0, 0, 8, 8, 8))
    m.cube(hd, [-4, 24, -4], [4, 32, 4], None, name="hat", skin=box_uv(32, 0, 8, 8, 8), inflate=0.5)
    m.cube(v, [-4, 12, -2], [4, 24, 2], None, name="body", skin=box_uv(16, 16, 8, 12, 4))
    m.cube(ar, [-8, 12, -2], [-4, 24, 2], None, name="arm", skin=box_uv(40, 16, 4, 12, 4))
    m.cube(al, [4, 12, -2], [8, 24, 2], None, name="arm", skin=box_uv(32, 48, 4, 12, 4))
    m.cube(v, [-4, 0, -2], [0, 12, 2], None, name="leg", skin=box_uv(0, 16, 4, 12, 4))
    m.cube(v, [0, 0, -2], [4, 12, 2], None, name="leg", skin=box_uv(16, 48, 4, 12, 4))
    s = scared if scared is not None else gone - 0.5
    look_rot = {"left": (0, -55, 0), "right": (0, 55, 0), "down": (28, 0, 0), "up": (-35, 0, 0)}[look]
    an.key(hd, "rotation", (0, (0, 0, 0)), (max(0.05, s - 0.3), (0, 0, 0)), (max(0.1, s - 0.1), look_rot, "back"))
    shake = [(0, (0, 0, 0)), (s, (0, 0, 0)), (s + 0.12, (-150, 0, 0), "back")]
    tt = s + 0.12
    i = 0
    while tt + 0.08 < gone:
        tt += 0.08
        shake.append((tt, (-150 + (12 if i % 2 else -12), 0, 0)))
        i += 1
    an.key(ar, "rotation", *[(k[0], (k[1][0], 0, -18 if k[0] > s else 0)) + tuple(k[2:]) for k in shake])
    an.key(al, "rotation", *[(k[0], (k[1][0] if k[0] <= s else -150 - (k[1][0] + 150), 0, 18 if k[0] > s else 0)) + tuple(k[2:]) for k in shake])
    an.key(v, "scale", (0, 1), (gone - 0.001, 1), (gone, 0, "step"))
    for ch, ks in (keys or {}).items():
        if ch == "scale":
            continue
        an.key(v, ch, *ks)
    if keys and "scale" in keys:
        an.tracks.pop((v, "scale"), None)
        an.key(v, "scale", *keys["scale"])
    return v


def hops(an, g, xs, t0, dt, height, squash=True, rot=True):
    """Hop from x to x along the ground: arc, stretch in the air, squash on landing."""
    pos, sc, rz = [], [], []
    t = t0
    for i in range(len(xs) - 1):
        a, b = xs[i], xs[i + 1]
        pos += [(t, (a, 0, 0)), (t + dt * 0.5, ((a + b) / 2, height, 0), "out"), (t + dt, (b, 0, 0), "in")]
        sc += [(t, (1.15, 0.85, 1.15)), (t + dt * 0.3, (0.9, 1.14, 0.9), "out"), (t + dt * 0.85, (0.95, 1.06, 0.95)), (t + dt, (1.18, 0.82, 1.18), "in")]
        rz += [(t, (0, 0, 0)), (t + dt * 0.35, (0, 0, 10 if b > a else -10), "out"), (t + dt, (0, 0, -6 if b > a else 6))]
        t += dt
    return pos, sc, rz


def write_events_trail(an, t0, t1, step, fn):
    t = t0
    while t <= t1 + 1e-6:
        fn(round(t, 3))
        t += step


# ================================================================================================ PAC-MAN
PAC = hexc("ffe417")


def m_pac(p, f, c, t):
    x, y, z = p
    cy = 18
    r = math.sqrt(x * x + (y - cy) ** 2 + z * z) or 1
    lit = 0.95 + 0.12 * ((y - cy) / r) + 0.04 * (z / r) + (h3(x, y, z, 1) - 0.5) * 0.05
    col = mul(PAC, lit)
    if (y - cy) > 11 and -6 < x < 2 and abs(z) < 9:  # shiny spot
        col = mix(col, (255, 255, 230), 0.6)
    return col


def m_mouth(p, f, c, t):
    x, y, z = p
    r = math.sqrt(x * x + z * z)
    if r < 8 and abs(z) < 5 and x > -6:
        return mul(hexc("ff5c8a"), 0.95 + h3(x, y, z) * 0.1)  # tongue
    return mul(hexc("7a0c2e") if r < 13 else hexc("4a0620"), 0.9 + h3(x, y, z) * 0.2)


def m_black(p, f, c, t):
    k, j, w, h = t
    if (k, j) == (w - 2, 1):
        return (255, 255, 255)
    return (18, 14, 20)


def make_pacman():
    m = Model("pacman")
    an = Anim(3.4)
    add_victim(m, an, 1.0, "left", scared=0.55)
    R, CY = 18.0, 18.0
    root = m.group("pacman", [0, CY, 0])
    up = m.group("jaw_upper", [-3, CY, 0], root)
    lo = m.group("jaw_lower", [-3, CY, 0], root)
    for half, g in ((1, up), (-1, lo)):
        for i in range(9):
            y0, y1 = i * 2.0, i * 2.0 + 2.0
            r = math.sqrt(max(1.0, R * R - (y0 + 1) ** 2))
            a, b = (CY + y0, CY + y1) if half > 0 else (CY - y1, CY - y0)
            faces = {"down" if half > 0 else "up": m_mouth} if i == 0 else {}
            m.cube(g, [-r, a, -r * 0.62], [r, b, r * 0.62], m_pac, faces=faces, name="slab%d" % i)
            m.cube(g, [-r * 0.62, a + 0.06, -r], [r * 0.62, b - 0.06, r], m_pac, faces=dict(faces), name="slab%db" % i)  # no shared faces
    for s in (1, -1):
        z0, z1 = (13.6, 14.6) if s > 0 else (-14.6, -13.6)
        m.cube(up, [2, CY + 8, z0], [6, CY + 13, z1], m_black, name="eye")
    chomp, t = [], 0.0
    while t < 0.66:
        chomp += [(round(t + 0.1, 2), 34, "out"), (round(t + 0.2, 2), 4, "in")]
        t += 0.2
    mouth = [(0, 4)] + chomp + [(0.72, 30, "out"), (0.84, 62, "out"), (0.92, 62), (1.0, 0, "in"), (1.06, 9, "out"), (1.12, 0, "in"),
                                (1.26, 16, "out"), (1.4, 0, "in"), (1.54, 16, "out"), (1.68, 0, "in"), (1.82, 16, "out"), (1.96, 0, "in"), (2.3, 0)]
    t = 2.3
    while t < 3.3:
        mouth += [(round(t + 0.1, 2), 34, "out"), (round(t + 0.2, 2), 4, "in")]
        t += 0.2
    an.key(up, "rotation", *[(k[0], k[1], k[2] if len(k) > 2 else "io") for k in mouth])
    an.key(lo, "rotation", *[(k[0], (0, 0, -k[1]), k[2] if len(k) > 2 else "io") for k in mouth])
    an.key(root, "position", (0, (-110, 0, 0)), (0.7, (-26, 0, 0), "lin"), (0.84, (-34, 3, 0), "out"), (0.92, (-34, 3, 0)),
           (1.0, (-3, 1, 0), "in"), (1.1, (-5, 0, 0), "out"), (2.3, (-5, 0, 0)), (3.4, (120, 0, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, -6)), (0.7, (0, 0, -6)), (0.84, (0, 0, 16), "out"), (0.92, (0, 0, 18)), (1.0, (0, 0, -14), "in"),
           (1.14, (0, 0, 4), "out"), (1.24, (0, 0, 0)), (1.36, (0, -38, 0), "back"), (2.15, (0, -38, 0)), (2.4, (0, 0, -8), "io"), (3.4, (0, 0, -8)))
    an.key(root, "scale", (0, 1), (0.84, (0.92, 1.12, 0.92), "out"), (0.92, (0.92, 1.12, 0.92)), (1.0, (1.22, 0.8, 1.22), "in"),
           (1.12, (0.94, 1.08, 0.94), "out"), (1.22, 1.0, "out"), (1.33, (1.08, 0.95, 1.14)), (1.47, 1.0), (1.61, (1.08, 0.95, 1.14)), (1.75, 1.0),
           (1.89, (1.08, 0.95, 1.14)), (2.03, 1.0), (2.12, (0.88, 1.18, 0.88), "out"), (2.24, (1.14, 0.9, 1.14), "out"), (2.36, 1.0, "back"),
           (3.05, 1.0), (3.4, 0.0, "in"))
    for i, tt in enumerate([0.2, 0.4, 0.6, 2.5, 2.7, 2.9, 3.1]):
        an.ev(tt, "s block.note_block.bit 0.45 %s" % ("1.6" if i % 2 == 0 else "1.2"))
    an.ev(0.84, "s entity.player.breath 0.4 1.6")
    an.ev(1.0, "s entity.player.attack.crit 1 0.7")
    an.ev(1.0, "s entity.generic.eat 1 0.6")
    an.ev(1.0, "p confetti 34 0 18 0 7 0.35")
    an.ev(1.0, "p sparkle 14 0 18 0 6 0.2")
    for tt in (1.4, 1.68, 1.96):
        an.ev(tt, "s entity.generic.eat 0.8 0.9")
        an.ev(tt, "p confetti 6 6 22 0 4 0.15")
    an.ev(2.24, "s entity.player.burp 1 0.9")
    an.ev(2.24, "p gold 16 10 20 0 4 0.3")
    an.ev(3.3, "p sparkle 12 120 18 0 8 0.05")
    return m, an


# ================================================================================================ SHARK
SH_TOP, SH_BELLY, SH_DARK = hexc("2e94ec"), hexc("f4fbff"), hexc("1765c2")


def m_shark(p, f, c, t):
    x, y, z = p
    edge = -1.5 + math.sin(x * 0.45) * 0.8
    if f == "down" or y < edge:
        return mul(SH_BELLY, 0.96 + h3(x, y, z, 3) * 0.06)
    k = min(1.0, max(0.0, (y - edge) / 9.0))
    col = mul(mix(SH_TOP, SH_DARK, k * 0.5), 0.96 + h3(x, y, z, 2) * 0.08)
    if f in ("south", "north") and -13 < x < -3 and -2 < y < 6 and int(math.floor(x)) % 3 == 0:
        col = mul(SH_DARK, 0.75)  # gills
    return col


def m_fin(p, f, c, t):
    k, j, w, h = t
    return mul(SH_DARK, (0.95 + h3(*p, 4) * 0.08) * (0.85 if j == 0 and f not in ("up", "down") else 1))


def m_tooth(p, f, c, t):
    return mul((255, 255, 246), 0.95 + h3(*p) * 0.05)


def m_gum(p, f, c, t):
    x, y, z = p
    if abs(z) < 3.5 and x > 3:
        return mul(hexc("ff5c8a"), 0.95 + h3(x, y, z) * 0.1)
    return mul(hexc("b0153d"), 0.88 + h3(x, y, z, 5) * 0.2)


def make_shark():
    m = Model("shark")
    an = Anim(2.45)
    add_victim(m, an, 1.0, "left", scared=0.45)
    root = m.group("shark", [0, 0, 0])
    up = m.group("jaw_upper", [0, 0, 0], root)
    lo = m.group("jaw_lower", [0, 0, 0], root)
    tail = m.group("tail", [-34, 0, 0], root)
    fin = m.group("tail_fin", [-58, 0, 0], tail)
    pl = m.group("fin_left", [-8, -5, 7], root)
    pr = m.group("fin_right", [-8, -5, -7], root)
    m.cube(root, [-14, -7, -8], [0, 8, 8], m_shark, name="head_back")
    m.cube(root, [-34, -6, -7.5], [-14, 7, 7.5], m_shark, name="body")
    m.cube(tail, [-48, -4, -5.5], [-34, 5, 5.5], m_shark, name="rear")
    m.cube(tail, [-58, -2, -3], [-48, 3, 3], m_shark, name="peduncle")
    m.cube(fin, [-61, 0, -1], [-57, 17, 1], m_fin, rot=[0, 0, 35], origin=[-58, 1, 0], name="lobe_top")
    m.cube(fin, [-60, -11, -1], [-56, 0, 1], m_fin, rot=[0, 0, -35], origin=[-58, 0, 0], name="lobe_bottom")
    for a, b, nm in (([-27, 6, -1], [-12, 10, 1], "dorsal_1"), ([-25, 10, -1], [-15, 15, 1], "dorsal_2"), ([-24, 15, -0.7], [-19, 21, 0.7], "dorsal_3")):
        m.cube(root, a, b, m_fin, rot=[0, 0, 15], origin=[-18, 6, 0], name=nm)
    m.cube(tail, [-46, 4.5, -0.5], [-41, 8, 0.5], m_fin, name="dorsal_small")
    m.cube(tail, [-44, -7, -0.5], [-40, -3.5, 0.5], m_fin, name="anal_fin")
    m.cube(pl, [-13, -6, 7], [-3, -5, 19], m_fin, rot=[28, 0, 0], origin=[-8, -5, 7], name="pectoral")
    m.cube(pr, [-13, -6, -19], [-3, -5, -7], m_fin, rot=[-28, 0, 0], origin=[-8, -5, -7], name="pectoral")
    m.cube(up, [0, 0, -7], [16, 7, 7], m_shark, faces={"down": m_gum}, name="snout")
    m.cube(up, [16, 1, -5], [22, 6, 5], m_shark, faces={"down": m_gum}, name="nose")
    m.cube(up, [22, 2, -3], [24.5, 5, 3], m_shark, name="nose_tip")
    for s in (1, -1):
        z0, z1 = (6.8, 7.4) if s > 0 else (-7.4, -6.8)
        m.cube(up, [10, 3, z0], [13, 6, z1], m_black, name="eye")
    m.cube(lo, [0, -6, -6.5], [17, 0, 6.5], m_shark, faces={"up": m_gum}, name="jaw")
    m.cube(lo, [17, -5, -4.5], [21, -0.5, 4.5], m_shark, faces={"up": m_gum}, name="chin")
    for x in range(3, 21, 3):
        zz = 6.0 if x < 16 else 4.0
        for s in (1, -1):
            m.cube(up, [x - 0.9, -1.6, s * zz - 0.5], [x + 0.9, 0.2, s * zz + 0.5], m_tooth, rot=[0, 0, 45], origin=[x, 0, s * zz], name="tooth")
            if x < 19:
                m.cube(lo, [x + 0.6, -0.2, s * (zz - 0.3) - 0.5], [x + 2.4, 1.6, s * (zz - 0.3) + 0.5], m_tooth, rot=[0, 0, 45],
                       origin=[x + 1.5, 0, s * (zz - 0.3)], name="tooth")
    for z in (-2, 0, 2):
        m.cube(up, [21, -1.4, z - 0.5], [22.6, 0.4, z + 0.5], m_tooth, rot=[0, 0, 45], origin=[21.8, 0, z], name="tooth")
    S = 1.3  # big enough to swallow a standing player
    an.key(root, "position", (0, (-90, -16, 0)), (0.48, (-26, -16, 0), "lin"), (0.6, (-8, -60, 0), "in"), (0.66, (0, -66, 0)),
           (0.96, (0, 6, 0), "out"), (1.0, (0, 5, 0)), (1.42, (-26, 46, 0), "out"), (1.9, (-60, -6, 0), "in"), (2.25, (-72, -96, 0), "lin"))
    an.key(root, "rotation", (0, (0, 0, 0)), (0.48, (0, 0, -4)), (0.6, (0, 0, -40), "in"), (0.66, (0, 0, 90), "step"), (0.96, (0, 0, 90)),
           (1.02, (0, 18, 90), "out"), (1.08, (0, -16, 90)), (1.14, (0, 14, 92)), (1.2, (0, -10, 100)), (1.26, (0, 0, 112)),
           (1.6, (0, 0, 190), "io"), (1.95, (0, 0, 255), "in"), (2.25, (0, 0, 268)))
    an.key(root, "scale", (0, S), (0.66, S), (0.82, (1.14 * S, 0.92 * S, 0.92 * S), "out"), (0.96, (1.1 * S, 0.95 * S, 0.95 * S)),
           (1.02, (0.9 * S, 1.1 * S, 1.1 * S), "out"), (1.16, S, "back"), (1.7, S), (1.85, (1.1 * S, 0.95 * S, 0.95 * S)), (2.25, S))
    jaw = [(0, 0), (0.48, 4), (0.7, 6), (0.84, 58, "out"), (0.95, 60), (1.0, 0, "in"), (1.06, 10, "out"), (1.12, 0, "in"), (1.6, 0),
           (1.75, 22, "out"), (1.9, 0, "in"), (2.25, 0)]
    an.key(up, "rotation", *[(k[0], (0, 0, k[1]), k[2] if len(k) > 2 else "io") for k in jaw])
    an.key(lo, "rotation", *[(k[0], (0, 0, -k[1] * 0.85), k[2] if len(k) > 2 else "io") for k in jaw])
    wag = [(round(i * 0.19, 2), (0, 22 if i % 2 else -22, 0), "io") for i in range(13)]
    an.key(tail, "rotation", *wag)
    an.key(fin, "rotation", *[(k[0], (0, k[1][1] * 0.6, 0), "io") for k in wag])
    flap = [(round(i * 0.25, 2), (0, 0, 10 if i % 2 else -6), "io") for i in range(10)]
    an.key(pl, "rotation", *flap)
    an.key(pr, "rotation", *flap)
    for i in range(6):
        tt = 0.04 + i * 0.08
        x = -90 + 64 * (tt / 0.48) - 22
        an.ev(tt, "p splash 8 %d 1 0 3 0.25" % x)
        an.ev(tt, "p water 4 %d 1 0 2 0.1" % x)
    an.ev(0.05, "s entity.fish.swim 0.8 0.7")
    an.ev(0.55, "p splash 20 -10 1 0 5 0.4")
    an.ev(0.72, "ring splash 48 0 1 0 14 0.45")
    an.ev(0.72, "p water 40 0 4 0 8 0.7")
    an.ev(0.72, "p chunks 30 0 4 0 8 0.8")
    an.ev(0.72, "s entity.player.splash.high_speed 1 0.8")
    an.ev(1.0, "s entity.player.attack.crit 1 0.6")
    an.ev(1.0, "s entity.generic.eat 1 0.55")
    an.ev(1.0, "p confetti 30 0 24 0 7 0.35")
    an.ev(1.0, "p water 20 0 20 0 8 0.4")
    an.ev(1.0, "p sparkle 12 0 24 0 6 0.2")
    an.ev(1.95, "ring splash 48 -62 1 0 14 0.45")
    an.ev(1.95, "p water 40 -62 4 0 8 0.7")
    an.ev(1.95, "p chunks 24 -62 4 0 8 0.7")
    an.ev(1.95, "s entity.player.splash 1 0.6")
    return m, an


# ================================================================================================ MIMIC CHEST
def m_wood(p, f, c, t):
    x, y, z = p
    k, j, w, h = t
    base = hexc("e09a3e")
    if k < 1 or j < 1 or k >= w - 1 or j >= h - 1:
        return mul(hexc("7a4415"), 0.95 + h3(x, y, z) * 0.1)  # frame
    v = 0.94 + h3(x * 0.3, y, z * 0.3, 2) * 0.1
    if f not in ("up", "down") and int(math.floor(y)) % 4 == 0:
        v *= 0.8  # plank lines
    if f in ("up", "down") and int(math.floor(x)) % 5 == 0:
        v *= 0.82
    return mul(base, v)


def m_iron(p, f, c, t):
    k, j, w, h = t
    if k == 0 or j == 0 or k == w - 1 or j == h - 1:
        return (70, 74, 86)
    return (236, 240, 246) if (k + j) % 3 else (205, 212, 224)


def m_inside(p, f, c, t):
    x, y, z = p
    # the inside of the lid: a mouth with two glowing eyes
    if f == "down":
        for ez in (-6, 6):
            dx, dz = x + 3, z - ez
            if abs(dx) < 3.2 and abs(dz) < 2.6:
                return (20, 10, 10) if abs(dz) < 0.7 else (255, 230, 40)
    return mul(hexc("8d0f3c"), 0.85 + h3(x, y, z, 6) * 0.2)


def m_tongue(p, f, c, t):
    return mul(hexc("ff5c9a"), 0.92 + h3(*p, 8) * 0.12)


def make_mimic():
    m = Model("mimic")
    an = Anim(3.25)
    add_victim(m, an, 1.1, "left", scared=0.6)
    root = m.group("chest", [0, 0, 0])
    lid = m.group("lid", [-14, 18, 0], root)
    tongue = m.group("tongue", [-4, 18, 0], root)
    m.cube(root, [-14, 0, -12], [14, 18, 12], m_wood, faces={"up": m_inside}, name="body")
    m.cube(lid, [-14, 18, -12], [14, 26, 12], m_wood, faces={"down": m_inside}, name="lid")
    m.cube(lid, [14, 16, -2.5], [15.6, 22, 2.5], m_iron, name="latch")
    for z in range(-10, 11, 4):
        m.cube(root, [11, 17.5, z - 1], [13, 21, z + 1], m_tooth, rot=[45, 0, 0], origin=[12, 18, z], name="tooth")
        m.cube(lid, [11, 15, z + 1], [13, 18.5, z + 3], m_tooth, rot=[45, 0, 0], origin=[12, 18, z + 2], name="tooth")
    for x in range(-8, 10, 5):
        for s in (1, -1):
            m.cube(root, [x - 1, 17.5, s * 10.5 - 1], [x + 1, 20.5, s * 10.5 + 1], m_tooth, rot=[0, 45, 0], origin=[x, 18, s * 10.5], name="tooth")
    m.cube(tongue, [-4, 18, -4], [10, 20, 4], m_tongue, name="tongue")
    # hops in
    pos, sc, rz = hops(an, root, [-125, -90, -58, -28], 0.0, 0.27, 11)
    pos += [(0.84, (-34, 0, 0), "out"), (0.95, (-34, 0, 0)), (1.1, (2, 0, 0), "in"), (1.2, (0, 0, 0), "out")]
    sc += [(0.9, (0.94, 1.1, 0.94), "out"), (1.1, (1.22, 0.8, 1.22), "in"), (1.22, (0.95, 1.06, 0.95), "out"), (1.32, 1.0)]
    rz += [(0.84, (0, 0, 12), "out"), (0.95, (0, 0, 14)), (1.1, (0, 0, -12), "in"), (1.25, (0, 0, 0), "out")]
    # chews, burps coins, hops away
    t = 1.32
    for i in range(3):
        sc += [(t + 0.12, (1.1, 0.94, 1.12), "out"), (t + 0.25, 1.0)]
        t += 0.25
    sc += [(2.0, (0.9, 1.2, 0.9), "out"), (2.15, 1.0, "back")]
    p2, s2, r2 = hops(an, root, [0, 40, 85, 135], 2.25, 0.3, 12)
    pos += [(2.25, (0, 0, 0))] + p2
    sc += s2 + [(3.0, 1.0), (3.25, 0.0, "in")]
    rz += [(2.25, (0, 0, 0))] + r2
    an.key(root, "position", *pos)
    an.key(root, "scale", *sc)
    an.key(root, "rotation", *rz)
    lk = [(0, 0)]
    for i in range(3):
        lk += [(0.27 * i + 0.13, 14, "out"), (0.27 * i + 0.27, 0, "in")]
    lk += [(0.84, 50, "out"), (0.95, 82, "out"), (1.1, 0, "in"), (1.16, 8, "out"), (1.22, 0, "in")]
    t = 1.32
    for i in range(3):
        lk += [(t + 0.12, 16, "out"), (t + 0.25, 0, "in")]
        t += 0.25
    lk += [(2.0, 40, "out"), (2.15, 0, "in"), (3.25, 0)]
    an.key(lid, "rotation", *[(k[0], (0, 0, k[1])) + tuple(k[2:]) for k in lk])
    an.key(tongue, "position", (0, (0, 0, 0)), (0.86, (0, 0, 0)), (0.98, (12, 1, 0), "out"), (1.06, (0, 0, 0), "in"), (1.98, (0, 0, 0)),
           (2.06, (10, 2, 0), "out"), (2.2, (0, 0, 0), "in"))
    an.key(tongue, "rotation", (0, (0, 0, 0)), (0.86, (0, 0, 0)), (0.98, (0, 0, -25), "out"), (1.06, (0, 0, 0), "in"))
    for tt in (0.27, 0.54, 0.81, 2.55, 2.85, 3.15):
        an.ev(tt, "s block.wood.step 1 0.8")
        an.ev(tt, "p poof 4 0 1 0 6 0.03")
    an.ev(0.84, "s block.chest.open 1 0.7")
    an.ev(1.1, "s block.chest.close 1 0.7")
    an.ev(1.1, "s entity.generic.eat 1 0.6")
    an.ev(1.1, "p confetti 34 6 20 0 7 0.35")
    an.ev(1.1, "p sparkle 12 6 20 0 6 0.2")
    for tt in (1.44, 1.69, 1.94):
        an.ev(tt, "s entity.generic.eat 0.8 0.85")
    an.ev(2.02, "s entity.player.burp 1 0.8")
    an.ev(2.02, "p gold 30 12 24 0 6 0.4")
    an.ev(2.02, "s entity.experience_orb.pickup 1 0.8")
    return m, an


# ================================================================================================ GIANT FROG
FROG, FROG_D, FROG_B = hexc("5ee32a"), hexc("2fae22"), hexc("ffe66b")


def m_frog(p, f, c, t):
    x, y, z = p
    if f == "down" or (y < 6 and x > -2) or (f == "east" and y < 8):
        return mul(FROG_B, 0.95 + h3(x, y, z) * 0.08)
    if h3(x * 0.4, y * 0.4, z * 0.4, 11) > 0.83:
        return mul(FROG_D, 0.95 + h3(x, y, z) * 0.08)  # spots
    return mul(FROG, 0.94 + h3(x, y, z, 2) * 0.1)


def m_frog_mouth(p, f, c, t):
    return mul(hexc("ff4f86"), 0.88 + h3(*p, 9) * 0.15)


def make_frog():
    m = Model("frog")
    an = Anim(2.5)
    FX = -46.0  # where the frog sits
    root = m.group("frog", [0, 0, 0])
    head = m.group("head", [-4, 9, 0], root)
    jaw = m.group("jaw", [-4, 9, 0], root)
    aim = m.group("tongue_aim", [8, 9, 0], jaw)  # keeps the tongue pointing at the player while the jaw is open
    tongue = m.group("tongue", [8, 9, 0], aim)
    tip = m.group("tongue_tip", [8, 9, 0], aim)
    m.cube(root, [-14, 2, -11], [6, 13, 11], m_frog, name="body")
    m.cube(head, [-4, 9, -10], [14, 17, 10], m_frog, faces={"down": m_frog_mouth}, name="head")
    for s in (1, -1):
        lo_, hi_ = (lambda a, b: (a, b) if s > 0 else (-b, -a)), None
        z = lo_(4.5, 10.6)
        m.cube(head, [3, 15, z[0]], [9, 21, z[1]], eye_mat("east"), name="eye")
        z = lo_(11, 15)
        m.cube(root, [-14, 0, z[0]], [-2, 7, z[1]], m_frog, name="thigh")
        z = lo_(12, 16)
        m.cube(root, [-2, 0, z[0]], [6, 1.5, z[1]], m_frog, name="foot")
        z = lo_(9.5, 12.5)
        m.cube(root, [4, 0.3, z[0]], [9, 6, z[1]], m_frog, name="arm")
    m.cube(jaw, [-4, 4, -9.5], [13, 9, 9.5], m_frog, faces={"up": m_frog_mouth}, name="jaw")
    m.cube(tongue, [8, 8.2, -1.6], [9, 10.2, 1.6], m_tongue, name="tongue")
    m.cube(tip, [8, 7, -3.2], [14, 11.5, 3.2], m_tongue, name="tip")
    # one big hop in
    an.key(root, "position", (0, (-135, 0, 0)), (0.22, ((FX - 135) / 2, 30, 0), "out"), (0.45, (FX, 0, 0), "in"), (1.75, (FX, 0, 0)),
           (1.85, (FX - 4, 0, 0), "out"), (2.4, (FX + 30, 150, 0), "out"))
    an.key(root, "rotation", (0, (0, 0, 18)), (0.22, (0, 0, 0)), (0.42, (0, 0, -12)), (0.52, (0, 0, 0), "out"), (1.75, (0, 0, 0)),
           (1.85, (0, 0, -8), "out"), (2.0, (0, 0, 25), "out"), (2.4, (0, 0, 30)))
    an.key(root, "scale", (0, (0.9, 1.15, 0.9)), (0.4, (0.95, 1.08, 0.95)), (0.5, (1.25, 0.78, 1.25), "in"), (0.62, (0.96, 1.05, 0.96), "out"),
           (0.7, 1.0), (1.3, 1.0), (1.4, (1.2, 1.28, 1.3), "out"), (1.55, 1.0, "back"), (1.75, 1.0), (1.85, (1.2, 0.8, 1.2), "out"),
           (1.98, (0.85, 1.25, 0.85), "out"), (2.25, (0.85, 1.25, 0.85)), (2.4, 0.0, "in"))
    # throat puffs, mouth opens, tongue shoots out and pulls the player in
    an.key(jaw, "scale", (0, 1), (0.6, 1), (0.66, (1, 1.25, 1.22), "out"), (0.72, 1), (0.78, (1, 1.25, 1.22), "out"), (0.84, 1))
    an.key(head, "rotation", (0, (0, 0, 0)), (0.84, (0, 0, 0)), (0.9, (0, 0, 20), "out"), (1.24, (0, 0, 22)), (1.3, (0, 0, 0), "in"))
    an.key(jaw, "rotation", (0, (0, 0, 0)), (0.84, (0, 0, 0)), (0.9, (0, 0, -24), "out"), (1.24, (0, 0, -24)), (1.3, (0, 0, 0), "in"))
    an.key(aim, "rotation", (0, (0, 0, 0)), (0.84, (0, 0, 0)), (0.9, (0, 0, 34), "out"), (1.24, (0, 0, 34)), (1.3, (0, 0, 0), "in"))
    REACH = -FX - 8 - 4  # from the mouth to the player

    def length(t):
        if t < 0.9:
            return 1.0
        if t < 0.98:
            return 1 + (REACH - 1) * ease("out", (t - 0.9) / 0.08)
        if t < 1.03:
            return REACH
        if t < 1.25:
            return REACH + (1 - REACH) * ease("io", (t - 1.03) / 0.22)
        return 1.0
    tk, pk, vk = [], [], []
    t = 0.0
    while t <= 1.3 + 1e-6:
        L = length(t)
        tk.append((round(t, 3), (L, 1, 1), "lin"))
        pk.append((round(t, 3), (L - 1, 0, 0), "lin"))
        pulled = REACH - L if t > 1.03 else 0
        vk.append((round(t, 3), (-pulled, 9 * math.sin(math.pi * min(1, pulled / REACH)), 0), "lin"))
        t += 0.02
    an.key(tongue, "scale", *tk)
    an.key(tip, "position", *pk)
    gone = 1.25
    add_victim(m, an, gone, "left", scared=0.7, keys={
        "position": vk, "rotation": [(0, (0, 0, 0)), (1.03, (0, 0, 0)), (1.25, (0, 0, 70), "in")],
        "scale": [(0, 1), (1.15, 1), (1.24, 0.5, "in"), (1.25, 0, "step")]})
    an.ev(0.0, "s entity.frog.long_jump 1 0.7")
    an.ev(0.45, "s entity.frog.step 1 0.6")
    an.ev(0.45, "p poof 8 -46 1 0 10 0.05")
    an.ev(0.62, "s entity.frog.ambient 1 0.6")
    an.ev(0.9, "s entity.frog.tongue 1 0.8")
    an.ev(0.98, "p sparkle 8 0 18 0 5 0.1")
    an.ev(1.25, "s entity.frog.eat 1 0.7")
    an.ev(1.25, "p confetti 28 -36 14 0 7 0.35")
    an.ev(1.6, "s entity.player.burp 1 0.8")
    an.ev(1.6, "p heart 6 -36 24 0 6 0.1")
    an.ev(1.85, "s entity.frog.long_jump 1 0.8")
    an.ev(1.85, "p poof 10 -46 1 0 10 0.05")
    return m, an


# ================================================================================================ FLYTRAP
TRAP_G, TRAP_D, TRAP_IN = hexc("3fdc4c"), hexc("1f9a35"), hexc("ff3d86")


def m_trap(p, f, c, t):
    x, y, z = p
    v = 0.94 + h3(x, y, z, 3) * 0.1
    if int(math.floor(y)) % 6 == 0 or int(math.floor(z + 40)) % 7 == 0:
        return mul(TRAP_D, v)  # veins
    return mul(TRAP_G, v)


def m_trap_in(p, f, c, t):
    x, y, z = p
    if h3(x, y, z, 12) > 0.9:
        return (255, 231, 77)
    return mul(TRAP_IN, 0.92 + h3(x, y, z, 4) * 0.12)


def m_spike(p, f, c, t):
    return mul(hexc("d6ff5c"), 0.94 + h3(*p) * 0.08)


def make_flytrap():
    m = Model("flytrap")
    an = Anim(2.7)
    add_victim(m, an, 1.0, "down", scared=0.3, keys={"position": [(0, (0, 0, 0))] + [(round(0.05 * i, 2), ((0.6 if i % 2 else -0.6), 0, 0))
                                                                                      for i in range(1, 10)] + [(0.5, (0, 0, 0))]})
    root = m.group("plant", [0, 0, 0])
    lj = m.group("leaf_left", [-0.4, 6, 0], root)
    rj = m.group("leaf_right", [0.4, 6, 0], root)
    m.cube(root, [-3, -44, -3], [3, 4.9, 3], m_trap, name="stem")
    for s in (1, -1):
        m.cube(root, [-2, -1, -16] if s < 0 else [-2, -1, 4], [2, 1, -4] if s < 0 else [2, 1, 16], m_trap, rot=[s * -12, 0, 0],
               origin=[0, 0, 4 * s], name="ground_leaf")
    for g, sx in ((lj, -1), (rj, 1)):
        def X(a):
            return a * sx + 0.4 * sx if a <= 0 else a * sx

        def box(a, b):
            return [min(X(a[0]), X(b[0])), a[1], a[2]], [max(X(a[0]), X(b[0])), b[1], b[2]]
        inner_back = "east" if sx < 0 else "west"
        m.cube(g, *box([-16, 8, -12], [-13, 40, 12]), m_trap, faces={inner_back: m_trap_in}, name="back")
        m.cube(g, *box([-13, 5, -12], [0, 8, 12]), m_trap, faces={"up": m_trap_in}, name="bottom")
        m.cube(g, *box([-13, 40, -12], [-1, 43, 12]), m_trap, faces={"down": m_trap_in}, name="top")
        m.cube(g, *box([-13, 8, 10], [0, 40, 12]), m_trap, faces={"north": m_trap_in}, name="side")
        m.cube(g, *box([-13, 8, -12], [0, 40, -10]), m_trap, faces={"south": m_trap_in}, name="side")
        off = 0 if sx < 0 else 2.5
        for z in (-11.6, 11.6):  # just outside the side walls, so closed spikes never go through the other half
            y = 9 + off
            while y < 40:
                m.cube(g, *box([0.5, y, z - 0.5], [5, y + 1.2, z + 0.5]), m_spike, name="spike")
                y += 5
        for x in range(-11, 0, 4):
            m.cube(g, *box([x - 0.6 + off * 0.4, 43, -0.6], [x + 0.6 + off * 0.4, 47, 0.6]), m_spike, name="spike")
    an.key(root, "position", (0, (0, -66, 0)), (0.5, (0, -66, 0)), (0.72, (0, 5, 0), "out"), (0.82, (0, 0, 0), "io"), (2.05, (0, 0, 0)),
           (2.45, (0, -70, 0), "in"))
    an.key(root, "scale", (0, 1), (0.6, (0.88, 1.15, 0.88)), (0.75, (0.92, 1.1, 0.92)), (0.86, 1.0, "back"), (0.95, 1.0), (1.0, (1.12, 0.92, 1.12), "in"),
           (1.1, (0.95, 1.05, 0.95), "out"), (1.2, 1.0), (1.35, (1.08, 0.95, 1.08)), (1.5, 1.0), (1.65, (1.08, 0.95, 1.08)), (1.8, 1.0))
    an.key(root, "rotation", (0, (0, 0, 0)), (0.82, (0, 0, 0)), (0.88, (0, 0, 4)), (0.94, (0, 0, -3)), (1.0, (0, 0, 0)), (1.2, (0, 14, 0), "out"),
           (1.4, (0, -12, 0)), (1.6, (0, 10, 0)), (1.8, (0, 0, 0)), (2.0, (0, 0, 0)))
    open_k = [(0, 0), (0.5, 0), (0.75, 72, "back"), (0.95, 72), (1.0, 0, "in"), (1.06, 6, "out"), (1.12, 0, "in"), (1.85, 0), (1.95, 28, "out"),
              (2.1, 0, "in")]
    an.key(lj, "rotation", *[(k[0], (0, 0, k[1])) + tuple(k[2:]) for k in open_k])
    an.key(rj, "rotation", *[(k[0], (0, 0, -k[1])) + tuple(k[2:]) for k in open_k])
    for tt in (0.05, 0.25, 0.45):
        an.ev(tt, "ring dirt 16 0 1 0 10 0.25")
        an.ev(tt, "s block.rooted_dirt.break 1 0.6")
    an.ev(0.55, "p dirt 40 0 2 0 12 0.5")
    an.ev(0.55, "ring dirt 28 0 1 0 16 0.45")
    an.ev(0.55, "s block.grass.break 1 0.5")
    an.ev(0.55, "s block.big_dripleaf.tilt_up 1 0.6")
    an.ev(1.0, "s entity.evoker_fangs.attack 1 1.2")
    an.ev(1.0, "s entity.generic.eat 1 0.6")
    an.ev(1.0, "p confetti 30 0 30 0 8 0.35")
    an.ev(1.0, "p sparkle 12 0 30 0 6 0.2")
    an.ev(1.95, "s entity.player.burp 1 0.8")
    an.ev(1.95, "p heart 5 0 46 0 6 0.1")
    an.ev(2.2, "ring dirt 24 0 1 0 12 0.35")
    an.ev(2.2, "s block.rooted_dirt.break 1 0.6")
    return m, an


# ================================================================================================ UFO
def m_silver(p, f, c, t):
    x, y, z = p
    k, j, w, h = t
    if f not in ("up", "down") and j == max(1, h // 2) and k % 4 == 1:
        hue = (k // 4) % 6
        return [(255, 60, 90), (255, 200, 40), (80, 255, 120), (40, 220, 255), (150, 90, 255), (255, 90, 230)][hue]  # rainbow lights
    return mul(hexc("dfe7f2"), 0.9 + h3(x, y, z, 2) * 0.12)


def m_dome(p, f, c, t):
    x, y, z = p
    if y > 86 and -6 < x < 0:
        return (230, 255, 255)
    return mul(hexc("6ff2ff"), 0.9 + h3(x, y, z, 3) * 0.12)


def m_bottom(p, f, c, t):
    x, y, z = p
    r = math.hypot(x, z)
    if f == "down" and r < 9:
        return mul(hexc("7cffad"), 0.95 + h3(x, y, z) * 0.08)
    return mul(hexc("8f9bb0"), 0.92 + h3(x, y, z, 4) * 0.1)


def m_beam(p, f, c, t):
    k, j, w, h = t
    if (k + j) % 3 == 0 or (k * 7 + j * 3) % 11 == 0:
        return (140, 255, 170, 255)
    return (0, 0, 0, 0)


def make_ufo():
    m = Model("ufo")
    an = Anim(2.75)
    gone = 1.85
    root = m.group("ufo", [0, 72, 0])
    beam = m.group("beam", [0, 68, 0], root)
    m.cube(root, [-26, 72, -26], [26, 75, 26], m_silver, name="rim")
    m.cube(root, [-26, 72.2, -26], [26, 74.8, 26], m_silver, rot=[0, 45, 0], origin=[0, 72, 0], name="rim45")
    m.cube(root, [-19, 75, -19], [19, 80, 19], m_silver, name="upper")
    m.cube(root, [-19, 75.2, -19], [19, 79.8, 19], m_silver, rot=[0, 45, 0], origin=[0, 75, 0], name="upper45")
    m.cube(root, [-15, 68, -15], [15, 72, 15], m_bottom, name="bottom")
    m.cube(root, [-10, 80, -10], [10, 90, 10], m_dome, name="dome")
    m.cube(root, [-6, 90, -6], [6, 92, 6], m_dome, name="dome_top")
    m.cube(beam, [-12, 0, -12], [12, 68, 12], m_beam, name="beam")
    m.cube(beam, [-5, 0.5, -5], [5, 67.5, 5], m_beam, name="core")
    an.key(root, "position", (0, (-170, 50, 0)), (0.6, (0, 0, 0), "out"), (0.9, (0, 0, 0)), (1.2, (0, 3, 0)), (1.5, (0, -2, 0)), (1.8, (0, 2, 0)),
           (2.1, (0, 0, 0)), (2.25, (-12, -4, 0), "out"), (2.75, (240, 90, 0), "in"))
    an.key(root, "rotation", (0, (0, 0, -22)), (0.6, (0, 240, 9), "out"), (0.75, (0, 300, -5)), (0.9, (0, 360, 0), "out"), (2.1, (0, 1100, 0), "lin"),
           (2.25, (0, 1180, 12), "out"), (2.75, (0, 1500, -18)))
    an.key(root, "scale", (0, 1), (2.5, 1), (2.75, 0.3, "in"))
    an.key(beam, "scale", (0, (0, 1, 0)), (0.65, (0, 1, 0)), (0.8, (1.15, 1, 1.15), "out"), (0.9, 1), (1.95, 1), (2.05, (0, 1, 0), "in"))
    vk = [(0, (0, 0, 0)), (0.85, (0, 0, 0)), (1.0, (0, 6, 0), "out"), (1.8, (0, 64, 0), "io")]
    add_victim(m, an, gone, "up", scared=0.75, keys={
        "position": vk, "rotation": [(0, (0, 0, 0)), (0.9, (0, 0, 0)), (1.8, (0, 900, 10), "in")],
        "scale": [(0, 1), (1.2, 1), (1.82, 0.5, "in"), (1.85, 0, "step")]})
    an.ev(0.1, "s entity.phantom.flap 1 0.5")
    an.ev(0.65, "s block.beacon.activate 1 1.6")
    an.ev(1.1, "s block.beacon.ambient 1 1.5")
    write_events_trail(an, 0.85, 1.8, 0.12, lambda t: an.ev(t, "p sparkle 5 0 %d 0 9 0.02" % int(8 + 50 * (t - 0.85))))
    an.ev(1.85, "s entity.enderman.teleport 1 1.4")
    an.ev(1.85, "p confetti 34 0 66 0 12 0.3")
    an.ev(1.85, "p sparkle 18 0 66 0 10 0.2")
    an.ev(2.0, "s block.beacon.deactivate 1 1.6")
    an.ev(2.25, "s entity.firework_rocket.launch 1 1.4")
    return m, an


# ================================================================================================ BLACK HOLE
SWIRL = [(hexc("ff2bd6"), hexc("ffb3f2")), (hexc("9b30ff"), hexc("ff5ce6")), (hexc("3a6bff"), hexc("b26bff"))]


def ring_mat(i):
    a_col, b_col = SWIRL[i]

    def m(p, f, c, t):
        x, y, z = p
        ang = math.atan2(z, x) / (2 * math.pi)
        r = math.hypot(x, z)
        v = (ang * 4 + r / 7) % 1.0
        col = a_col if v < 0.5 else b_col
        return mul(col, 0.92 + h3(x, y, z, 7 + i) * 0.12)
    return m


def m_core(p, f, c, t):
    if h3(*p, 21) > 0.93:
        return (255, 255, 255)
    return (12, 4, 24)


def make_blackhole():
    m = Model("blackhole")
    an = Anim(2.35)
    gone = 1.35
    root = m.group("hole", [0, 0, 0])
    radii = [31, 23, 15]
    rings = []
    for i, R in enumerate(radii):
        g = m.group("ring%d" % i, [0, 0, 0], root)
        rings.append(g)
        y0 = 0.15 + i * 0.2
        for a in (0, 30, 60):
            m.cube(g, [-R, y0, -R], [R, y0 + 0.4, R], ring_mat(i), rot=[0, a, 0], origin=[0, 0, 0], name="ring")
    core = m.group("core", [0, 0, 0], root)
    for a in (0, 45):
        m.cube(core, [-8, 0.8, -8], [8, 1.3, 8], m_core, rot=[0, a, 0], origin=[0, 0, 0], name="core")
    an.key(root, "scale", (0, 0), (0.35, 1.18, "out"), (0.5, 1.0, "io"), (1.4, 1.0), (1.55, 1.25, "out"), (1.65, 1.15), (1.85, 0, "in"))
    for g, sp in zip(rings + [core], (-1080, 1440, -2000, 720)):
        an.key(g, "rotation", (0, (0, 0, 0)), (2.35, (0, sp, 0), "lin"))
    add_victim(m, an, gone, "down", scared=0.4, keys={
        "position": [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (1.0, (0, 4, 0), "out"), (1.33, (0, -34, 0), "in")],
        "rotation": [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (1.33, (0, 1440, 0), "in")],
        "scale": [(0, 1), (0.5, 1), (1.1, (0.7, 1.4, 0.7), "in"), (1.33, (0.2, 2.3, 0.2), "in"), (1.35, 0, "step")]})
    write_events_trail(an, 0.2, 1.4, 0.2, lambda t: an.ev(t, "ring portal 18 0 2 0 30 0.0"))
    an.ev(0.05, "s block.portal.trigger 0.5 1.8")
    an.ev(0.05, "s block.respawn_anchor.charge 1 0.7")
    an.ev(1.35, "s entity.enderman.teleport 1 0.6")
    an.ev(1.35, "p sparkle 18 0 4 0 6 0.25")
    an.ev(1.85, "s entity.firework_rocket.blast 1 1")
    an.ev(1.85, "s block.amethyst_block.chime 1 1.2")
    an.ev(1.85, "p confetti 50 0 4 0 8 0.5")
    an.ev(1.85, "p firework 24 0 6 0 6 0.25")
    return m, an


# ================================================================================================ FIREWORK ROCKET
def m_rocket(p, f, c, t):
    x, y, z = p
    band = int(math.floor(y / 6)) % 2
    col = hexc("ff3348") if band == 0 else hexc("ffffff")
    if f in ("south", "north", "east", "west") and band == 1 and abs((x if f in ("south", "north") else z)) < 2 and int(y) % 6 in (2, 3):
        col = hexc("ffd21a")  # stars
    return mul(col, 0.94 + h3(x, y, z, 3) * 0.08)


def make_rocket():
    m = Model("rocket")
    an = Anim(2.7)
    gone = 1.6
    APEX = 100.0
    root = m.group("rocket", [0, 0, 0])
    m.cube(root, [-6, 0, -6], [6, 34, 6], m_rocket, name="body")
    m.cube(root, [-4.5, 34, -4.5], [4.5, 39, 4.5], solid("ffd21a"), name="nose")
    m.cube(root, [-3, 39, -3], [3, 43, 3], solid("ffd21a"), name="nose")
    m.cube(root, [-1.5, 43, -1.5], [1.5, 46, 1.5], solid("ff8a1a"), name="tip")
    for a, b in (([6, 0, -1], [11, 10, 1]), ([-11, 0, -1], [-6, 10, 1]), ([-1, 0, 6], [1, 10, 11]), ([-1, 0, -11], [1, 10, -6])):
        m.cube(root, a, b, solid("2f7bff"), name="fin")
    m.cube(root, [-4, -3, -4], [4, 0, 4], solid("6b7280"), name="nozzle")

    def ry(t):
        if t < 0.35:
            return -50.0
        if t < 0.55:
            return -50 + 4 * ease("out", (t - 0.35) / 0.2)
        if t < 0.68:
            return -46 + 12 * ease("out", (t - 0.55) / 0.13)
        if t < 0.78:
            return -34 - 4 * ease("in", (t - 0.68) / 0.1)
        if t < gone:
            return -38 + (APEX + 38) * ease("in", (t - 0.78) / (gone - 0.78))
        return APEX
    rk, vk = [], []
    t = 0.0
    while t <= gone + 1e-6:
        y = ry(t)
        shake = (0.7 if int(t * 40) % 2 else -0.7) if t < 0.78 else 0
        rk.append((round(t, 3), (shake, y, 0), "lin"))
        vk.append((round(t, 3), (shake, max(0.0, y + 46), 0), "lin"))
        t += 0.02
    an.key(root, "position", *rk)
    an.key(root, "rotation", (0, (0, 0, 0)), (0.78, (0, 0, 0)), (gone, (0, 720, 0), "in"))
    an.key(root, "scale", (0, 1), (gone - 0.001, 1), (gone, 0, "step"))
    add_victim(m, an, gone, "down", scared=0.3, keys={"position": vk, "rotation": [(0, (0, 0, 0)), (0.78, (0, 0, 0)), (gone, (0, 540, 0), "in")]})
    an.ev(0.05, "s entity.creeper.primed 1 1.4")
    write_events_trail(an, 0.05, 0.7, 0.1, lambda t: an.ev(t, "p smoke 3 0 1 0 6 0.03"))
    write_events_trail(an, 0.4, 0.7, 0.1, lambda t: an.ev(t, "p flame 3 0 1 0 5 0.03"))
    an.ev(0.78, "s entity.firework_rocket.launch 1 0.8")
    write_events_trail(an, 0.8, gone - 0.05, 0.06, lambda t: (an.ev(t, "p flame 4 0 %d 0 2 0.03" % int(ry(t) - 3)),
                                                                an.ev(t, "p cloud 2 0 %d 0 2 0.02" % int(ry(t) - 3))))
    Y = int(APEX + 30)
    an.ev(gone, "s entity.firework_rocket.large_blast 1 1")
    an.ev(gone, "p firework 70 0 %d 0 10 0.35" % Y)
    an.ev(gone, "p confetti 90 0 %d 0 14 0.45" % Y)
    an.ev(gone, "p sparkle 30 0 %d 0 10 0.3" % Y)
    an.ev(gone + 0.25, "s entity.firework_rocket.twinkle 1 1")
    return m, an


# ================================================================================================ main
EFFECTS = [("pacman", "Pac-Man", make_pacman), ("shark", "Shark", make_shark), ("mimic", "Mimic Chest", make_mimic), ("frog", "Giant Frog", make_frog),
           ("flytrap", "Flytrap", make_flytrap), ("ufo", "UFO", make_ufo), ("blackhole", "Black Hole", make_blackhole),
           ("rocket", "Firework Rocket", make_rocket)]


def check_overlaps(m, fx_id):
    """Faces of two cubes in the same bone that lie in the same plane and overlap flicker (z-fighting): report them."""
    bad = []
    by = {}
    for c in m.cubes:
        if not any(c["rot"]) and not c.get("inflate"):
            by.setdefault((c["group"], "skin" in c), []).append(c)
    for cs in by.values():
        for i in range(len(cs)):
            for j in range(i + 1, len(cs)):
                a, b = cs[i], cs[j]
                for ax in range(3):
                    o1, o2 = [k for k in range(3) if k != ax]
                    ov = min(a["to"][o1], b["to"][o1]) - max(a["from"][o1], b["from"][o1])
                    ov2 = min(a["to"][o2], b["to"][o2]) - max(a["from"][o2], b["from"][o2])
                    if ov <= 0.01 or ov2 <= 0.01:
                        continue
                    for side in ("from", "to"):
                        if abs(a[side][ax] - b[side][ax]) < 0.02:
                            bad.append("%s/%s %s %s axis %d" % (a["name"], b["name"], a["group"], side, ax))
    if bad:
        print("  !! %s: %d overlapping faces, e.g. %s" % (fx_id, len(bad), bad[:4]))
    return bad


def build(fx_id, name, maker, skin):
    import bbmodel_to_killfx as conv
    m, an = maker()
    check_overlaps(m, fx_id)
    img = paint_and_pack(m)
    os.makedirs(os.path.join(HERE, "models"), exist_ok=True)
    path = os.path.join(HERE, "models", fx_id + ".bbmodel")
    write_bbmodel(m, an, img, path, fx_id, skin)
    return conv.convert(path, fx_id, name)


if __name__ == "__main__":
    import sys
    sys.path.insert(0, HERE)
    import effects2
    import effects3
    EFFECTS = EFFECTS + effects2.EFFECTS + effects3.EFFECTS
    skin = default_skin()
    skin.save(os.path.join(HERE, "default_skin.png"))
    only = sys.argv[1:]
    for fx_id, name, maker in EFFECTS:
        if not only or fx_id in only:
            build(fx_id, name, maker, skin)
