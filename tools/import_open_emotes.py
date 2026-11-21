#!/usr/bin/env python3
"""
Puts the open-licensed Emotecraft emotes into the client (replacing every emote it had).

Sources (data files only, nothing is executed):
  KosmX/Emotecraft-emotes  (CC0 1.0, public domain)   https://github.com/KosmX/Emotecraft-emotes
  JustARoy/Emotecraft-Emotes (MIT)                    https://github.com/JustARoy/Emotecraft-Emotes

Usage:  python3 tools/import_open_emotes.py <folder with kosmx/ and justaroy/>
Writes: assets/sleep_client/emotes/*.json + index.json + CREDITS.md, and emotes-catalogue.json (for the site).
Left out on purpose: files from the NSFW folder, Twerk, and the ones where the head comes off or the player dies
(headspin, headyeet, headball, into_pieces, do_eternal_rest_and_respawn), plus server-specific / duplicate ones.
"""
import glob, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "sleep_client", "emotes")

# source file, id, name, tag, price, rarity, free, license
K, J = "CC0-1.0", "MIT"
LIST = [
    ("kosmx/take the l.json", "take_the_l", "Take the L", "dance", 400, "epic", False, K),
    ("kosmx/orange justice.json", "orange_justice", "Orange Justice", "dance", 600, "legendary", False, K),
    ("kosmx/floss.json", "floss", "The Floss", "dance", 400, "epic", False, K),
    ("kosmx/floss_dance3.json", "floss_dance", "Floss Dance", "dance", 250, "rare", False, K),
    ("kosmx/club_penguin_dance.json", "club_penguin", "Club Penguin Dance", "dance", 400, "epic", False, K),
    ("kosmx/narutonew.json", "naruto", "Naruto", "dance", 350, "epic", False, K),
    ("kosmx/grace.json", "grace", "Grace", "dance", 250, "rare", False, K),
    ("kosmx/dab.json", "dab", "Dab", "funny", 150, "common", False, K),
    ("kosmx/the_dab.json", "dab_hard", "Dab Hard", "funny", 150, "common", False, K),
    ("kosmx/jump.json", "jump", "Jump", "funny", 0, "common", True, K),
    ("kosmx/tpose.json", "tpose", "T-Pose", "funny", 0, "common", True, K),
    ("kosmx/nervous.json", "nervous", "Nervous", "funny", 150, "common", False, K),
    ("kosmx/lookaround.json", "look_around", "Look Around", "funny", 150, "common", False, K),
    ("kosmx/penguin.json", "penguin", "Penguin", "funny", 150, "common", False, K),
    ("kosmx/shake.json", "head_shake", "Head Shake", "funny", 150, "common", False, K),
    ("kosmx/selfie.json", "selfie", "Selfie", "funny", 250, "rare", False, K),
    ("kosmx/Inspect.json", "inspect", "Inspect", "funny", 250, "rare", False, K),
    ("kosmx/item.json", "item_look", "Item Look", "funny", 150, "common", False, K),
    ("kosmx/jumping jacks.json", "jumping_jacks", "Jumping Jacks", "sport", 250, "rare", False, K),
    ("justaroy/squats.json", "squats", "Squats", "sport", 250, "rare", False, J),
    ("justaroy/kneel.json", "kneel", "Kneel", "sport", 150, "common", False, J),
    ("kosmx/sit.json", "sit", "Sit", "chill", 0, "common", True, K),
    ("kosmx/cool_sit.json", "cool_sit", "Cool Sit", "chill", 0, "common", True, K),
    ("kosmx/campfire_sit1.json", "ground_sit", "Ground Sit", "chill", 250, "rare", False, K),
    ("kosmx/lay_down5.json", "lay_down", "Lay Down", "chill", 250, "rare", False, K),
    ("kosmx/lejat.json", "lay_back", "Lay on Your Back", "chill", 250, "rare", False, K),
    ("kosmx/meditation_fly.json", "meditation", "Meditation", "chill", 250, "rare", False, K),
    ("kosmx/lean.json", "lean", "Lean", "chill", 150, "common", False, K),
    ("kosmx/hug.json", "hug", "Hug", "greeting", 0, "common", True, K),
    ("kosmx/heart.json", "hand_on_heart", "Hand on Heart", "greeting", 150, "common", False, K),
    ("kosmx/hearthands.json", "heart_hands", "Heart Hands", "greeting", 0, "common", True, K),
    ("kosmx/F.json", "pay_respects", "Pay Respects", "greeting", 0, "common", True, K),
    ("kosmx/bow1.json", "bow", "Bow", "greeting", 0, "common", True, K),
    ("kosmx/bow2.json", "deep_bow", "Deep Bow", "greeting", 150, "common", False, K),
    ("kosmx/meeting.json", "hello", "Hello", "greeting", 0, "common", True, K),
    ("kosmx/make_gestures.json", "gestures", "Gestures", "greeting", 150, "common", False, K),
]


def rnd(o):
    if isinstance(o, float):
        return round(o, 4)
    if isinstance(o, list):
        return [rnd(x) for x in o]
    if isinstance(o, dict):
        return {k: rnd(v) for k, v in o.items()}
    return o


def main(src):
    os.makedirs(OUT, exist_ok=True)
    for f in glob.glob(os.path.join(OUT, "*.json")):
        os.remove(f)  # every emote the client had before goes
    index, cat, credits = [], [], []
    for path, eid, name, tag, price, rarity, free, lic in LIST:
        d = json.load(open(os.path.join(src, path), encoding="utf-8"))
        e = d["emote"]
        if len(e.get("moves", [])) < 1:
            raise SystemExit("empty emote: " + path)
        loop = str(e.get("isLoop")).lower() == "true"
        author = re.sub(r"[§#]", "", str(d.get("author", ""))).strip() or "Unknown"
        with open(os.path.join(OUT, eid + ".json"), "w") as f:
            json.dump(rnd({"emote": e}), f, separators=(",", ":"))
        index.append({"id": eid, "name": name, "icon": False, "author": author, "loop": loop})
        cat.append({"id": eid, "name": name, "loop": loop, "tags": [tag], "price": price, "rarity": rarity, "free": free,
                    "author": author, "license": lic})
        credits.append("| %s | %s | %s | %s |" % (name, author, lic, ("https://github.com/KosmX/Emotecraft-emotes" if lic == K else
                                                                      "https://github.com/JustARoy/Emotecraft-Emotes")))
    json.dump(index, open(os.path.join(OUT, "index.json"), "w"), indent=1)
    json.dump({"count": len(cat), "emotes": cat}, open(os.path.join(ROOT, "emotes-catalogue.json"), "w"), indent=1)
    open(os.path.join(OUT, "CREDITS.md"), "w").write(
        "# Emote credits\n\nThese animations were made by the community for Emotecraft and published under open licenses. "
        "The client plays them unchanged (same keyframes).\n\n- KosmX/Emotecraft-emotes: CC0 1.0 (public domain dedication)\n"
        "- JustARoy/Emotecraft-Emotes: MIT (Copyright (c) JustARoy)\n\n| Emote | Author | License | Source |\n| --- | --- | --- | --- |\n"
        + "\n".join(credits) + "\n")
    print(len(LIST), "emotes written")


if __name__ == "__main__":
    main(sys.argv[1])
