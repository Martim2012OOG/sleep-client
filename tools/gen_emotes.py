#!/usr/bin/env python3
"""
Makes the Sleep client's own emotes (assets/sol_client/emotes/*.json + index.json) and emotes-catalogue.json.

Every emote here is written for the classic six-part body (no elbows / knees, see LimbSplit.R6), as a small function
"time in ticks -> pose", sampled every 2 ticks. Nothing is imported from anywhere: change a function, run this file again.

    python3 tools/gen_emotes.py            # writes the json files
    python3 tools/gen_emotes.py --list     # only prints the list

Pose (angles in DEGREES, positions in model pixels):
    h / ra / la / rl / ll = (pitch, yaw, roll) of head, right arm, left arm, right leg, left leg
    b = (pitch, yaw, roll) of the whole body, turning around the hips     pos = (x, y, z) of the whole body, y = up
    rao / lao / rlo / llo / ho = (x, y, z) pivot offsets of that part
Signs (same as the game's model): arm / leg pitch -90 = pointing forward; right arm roll + = away from the body, left arm roll - =
away from the body; body pitch - = leaning forward; head pitch + = looking down; head yaw + = looking to the player's right.
"""
import json
import math
import os
import sys

PX = 16.0 / 0.9375  # model pixels per block (EmoteRig.PX)
STEP = 2

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "sol_client", "emotes")

# The 12 old (imported) emotes: kept in index.json as hidden so the positions other players send stay the same.
OLD = [("amogus", "Amogus"), ("ascend", "Ascend"), ("becomehelicopter", "Become Helicopter"), ("brakedance", "Brakedance"),
       ("californiagirls", "California Girls"), ("coolsit", "Cool Sit"), ("frontflip", "Front Flip"), ("getthegriddy", "Get the Griddy"),
       ("headpat", "Headpat"), ("helicopterhelicopter", "Helicopter Helicopter"), ("hug", "Hug"), ("lean", "Lean")]

TAU = 2 * math.pi


def s(x):
    """sin of x turns"""
    return math.sin(TAU * x)


def c(x):
    return math.cos(TAU * x)


def up(x):
    """0..1..0 bump once per turn (always positive)"""
    return abs(math.sin(math.pi * x))


def ss(a, b, t):
    """smooth 0 -> 1 while t goes a -> b"""
    if a == b:
        return 1.0 if t >= b else 0.0
    u = max(0.0, min(1.0, (t - a) / float(b - a)))
    return u * u * (3 - 2 * u)


def hold(t, a, b, c2, d):
    """0 -> 1 between a..b, stays, 1 -> 0 between c2..d"""
    return ss(a, b, t) * (1 - ss(c2, d, t))


def mix(p, q, u):
    return tuple(a + (b - a) * u for a, b in zip(p, q))


def P(**kw):
    return kw


# ------------------------------------------------------------------------------------------------ the emotes
def take_the_l(t):
    ph = t / 16.0
    k = s(ph)
    return P(h=(-6, 0, 6 * k), ra=(-150, 0, 26), la=(8 * c(ph), 0, -14 - 6 * up(ph * 2)),
             rl=(0, 0, 38 * max(0, k)), ll=(0, 0, -38 * max(0, -k)),
             b=(0, 0, -7 * k), pos=(-1.2 * k, 1.6 * up(ph * 2), 0))


def griddy(t):
    ph = t / 10.0  # one heel tap
    gog = ss(38, 44, t % 80) * (1 - ss(74, 80, t % 80))  # second half: "goggles"
    sw = 45 * s(ph / 2)
    arms_r = mix((sw, 0, 12), (-150, 0, 20), gog)
    arms_l = mix((sw, 0, -12), (-150, 0, -20), gog)
    k = s(ph / 2)
    return P(h=(-6 - 14 * gog, 0, 0), ra=arms_r, la=arms_l,
             rl=(-34 * max(0, k) + 6, 0, 3), ll=(-34 * max(0, -k) + 6, 0, -3),
             b=(6, 0, 0), pos=(0, 1.4 * up(ph), 0))


