#!/usr/bin/env python3
"""Importa ficheiros .emotecraft (formato binario do Emotecraft) para o mod.

Uso:
  python3 tools/import_emotecraft.py <ficheiro.emotecraft> [<outro.emotecraft> ...] [--replace]

--replace  apaga primeiro TODOS os emotes que o client ja tinha (so ficam os que estas a importar).
Sem --replace os novos sao acrescentados aos existentes (mesmo id = substitui).
Depois: ./gradlew build   (e gera-se o catalogo para o site com tools/make_emotes_catalogue.py)
"""
import glob, json, os, re, shutil, struct, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
res = os.path.join(root, "src/main/resources/assets/sol_client")
PARTS_ORDER = ["head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"]
# Emotecraft (ids 6..35) -> nomes que o client conhece
EASE = {0: "LINEAR", 1: "CONSTANT", 36: "LINEAR", 37: "CONSTANT"}
NAMES = ["EASEINSINE", "EASEOUTSINE", "EASEINOUTSINE", "EASEINCUBIC", "EASEOUTCUBIC", "EASEINOUTCUBIC",
         "EASEINQUAD", "EASEOUTQUAD", "EASEINOUTQUAD", "EASEINQUART", "EASEOUTQUART", "EASEINOUTQUART",
         "EASEINQUINT", "EASEOUTQUINT", "EASEINOUTQUINT", "EASEINEXPO", "EASEOUTEXPO", "EASEINOUTEXPO",
         "EASEINCIRC", "EASEOUTCIRC", "EASEINOUTCIRC", "EASEINBACK", "EASEOUTBACK", "EASEINOUTBACK",
         "EASEINELASTIC", "EASEOUTELASTIC", "EASEINOUTELASTIC", "EASEINBOUNCE", "EASEOUTBOUNCE", "EASEINOUTBOUNCE"]
for i, n in enumerate(NAMES):
    EASE[6 + i] = n


class Bad(Exception):
    pass


class R:
    def __init__(self, b, p):
        self.b, self.p = b, p

    def take(self, fmt):
        n = struct.calcsize(fmt)
        if self.p + n > len(self.b):
            raise Bad("eof")
        v = struct.unpack_from(fmt, self.b, self.p)
        self.p += n
        return v[0] if len(v) == 1 else v


def read_axis(r, ver, ksize):
    if ver >= 2:
        en = r.take(">B")
        n = r.take(">i")
    else:
        n = r.take(">i")
        en = 1 if n >= 0 else 0
        n = max(n, 0)
    if n == -1:
        n = 0  # eixo desligado (alguns ficheiros escrevem -1)
    if n < 0 or n > 20000:
        raise Bad("count")
    out = []
    last = -1
    for _ in range(n):
        start = r.p
        tick = r.take(">i")
        val = r.take(">f")
        ease = r.take(">B")
        if ver >= 4:
            r.take(">f")
        if tick < last or tick > 100000 or val != val or abs(val) > 1e6 or ease > 40:
            raise Bad("kf")
        last = tick
        r.p = start + ksize
        out.append((tick, val, ease))
    return out


def read_part(r, ver, ksize, bend, scale):
    ax = {}
    for k in ("x", "y", "z", "pitch", "yaw", "roll"):
        ax[k] = read_axis(r, ver, ksize)
    if bend:
        ax["bendDirection"] = read_axis(r, ver, ksize)
        ax["bend"] = read_axis(r, ver, ksize)
    if scale and ver >= 3:
        for k in ("scaleX", "scaleY", "scaleZ"):
            ax[k] = read_axis(r, ver, ksize)
    return ax


