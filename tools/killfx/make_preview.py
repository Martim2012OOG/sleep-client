#!/usr/bin/env python3
"""Writes a self-contained HTML preview of the kill effects from the files the client loads.
Usage: python3 tools/killfx/make_preview.py out.html [id ...]"""
import base64, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "sleep_client")


def main(out, ids):
    data = {}
    for i in ids:
        j = json.load(open(os.path.join(RES, "killfx", i + ".json")))
        png = base64.b64encode(open(os.path.join(RES, "textures", "killfx", i + ".png"), "rb").read()).decode()
        data[i] = {"json": j, "png": png}
    skin = base64.b64encode(open(os.path.join(HERE, "default_skin.png"), "rb").read()).decode()
    html = open(os.path.join(HERE, "preview_template.html")).read().replace("/*FXDATA*/", json.dumps(data, separators=(",", ":"))).replace("/*SKIN*/", skin)
    open(out, "w").write(html)
    print("wrote", out, len(html) // 1024, "KB")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:] or ["pacman", "shark", "mimic", "frog", "flytrap", "ufo", "blackhole", "rocket", "whale", "dragon", "worm", "snake", "croc", "trex", "toilet", "hand", "kraken", "pig", "ghost", "eagle", "piano", "tornado", "gift", "cannon"])
