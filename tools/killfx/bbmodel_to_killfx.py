#!/usr/bin/env python3
"""
Converts a Blockbench project (.bbmodel, "Generic Model" format) into a Kubo kill effect:
  src/main/resources/assets/sleep_client/killfx/<id>.json
  src/main/resources/assets/sleep_client/textures/killfx/<id>.png

Usage:  python3 tools/killfx/bbmodel_to_killfx.py my_effect.bbmodel [id] ["Display Name"]

What is read from the project:
  - cubes (from / to / inflate / origin / rotation) with per-face UV and the first texture
  - groups (bones): origin = pivot, rotation = rest rotation, nesting
  - the first animation whose name contains "kill" (or the first one): bone rotation / position / scale keyframes
    (linear, catmullrom and step; bezier is played as linear)
  - the "Effects" animator: every line of a timeline keyframe becomes an event, sound keyframes become "s <sound>"
  - groups whose name starts with "preview" are kept but flagged: the game skips them (the dead player stand-in)

Events (timeline scripts, one per line, units = model pixels, x/z are turned to face the viewer in game):
  s <sound.id> [volume] [pitch]
  p <particle> <count> <x> <y> <z> [spread] [speed]      particle: splash, water, blood, gold, crit, cloud, poof, ...
  ring <particle> <count> <x> <y> <z> <radius> [speed]
  hide                                                   (preview only: the victim disappears)
"""
import base64, json, math, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "sleep_client")

# corners of each face in texture order TL, TR, BR, BL (seen from outside), Minecraft / Blockbench convention
def face_corners(f, a, b):
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


def rot_zyx(v, r, o):
    """Euler rotation in degrees, order ZYX (matrix = Rz * Ry * Rx), around origin o."""
    x, y, z = v[0] - o[0], v[1] - o[1], v[2] - o[2]
    ax, ay, az = (math.radians(t) for t in r)
    # Rx
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    # Ry
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    # Rz
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + o[0], y + o[1], z + o[2])


def num(v):
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(str(v).strip() or 0)
    except ValueError:
        return 0.0  # molang is not supported


