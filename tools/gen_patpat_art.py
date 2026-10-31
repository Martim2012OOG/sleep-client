"""Original art for PatPat and Christmas World (no Mojang textures):
- textures/gui/icon/pat_pat.png, christmas_world.png   64x64 white glyphs like the other mod icons

Run:  python3 tools/gen_patpat_art.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_notify_icons as g  # noqa: E402  (shapes + PNG writer)

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "sol_client", "textures", "gui")


def png_rgba(w, h, pixels):
    """pixels: list of rows of (r, g, b, a)."""
    import struct
    import zlib
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for p in row:
            raw += bytes(p)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# 32 x 32 hand, palm down, fingers pointing up the picture. '.' empty, letters = colours
HAND = [
    "................................",
    "................................",
    "...........aa..aa...............",
    "........aa.bba.bba.aa...........",
    ".......abba.bba.bba.bba.........",
    ".......abba.bba.bba.bba.........",
    ".......abba.bba.bba.bba.........",
    ".......abba.bba.bba.bba.........",
    ".......abba.bba.bba.bba.........",
    ".......abbaabbaabbaabba.........",
    ".......abbbbbbbbbbbbbba.........",
    ".......abbbbbbbbbbbbbba..aa.....",
    ".......abbbbbbbbbbbbbbaabba.....",
    ".......abbbbbbbbbbbbbbbbbba.....",
    ".......abbbbbbbbbbbbbbbbba......",
    ".......abbbbbbbbbbbbbbbba.......",
    ".......abbbbbbbbbbbbbbba........",
    ".......abbbbbbbbbbbbbba.........",
    ".......acbbbbbbbbbbbbca.........",
    ".......accbbbbbbbbbbcca.........",
    "........acccccccccccca..........",
    "........addddddddddda...........",
    "........addddddddddda...........",
    "........addddddddddda...........",
    "........aeeeeeeeeeeea...........",
    "........aeeeeeeeeeeea...........",
    "........aeeeeeeeeeeea...........",
    "........aeeeeeeeeeeea...........",
    "........aeeeeeeeeeeea...........",
    "................................",
    "................................",
    "................................",
]
COLOURS = {
    "a": (120, 72, 48, 255),    # outline
    "b": (240, 190, 150, 255),  # skin
    "c": (214, 160, 122, 255),  # skin shade
    "d": (70, 120, 210, 255),   # sleeve cuff
    "e": (52, 92, 170, 255),    # sleeve
}

ICONS = {
    # an open hand over a little head
    "pat_pat": [("add", g.rrect(18, 8, 46, 30, 6), 1), ("add", g.rrect(14, 4, 20, 22, 3), 1), ("add", g.rrect(22, 2, 28, 18, 3), 1),
                ("add", g.rrect(30, 2, 36, 18, 3), 1), ("add", g.rrect(38, 4, 44, 20, 3), 1), ("add", g.line(44, 22, 54, 14, 6), 1),
                ("add", g.rrect(14, 38, 50, 60, 8), 1), ("sub", g.circle(25, 49, 3.5), 0), ("sub", g.circle(39, 49, 3.5), 0)],
    # snowflake
    "christmas_world": [("add", g.line(32, 4, 32, 60, 5), 1), ("add", g.line(8, 18, 56, 46, 5), 1), ("add", g.line(8, 46, 56, 18, 5), 1),
                        ("add", g.line(32, 14, 24, 7, 4), 1), ("add", g.line(32, 14, 40, 7, 4), 1),
                        ("add", g.line(32, 50, 24, 57, 4), 1), ("add", g.line(32, 50, 40, 57, 4), 1),
                        ("add", g.circle(32, 32, 7), 1), ("sub", g.circle(32, 32, 3), 0)],
}

if __name__ == "__main__":
    os.makedirs(os.path.join(ASSETS, "patpat"), exist_ok=True)
    # the hand in game is textures/gui/patpat/hand_frames.png (supplied by the owner); HAND above is no longer written
    for name, ops in ICONS.items():
        with open(os.path.join(ASSETS, "icon", name + ".png"), "wb") as f:
            f.write(g.png(g.render(ops)))
        with open(os.path.join(ASSETS, "icon", name + ".png.mcmeta"), "w") as f:
            f.write('{"texture":{"blur":true}}')
    print("wrote hand + icons")