def floss(t):
    ph = t / 14.0
    k = s(ph)
    front = 28 * c(ph / 2) / max(0.35, abs(c(ph / 2))) * min(1.0, abs(c(ph / 2)) / 0.35)  # in front, then behind
    return P(h=(0, 0, -4 * k), ra=(-front, 0, 42 * k), la=(-front, 0, 42 * k),
             rl=(0, 0, 9 * k), ll=(0, 0, 9 * k), b=(0, 0, -9 * k), pos=(1.5 * k, 0, 0))


def dab(t):
    u = hold(t, 0, 5, 28, 34)
    return P(h=(28 * u, -24 * u, 0), ra=(-108 * u, -62 * u, 0), la=(0, 0, -138 * u),
             rl=(0, 0, 6 * u), ll=(0, 0, -10 * u), b=(-6 * u, 0, 10 * u))


def wave(t):
    u = ss(0, 5, t)
    return P(h=(0, 0, -5 * u), ra=(0, 0, (152 + 20 * s(t / 10.0)) * u), la=(0, 0, -4), b=(0, 0, -2 * u))


def clap(t):
    k = (c(t / 8.0) + 1) / 2
    return P(h=(4, 0, 0), ra=(-72, -5 - 17 * k, 0), la=(-72, 5 + 17 * k, 0), pos=(0, 0.5 * k, 0))


def salute(t):
    u = hold(t, 0, 4, 34, 40)
    return P(h=(-4 * u, 0, 0), ra=(-146 * u, 0, 30 * u), la=(0, 0, 0), b=(2 * u, 0, 0))


def facepalm(t):
    u = hold(t, 0, 7, 44, 52)
    shake = 7 * s(t / 9.0) * hold(t, 10, 14, 36, 42)
    return P(h=(24 * u, shake, 0), ra=(-138 * u, 0, 30 * u), la=(0, 0, -4), b=(-5 * u, 0, 0))


def tpose(t):
    u = ss(0, 6, t)
    return P(ra=(0, 0, 90 * u), la=(0, 0, -90 * u), pos=(0, (5 + 1.2 * s(t / 40.0)) * u, 0), rl=(0, 0, 2), ll=(0, 0, -2))


def jumping_jacks(t):
    k = (1 - c(t / 14.0)) / 2
    return P(ra=(0, 0, 168 * k + 4), la=(0, 0, -168 * k - 4), rl=(0, 0, 24 * k), ll=(0, 0, -24 * k),
             pos=(0, 2.4 * up(t / 7.0), 0))


def pushups(t):
    u = ss(0, 8, t)
    k = (1 - c((t - 8) / 22.0)) / 2 if t > 8 else 0  # 0 = up, 1 = down
    sh = 9.6 - 4.8 * k  # shoulders above the floor (arm length 10)
    th = math.degrees(math.asin(sh / 22.0))  # the plank's slope: feet on the floor, hips half way up
    hip = 12 * math.sin(math.radians(th))
    roll = math.degrees(math.acos(min(1.0, sh / 10.0)))
    tilt = -(90 - th)
    return P(h=(-58 * u, 0, 0), ra=(tilt * u, 0, roll * u), la=(tilt * u, 0, -roll * u), rl=(0, 0, 2), ll=(0, 0, -2),
             b=(tilt * u, 0, 0), pos=(0, -(12 - hip) * u, 5 * u))


def backflip(t):
    u = ss(4, 18, t)
    crouch = hold(t, 0, 3, 4, 6) + hold(t, 17, 19, 20, 24) * 0.6
    air = math.sin(math.pi * max(0.0, min(1.0, (t - 4) / 14.0)))
    return P(h=(-10 * air, 0, 0), ra=(-150 * air + 20 * crouch, 0, 12), la=(-150 * air + 20 * crouch, 0, -12),
             rl=(-30 * air, 0, 3), ll=(-18 * air, 0, -3), b=(360 * u - 6 * crouch, 0, 0), pos=(0, 15 * air - 1.2 * crouch, 0))


