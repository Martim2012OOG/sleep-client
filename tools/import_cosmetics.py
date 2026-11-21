#!/usr/bin/env python3
"""Imports the bought cosmetic packs into Kubo.

Reads Java item models (Oraxen / ItemsAdder / Nexo resource packs) and Blockbench .bbmodel files and writes:
  - accessories -> assets/sleep_client/cosmetics/<id>.bin + textures/cosmetics/<texture>.png + an entry in index.json
  - pets        -> assets/sleep_client/pets/<id>.json (a rig of bones the game animates) + its texture + index.json

Hats are placed exactly like vanilla puts a helmet item on the head (HeadFeatureRenderer.translate + the model's
display.head transform), so they sit where the pack author designed them. Back items (tails, wings) use the same
transform on the body, wings are split in a left and a right half so the game can flap them.

Run:  python3 tools/import_cosmetics.py "<folder with the zips>" [--preview <dir>]
The packs are NOT stored in this repo (some licences forbid redistributing the source files); only the converted
models end up in the jar. See assets/sleep_client/cosmetics/CREDITS.md.
"""
import base64
import io
import json
import math
import os
import re
import struct
import sys
import tempfile
import zipfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "sleep_client")
HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, PET, WING_LEFT, WING_RIGHT = range(9)


# ---------------------------------------------------------------------------------------------------- math
def mat_id():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_t(x, y, z):
    m = mat_id()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def mat_s(x, y, z):
    m = mat_id()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def mat_rx(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def mat_ry(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def mat_rz(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def chain(*ms):
    r = mat_id()
    for m in ms:
        r = mat_mul(r, m)
    return r


# ---------------------------------------------------------------------------------------------------- meshes
class Quad:
    __slots__ = ("v", "tex", "bone")

    def __init__(self, v, tex, bone=None):
        self.v = v  # 4 x (x, y, z, u, v): u, v normalised 0..1 inside texture 'tex'
        self.tex = tex
        self.bone = bone


class Mesh:
    def __init__(self):
        self.quads = []
        self.textures = []  # PIL images (RGBA)
        self.display_head = None
        self.bones = {}  # name -> dict(origin=(x,y,z), parent=name or None)

    def transform(self, m):
        for q in self.quads:
            q.v = [apply(m, p[:3]) + (p[3], p[4]) for p in q.v]
        for b in self.bones.values():
            b["origin"] = apply(m, b["origin"])

    def bbox(self):
        pts = [p for q in self.quads for p in q.v]
        return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


# vanilla CubeFace corner order and default uvs (FaceBakery / ModelElementFace)
FACE_CORNERS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def default_uv(face, f, t):
    if face == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if face == "up":
        return [f[0], f[2], t[0], t[2]]
    if face == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if face == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if face == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]


def cube_quads(frm, to, faces, rot_m, uv_size, tex_of, bone=None, inflate=0.0):
    """faces: name -> dict(uv, rotation, texture). uv_size: (w, h) of the uv space for a texture index."""
    out = []
    f = [frm[i] - inflate for i in range(3)]
    t = [to[i] + inflate for i in range(3)]
    for name, fd in faces.items():
        if fd is None or name not in FACE_CORNERS:
            continue
        ti = tex_of(fd.get("texture"))
        if ti is None:
            continue
        uv = fd.get("uv") or default_uv(name, frm, to)
        uw, vh = uv_size(ti)
        r = int(fd.get("rotation", 0) or 0) // 90
        corners = [(uv[0], uv[1]), (uv[0], uv[3]), (uv[2], uv[3]), (uv[2], uv[1])]
        verts = []
        for i, c in enumerate(FACE_CORNERS[name]):
            p = (f[0] if c[0] == 0 else t[0], f[1] if c[1] == 0 else t[1], f[2] if c[2] == 0 else t[2])
            p = apply(rot_m, p)
            cu, cv = corners[(i + r) % 4]
            verts.append(p + (cu / uw, cv / vh))
        out.append(Quad(verts, ti, bone))
    return out


def element_rotation(rot):
    """Java model element rotation (one axis + angle, optional rescale; or the newer x/y/z form)."""
    if not rot:
        return mat_id()
    o = rot.get("origin", [8, 8, 8])
    if "axis" in rot:
        a = float(rot.get("angle", 0))
        ax = rot["axis"]
        r = mat_rx(a) if ax == "x" else mat_ry(a) if ax == "y" else mat_rz(a)
        if rot.get("rescale"):
            k = 1 / math.cos(math.radians(a)) if abs(a) < 89 else 1
            s = mat_s(1 if ax == "x" else k, 1 if ax == "y" else k, 1 if ax == "z" else k)
            r = mat_mul(r, s)
    else:
        r = chain(mat_rx(rot.get("x", 0)), mat_ry(rot.get("y", 0)), mat_rz(rot.get("z", 0)))
    return chain(mat_t(*o), r, mat_t(-o[0], -o[1], -o[2]))


# ---------------------------------------------------------------------------------------------------- loaders
def first_frame(img, png_path, search_roots=()):
    """Animated textures (a .mcmeta next to it, or anywhere in the pack with the same name): keep the first frame."""
    if img.height <= img.width or img.height % img.width:
        return img
    meta = os.path.exists(png_path + ".mcmeta")
    name = os.path.basename(png_path) + ".mcmeta"
    for root in search_roots:
        for _, _, fs in os.walk(root):
            if name in fs:
                meta = True
                break
    return img.crop((0, 0, img.width, img.width)) if meta else img


def find_texture(ref, search_roots):
    """'ns:path/name' or 'path/name' -> a png inside the pack folders."""
    ref = ref.split(":")[-1]
    want = "/" + ref + ".png"
    base = "/" + ref.split("/")[-1] + ".png"
    best = None
    for root in search_roots:
        for dp, _, fs in os.walk(root):
            for fn in fs:
                p = os.path.join(dp, fn).replace("\\", "/")
                if p.endswith(want):
                    return p
                if best is None and p.endswith(base):
                    best = p
    return best


def read_json(path):
    """Pack JSON is often hand-edited: allow trailing commas and // comments."""
    t = open(path, encoding="utf-8-sig").read()
    t = re.sub(r"^\s*//.*$", "", t, flags=re.M)
    t = re.sub(r",(\s*[}\]])", r"\1", t)
    return json.loads(t)


def load_java(path, search_roots):
    d = read_json(path)
    m = Mesh()
    texmap = dict(d.get("textures") or {})

    def resolve(key):
        seen = 0
        while key and key.startswith("#") and seen < 8:
            key = texmap.get(key[1:])
            seen += 1
        return key

    index = {}

    def tex_of(ref):
        if ref is None:
            return None
        if ref.startswith("#"):
            ref = resolve(ref)
        if ref is None:
            return None
        if ref not in index:
            p = find_texture(ref, search_roots)
            if p is None:
                print("   ! missing texture", ref, "for", os.path.basename(path))
                index[ref] = None
            else:
                img = first_frame(Image.open(p).convert("RGBA"), p, search_roots)
                index[ref] = len(m.textures)
                m.textures.append(img)
        return index[ref]

    for i, e in enumerate(d.get("elements", [])):
        rm = element_rotation(e.get("rotation"))
        m.quads += cube_quads(e["from"], e["to"], e.get("faces", {}), rm, lambda t: (16.0, 16.0), tex_of, "e%d" % i)
    m.display_head = (d.get("display") or {}).get("head")
    return m


def load_bbmodel(path, groups_as_bones=True):
    d = json.load(open(path, encoding="utf-8"))
    m = Mesh()
    res = d.get("resolution", {"width": 16, "height": 16})
    fmt = d.get("meta", {}).get("model_format", "java_block")
    sizes = []
    for t in d.get("textures", []):
        src = t.get("source", "")
        if src.startswith("data:"):
            img = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
        else:
            img = Image.open(t["path"]).convert("RGBA")
        fh = t.get("frame_time") and img.height > img.width
        if img.height > img.width and img.height % img.width == 0 and (fh or t.get("frame_order_type")):
            img = img.crop((0, 0, img.width, img.width))
        m.textures.append(img)
        sizes.append((float(t.get("uv_width", res["width"])), float(t.get("uv_height", res["height"]))))
    by_uuid = {e["uuid"]: e for e in d.get("elements", [])}
    group_info = {g["uuid"]: g for g in d.get("groups", []) or []}
    parent_of = {}

    def euler(rot):
        rot = rot or [0, 0, 0]
        return chain(mat_rz(rot[2]), mat_ry(rot[1]), mat_rx(rot[0]))

    def pivot_rot(origin, rot):
        o = origin or [0, 0, 0]
        return chain(mat_t(*o), euler(rot), mat_t(-o[0], -o[1], -o[2]))

    def walk(nodes, parent_m, bone, parent_name):
        for n in nodes:
            if isinstance(n, str):
                e = by_uuid.get(n)
                if e is None or e.get("visibility") is False or e.get("export") is False:
                    continue
                if e.get("type", "cube") != "cube":
                    continue
                em = chain(parent_m, pivot_rot(e.get("origin"), e.get("rotation")))
                faces = {}
                for fn, fd in e.get("faces", {}).items():
                    if fd.get("texture") is None:
                        continue
                    faces[fn] = {"uv": fd.get("uv"), "rotation": fd.get("rotation", 0), "texture": fd.get("texture")}
                m.quads += cube_quads(e["from"], e["to"], faces, em, lambda i: sizes[i],
                                      lambda r: r if isinstance(r, int) and 0 <= r < len(m.textures) else None,
                                      bone, float(e.get("inflate", 0) or 0))
            else:
                g = dict(group_info.get(n.get("uuid"), {}))
                g.update({k: v for k, v in n.items() if k != "children"})
                if g.get("visibility") is False:
                    continue
                name = g.get("name") or ("g%d" % len(m.bones))
                while name in m.bones:
                    name += "_"
                origin = g.get("origin", [0, 0, 0])
                gm = chain(parent_m, pivot_rot(origin, g.get("rotation")))
                m.bones[name] = {"origin": apply(parent_m, origin), "parent": parent_name}
                walk(n.get("children", []), gm, name if groups_as_bones else bone, name)

    walk(d.get("outliner", []), mat_id(), None, None)
    m.display_head = (d.get("display") or {}).get("head")
    m.format = fmt
    return m


# ---------------------------------------------------------------------------------------------------- placement
def head_item_matrix(display):
    """Item model space (pixels, y up, 0..16) -> head part space of the player model (pixels, y down)."""
    t = [0, 0, 0]
    r = [0, 0, 0]
    s = [1, 1, 1]
    if display:
        t = [max(-80, min(80, v)) / 16.0 for v in display.get("translation", t)]
        r = display.get("rotation", r)
        s = [max(-4, min(4, v)) for v in display.get("scale", s)]
    # JOML rotationXYZ = Rx * Ry * Rz
    disp = chain(mat_t(*t), mat_rx(r[0]), mat_ry(r[1]), mat_rz(r[2]), mat_s(*s), mat_t(-0.5, -0.5, -0.5))
    head = chain(mat_t(0, -0.25, 0), mat_ry(180), mat_s(0.625, -0.625, -0.625))
    return chain(mat_s(16, 16, 16), head, disp, mat_s(1 / 16.0, 1 / 16.0, 1 / 16.0))


# ---------------------------------------------------------------------------------------------------- output
def build_atlas(textures):
    """Stacks the textures of one model under each other; returns the image and per-texture (x0, y0, w, h)."""
    if not textures:
        return Image.new("RGBA", (16, 16), (255, 0, 255, 255)), []
    w = max(t.width for t in textures)
    h = sum(t.height for t in textures)
    atlas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rects = []
    y = 0
    for t in textures:
        atlas.paste(t, (0, y))
        rects.append((0, y, t.width, t.height))
        y += t.height
    return atlas, rects


def atlas_uv(q, rects, W, H):
    x0, y0, w, h = rects[q.tex]
    return [(p[0], p[1], p[2], (x0 + p[3] * w) / W, (y0 + p[4] * h) / H) for p in q.v]


def write_bin(path, parts):
    """parts: list of (attach, quads with atlas uvs in .v). Two triangles per quad."""
    out = bytearray(struct.pack(">iii", 0x504C4347, 1, len(parts)))
    for attach, quads in parts:
        tris = []
        for q in quads:
            a, b, c, d = q
            tris.append((a, b, c))
            tris.append((a, c, d))
        out += struct.pack(">bi", attach, len(tris))
        for tri in tris:
            for p in tri:
                out += struct.pack(">5f", *p)
    with open(path, "wb") as f:
        f.write(out)


# ---------------------------------------------------------------------------------------------------- preview
def render_preview(path, items, size=150):
    """items: list of (title, list of (quads in player model space px, y down) , atlas image). Draws a grey player."""
    try:
        from PIL import ImageDraw
    except ImportError:
        return
    views = [(-35, 15), (145, 15), (90, 5)]
    cols = len(views)
    sheet = Image.new("RGB", (size * cols, (size + 14) * len(items)), (40, 42, 50))
    dr = ImageDraw.Draw(sheet)
    player = []

    def box(x0, y0, z0, x1, y1, z1, col):
        c = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        for f in [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]:
            player.append(([c[i] for i in f], col))
    box(-4, -8, -4, 4, 0, 4, (150, 150, 160))
    box(-1.5, -5, -4.05, -0.5, -4, -4.05, (30, 30, 200))  # an eye on the front (-z): shows where the face is
    box(0.5, -5, -4.05, 1.5, -4, -4.05, (30, 30, 200))
    box(-4, 0, -2, 4, 12, 2, (110, 110, 125))
    box(-8, 0, -2, -4, 12, 2, (130, 130, 140))
    box(4, 0, -2, 8, 12, 2, (130, 130, 140))
    for row, (title, parts, atlas) in enumerate(items):
        px = atlas.load()
        AW, AH = atlas.size
        for col, (yaw, pitch) in enumerate(views):
            img = Image.new("RGB", (size, size), (40, 42, 50))
            zb = [[1e9] * size for _ in range(size)]
            ip = img.load()
            cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
            cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
            sc = size / 44.0

            def proj(p):
                x, y, z = p[0], p[1] - 2, p[2]
                x, z = x * cy - z * sy, x * sy + z * cy
                y, z = y * cp - z * sp, y * sp + z * cp
                return (size / 2 + x * sc, size / 2 + y * sc, z)

            tris = []
            for quad, colr in player:
                pr = [proj(p) for p in quad]
                tris.append((pr[0], pr[1], pr[2], None, colr))
                tris.append((pr[0], pr[2], pr[3], None, colr))
            for quads in parts:
                for q in quads:
                    pr = [proj(p[:3]) + (p[3], p[4]) for p in q]
                    tris.append((pr[0], pr[1], pr[2], True, None))
                    tris.append((pr[0], pr[2], pr[3], True, None))
            for a, b, c, tex, colr in tris:
                minx, maxx = int(max(0, min(a[0], b[0], c[0]))), int(min(size - 1, max(a[0], b[0], c[0])))
                miny, maxy = int(max(0, min(a[1], b[1], c[1]))), int(min(size - 1, max(a[1], b[1], c[1])))
                den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(den) < 1e-9:
                    continue
                for yy in range(miny, maxy + 1):
                    for xx in range(minx, maxx + 1):
                        l1 = ((b[1] - c[1]) * (xx + .5 - c[0]) + (c[0] - b[0]) * (yy + .5 - c[1])) / den
                        l2 = ((c[1] - a[1]) * (xx + .5 - c[0]) + (a[0] - c[0]) * (yy + .5 - c[1])) / den
                        l3 = 1 - l1 - l2
                        if l1 < 0 or l2 < 0 or l3 < 0:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                        if z >= zb[yy][xx]:
                            continue
                        if tex:
                            u = l1 * a[3] + l2 * b[3] + l3 * c[3]
                            v = l1 * a[4] + l2 * b[4] + l3 * c[4]
                            t = px[min(AW - 1, max(0, int(u * AW))), min(AH - 1, max(0, int(v * AH)))]
                            if t[3] < 30:
                                continue
                            colr2 = t[:3]
                        else:
                            colr2 = colr
                        zb[yy][xx] = z
                        ip[xx, yy] = colr2
            sheet.paste(img, (col * size, row * (size + 14) + 14))
        dr.text((4, row * (size + 14) + 1), title, fill=(230, 230, 240))
    sheet.save(path)


# ---------------------------------------------------------------------------------------------------- packs
def extract_all(src):
    """Unzips every zip (and zips inside zips) of the source folder into a temp folder."""
    tmp = tempfile.mkdtemp(prefix="kubo_cosm_")
    todo = [(os.path.join(src, f), os.path.join(tmp, os.path.splitext(f)[0])) for f in os.listdir(src) if f.endswith(".zip")]
    while todo:
        z, dst = todo.pop()
        os.makedirs(dst, exist_ok=True)
        with zipfile.ZipFile(z) as zf:
            for n in zf.namelist():
                if "__MACOSX" in n or n.endswith("/"):
                    continue
                target = os.path.realpath(os.path.join(dst, n))
                if not target.startswith(os.path.realpath(dst)):
                    continue
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with zf.open(n) as fi, open(target, "wb") as fo:
                    fo.write(fi.read())
        for dp, _, fs in os.walk(dst):
            for f in fs:
                if f.endswith(".zip") and "Versions" not in dp:
                    todo.append((os.path.join(dp, f), os.path.join(dp, os.path.splitext(f)[0])))
    return tmp


def find(root, *parts):
    """First file whose path contains every part (in order)."""
    for dp, _, fs in sorted(os.walk(root)):
        for f in sorted(fs):
            p = os.path.join(dp, f).replace("\\", "/")
            pos = 0
            ok = True
            for part in parts:
                i = p.find(part, pos)
                if i < 0:
                    ok = False
                    break
                pos = i + len(part)
            if ok:
                return p
    return None


def nice(s):
    return " ".join(w[:1].upper() + w[1:] for w in re.split(r"[_\s-]+", s) if w)


# ---------------------------------------------------------------------------------------------------- catalogue
REHIGA = "Rehiga (free to edit and use in servers/content with credit, not for resale)"
COLOURS7 = ["white", "black", "red", "blue", "green", "purple", "yellow", "orange", "pink", "gray", "brown"]


def accessories(Z):
    """(id, name, category, kind, model path, credit, extra) for every accessory in the packs."""
    out = []

    def add(id_, name, cat, kind, path, credit, **kw):
        if path is None:
            print("   ! not found:", id_)
            return
        out.append(dict(id=id_, name=name, category=cat, kind=kind, path=path, credit=credit, **kw))

    # Among Us hats (7 colours)
    for c in ["White", "Black", "Red", "Blue", "Green", "Pink", "Purple", "Yellow"]:
        add("amongus_hat_" + c.lower(), "Among Us Hat (" + c + ")", "Headwear", "head",
            find(Z, "AmongUs Hat/AmongUs" + c + "/AmongUs" + c + ".json"), "AmongUs Hat by HappyKrazy")
    # Hat Parade (30 original hats with editable models)
    cat = json.load(open(find(Z, "Free_Hats_Final_EN/catalog.json")))
    for h in cat:
        add("hp_" + h["id"], h["name"], "Headwear", "head", find(Z, "Free_Hats_Final_EN/Models/" + h["id"] + ".json"),
            "Hat Parade free hats pack")
    # Artillex-Studios hats
    for n, nm in [("diving_helmet", "Diving Helmet"), ("chicken_hat", "Chicken Hat"), ("flight_hat", "Flight Hat"),
                  ("military_helmet", "Military Helmet"), ("motorcycle_helmet", "Motorcycle Helmet"), ("pot_hat", "Pot Hat")]:
        add("ax_" + n, nm, "Headwear", "head", find(Z, "Artillex-Studios/Oraxen/pack/models/ax_free_hats_pack/" + n + ".json"),
            "Free Hats Pack by Artillex-Studios")
    # Hats.zip
    for n, nm in [("tophat", "Classic Top Hat"), ("dimmadome", "Dimmadome Hat"), ("propeller_hat", "Propeller Hat"),
                  ("crown", "Royal Gold Crown"), ("sstraw_hat", "Summer Straw Hat"), ("clown_mask", "Clown Mask")]:
        add("hats_" + n, nm, "Headwear", "head", find(Z, "Hats/Hats/json/" + n + ".json"), "Hats pack")
    # HeisDevs winter hats
    for n, nm, kind in [("santa", "Santa Beard Hat", "head"), ("santahat", "Winter Santa Hat", "head"), ("deer", "Reindeer Hood", "head"),
                        ("deerhat", "Reindeer Hat", "head"), ("light_elf", "Light Elf Hat", "head"), ("dark_elf", "Dark Elf Hat", "head"),
                        ("snowman", "Snowman Head", "head"), ("scarf", "Winter Scarf", "neck")]:
        add("winter_" + n, nm, "Bodywear" if kind == "neck" else "Headwear", kind,
            find(Z, "HeisDevs_Winter_Hats", "decorations/" + n + ".json"), "Winter Hats by HeisDev's")
    # cactus hat
    add("cactus_hat", "Cactus Hat", "Headwear", "head", find(Z, "cactushat", "models/hat/cactus_hat.json"), "Cactus Hat")
    # Rehiga: bucket in head, halo, short hat (colours) + elven ranger wings
    for base, nm in [("bucket_in_head", "Bucket Head"), ("halo", "Halo"), ("short_hat", "Short Hat")]:
        for c in COLOURS7:
            p = find(Z, "[Rehiga]" + base, "Oraxen/pack/models", base + "_" + c + ".json")
            if p:
                add(base + "_" + c, nm + " (" + c.capitalize() + ")", "Headwear", "head", p, REHIGA)
    for v in ["elf", "forest", "leaf", "river", "royal", "waterfall", "corrupt"]:
        add("elven_wings_" + v, "Elven Ranger Wings (" + v.capitalize() + ")", "Wings", "wings",
            find(Z, "elven_ranger_wings/Oraxen/pack/models", "_" + v + ".json"), REHIGA, dy=-3)
    # fairy wings (Blockbench)
    add("fairy_wings", "Fairy Wings", "Wings", "wings", find(Z, "fairywings", "fairy_wings.bbmodel"), "Fairy Wings by SheepModels (ciaran_sh)")
    # Feral Fantasy: ears/horns on the head, tails on the back
    for a in ["bear", "cat", "fox", "rabbit", "tiger", "wolf"]:
        add("feral_" + a + "_ears", nice(a) + " Ears", "Headwear", "head", find(Z, "Vol 1/j/Oraxen", "/" + a + "_ears.json"),
            "Feral Fantasy Set by Erocavontic")
        add("feral_" + a + "_tail", nice(a) + " Tail", "Tails", "tail", find(Z, "Vol 1/j/Oraxen", "/" + a + "_tail_cosmetic.json"),
            "Feral Fantasy Set by Erocavontic")
    for a in ["cow", "deer", "goat", "horse", "sheep"]:
        add("feral_" + a + "_ears", nice(a) + " Ears", "Headwear", "head", find(Z, "Vol 2/j/Oraxen", "/" + a + "_ears_hat.json"),
            "Feral Fantasy Set Vol 2 by Erocavontic")
        add("feral_" + a + "_horns", nice(a) + " Horns", "Headwear", "head", find(Z, "Vol 2/j/Oraxen", "/" + a + "_horn_hat.json"),
            "Feral Fantasy Set Vol 2 by Erocavontic")
        add("feral_" + a + "_tail", nice(a) + " Tail", "Tails", "tail", find(Z, "Vol 2/j/Oraxen", "/" + a + "_tail_backpack_cosmetic.json"),
            "Feral Fantasy Set Vol 2 by Erocavontic")
    return out


def pack_roots(path):
    """Folders to search the textures of a model in: its resource pack (a few levels up)."""
    roots = []
    d = os.path.dirname(path)
    for _ in range(6):
        roots.append(d)
        d = os.path.dirname(d)
    return [roots[min(4, len(roots) - 1)]]


def convert_accessory(a):
    if a["path"].endswith(".bbmodel"):
        m = load_bbmodel(a["path"], groups_as_bones=False)
    else:
        m = load_java(a["path"], pack_roots(a["path"]))
    if not m.quads:
        print("   ! empty model:", a["id"])
        return None
    m.transform(head_item_matrix(m.display_head))
    kind = a["kind"]
    if kind in ("tail", "wings"):
        # the packs place these for a server-side display entity; put them on the back of the body instead
        (x0, y0, z0), (x1, y1, z1) = m.bbox()
        if kind == "tail":
            dy, dz = 8.5 - y0, 1.6 - z0
        else:
            dy, dz = 4.5 - (y0 + y1) / 2 + a.get("dy", 0), 2.2 - z0
        m.transform(mat_t(-(x0 + x1) / 2, dy, dz))
    atlas, rects = build_atlas(m.textures)
    W, H = atlas.size
    if kind == "wings":
        left = [atlas_uv(q, rects, W, H) for q in m.quads if sum(p[0] for p in q.v) >= 0]
        right = [atlas_uv(q, rects, W, H) for q in m.quads if sum(p[0] for p in q.v) < 0]
        parts = [(WING_LEFT, left), (WING_RIGHT, right)]
    else:
        parts = [(HEAD if kind == "head" else BODY, [atlas_uv(q, rects, W, H) for q in m.quads])]
    return parts, atlas


def main():
    args = sys.argv[1:]
    preview = None
    if "--preview" in args:
        i = args.index("--preview")
        preview = args[i + 1]
        del args[i:i + 2]
    only = None
    if "--only" in args:
        i = args.index("--only")
        only = args[i + 1]
        del args[i:i + 2]
    src = args[0] if args else os.path.expanduser("~/Downloads/AdicionarNoSolar(Kubo)")
    Z = src if not any(f.endswith(".zip") for f in os.listdir(src)) else extract_all(src)
    print("packs in", Z)
    index_path = os.path.join(ASSETS, "cosmetics", "index.json")
    index = json.load(open(index_path))
    by_id = {e["id"]: e for e in index}
    previews = []
    credits = {}
    for a in accessories(Z):
        if only and not re.search(only, a["id"]):
            continue
        r = convert_accessory(a)
        if r is None:
            continue
        parts, atlas = r
        write_bin(os.path.join(ASSETS, "cosmetics", a["id"] + ".bin"), parts)
        atlas.save(os.path.join(ASSETS, "textures", "cosmetics", a["id"] + ".png"), optimize=True)
        e = {"id": a["id"], "name": a["name"], "category": a["category"], "geometry": a["id"], "texture": a["id"],
             "credit": a["credit"], "textureSize": [atlas.width, atlas.height], "source": "pack"}
        if a["kind"] == "wings":
            e["anim"] = "wings"
        by_id[a["id"]] = e
        credits.setdefault(a["credit"], []).append(a["name"])
        previews.append((a["id"], [p[1] for p in parts], atlas))
        print("  ok", a["id"], sum(len(p[1]) for p in parts), "quads")
    try:
        import import_pets
        for e in import_pets.run(Z, ASSETS, previews if preview else None, only):
            by_id[e["id"]] = e
            credits.setdefault(e["credit"], []).append(e["name"])
    except ImportError:
        pass
    json.dump(list(by_id.values()), open(index_path, "w"), indent=1)
    write_credits(credits)
    if preview:
        os.makedirs(preview, exist_ok=True)
        for i in range(0, len(previews), 8):
            render_preview(os.path.join(preview, "sheet_%02d.png" % (i // 8)), previews[i:i + 8])
    print("done:", len(by_id), "cosmetics in index.json")


def write_credits(credits):
    path = os.path.join(ASSETS, "cosmetics", "CREDITS.md")
    text = open(path).read()
    marker = "\n## Bought / free packs (converted by tools/import_cosmetics.py)\n"
    text = text.split(marker)[0].rstrip() + "\n" + marker
    for c, names in sorted(credits.items()):
        text += "- **" + c + "**: " + ", ".join(sorted(set(names))) + "\n"
    open(path, "w").write(text)


if __name__ == "__main__":
    main()
