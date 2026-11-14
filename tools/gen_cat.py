"""Draws the click-counter cat pixel by pixel (own art, no screenshots): base, four paw frames and the outfits, all on ONE shared grid
so everything lines up. Writes the PNG layers into src/main/resources/assets/sol_client/textures/gui/cat/ ."""
import os, sys
from PIL import Image

W, H = 76, 58
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/sol_client/textures/gui/cat")
LINE = (91, 68, 72, 255); WHITE = (255, 255, 255, 255); SHADE = (233, 228, 230, 255); PINK = (240, 160, 150, 255); BLUSH = (246, 201, 201, 255)
BLACK = (50, 38, 42, 255)

def layer(): return Image.new("RGBA", (W, H), (0, 0, 0, 0))
def put(im, x, y, c):
    if 0 <= x < W and 0 <= y < H: im.putpixel((x, y), c)
def rect(im, x, y, w, h, c):
    for j in range(h):
        for i in range(w): put(im, x + i, y + j, c)
def ellipse_pts(cx, cy, rx, ry):
    pts = set()
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if ((x - cx) / (rx + 0.35)) ** 2 + ((y - cy) / (ry + 0.35)) ** 2 <= 1: pts.add((x, y))
    return pts
def tri_pts(a, b, c):
    pts = set()
    def s(p, q, r): return (p[0] - r[0]) * (q[1] - r[1]) - (q[0] - r[0]) * (p[1] - r[1])
    for y in range(min(a[1], b[1], c[1]), max(a[1], b[1], c[1]) + 1):
        for x in range(min(a[0], b[0], c[0]), max(a[0], b[0], c[0]) + 1):
            d1, d2, d3 = s((x, y), a, b), s((x, y), b, c), s((x, y), c, a)
            if not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0)): pts.add((x, y))
    return pts
def paint(im, pts, fill, line=LINE, outline=True):
    if outline:
        edge = set()
        for (x, y) in pts:
            for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                if (x+dx, y+dy) not in pts: edge.add((x+dx, y+dy))
        for p in edge: put(im, p[0], p[1], line)
    for (x, y) in pts: put(im, x, y, fill)

# ---------------------------------------------------------------- the cat (head, ears, face) -- no paws
def base():
    im = layer()
    head = ellipse_pts(38, 37, 27, 12)
    earL = tri_pts((14, 28), (19, 13), (31, 25)); earR = tri_pts((45, 25), (57, 13), (62, 28))
    # the ears first, then the head over them: one outline round the whole shape
    allp = head | earL | earR
    paint(im, allp, WHITE)
    for (x, y) in head | earL | earR:   # a touch of shade under the head
        pass
    for y in range(46, 49):
        for x in range(14, 63):
            if (x, y) in head and im.getpixel((x, y)) == WHITE: put(im, x, y, SHADE)
    paint(im, tri_pts((17, 25), (20, 17), (26, 24)), PINK, outline=False)
    paint(im, tri_pts((50, 24), (56, 17), (59, 25)), PINK, outline=False)
    # ginger patch over the right ear and the top right of the head, a tiny difference of its own
    GING = (238, 176, 108, 255)
    for (x, y) in tri_pts((47, 24), (56, 15), (60, 26)) | ellipse_pts(52, 29, 8, 3):
        if (x, y) in allp and im.getpixel((x, y)) in (WHITE, PINK): put(im, x, y, GING)
    for (x, y) in tri_pts((50, 24), (56, 17), (59, 25)): put(im, x, y, (214, 126, 96, 255))
    # whiskers
    for (x, y) in ((11, 36), (12, 36), (13, 36), (11, 39), (12, 38), (13, 38), (63, 36), (64, 36), (65, 36), (63, 38), (64, 38), (65, 39)): put(im, x, y, LINE)
    # eyes (3x4 with a white glint), mouth (an omega), blush
    for ex in (25, 48):
        rect(im, ex, 33, 3, 4, BLACK); put(im, ex + 1, 34, WHITE)
    for (x, y) in ((34, 38), (35, 39), (36, 39), (37, 38), (38, 39), (39, 39), (40, 38)): put(im, x, y, BLACK)
    for (x, y, w) in ((17, 39, 5), (17, 40, 5), (55, 39, 5), (55, 40, 5)):
        rect(im, x, y, w, 1, BLUSH)
    return im