def spin(t):
    ph = (t % 24) / 24.0
    return P(ra=(0, 0, 82), la=(0, 0, -82), rl=(0, 0, 6), ll=(0, 0, -14), b=(0, 360 * ph, 0), pos=(0, 1.0 + 0.8 * s(ph * 2), 0),
             h=(-8, 0, 0))


def sit(t):
    u = ss(0, 7, t)
    br = 0.6 * s(t / 60.0)
    return P(h=(-4 * u + br, 0, 0), ra=(26 * u, 0, 12 * u), la=(26 * u, 0, -12 * u),
             rl=(-82 * u, 0, 8 * u), ll=(-82 * u, 0, -8 * u), b=(8 * u, 0, 0), pos=(0, -10 * u, 0))


def laugh(t):
    u = ss(0, 5, t)
    j = up(t / 4.0)
    return P(h=(-22 * u - 6 * j, 0, 0), ra=(-52 * u, -40 * u, 0), la=(-52 * u, 40 * u, 0), rl=(12 * u, 0, 4), ll=(12 * u, 0, -4),
             b=((12 + 3 * j) * u, 0, 0), pos=(0, 0.9 * j * u, 0), rao=(0, -0.8 * j, 0), lao=(0, -0.8 * j, 0))


def cry(t):
    u = ss(0, 6, t)
    j = s(t / 6.0)
    return P(h=(22 * u + 3 * j, 0, 0), ra=(-146 * u, 0, 22 * u), la=(-146 * u, 0, -22 * u), b=(-8 * u, 0, 0),
             pos=(0.25 * j * u, 0, 0), rao=(0, -0.6 * up(t / 6.0), 0), lao=(0, -0.6 * up(t / 6.0), 0))


def point(t):
    u = hold(t, 0, 5, 36, 42)
    jab = 4 * up(t / 12.0) * hold(t, 6, 8, 30, 34)
    return P(h=(-2 * u, -6 * u, 0), ra=((-92 - jab) * u, 0, 0), la=(6 * u, 0, -6 * u), b=(-3 * u, 12 * u, 0),
             rl=(8 * u, 0, 0), ll=(-10 * u, 0, 0))


def victory(t):
    u = ss(0, 4, t)
    j = up(t / 8.0)
    return P(h=(-14 * u, 0, 0), ra=(0, 0, (150 + 10 * j) * u), la=(0, 0, -(150 + 10 * j) * u), rl=(0, 0, 4 + 6 * j), ll=(0, 0, -4 - 6 * j),
             pos=(0, 3.2 * j * u, 0))


def zombie(t):
    u = ss(0, 6, t)
    k = s(t / 40.0)
    return P(h=(6 * u, 8 * k * u, 14 * u), ra=((-90 + 5 * s(t / 20.0)) * u, 0, 0), la=((-84 - 5 * s(t / 20.0)) * u, 0, 0),
             rl=(16 * k * u, 0, 2), ll=(-16 * k * u, 0, -2), b=(-7 * u, 0, 5 * k * u), pos=(0, 0.5 * up(t / 20.0) * u, 0))


