"""Better-looking emote props (replaces the crude ones): each prop is drawn as a small pixel sprite (x right, y up; the long
direction goes bottom-left -> top-right like a sword item) and extruded 2 px thick, using the colour swatches of
textures/item/emote_props.png (a 16x16 grid of 4x4 swatches, uv = column,row).

Run:  python3 tools/gen_props.py
"""
import json
import math
import os

A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sol_client", "models", "item")
C = dict(wood=(0, 0), lwood=(1, 0), dwood=(2, 0), orange=(3, 0), gold=(4, 0), dgold=(5, 0), wood2=(6, 0), steel=(7, 0), lsteel=(8, 0), psteel=(9, 0),
         white=(10, 0), charcoal=(11, 0), dgrey=(12, 0), black=(13, 0), red=(14, 0), dred=(15, 0), blue=(0, 1), dblue=(1, 1), white2=(2, 1),
         yellow=(3, 1), purple=(4, 1), dpurple=(5, 1), cream=(6, 1), dcream=(7, 1), orange2=(8, 1), mgrey=(9, 1), grey=(10, 1), lgrey=(11, 1),
         paper=(12, 1), paper2=(13, 1), ice=(14, 1), lice=(15, 1), brown2=(0, 2), green=(1, 2), skin=(2, 2), yellow2=(3, 2))


class Sprite:
    def __init__(self):
        self.px = {}

    def dot(self, x, y, col):
        self.px[(int(x), int(y))] = col

    def rect(self, x0, y0, x1, y1, col):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.dot(x, y, col)

    def line(self, x0, y0, x1, y1, w, col):
        """Thick segment: every pixel whose centre is within w/2 of it."""
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1
        r = w / 2 + 0.01
        for x in range(int(min(x0, x1) - w) - 1, int(max(x0, x1) + w) + 2):
            for y in range(int(min(y0, y1) - w) - 1, int(max(y0, y1) + w) + 2):
                t = max(0, min(1, ((x + .5 - x0) * dx + (y + .5 - y0) * dy) / L2))
                px, py = x0 + t * dx, y0 + t * dy
                if math.hypot(x + .5 - px, y + .5 - py) <= r:
                    self.dot(x, y, col)

    def disc(self, cx, cy, r, col):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for y in range(int(cy - r) - 1, int(cy + r) + 2):
                if math.hypot(x + .5 - cx, y + .5 - cy) <= r:
                    self.dot(x, y, col)

    def model(self, base, z0=7.0, z1=9.0, thick=None):
        els = []
        rows = {}
        for (x, y), col in self.px.items():
            rows.setdefault((y, col), []).append(x)
        for (y, col), xs in rows.items():
            xs.sort()
            run = [xs[0]]
            for x in xs[1:] + [None]:
                if x is not None and x == run[-1] + 1:
                    run.append(x)
                    continue
                u, v = C[col] if isinstance(col, str) else col
                z0_, z1_ = (thick.get(col, (z0, z1)) if thick else (z0, z1))
                els.append({"from": [run[0], y, z0_], "to": [run[-1] + 1, y + 1, z1_],
                            "faces": {k: {"uv": [u, v, u + 1, v + 1], "texture": "#p"} for k in ("north", "south", "east", "west", "up", "down")}})
                if x is not None:
                    run = [x]
        return {"textures": base["textures"], "elements": els, "display": base["display"]}


def spear():
    s = Sprite()
    s.line(-3, -3, 19, 19, 2.0, "wood")                     # long shaft
    s.line(-3, -3, 19, 19, 0.9, "lwood")
    s.line(4, 4, 8, 8, 2.4, "dwood")                          # leather grip
    s.line(-3, -3, -1, -1, 2.6, "dgold")                      # butt cap
    s.line(18, 18, 20, 20, 3.0, "gold")                       # socket
    s.line(16, 20, 18, 22, 1.4, "red")                        # little red tassel
    # big leaf shaped blade, widest about a third of the way up
    for t, w in zip(range(20, 32), (2.2, 3.6, 5.0, 6.2, 6.6, 6.4, 5.8, 5.0, 4.0, 3.0, 2.0, 1.0)):
        s.line(t, t, t + 1, t + 1, w, "lsteel")
    s.line(20, 20, 31, 31, 1.4, "psteel")                     # bright centre ridge
    return s