# ---------------------------------------------------------------- paws: up (raised) and down (on the counter)
def paw(cx, cy, down, left):
    im = layer()
    if not down:   # raised: a rounded paw with the pad and three toe beans
        pts = ellipse_pts(cx, cy, 6, 7)
        paint(im, pts, WHITE)
        for (x, y) in ellipse_pts(cx, cy + 2, 3, 3): put(im, x, y, PINK)
        for dx in (-3, 0, 3): put(im, cx + dx, cy - 3 + (1 if dx else 0), PINK); put(im, cx + dx, cy - 2 + (1 if dx else 0), PINK)
    else:          # down: flat on the counter, squashed, the beans seen from above
        pts = ellipse_pts(cx, cy, 7, 4)
        paint(im, pts, WHITE)
        for dx in (-3, 0, 3): put(im, cx + dx, cy - 1, PINK); put(im, cx + dx, cy, PINK)
        for (x, y) in ellipse_pts(cx, cy + 2, 2, 1): put(im, x, y, PINK)
    return im

# ---------------------------------------------------------------- outfits: each on the same grid, at the exact spots of the face
def hat_santa():
    im = layer()
    paint(im, tri_pts((22, 22), (49, 6), (56, 22)), (217, 58, 58, 255))
    # the tip falls to the side
    paint(im, ellipse_pts(54, 9, 5, 3), (217, 58, 58, 255))
    paint(im, ellipse_pts(59, 11, 3, 3), (168, 39, 39, 255), outline=False)
    rect(im, 20, 21, 38, 4, WHITE)  # the band, covering the top of the head
    for x in range(20, 58): put(im, x, 20, LINE); put(im, x, 25, LINE)
    for y in range(21, 25): put(im, 19, y, LINE); put(im, 58, y, LINE)
    paint(im, ellipse_pts(62, 12, 3, 3), WHITE)
    return im
def hat_elf():
    im = layer()
    paint(im, tri_pts((24, 22), (46, 5), (54, 22)), (47, 158, 85, 255))
    paint(im, ellipse_pts(52, 8, 4, 2), (47, 158, 85, 255), outline=False)
    rect(im, 22, 21, 34, 4, (217, 58, 58, 255))
    for x in range(22, 56): put(im, x, 20, LINE); put(im, x, 25, LINE)
    for y in range(21, 25): put(im, 21, y, LINE); put(im, 56, y, LINE)
    paint(im, ellipse_pts(56, 7, 2, 2), (242, 194, 58, 255))
    return im
def hat_party(accent):
    im = layer()
    paint(im, tri_pts((30, 24), (40, 4), (50, 24)), accent)
    for i, y in enumerate(range(10, 24, 4)):
        for x in range(34 + (0 if i else 0), 47):
            if im.getpixel((x, y)) == accent: put(im, x, y, WHITE)
    paint(im, ellipse_pts(40, 4, 2, 2), (242, 194, 58, 255))
    return im
def crown(accent):
    im = layer()
    gold = (242, 194, 58, 255); dg = (199, 154, 31, 255)
    pts = set()
    for x in range(26, 51):
        for y in range(18, 25): pts.add((x, y))
    for sx in (26, 36, 46):
        for x in range(sx, sx + 5):
            for y in range(12, 18): pts.add((x, y))
    paint(im, pts, gold)
    rect(im, 27, 22, 23, 2, dg)
    rect(im, 37, 19, 3, 3, accent)
    return im
def headphones(accent):
    im = layer(); dk = BLACK
    for x in range(17, 60):   # the band over the head
        y = 22 + round(((x - 38) / 21.0) ** 2 * 9)
        rect(im, x, y, 1, 3, dk)
    paint(im, ellipse_pts(17, 35, 3, 6), accent)
    paint(im, ellipse_pts(59, 35, 3, 6), accent)
    return im
def sunglasses():
    im = layer(); dk = BLACK
    rect(im, 21, 31, 11, 7, dk); rect(im, 44, 31, 11, 7, dk); rect(im, 32, 33, 12, 2, dk)
    rect(im, 22, 32, 3, 1, (120, 112, 116, 255)); rect(im, 45, 32, 3, 1, (120, 112, 116, 255))
    return im
