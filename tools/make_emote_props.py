#!/usr/bin/env python3
"""Gera os adereços 3D dos emotes (vassoura, guarda-chuva, guitarra, armas...) como modelos de item do Kubo.

Uso:  python3 tools/make_emote_props.py      (depois: ./gradlew build)

Cada adereço é desenhado "em pé": o eixo y vai do punho (0,0) para a ponta, o eixo x vai para o lado dos dedos
(a direção do antebraço). O script roda isso 45 graus (como os sprites de ferramentas do Minecraft) e transforma-o
em cubinhos com a profundidade pedida. Sai:
  assets/sleep_client/textures/item/emote_props.png     (paleta)
  assets/sleep_client/models/item/prop_<nome>.json
  assets/sleep_client/items/prop_<nome>.json
No jogo, o emote usa um item de pau com o componente item_model = sleep_client:prop_<nome>.
"""
import json, math, os

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = os.path.join(root, "src/main/resources/assets/sleep_client")

COL = {
    "wood": "8b5a2b", "wood2": "a8743a", "darkwood": "4a2e17", "guitar": "c9772e", "straw": "e3c04a", "straw2": "c9a23a",
    "rope": "6b4a2a", "metal": "9aa0a6", "metal2": "c5cad0", "steel": "d7dde3", "steel2": "f4f7fa", "dark": "2b2b30",
    "dark2": "3d3d44", "black": "17171a", "red": "d6322f", "red2": "a42220", "blue": "2f5fd6", "blue2": "2247a4",
    "white": "f2f2f2", "gold": "e8b730", "purple": "7d3fc4", "purple2": "5f2e98", "cloth": "e8e4d6", "cloth2": "c9c4b0",
    "orange": "e8801f", "pan": "3a3a40", "pan2": "55555d", "strings": "dddddd", "paper": "efe9d2", "paper2": "d5ceb2",
    "glass": "bfe3f2", "mirror": "d4f1ff", "brown": "6f4326", "green": "3f9b4a", "skin": "e8b894", "ribbon": "ffd84a",
}
NAMES = list(COL)
IDX = {n: i for i, n in enumerate(NAMES)}

# prop -> list of (x0, y0, x1, y1, colour or None, depth)   y = along the prop, x = towards the fingers
P = {}
P["broom"] = [(-0.7, -5, 0.7, 17, "wood", 2), (-1.7, 16, 1.7, 18, "rope", 3), (-3.2, 18, 3.2, 27, "straw", 4),
              (-2.4, 27, 2.4, 28.5, "straw2", 4), (-3.2, 18, 3.2, 20, "straw2", 4)]
P["umbrella"] = [(-0.5, -6, 0.5, 20, "dark", 2), (-2.6, -7.2, 0.5, -5.6, "dark", 2), (-2.6, -7.2, -1.4, -4, "dark", 2),
                 (-7, 15, 7, 16.6, "purple2", 4), (-6, 16.6, 6, 18.6, "purple", 5), (-4.6, 18.6, 4.6, 20.6, "purple", 5),
                 (-2.6, 20.6, 2.6, 22.2, "purple2", 5), (-0.6, 22.2, 0.6, 24.5, "dark", 2)]
P["mop"] = [(-0.6, -4, 0.6, 19, "wood", 2), (-2.1, 18, 2.1, 20, "metal", 3), (-3.4, 20, 3.4, 27, "cloth", 5),
            (-3.4, 20, 3.4, 22, "cloth2", 5), (-3.4, 25, 3.4, 27, "cloth2", 5)]
P["cane"] = [(-0.5, -6, 0.5, 14, "darkwood", 2), (-0.5, -6, 0.5, -4.8, "metal", 2), (-0.5, 13, 0.5, 15.4, "darkwood", 2),
             (-3, 14.2, 0.5, 16, "darkwood", 2), (-3, 12.4, -1.8, 16, "darkwood", 2), (-1.1, 14.6, 0.2, 15.6, "gold", 2.4)]
P["crutch"] = [(-0.6, -8, 0.6, 17, "metal", 2), (-3.2, 15.4, 3.2, 17.4, "metal2", 3), (-3.2, 17.4, 3.2, 18.6, "black", 4),
               (-2.4, 4.5, 2.4, 6.3, "black", 4), (-0.6, -9, 0.6, -8, "black", 2)]
P["guitar"] = [(-0.95, -2, 0.95, 12, "darkwood", 2), (-1.7, 12, 1.7, 15.6, "darkwood", 3), (-2.4, 13, 2.4, 13.8, "metal", 3.6),
               (-5.2, -14.5, 5.2, -8, "guitar", 5), (-3.7, -8, 3.7, -4.6, "guitar", 5), (-4.5, -4.6, 4.5, -1.4, "guitar", 5),
               (-1.7, -10, 1.7, -6.6, "black", 5.4), (-2, -13.2, 2, -12.4, "darkwood", 6), (-0.15, -13, 0.15, 13, "strings", 3)]
