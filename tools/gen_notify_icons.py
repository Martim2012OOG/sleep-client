"""Draws the white glyphs of the Kubo notifications (textures/gui/notify/*.png, 64x64, original art).

Run:  python3 tools/gen_notify_icons.py
No dependencies: shapes are filled with 4x4 supersampling and written as plain RGBA PNGs.
"""
import math
import os
import struct
import zlib

S = 64
SS = 4
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "sleep_client", "textures", "gui", "notify")


# ------------------------------------------------------------------ shapes: (x, y) -> inside?
def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ring(cx, cy, r, t, only=None):
    def f(x, y):
        d = math.hypot(x - cx, y - cy)
        return r - t <= d <= r and (only is None or only(x, y))
    return f


def rrect(x0, y0, x1, y1, r):
    def f(x, y):
        if x < x0 or x > x1 or y < y0 or y > y1:
            return False
        dx = max(x0 + r - x, 0, x - (x1 - r))
        dy = max(y0 + r - y, 0, y - (y1 - r))
        return dx * dx + dy * dy <= r * r
    return f


def poly(*pts):
    def f(x, y):
        inside = False
        n = len(pts)
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                inside = not inside
        return inside
    return f


def line(ax, ay, bx, by, w):
    """A round-capped stroke from a to b, w wide."""
    def f(x, y):
        vx, vy = bx - ax, by - ay
        t = max(0, min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)))
        return math.hypot(x - ax - vx * t, y - ay - vy * t) <= w / 2
    return f


def path(pts, w):
    segs = [line(*pts[i], *pts[i + 1], w) for i in range(len(pts) - 1)]
    return lambda x, y: any(s(x, y) for s in segs)


# ------------------------------------------------------------------ icons: list of (op, shape, alpha)
def person(cx, cy, k=1.0):
    return [("add", circle(cx, cy - 11 * k, 9 * k), 1),
            ("add", rrect(cx - 17 * k, cy + 2 * k, cx + 17 * k, cy + 22 * k, 10 * k), 1)]


ICONS = {
    # speech bubble with three dots
    "msg": [("add", rrect(8, 12, 56, 44, 10), 1), ("add", poly((16, 40), (16, 55), (31, 42)), 1),
            ("sub", circle(21, 28, 3.6), 0), ("sub", circle(32, 28, 3.6), 0), ("sub", circle(43, 28, 3.6), 0)],
    # paper plane with its fold
    "sent": [("add", poly((6, 30), (58, 8), (40, 56), (31, 38)), 1), ("sub", line(58, 8, 30, 37, 3.4), 0)],
    # game pad (invites / "is playing")
    "play": [("add", rrect(6, 19, 58, 47, 14), 1),
             ("sub", rrect(14.5, 31, 27.5, 35, 1.5), 0), ("sub", rrect(19, 26.5, 23, 39.5, 1.5), 0),
             ("sub", circle(41, 29.5, 3.4), 0), ("sub", circle(48, 36.5, 3.4), 0)],
    # person with a green-dot badge (rendered white)
    "online": person(28, 30) + [("sub", circle(47, 47, 11), 0), ("add", circle(47, 47, 7), 1)],
    # person with a plus
    "friend": person(26, 30) + [("sub", circle(48, 22, 12), 0),
                                ("add", rrect(46, 12, 50, 32, 2), 1), ("add", rrect(38, 20, 58, 24, 2), 1)],
    # record: ring + dot
    "rec": [("add", ring(32, 32, 25, 5.5), 1), ("add", circle(32, 32, 13), 1)],
    # video clip: screen with a play triangle
    "clip": [("add", rrect(7, 13, 57, 51, 9), 1), ("sub", poly((26, 22), (26, 42), (43, 32)), 0)],
    # warning triangle with "!"
    "error": [("add", poly((32, 6), (60, 55), (4, 55)), 1),
              ("sub", rrect(29, 22, 35, 40, 3), 0), ("sub", circle(32, 47, 3.6), 0)],
    # round "i"
    "info": [("add", circle(32, 32, 26), 1), ("sub", rrect(28.5, 28, 35.5, 47, 3), 0), ("sub", circle(32, 19.5, 4.2), 0)],
    # microphone
    "voice": [("add", rrect(23, 6, 41, 39, 9), 1),
              ("add", ring(32, 29, 17.5, 4.5, only=lambda x, y: y >= 29), 1),
              ("add", rrect(30, 45, 34, 54, 1), 1), ("add", rrect(21, 52, 43, 57, 2.5), 1)],
    # arrow into a tray
    "download": [("add", rrect(28, 6, 36, 31, 2), 1), ("add", poly((14, 28), (50, 28), (32, 46)), 1),
                 ("add", rrect(8, 50, 56, 57, 3.5), 1)],
    # bold check mark in a circle
    "check": [("add", circle(32, 32, 26), 1), ("sub", path([(19, 33), (28, 42), (45, 23)], 7), 0)],
    # the Kubo cube: three faces, shaded by alpha
    "kubo": [("add", poly((32, 6), (55, 19), (32, 32), (9, 19)), 1),
             ("add", poly((9, 21.5), (30.6, 34), (30.6, 58), (9, 45.5)), 0.72),
             ("add", poly((33.4, 34), (55, 21.5), (55, 45.5), (33.4, 58)), 0.45)],
}


def render(ops):
    px = bytearray()
    for y in range(S):
        px.append(0)  # filter: none
        for x in range(S):
            acc = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    fx, fy = x + (sx + 0.5) / SS, y + (sy + 0.5) / SS
                    a = 0.0
                    for op, shape, alpha in ops:
                        if shape(fx, fy):
                            a = alpha if op == "add" else 0.0
                    acc += a
            px += bytes((255, 255, 255, round(255 * acc / (SS * SS))))
    return bytes(px)


def png(data):
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(data, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    for name, ops in ICONS.items():
        with open(os.path.join(OUT, name + ".png"), "wb") as f:
            f.write(png(render(ops)))
        print("wrote", name)