def parse_anim(b, start):
    r = R(b, start)
    begin, end, stop = r.take(">i"), r.take(">i"), r.take(">i")
    loop = r.take(">B")
    ret = r.take(">i")
    r.take(">B")  # easing before
    r.take(">B")  # nsfw
    ksize = r.take(">b")
    if ksize not in (9, 13) or end < 0 or end > 100000 or stop < 0 or stop > 100000 or loop > 1:
        raise Bad("hdr")
    ver_try = (4,) if ksize == 13 else (3, 2)
    count0 = r.p
    for ver in ver_try:
        r.p = count0
        try:
            n = r.take(">i")
            if n < 1 or n > 32:
                raise Bad("n")
            parts = {}
            for _ in range(n):
                ln = r.take(">i")
                if ln < 1 or ln > 40:
                    raise Bad("name")
                name = r.b[r.p:r.p + ln].decode("ascii")
                r.p += ln
                # cada parte: com/sem dobra e com/sem escala -> procura a combinacao que encaixa
                got = None
                for bend, scale in ((True, False), (False, False), (True, True), (False, True)):
                    save = r.p
                    try:
                        got = read_part(r, ver, ksize, bend, scale)
                        # so e valido se o que vem a seguir parece um nome de parte (ou o UUID)
                        break
                    except Bad:
                        r.p = save
                if got is None:
                    raise Bad("part")
                parts[name] = (got, r.p)
            return dict(begin=begin, end=end, stop=stop, loop=bool(loop), ret=ret, parts=parts, endpos=r.p, ver=ver)
        except (Bad, UnicodeDecodeError):
            continue
    raise Bad("anim")


def parse_anim_strict(b, start):
    """Tenta todas as combinacoes de dobra/escala por parte (DFS) ate o ficheiro encaixar ao byte (UUID de 16 bytes no fim)."""
    r0 = R(b, start)
    begin, end, stop = r0.take(">i"), r0.take(">i"), r0.take(">i")
    loop = r0.take(">B")
    ret = r0.take(">i")
    r0.take(">B")
    r0.take(">B")
    ksize = r0.take(">b")
    if ksize not in (9, 13):
        raise Bad("ksize")
    if b[7] == 1:  # formato 1: seis partes sempre pela mesma ordem, sem nomes
        r1 = R(b, r0.p)
        parts = []
        for name in ("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"):
            parts.append((name, read_part(r1, 1, ksize, name != "head", False)))
        return dict(begin=begin, end=end, stop=stop, loop=bool(loop), ret=ret, parts=dict(parts), endpos=r1.p + 16, ver=1)
    n = r0.take(">i")
    base = r0.p
    ver_try = (4,) if ksize == 13 else (3, 2)
    combos = ((True, False), (False, False), (True, True), (False, True))

    def dfs(pos, left, ver, acc):
        if left == 0:
            return acc, pos
        r = R(b, pos)
        try:
            ln = r.take(">i")
            if ln < 1 or ln > 40:
                return None
            name = b[r.p:r.p + ln].decode("ascii")
            r.p += ln
        except (Bad, UnicodeDecodeError):
            return None
        for bend, scale in combos:
            rr = R(b, r.p)
            try:
                ax = read_part(rr, ver, ksize, bend, scale)
            except Bad:
                continue
            res = dfs(rr.p, left - 1, ver, acc + [(name, ax)])
            if res:
                return res
        return None

    for ver in ver_try:
        res = dfs(base, n, ver, [])
        if res:
            parts, pos = res
            # o UUID (16 bytes) vem logo a seguir
            return dict(begin=begin, end=end, stop=stop, loop=bool(loop), ret=ret, parts=dict(parts), endpos=pos + 16, ver=ver)
    raise Bad("no parse")


def lerp(kfs, t):
    if not kfs:
        return 0.0
    if t <= kfs[0][0]:
        return kfs[0][1]
    for (t0, v0, _), (t1, v1, _) in zip(kfs, kfs[1:]):
        if t <= t1:
            return v0 if t1 == t0 else v0 + (v1 - v0) * (t - t0) / (t1 - t0)
    return kfs[-1][1]


def merge(a, b):
    """Soma duas pistas (cada eixo: lista de (tick, valor, ease)); onde so uma existe usa-se essa."""
    a, b = a or {}, b or {}
    res = {}
    for k in set(a) | set(b):
        ka, kb = a.get(k) or [], b.get(k) or []
        if not ka or not kb:
            res[k] = ka or kb
            continue
        ticks = sorted({t for t, _, _ in ka} | {t for t, _, _ in kb})
        ease = {t: e for t, _, e in kb}
        res[k] = [(t, lerp(ka, t) + lerp(kb, t), ease.get(t, 0)) for t in ticks]
    return res