P["glove_red"] = [(-3.4, -3.4, 5.4, 3.4, "red", 6), (-2.4, 3.4, 4, 4.6, "red", 5), (-3.4, 3, 0.5, 5.6, "red2", 4),
                  (-7, -2.6, -3.4, 2.6, "white", 5)]
P["glove_blue"] = [(-3.4, -3.4, 5.4, 3.4, "blue", 6), (-2.4, 3.4, 4, 4.6, "blue", 5), (-3.4, 3, 0.5, 5.6, "blue2", 4),
                   (-7, -2.6, -3.4, 2.6, "white", 5)]
P["brush"] = [(-2.8, -1.5, 2.8, 3.2, "wood", 4.6), (-2.6, 3.2, 2.6, 5.4, "straw2", 4.2), (-1.4, 0, 1.4, 1.4, "wood2", 5)]
P["pan"] = [(1, -0.8, 6, 0.8, "black", 2), (-6, -0.8, 1, 0.8, "black", 2),
            (5, -1.5, 16.5, 1.5, "metal", 13), (6, -1.2, 15.5, 1.2, "pan", 11.4), (7, -1.0, 14.5, 1.0, "pan2", 10.4)]
P["spatula"] = [(-0.6, -5, 0.6, 8, "wood", 2), (-2.8, 8, 2.8, 15.5, "metal", 1.6), (-1.6, 10, -0.6, 14, None, 1.6),
                (0.6, 10, 1.6, 14, None, 1.6)]
P["wrench"] = [(-1.1, -6, 1.1, 10, "metal", 3), (-3.4, 10, 3.4, 15.4, "metal", 3.2), (-1.3, 12.2, 1.4, 15.4, None, 3),
               (1.3, 10.4, 3.4, 13.4, "metal2", 3.4), (-1.1, -6, 1.1, -2, "red", 3.4)]
P["fork"] = [(-0.6, -4, 0.6, 8, "metal", 1.6), (-1.8, 8, 1.8, 9.6, "metal", 1.6), (-2.2, 9.6, -1.3, 14.4, "metal2", 1.6),
             (-0.45, 9.6, 0.45, 14.4, "metal2", 1.6), (1.3, 9.6, 2.2, 14.4, "metal2", 1.6)]
P["spoon"] = [(-0.6, -4, 0.6, 9, "metal", 1.6), (-2.4, 9, 2.4, 14.4, "metal2", 2.4), (-1.5, 10, 1.5, 13.4, "metal", 1.8)]
P["katana"] = [(-0.75, -4, 0.75, 4, "black", 2.2), (-0.75, -2.5, 0.75, -1.5, "red2", 2.4), (-2, 4, 2, 5.2, "gold", 3),
               (-0.6, 5.2, 0.6, 27, "steel", 1.6), (0, 5.2, 0.6, 27, "steel2", 1.6), (-0.3, 27, 0.5, 28.6, "steel2", 1.4)]
P["knife"] = [(-1, -3.5, 1, 4, "black", 2.6), (-1, -3.5, 1, -2.5, "metal", 2.8), (-2, 4, 2, 5.2, "metal", 2.8),
              (-1.5, 5.2, 1.5, 14, "steel", 1.4), (-0.8, 14, 0.8, 16.4, "steel2", 1.4)]
P["greatsword"] = [(-0.9, -4.5, 0.9, 4.5, "darkwood", 2.6), (-1.6, -6, 1.6, -4.5, "gold", 3), (-4, 4.5, 4, 6.3, "dark2", 3.6),
                   (-2.4, 6.3, 2.4, 29, "steel", 2), (-0.5, 6.8, 0.5, 28, "steel2", 2.4), (-1.2, 29, 1.2, 31.5, "steel", 2)]
P["chainsaw"] = [(-4, -3.4, 5, 3.4, "orange", 6.4), (-1.2, -6, 1.2, -3.4, "black", 3.4), (-2.6, 3.4, 2.6, 5, "black", 4.4),
                 (5, -1.7, 20, 1.7, "steel", 2.4), (5, -2.1, 20, -1.6, "dark2", 3), (5, 1.6, 20, 2.1, "dark2", 3),
                 (5, -2.1, 6.4, 2.1, "dark", 3.6)]