def pistol():
    s = Sprite()
    s.line(3, 3, 14, 14, 3.0, "steel")                         # slide
    s.line(3, 3, 14, 14, 1.2, "lsteel")                       # shine on top
    s.line(14, 14, 16, 16, 1.6, "dgrey")                      # muzzle
    s.line(5, 2, 8, -2, 3.0, "dwood")                         # grip
    s.line(5, 2, 8, -2, 1.2, "wood")
    s.line(7, 3, 8, 1, 1.0, "black")                          # trigger guard
    s.dot(13, 12, "black")                                    # front sight
    s.dot(4, 5, "black")                                      # rear sight
    return s


def rifle():
    s = Sprite()
    s.line(-6, -6, 3, 3, 4.0, "dwood")                        # stock
    s.line(-6, -6, 3, 3, 1.6, "wood")
    s.line(3, 3, 17, 17, 3.0, "charcoal")                     # receiver + handguard
    s.line(3, 3, 17, 17, 1.0, "mgrey")
    s.line(17, 17, 27, 27, 1.4, "black")                      # barrel
    s.line(27, 27, 29, 29, 2.0, "dgrey")                      # muzzle brake
    s.line(9, 4, 12, -2, 2.0, "dgrey")                        # magazine
    s.line(5, 2, 6, -2, 1.6, "dwood")                         # grip
    s.line(8, 9, 12, 13, 1.0, "lgrey")                        # top rail / sight
    s.dot(24, 26, "black")
    return s


def recorder():
    s = Sprite()
    s.rect(0, 0, 9, 6, "dgrey")                               # body
    s.rect(1, 4, 8, 5, "mgrey")                               # top plate
    s.disc(2.8, 2.4, 1.5, "lgrey")                            # reels
    s.disc(6.2, 2.4, 1.5, "lgrey")
    s.dot(2, 2, "black")
    s.dot(6, 2, "black")
    s.rect(1, 6, 2, 6, "red")                                 # record button
    s.rect(4, 6, 8, 6, "steel")
    s.line(9, 3, 12, 6, 1.0, "black")                         # handle strap
    return s


def brush():
    s = Sprite()
    s.rect(0, 0, 9, 3, "wood")                                # wooden back
    s.rect(0, 3, 9, 3, "lwood")
    s.rect(1, 1, 8, 1, "dwood")
    s.rect(0, -3, 9, -1, "yellow")                            # bristles
    for x in range(0, 10, 2):
        s.dot(x, -3, "gold")
    s.line(9, 3, 13, 7, 1.6, "wood")                          # little handle
    return s


def mug():
    s = Sprite()
    s.rect(0, 0, 6, 7, "white2")                              # body
    s.rect(0, 3, 6, 4, "blue")                                # stripe
    s.rect(1, 7, 5, 7, "dcream")                              # coffee
    s.rect(0, 0, 6, 0, "lgrey")
    s.disc(8.2, 4.0, 2.0, "white2")                           # handle
    s.disc(8.2, 4.0, 0.9, "ice")
    for y in (1, 6):
        s.dot(6, y, "white2")
    return s


def mirror():
    s = Sprite()
    s.line(0, 0, 6, 6, 1.8, "gold")                           # handle
    s.disc(11, 11, 6.2, "gold")                               # frame
    s.disc(11, 11, 5.0, "lice")                               # glass
    s.line(8, 14, 12, 15, 1.0, "white")                       # shine
    s.line(7, 12, 9, 15, 0.8, "white")
    return s


def idcard():
    s = Sprite()
    s.rect(0, 0, 9, 6, "white2")
    s.rect(0, 5, 9, 6, "blue")                                # header
    s.rect(1, 1, 3, 4, "skin")                                # photo
    s.rect(1, 1, 3, 1, "dwood")
    s.rect(5, 3, 8, 3, "mgrey")
    s.rect(5, 1, 7, 1, "mgrey")
    return s


NEW = dict(spear=spear, pistol=pistol, rifle=rifle, recorder=recorder, brush=brush, cup=mug, mirror=mirror, idcard=idcard)

if __name__ == "__main__":
    for name, fn in NEW.items():
        path = os.path.join(A, "prop_%s.json" % name)
        base = json.load(open(path))
        thick = {"white2": (7.5, 8.5)} if name == "idcard" else None
        json.dump(fn().model(base, thick=thick), open(path, "w"), separators=(",", ":"))
        print("ok", name)
