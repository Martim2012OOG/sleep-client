"""Puts the colour variants of a cosmetic into one group (index.json: group, groupName, variant, swatch), so the Locker shows ONE
item with a colour picker. Run after gen_cosmetic_thumbs.py:  python3 tools/group_variants.py"""
import json
import os
import re
from PIL import Image

A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sleep_client")
GROUPS = [("amongus_hat_", "amongus_hat", "Among Us Hat"), ("bucket_in_head_", "bucket_head", "Bucket Head"), ("halo_", "halo", "Halo"),
          ("short_hat_", "short_hat", "Short Hat"), ("mini_sus_", "mini_crewmate", "Mini Crewmate"), ("elven_wings_", "elven_wings", "Elven Ranger Wings")]
SPECIAL = {"ram_horns": ("ram_horns", "Ram Horns", "I"), "ram_horns_ii": ("ram_horns", "Ram Horns", "II"), "ram_horns_iii": ("ram_horns", "Ram Horns", "III"),
           "fox_ears": ("fox_ears", "Fox Ears", "Orange"), "dark_fox_ears": ("fox_ears", "Fox Ears", "Dark")}


def swatch(eid):
    p = os.path.join(A, "textures", "cosmetics", "thumb", eid + ".png")
    if not os.path.exists(p):
        return "#888888"
    im = Image.open(p).convert("RGBA")
    best = []
    for r, g, b, a in im.getdata():
        if a > 200:
            sat = max(r, g, b) - min(r, g, b)
            best.append((sat, r, g, b))
    if not best:
        return "#888888"
    best.sort(reverse=True)
    top = best[:max(1, len(best) // 4)]  # the most colourful quarter: what the eye calls "the colour"
    r = sum(t[1] for t in top) // len(top)
    g = sum(t[2] for t in top) // len(top)
    b = sum(t[3] for t in top) // len(top)
    return "#%02x%02x%02x" % (r, g, b)


idx = json.load(open(os.path.join(A, "cosmetics", "index.json")))
n = 0
for e in idx:
    for k in ("group", "groupName", "variant", "swatch"):
        e.pop(k, None)
    eid = e["id"]
    if eid in SPECIAL:
        g, gn, v = SPECIAL[eid]
    else:
        g = None
        for prefix, gid, gname in GROUPS:
            if eid.startswith(prefix):
                g, gn, v = gid, gname, eid[len(prefix):].replace("_", " ").title()
        if g is None:
            continue
    # colour names give the exact colour (the picture average would pick up a visor or trim); the others use the picture
    hexes = {"white": "#f2f2f2", "black": "#2b2b30", "red": "#d62f2f", "blue": "#2f5fd6", "green": "#3fae4a", "pink": "#e64ab8", "purple": "#7d3fc4",
             "yellow": "#f2d13a", "orange": "#e8801f", "gray": "#8a8f99", "brown": "#8b5a2b", "dark": "#3b3552", "i": "#d9c7a3", "ii": "#6c7a99",
             "iii": "#b5253f"}
    e["group"], e["groupName"], e["variant"], e["swatch"] = g, gn, v, hexes.get(v.lower(), swatch(eid))
    n += 1
json.dump(idx, open(os.path.join(A, "cosmetics", "index.json"), "w"), indent=1, ensure_ascii=False)
print("grouped", n, "variants")