def robot(t):
    # snapping from pose to pose every 8 ticks
    poses = [P(h=(0, 40, 0), ra=(-90, 0, 0), la=(0, 0, -90), b=(0, 0, 0)),
             P(h=(0, -40, 0), ra=(0, 0, 90), la=(-90, 0, 0), b=(0, 18, 0)),
             P(h=(0, 0, 12), ra=(-180, 0, 0), la=(-90, 0, 0), b=(0, 0, 6)),
             P(h=(0, 0, -12), ra=(-90, 0, 0), la=(-180, 0, 0), b=(0, -18, -6)),
             P(h=(10, 0, 0), ra=(-90, -60, 0), la=(-90, 60, 0), b=(-8, 0, 0))]
    i = int(t // 8) % len(poses)
    u = ss(0, 3, t % 8)
    a, b2 = poses[i - 1], poses[i]
    out = {}
    for key in ("h", "ra", "la", "b"):
        out[key] = mix(a[key], b2[key], u)
    out["pos"] = (0, 0.7 * up((t % 8) / 6.0) * (1 if t % 8 < 3 else 0), 0)
    return out


def headbang(t):
    u = ss(0, 4, t)
    k = (1 - c(t / 8.0)) / 2
    return P(h=(46 * k * u - 8, 0, 0), ra=((-38 - 26 * s(t / 4.0)) * u, -36 * u, 0), la=(-64 * u, 0, -58 * u),
             rl=(0, 0, 10 * u), ll=(0, 0, -10 * u), b=((-6 - 12 * k) * u, 0, 0), pos=(0, -0.6 * k * u, 0))


def nap(t):
    u = ss(0, 12, t)
    br = s(t / 50.0)
    return P(h=(-6 * u, 14 * u, 0), ra=(0, 0, 148 * u), la=(0, 0, -148 * u), rl=(0, 0, 5 * u), ll=(-14 * u, 0, -8 * u),
             b=(90 * u, 0, 0), pos=(0, (-9.6 + 0.35 * br) * u, -2 * u))


def bow(t):
    u = hold(t, 0, 9, 30, 40)
    return P(h=(18 * u, 0, 0), ra=(-48 * u, -66 * u, 0), la=(34 * u, 0, -8 * u), rl=(-52 * u, 0, 0), ll=(-52 * u, 0, 0),
             b=(-52 * u, 0, 0))


def hype(t):
    u = ss(0, 4, t)
    k = s(t / 12.0)
    return P(h=(-10 * u, 0, 5 * k), ra=((-130 - 45 * max(0, k)) * u, 0, -12), la=((-130 - 45 * max(0, -k)) * u, 0, 12),
             rl=(-12 * max(0, -k), 0, 3), ll=(-12 * max(0, k), 0, -3), b=(0, 8 * k * u, 0), pos=(0, 2.2 * up(t / 6.0) * u, 0))


def shuffle(t):
    k = s(t / 12.0)
    return P(h=(4, 0, 0), ra=(44 * k, 0, 14), la=(-44 * k, 0, -14), rl=(-32 * k - 6, 0, 2), ll=(32 * k - 6, 0, -2),
             b=(-7, 0, 0), pos=(0, 1.8 * up(t / 6.0), 0.8 * c(t / 6.0)))


def disco(t):
    k = (1 - c(t / 20.0)) / 2  # 0 = pointing up, 1 = pointing down across the body
    arm = mix((0, 0, 142), (-48, -62, 0), k)
    hip = s(t / 10.0)
    return P(h=(-10 + 20 * k, 20 - 40 * k, 0), ra=arm, la=(8, 0, -24), rl=(0, 0, 6 + 4 * hip), ll=(0, 0, -6 + 4 * hip),
             b=(0, 10 - 20 * k, -4 * hip), pos=(1.0 * hip, 0.8 * up(t / 10.0), 0))


def squat_kick(t):
    u = ss(0, 6, t)
    k = s(t / 12.0)
    r = -52 - 38 * max(0, k)
    l = -52 - 38 * max(0, -k)
    return P(h=(-4, 0, 0), ra=(-78 * u, -58 * u, 0), la=(-78 * u, 58 * u, 0), rao=(0, 0.6, 0), lao=(0, -0.6, 0),
             rl=(r * u, 0, 6 * u), ll=(l * u, 0, -6 * u), b=(6 * u, 0, 0), pos=(0, (-6.5 + 1.5 * up(t / 6.0)) * u, 0))


def helicopter(t):
    u = ss(0, 8, t)
    ph = (t % 16) / 16.0 if t >= 8 else 0.0
    return P(h=(-70 * u, 0, 0), ra=(0, 0, 100 * u), la=(0, 0, -100 * u), rl=(0, 0, 14 * u), ll=(0, 0, -14 * u),
             b=(-88 * u, 360 * ph, 0), pos=(0, (2 + 1.5 * s(t / 32.0)) * u, 0))


def flex(t):
    u = ss(0, 6, t)
    k = s(t / 36.0)
    pump = up(t / 9.0)
    return P(h=(-6 * u, 26 * k * u, 0), ra=(0, 0, (112 + 14 * pump) * u), la=(0, 0, -(112 + 14 * pump) * u),
             rl=(0, 0, 10 * u), ll=(0, 0, -10 * u), b=(4 * u, 18 * k * u, 0))


def shrug(t):
    u = hold(t, 0, 5, 22, 30)
    return P(h=(0, 0, 9 * u), ra=(-22 * u, 0, 36 * u), la=(-22 * u, 0, -36 * u), rao=(0, -1.6 * u, 0), lao=(0, -1.6 * u, 0))


# id, name, function, loop start tick, length, loop, tag, price, rarity, free
EMOTES = [
    ("take_the_l", "Take the L", take_the_l, 0, 32, True, "dance", 400, "epic", False),
    ("griddy", "Griddy", griddy, 0, 80, True, "dance", 400, "epic", False),
    ("floss", "Floss", floss, 0, 28, True, "dance", 300, "rare", False),
    ("dab", "Dab", dab, 0, 34, False, "funny", 150, "common", False),
    ("wave", "Wave", wave, 6, 26, True, "greeting", 0, "common", True),
    ("clap", "Clap", clap, 0, 8, True, "greeting", 0, "common", True),
    ("salute", "Salute", salute, 0, 40, False, "greeting", 0, "common", True),
    ("facepalm", "Facepalm", facepalm, 0, 52, False, "funny", 150, "common", False),
    ("tpose", "T-Pose", tpose, 6, 46, True, "funny", 200, "rare", False),
    ("jumping_jacks", "Jumping Jacks", jumping_jacks, 0, 14, True, "sport", 150, "common", False),
    ("pushups", "Push-ups", pushups, 8, 30, True, "sport", 250, "rare", False),
    ("backflip", "Backflip", backflip, 0, 24, False, "sport", 350, "epic", False),
    ("spin", "Spin", spin, 0, 24, True, "dance", 200, "rare", False),
    ("sit", "Sit", sit, 8, 68, True, "chill", 0, "common", True),
    ("laugh", "Laugh", laugh, 8, 16, True, "funny", 150, "common", False),
    ("cry", "Cry", cry, 6, 12, True, "funny", 150, "common", False),
    ("point", "Point", point, 0, 42, False, "greeting", 0, "common", True),
    ("victory", "Victory", victory, 8, 16, True, "sport", 200, "rare", False),
    ("zombie", "Zombie", zombie, 40, 80, True, "funny", 200, "rare", False),
    ("robot", "Robot", robot, 0, 40, True, "dance", 300, "rare", False),
    ("headbang", "Headbang", headbang, 8, 16, True, "dance", 250, "rare", False),
    ("nap", "Power Nap", nap, 50, 100, True, "chill", 300, "rare", False),
    ("bow", "Bow", bow, 0, 40, False, "greeting", 150, "common", False),
    ("hype", "Hype", hype, 12, 24, True, "dance", 300, "rare", False),
    ("shuffle", "Shuffle", shuffle, 0, 12, True, "dance", 300, "rare", False),
    ("disco", "Disco Fever", disco, 0, 20, True, "dance", 350, "epic", False),
    ("squat_kick", "Squat Kick", squat_kick, 12, 24, True, "dance", 400, "epic", False),
    ("helicopter", "Helicopter", helicopter, 32, 64, True, "funny", 500, "legendary", False),
    ("flex", "Flex", flex, 36, 72, True, "sport", 250, "rare", False),
    ("shrug", "Shrug", shrug, 0, 30, False, "greeting", 150, "common", False),
]

PARTS = (("head", "h", "ho", (0, 0, 0)), ("rightArm", "ra", "rao", (-5, 2, 0)), ("leftArm", "la", "lao", (5, 2, 0)),
         ("rightLeg", "rl", "rlo", (-1.9, 12, 0)), ("leftLeg", "ll", "llo", (1.9, 12, 0)))


def r4(v):
    return round(v, 4) + 0.0


def frame(tick, pose):
    m = {"tick": tick, "easing": "LINEAR", "turn": 0}
    for name, key, okey, pivot in PARTS:
        a = pose.get(key, (0, 0, 0))
        o = pose.get(okey, (0, 0, 0))
        m[name] = {"x": r4(pivot[0] + o[0]), "y": r4(pivot[1] + o[1]), "z": r4(pivot[2] + o[2]),
                   "pitch": r4(math.radians(a[0])), "yaw": r4(math.radians(a[1])), "roll": r4(math.radians(a[2]))}
    b = pose.get("b", (0, 0, 0))
    p = pose.get("pos", (0, 0, 0))
    m["torso"] = {"x": r4(p[0] / PX), "y": r4(p[1] / PX), "z": r4(p[2] / PX),
                  "pitch": r4(math.radians(b[0])), "yaw": r4(math.radians(b[1])), "roll": r4(math.radians(b[2]))}
    return m


def build(e):
    eid, name, fn, ret, length, loop = e[:6]
    end = ret + length if loop else length
    moves = [frame(t, fn(t)) for t in range(0, end + 1, STEP)]
    if loop:
        # the loop jumps from endTick back to returnTick: both must be the same pose (a full body turn = the same pose)
        last, first = moves[-1], [m for m in moves if m["tick"] == ret][0]
        for name2, _k, _o, _p in PARTS:
            last[name2] = dict(first[name2])
        keep_yaw = last["torso"]["yaw"]
        last["torso"] = dict(first["torso"])
        if abs(abs(keep_yaw - first["torso"]["yaw"]) - TAU) < 0.2 or keep_yaw == 0 and fn(end - 1).get("b", (0, 0, 0))[1] > 300:
            last["torso"]["yaw"] = r4(first["torso"]["yaw"] + TAU)
    return {"emote": {"isLoop": "true" if loop else "false", "returnTick": ret if loop else 0, "beginTick": 0, "endTick": end,
                      "stopTick": end, "degrees": False, "moves": moves}}


def main():
    if "--list" in sys.argv:
        for e in EMOTES:
            print(e[0], "|", e[1], "|", e[6], e[7], e[8], "free" if e[9] else "")
        return
    index = [{"id": i, "name": n, "icon": False, "author": "", "loop": True, "hidden": True} for i, n in OLD]
    cat = []
    for e in EMOTES:
        eid, name, _fn, _ret, _len, loop, tag, price, rarity, free = e
        with open(os.path.join(OUT, eid + ".json"), "w") as f:
            json.dump(build(e), f, separators=(",", ":"))
        index.append({"id": eid, "name": name, "icon": False, "author": "Sleep", "loop": loop})
        cat.append({"id": eid, "name": name, "loop": loop, "tags": [tag], "price": price, "rarity": rarity, "free": free})
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(index, f, indent=1)
    with open(os.path.join(ROOT, "emotes-catalogue.json"), "w") as f:
        json.dump({"count": len(cat), "emotes": cat}, f, indent=1)
    print("wrote", len(cat), "emotes")


if __name__ == "__main__":
    main()