def convert(path):
    b = open(path, "rb").read()
    info = None
    for start in range(8, 40):
        try:
            info = parse_anim_strict(b, start)
            break
        except (Bad, struct.error):
            continue
    if info is None:
        raise Bad("nao consegui ler " + path)
    # Emotecraft novo: "body" = deslocamento do corpo todo, "torso" = tronco. O client tem uma so pista "torso" (corpo todo): soma-se as duas.
    parts = info["parts"]
    out = {}
    for name, ax in parts.items():
        if name in PARTS_ORDER and name != "torso":
            out[name] = ax
    out["torso"] = merge(parts.get("body"), parts.get("torso"))
    if not any(out["torso"].values()):
        del out["torso"]
    moves = {}
    for part, ax in out.items():
        for prop, kfs in ax.items():
            if prop in ("bendDirection", "scaleX", "scaleY", "scaleZ"):
                continue
            for tick, val, ease in kfs:
                key = (tick, EASE.get(ease, "LINEAR"))
                m = moves.setdefault(key, {"tick": tick, "easing": key[1], "turn": 0})
                m.setdefault(part, {})[prop] = round(val, 4)
    lst = [moves[k] for k in sorted(moves, key=lambda k: k[0])]
    e = {"isLoop": "true" if info["loop"] else "false", "returnTick": info["ret"], "beginTick": info["begin"],
         "endTick": info["end"], "stopTick": info["stop"], "degrees": False, "moves": lst}
    tail = b[info["endpos"]:]
    quoted = re.findall(rb'"([^"\x00]{0,200})"', tail)
    author = ""
    if len(quoted) >= 3:
        author = quoted[2].decode("utf-8", "replace").strip("[] ")
    if not author.isprintable() or author.lower() == "fallback" or len(author) > 40:
        author = ""
    png = None
    i = tail.find(b"\x89PNG\r\n\x1a\n")
    if i >= 0:
        j = tail.find(b"IEND", i)
        if j >= 0:
            png = tail[i:j + 8]
    return e, author, png


def pretty(stem):
    s = re.sub(r"\s*\([^)]*\)", "", stem).strip()
    s = re.sub(r"\s+\d+(\.\d+)?$", "", s) if re.search(r"[A-Za-z]\s+\d+\.\d+$", s) else s
    return s.strip()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    replace = "--replace" in sys.argv
    files = []
    for a in args:
        files += sorted(glob.glob(a)) if any(c in a for c in "*?[") else [a]
    if not files:
        print(__doc__)
        sys.exit(1)
    em_dir = res + "/emotes"
    ic_dir = res + "/textures/gui/emote"
    os.makedirs(em_dir, exist_ok=True)
    os.makedirs(ic_dir, exist_ok=True)
    idx_path = em_dir + "/index.json"
    if replace:
        for f in glob.glob(em_dir + "/*.json"):
            os.remove(f)
        for f in glob.glob(ic_dir + "/*.png"):
            os.remove(f)
        index = {}
    else:
        index = {e["id"]: e for e in json.load(open(idx_path))} if os.path.exists(idx_path) else {}
    for f in files:
        stem = os.path.splitext(os.path.basename(f))[0]
        try:
            e, author, png = convert(f)
        except Bad as ex:
            print("IGNORADO", f, ex)
            continue
        eid = re.sub(r"[^a-z0-9]", "", pretty(stem).lower())
        with open(f"{em_dir}/{eid}.json", "w") as fh:
            json.dump({"emote": e}, fh, separators=(",", ":"))
        has_icon = False
        if png:
            with open(f"{ic_dir}/{eid}.png", "wb") as fh:
                fh.write(png)
            has_icon = True
        index[eid] = {"id": eid, "name": pretty(stem), "icon": has_icon, "author": author, "loop": e["isLoop"] == "true"}
        print("ok", eid, "-", len(e["moves"]), "passos,", e["endTick"], "ticks, loop" if e["isLoop"] == "true" else "")
    out = sorted(index.values(), key=lambda x: x["name"].lower())
    with open(idx_path, "w") as fh:
        json.dump(out, fh, indent=1)
    print(len(out), "emotes no indice")


if __name__ == "__main__":
    main()