def bowtie(accent):
    im = layer()
    dk = tuple(max(0, v - 40) for v in accent[:3]) + (255,)
    paint(im, tri_pts((26, 46), (36, 51), (26, 56)), accent); paint(im, tri_pts((50, 46), (40, 51), (50, 56)), accent)
    paint(im, ellipse_pts(38, 51, 2, 2), (150, 40, 40, 255))
    return im
def scarf(accent):
    im = layer()
    for i in range(0, 56, 4):
        rect(im, 10 + i, 46, 4, 6, accent if (i // 4) % 2 == 0 else WHITE)
    for x in range(10, 66): put(im, x, 45, LINE); put(im, x, 52, LINE)
    for y in range(46, 52): put(im, 9, y, LINE); put(im, 66, y, LINE)
    for i in range(0, 8, 4):  # the loose end hanging down the front
        rect(im, 56 + i, 52, 4, 5, accent if (i // 4) % 2 == 0 else WHITE)
    return im
def hat_witch():
    im = layer(); p1 = (76, 60, 97, 255); p2 = (58, 47, 74, 255)
    paint(im, tri_pts((26, 21), (46, 2), (52, 21)), p1)
    rect(im, 16, 20, 46, 4, p2)
    for x in range(16, 62): put(im, x, 19, LINE); put(im, x, 24, LINE)
    rect(im, 28, 16, 22, 3, (242, 194, 58, 255))
    return im

MAG = (255, 0, 255, 255)
def split(im):
    """(static, accent): the pixels drawn with the placeholder colour go to a white layer that the game tints with the chosen colour."""
    st, ac = layer(), layer()
    for y in range(H):
        for x in range(W):
            c = im.getpixel((x, y))
            if c[3] == 0: continue
            if c[:3] == MAG[:3]: ac.putpixel((x, y), WHITE)
            else: st.putpixel((x, y), c)
    return st, ac

def compose(parts):
    out = layer()
    for p in parts: out.alpha_composite(p)
    return out

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    A = MAG
    singles = {"base": base(), "paw_l_up": paw(9, 36, False, True), "paw_l_dn": paw(14, 50, True, True), "paw_r_up": paw(67, 36, False, False),
               "paw_r_dn": paw(62, 50, True, False), "santa": hat_santa(), "elf": hat_elf(), "sunglasses": sunglasses(), "witch": hat_witch()}
    tinted = {"party": hat_party(A), "crown": crown(A), "headphones": headphones(A), "bowtie": bowtie(A), "scarf": scarf(A)}
    files = dict(singles)
    for k, v in tinted.items():
        st, ac = split(v)
        files[k] = st; files[k + "_acc"] = ac
    for k, v in files.items(): v.save(os.path.join(OUT, k + ".png"))
    acc = (224, 69, 58, 255)
    def tintimg(im):
        o = im.copy(); px = o.load()
        for y in range(H):
            for x in range(W):
                if px[x, y][3]: px[x, y] = acc
        return o
    def outfit(name):
        if name in tinted: return [files[name], tintimg(files[name + "_acc"])]
        return [files[name]]
    paws = [files["paw_l_up"], files["paw_r_up"]]
    names = ["santa", "elf", "party", "crown", "headphones", "sunglasses", "bowtie", "scarf", "witch"]
    sheet = Image.new("RGBA", (W * 5 + 6 * 6, H * 3 + 4 * 6), (38, 40, 52, 255))
    frames = [["paw_l_up", "paw_r_up"], ["paw_l_dn", "paw_r_up"], ["paw_l_up", "paw_r_dn"], ["paw_l_dn", "paw_r_dn"]]
    for i, f in enumerate(frames): sheet.alpha_composite(compose([files["base"]] + [files[n] for n in f]), (6 + i * (W + 6), 6))
    for i, n in enumerate(names):
        x, y = 6 + (i % 5) * (W + 6), 6 + (1 + i // 5) * (H + 6)
        sheet.alpha_composite(compose([files["base"]] + outfit(n) + paws), (x, y))
    sheet.resize((sheet.size[0] * 4, sheet.size[1] * 4), Image.NEAREST).save(sys.argv[1] if len(sys.argv) > 1 else "/tmp/cat_sheet.png")
    print("ok", W, H)