P["scythe"] = [(-0.6, -6, 0.6, 24, "wood", 2), (-0.6, 22.6, 6.2, 24.6, "steel", 1.4), (5, 20.6, 8.6, 23.6, "steel", 1.4),
               (7, 17.4, 9.4, 21.4, "steel", 1.4), (-0.6, 24.6, 1.2, 25.4, "steel2", 1.4), (6.4, 15.6, 9.6, 18.4, "steel2", 1.2)]
P["spear"] = [(-0.6, -6, 0.6, 26, "wood", 2), (-1.9, 26, 1.9, 28, "steel", 2), (-1.3, 28, 1.3, 31, "steel2", 2),
              (-0.5, 31, 0.5, 33, "steel2", 2), (-1, -1, 1, 0.4, "rope", 2.6)]
P["pistol"] = [(-1.3, -3.2, 1.3, 2.6, "dark2", 2.8), (-1.3, 2.6, 10.5, 5, "dark", 2.8), (10.5, 3, 12.6, 4.8, "metal", 1.8),
               (1.4, -0.5, 3.4, 2.6, None, 2), (1.2, -0.7, 3.4, -0.1, "dark", 1.2), (-0.8, 5, 0.6, 5.8, "metal", 1)]
P["rifle"] = [(-1.3, -3.2, 1.3, 2.6, "dark2", 2.8), (-9, 0.4, 4, 4.4, "wood", 2.6), (-1.5, 2.6, 17, 5.2, "dark", 3),
              (17, 3, 25, 4.6, "metal", 1.8), (6, -4.6, 8.6, 2.6, "dark2", 2.4), (-1.5, 5.2, 14, 6, "metal", 1.2)]
P["recorder"] = [(-3.4, -3.4, 4.6, 3.6, "dark2", 6.4), (-2.4, 0.4, 3.6, 2.6, "metal2", 6.8), (-2.2, -2.6, -0.6, -1, "white", 6.8),
                 (1.6, -2.6, 3.2, -1, "white", 6.8), (-2.4, 3.6, 2, 4.4, "metal", 1.4)]
P["newspaper"] = [(-0.5, -6, 0.5, 11, "paper", 15), (-0.5, -4.4, 0.5, -3.6, "dark", 12), (-0.5, -2.4, 0.5, -1.6, "dark", 12),
                  (-0.5, 4.8, 0.5, 5.6, "dark", 12), (-0.5, 7.2, 0.5, 8, "dark", 10), (-0.5, 9, 0.5, 11, "paper2", 15)]
P["sign"] = [(-0.6, -8, 0.6, 14, "wood", 2), (-0.5, 12, 0.5, 26, "paper", 18), (-0.5, 12, 0.5, 13, "wood2", 18.6),
             (-0.5, 25, 0.5, 26, "wood2", 18.6), (-0.8, 15, 0.8, 17, "red", 11), (-0.8, 19.5, 0.8, 23, "red", 13)]
P["idcard"] = [(-0.4, -3.5, 0.4, 3.5, "white", 9), (-0.55, -2.6, 0.55, 1.2, "blue", 3.6), (-0.55, 2, 0.55, 2.6, "metal", 7)]
P["mirror"] = [(-0.6, -5, 0.6, 7, "gold", 2), (-0.5, 7, 0.5, 16, "gold", 10), (-0.7, 8, 0.7, 15, "mirror", 8.6)]
P["cup"] = [(-2.4, -3.4, 2.4, 3.6, "glass", 4.8), (-1.8, -2.6, 1.8, 3.6, "mirror", 3.6), (-1.8, -2.6, 1.8, 0.2, "blue", 3.4)]
P["scissors"] = [(-3.2, -5, -1.2, 0.4, "red", 1.6), (1.2, -5, 3.2, 0.4, "red", 1.6), (-1.2, 0.4, 0.2, 11, "metal2", 1.2),
                 (-0.2, 0.4, 1.2, 11, "metal", 1.4), (-1.2, -1.2, 1.2, 0.6, "metal", 2)]
P["iron"] = [(-1.5, -0.6, 10.5, 1.4, "metal2", 7), (-0.5, 1.4, 8.5, 3.6, "blue", 6), (1.5, 3.6, 6.5, 6.2, "black", 3.2),
             (-0.5, 1.4, 8.5, 2, "metal", 6.4)]
P["watch"] = [(-0.12, -3, 0.12, 10, "gold", 1), (-2.4, 10, 2.4, 14.8, "gold", 3.4), (-1.8, 10.6, 1.8, 14.2, "white", 3.9),
              (-0.15, 11.2, 0.15, 13.6, "black", 4.2), (-0.15, 12.4, 1.4, 12.8, "black", 4.2)]