def convert(path, fx_id=None, name=None, out_res=RES):
    bb = json.load(open(path))
    fx_id = fx_id or re.sub(r"[^a-z0-9_]", "_", os.path.splitext(os.path.basename(path))[0].lower())
    name = name or bb.get("name") or fx_id
    texs = bb["textures"]
    tex = texs[0]

    def tex_index(ref):
        if isinstance(ref, int) or (isinstance(ref, str) and ref.isdigit() and int(ref) < len(texs) and not any(t.get("uuid") == ref for t in texs)):
            return int(ref)
        for i, t in enumerate(texs):
            if t.get("uuid") == ref or t.get("id") == ref:
                return i
        return 0

    def uvsize(i):
        t = texs[i]
        return (t.get("uv_width") or t.get("width") or bb.get("resolution", {}).get("width", 16),
                t.get("uv_height") or t.get("height") or bb.get("resolution", {}).get("height", 16))

    def is_skin(i):
        return "skin" in texs[i].get("name", "").lower()
    elements = {e["uuid"]: e for e in bb["elements"] if e.get("type", "cube") == "cube"}

    bones, uuid_to_bone = [], {}

    def add_bone(g, parent):
        idx = len(bones)
        bones.append({"name": g["name"], "pivot": [num(c) for c in g.get("origin", [0, 0, 0])], "rest": [num(c) for c in g.get("rotation", [0, 0, 0])],
                      "parent": parent, "preview": (g["name"].lower().startswith("preview") or (parent >= 0 and bones[parent]["preview"])), "skin": False, "quads": []})
        uuid_to_bone[g["uuid"]] = idx
        for c in g.get("children", []):
            if isinstance(c, str):
                if c in elements:
                    add_cube(elements[c], idx)
            elif c.get("visibility", True) is not False:
                add_bone(c, idx)

    def add_cube(e, bi):
        inf = num(e.get("inflate", 0))
        a = [num(e["from"][i]) - inf for i in range(3)]
        b = [num(e["to"][i]) + inf for i in range(3)]
        o = [num(c) for c in e.get("origin", [0, 0, 0])]
        r = [num(c) for c in e.get("rotation", [0, 0, 0])]
        for f, fd in e["faces"].items():
            if fd.get("texture") is None or "uv" not in fd:
                continue
            ti = tex_index(fd["texture"])
            uw, uh = uvsize(ti)
            if is_skin(ti):
                bones[bi]["skin"] = True  # drawn with the dead player's own skin in game
            u0, v0, u1, v1 = (num(c) for c in fd["uv"])
            uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]  # TL TR BR BL
            k = int(num(fd.get("rotation", 0)) // 90) % 4
            uv = uv[-k:] + uv[:-k] if k else uv
            cs = [rot_zyx(c, r, o) if any(r) else c for c in face_corners(f, a, b)]
            for ci in (0, 3, 2, 1):  # TL, BL, BR, TR: counter-clockwise from outside -> outward normal
                p = cs[ci]
                bones[bi]["quads"] += [round(p[0], 4), round(p[1], 4), round(p[2], 4), round(uv[ci][0] / uw, 6), round(uv[ci][1] / uh, 6)]

    loose = []
    for item in bb["outliner"]:
        if isinstance(item, str):
            loose.append(item)
        else:
            add_bone(item, -1)
    if loose:
        bones.append({"name": "root", "pivot": [0, 0, 0], "rest": [0, 0, 0], "parent": -1, "preview": False, "skin": False, "quads": []})
        for u in loose:
            if u in elements:
                add_cube(elements[u], len(bones) - 1)

    anims = bb.get("animations", [])
    anim = next((a for a in anims if "kill" in a.get("name", "").lower()), anims[0] if anims else None)
    tracks, events, length = [], [], 0.0
    if anim:
        length = num(anim.get("length", 0))
        for uid, an in anim.get("animators", {}).items():
            if an.get("type") == "effect" or uid == "effects":
                for kf in an.get("keyframes", []):
                    t = num(kf["time"])
                    for dp in kf.get("data_points", []):
                        if kf["channel"] == "timeline":
                            for line in str(dp.get("script", "")).replace(";", "\n").splitlines():
                                if line.strip() and not line.strip().startswith("#"):
                                    events.append([round(t, 3), line.strip()])
                        elif kf["channel"] == "sound" and dp.get("effect"):
                            events.append([round(t, 3), "s " + dp["effect"]])
                continue
            bi = uuid_to_bone.get(uid)
            if bi is None:
                continue
            by = {}
            for kf in an.get("keyframes", []):
                by.setdefault(kf["channel"], []).append(kf)
            for ch, kfs in by.items():
                if ch not in ("rotation", "position", "scale"):
                    continue
                keys = []
                for kf in sorted(kfs, key=lambda k: num(k["time"])):
                    dp = kf["data_points"][0]
                    x, y, z = num(dp.get("x", 0)), num(dp.get("y", 0)), num(dp.get("z", 0))
                    if ch == "rotation":  # Blockbench shows bone rotation as x -= X, y -= Y, z += Z
                        x, y, z = -math.radians(x), -math.radians(y), math.radians(z)
                    elif ch == "position":  # x -= X, y += Y, z += Z
                        x = -x
                    it = {"linear": 0, "catmullrom": 1, "step": 2}.get(kf.get("interpolation", "linear"), 0)
                    keys.append([round(num(kf["time"]), 4), round(x, 5), round(y, 5), round(z, 5), it])
                tracks.append({"bone": bi, "ch": ch[0], "keys": keys})
    events.sort(key=lambda e: e[0])
    out = {"id": fx_id, "name": name, "length": round(length, 3), "texture": fx_id,
           "bones": [{"name": b["name"], "pivot": b["pivot"], "rest": [math.radians(c) for c in b["rest"]], "parent": b["parent"],
                      "preview": b["preview"], "skin": b["skin"], "quads": b["quads"]} for b in bones],
           "tracks": tracks, "events": events}
    os.makedirs(os.path.join(out_res, "killfx"), exist_ok=True)
    os.makedirs(os.path.join(out_res, "textures", "killfx"), exist_ok=True)
    with open(os.path.join(out_res, "killfx", fx_id + ".json"), "w") as f:
        json.dump(out, f, separators=(",", ":"))
    src = tex.get("source", "")
    if src.startswith("data:image/png;base64,"):
        with open(os.path.join(out_res, "textures", "killfx", fx_id + ".png"), "wb") as f:
            f.write(base64.b64decode(src.split(",", 1)[1]))
    elif tex.get("path") and os.path.exists(tex["path"]):
        import shutil
        shutil.copy(tex["path"], os.path.join(out_res, "textures", "killfx", fx_id + ".png"))
    else:
        print("warning: texture not embedded, copy it to textures/killfx/%s.png yourself" % fx_id)
    print("killfx %s: %d bones, %d quads, %d tracks, %d events, %.2fs" % (fx_id, len(bones), sum(len(b["quads"]) // 20 for b in bones), len(tracks),
                                                                     len(events), length))
    return out


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    convert(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None, sys.argv[3] if len(sys.argv) > 3 else None)
