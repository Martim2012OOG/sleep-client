#!/usr/bin/env python3
"""Importa emotes Emotecraft (pares X.json + X.png) para o mod.
Uso: python3 tools/import_emotes.py <pasta_com_emotes> [prefixo_a_remover, ex: SPE_]
Depois: ./gradlew build
"""
import glob, json, os, re, shutil, sys

FIX = {"Machiine": "Machine", "Cleanfloor": "Clean Floor"}
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = os.path.join(root, "src/main/resources/assets/sol_client")
src = sys.argv[1]
prefix = sys.argv[2] if len(sys.argv) > 2 else "SPE_"
os.makedirs(res + "/emotes", exist_ok=True)
os.makedirs(res + "/textures/gui/emote", exist_ok=True)
idx_path = res + "/emotes/index.json"
index = {e["id"]: e for e in json.load(open(idx_path))} if os.path.exists(idx_path) else {}

def rnd(o):
    if isinstance(o, float): return round(o, 4)
    if isinstance(o, list): return [rnd(x) for x in o]
    if isinstance(o, dict): return {k: rnd(v) for k, v in o.items()}
    return o

def pretty(stem):
    s = stem[len(prefix):] if stem.startswith(prefix) else stem
    for a, b in FIX.items(): s = s.replace(a, b)
    s = re.sub(r"(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Za-z])", " ", s)
    return s.replace("_", " ").strip()

for jf in sorted(glob.glob(os.path.join(src, "*.json"))):
    stem = os.path.splitext(os.path.basename(jf))[0]
    png = os.path.join(src, stem + ".png")
    eid = re.sub(r"[^a-z0-9_]", "", (stem[len(prefix):] if stem.startswith(prefix) else stem).lower())
    d = json.load(open(jf, encoding="utf-8"))
    e = d.get("emote")
    if not e or "moves" not in e:
        print("ignorado:", jf); continue
    with open(f"{res}/emotes/{eid}.json", "w") as f:
        json.dump(rnd({"emote": e}), f, separators=(",", ":"))
    has_icon = os.path.exists(png)
    if has_icon: shutil.copyfile(png, f"{res}/textures/gui/emote/{eid}.png")
    loop = str(e.get("isLoop", "false")).lower() == "true"
    index[eid] = {"id": eid, "name": pretty(stem), "icon": has_icon, "author": d.get("author") or "", "loop": loop}

out = sorted(index.values(), key=lambda x: x["name"].lower())
with open(idx_path, "w") as f: json.dump(out, f, indent=1)
print(len(out), "emotes no indice")