P["briefcase"] = [(-0.5, -2, 0.5, 2, "dark", 2), (-0.6, 2, 0.6, 3.2, "dark", 5), (2, -7, 11, 7, "brown", 3.4),
                  (2, -7, 3, 7, "darkwood", 3.8), (10, -7, 11, 7, "darkwood", 3.8), (6, -1, 7.4, 1, "gold", 4)]
P["gift"] = [(-1.5, -4.6, 8.5, 4.6, "red", 9.4), (-1.5, -0.8, 8.5, 0.8, "ribbon", 9.8), (3, -4.6, 4.6, 4.6, "ribbon", 9.8),
             (8.5, -2.4, 10.4, 2.4, "ribbon", 5), (8.5, -0.6, 11.4, 0.6, "ribbon", 2)]

P["phone"] = [(-1.0, -3.6, 6.4, 3.0, "black", 1.6), (-0.4, -3.0, 5.8, 2.4, "mirror", 1.9), (5.2, -2.6, 6.0, -1.8, "dark", 2.2)]
P["magnifier"] = [(-0.7, -4, 0.7, 4.2, "wood", 2), (-4.0, 4.2, 4.0, 12.2, "gold", 2.6), (-3.2, 5.0, 3.2, 11.4, "glass", 1.8)]
P["dumbbell"] = [(-0.8, -4.6, 0.8, 4.6, "metal2", 2.4), (-3.0, -7.2, 3.0, -4.6, "dark", 5.2), (-3.0, 4.6, 3.0, 7.2, "dark", 5.2),
                 (-2.2, -9.0, 2.2, -7.2, "dark2", 4.0), (-2.2, 7.2, 2.2, 9.0, "dark2", 4.0)]
P["ball"] = [(-4.2, -4.2, 4.2, 4.2, "white", 8.4), (-1.6, -1.6, 1.6, 1.6, "black", 8.8), (-4.2, 1.4, -2.6, 3.4, "black", 6.8),
             (2.6, -3.4, 4.2, -1.4, "black", 6.8)]

SPR = 2.5  # where the grip sits in the model (the vanilla tools use the same corner)
A = (math.sqrt(0.5), math.sqrt(0.5))
B = (-math.sqrt(0.5), math.sqrt(0.5))


def cells(rects):
    grid = {}
    for x0, y0, x1, y1, c, d in rects:
        for i in range(-20, 36):
            for j in range(-20, 36):
                dx, dy = i + 0.5 - SPR, j + 0.5 - SPR
                y = dx * A[0] + dy * A[1]
                x = dx * B[0] + dy * B[1]
                if x0 <= x <= x1 and y0 <= y <= y1:
                    if c is None:
                        grid.pop((i, j), None)
                    else:
                        grid[(i, j)] = (c, d)
    return grid


def face(k):
    u, v = k % 16, k // 16
    return {"uv": [u, v, u + 1, v + 1], "texture": "#p"}


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
}


def build(name, rects):
    g = cells(rects)
    rows = {}
    for (i, j), v in g.items():
        rows.setdefault((j, v), []).append(i)
    els = []
    for (j, (c, d)), xs in rows.items():
        xs.sort()
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            z0, z1 = 8 - d / 2, 8 + d / 2
            f = face(IDX[c])
            els.append({"from": [start, j, z0], "to": [prev + 1, j + 1, z1],
                        "faces": {k: dict(f) for k in ("north", "south", "east", "west", "up", "down")}})
            if x is not None:
                start = prev = x
    model = {"textures": {"p": "sleep_client:item/emote_props", "particle": "sleep_client:item/emote_props"},
             "elements": els, "display": DISPLAY}
    with open(f"{res}/models/item/prop_{name}.json", "w") as f:
        json.dump(model, f, separators=(",", ":"))
    with open(f"{res}/items/prop_{name}.json", "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"sleep_client:item/prop_{name}"}}, f)
    return len(els)


def palette():
    from PIL import Image
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for n, k in IDX.items():
        r, g, b = (int(COL[n][i:i + 2], 16) for i in (0, 2, 4))
        for y in range(4):
            for x in range(4):
                img.putpixel((k % 16 * 4 + x, k // 16 * 4 + y), (r, g, b, 255))
    os.makedirs(f"{res}/textures/item", exist_ok=True)
    img.save(f"{res}/textures/item/emote_props.png")


if __name__ == "__main__":
    os.makedirs(f"{res}/models/item", exist_ok=True)
    os.makedirs(f"{res}/items", exist_ok=True)
    palette()
    tot = 0
    for n, r in P.items():
        k = build(n, r)
        tot += k
        print(f"{n:12} {k:4} cubos")
    print(len(P), "adereços,", tot, "cubos")
