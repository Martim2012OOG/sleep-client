# Lovable prompt: 30 new emotes (replace the old 12) + new shop categories "Auras" and "Minimes"

Paste everything below the line into Lovable, in the project that hosts the Caos API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`). Attach `emotes-catalogue.json`, `cosmetics-catalogue.json` and the folder `images/cosmetics/` from this zip.

---

The Caos client was updated. Please change the shop data to match it. Nothing else about the API changes (same endpoints, same fields).

## 1. Emotes: replace the old list with these 30

Category `Emotes` must contain **exactly** these items and nothing else. The 12 old emotes (`amogus`, `ascend`, `becomehelicopter`, `brakedance`, `californiagirls`, `coolsit`, `frontflip`, `getthegriddy`, `headpat`, `helicopterhelicopter`, `hug`, `lean`) no longer exist in the client: stop returning them from `GET /shop`, `GET /shop?category=Emotes`, the admin screens and the crates. Do not delete what players own in the database, just stop returning those ids.

| id (= modelId) | name | tags | price (coins) | rarity | free | loop |
| --- | --- | --- | --- | --- | --- | --- |
| `take_the_l` | Take the L | dance | 400 | epic | no | yes |
| `griddy` | Griddy | dance | 400 | epic | no | yes |
| `floss` | Floss | dance | 300 | rare | no | yes |
| `dab` | Dab | funny | 150 | common | no | no |
| `wave` | Wave | greeting | 0 | common | yes | yes |
| `clap` | Clap | greeting | 0 | common | yes | yes |
| `salute` | Salute | greeting | 0 | common | yes | no |
| `facepalm` | Facepalm | funny | 150 | common | no | no |
| `tpose` | T-Pose | funny | 200 | rare | no | yes |
| `jumping_jacks` | Jumping Jacks | sport | 150 | common | no | yes |
| `pushups` | Push-ups | sport | 250 | rare | no | yes |
| `backflip` | Backflip | sport | 350 | epic | no | no |
| `spin` | Spin | dance | 200 | rare | no | yes |
| `sit` | Sit | chill | 0 | common | yes | yes |
| `laugh` | Laugh | funny | 150 | common | no | yes |
| `cry` | Cry | funny | 150 | common | no | yes |
| `point` | Point | greeting | 0 | common | yes | no |
| `victory` | Victory | sport | 200 | rare | no | yes |
| `zombie` | Zombie | funny | 200 | rare | no | yes |
| `robot` | Robot | dance | 300 | rare | no | yes |
| `headbang` | Headbang | dance | 250 | rare | no | yes |
| `nap` | Power Nap | chill | 300 | rare | no | yes |
| `bow` | Bow | greeting | 150 | common | no | no |
| `hype` | Hype | dance | 300 | rare | no | yes |
| `shuffle` | Shuffle | dance | 300 | rare | no | yes |
| `disco` | Disco Fever | dance | 350 | epic | no | yes |
| `squat_kick` | Squat Kick | dance | 400 | epic | no | yes |
| `helicopter` | Helicopter | funny | 500 | legendary | no | yes |
| `flex` | Flex | sport | 250 | rare | no | yes |
| `shrug` | Shrug | greeting | 150 | common | no | no |

Rules (same as before):
1. `id` and `modelId` are exactly the lower-case id above (the client matches emotes by it). `category` is exactly `"Emotes"`. `imageUrl` may be `null`: the client draws the emote on a 3D mannequin by itself.
2. Items with `free = yes` are owned by every user from the start (`owned: true`, price 0). The others are bought with the normal `POST /shop/buy`.
3. Crates may give these emotes as prizes by `rarity` (already owned = 40 % of the price back as coins, as before).
4. Players who owned an old emote: give them back its price in coins once (optional, but nice), since it no longer exists.
5. Website shop page: show the emotes as a grid with name, tag and price, like now.

## 2. New categories: `Auras` (12 items) and `Minimes` (4 items)

Add these 16 items to the shop. They work exactly like the existing wearable cosmetics (hats, wings...): bought with `POST /shop/buy`, equipped with the normal equip endpoint, returned in the player's worn items. **One item per category can be worn at a time** (an aura and a minime together is fine). The picture of each item is `images/cosmetics/<id>.png` in this zip (96x96 PNG, pixel art: show it with `image-rendering: pixelated`).

| id (= modelId) | name | category | price (coins) | rarity |
| --- | --- | --- | --- | --- |
| `aura_flame` | Inferno Aura | Auras | 900 | epic |
| `aura_frost` | Frost Aura | Auras | 900 | epic |
| `aura_heart` | Love Aura | Auras | 700 | rare |
| `aura_star` | Starlight Aura | Auras | 800 | rare |
| `aura_note` | Melody Aura | Auras | 700 | rare |
| `aura_cash` | Cash Aura | Auras | 1200 | legendary |
| `aura_bolt` | Storm Aura | Auras | 1000 | epic |
| `aura_leaf` | Autumn Aura | Auras | 600 | rare |
| `aura_bubble` | Bubble Aura | Auras | 600 | rare |
| `aura_rune` | Arcane Aura | Auras | 1200 | legendary |
| `aura_zzz` | Sleepy Aura | Auras | 500 | rare |
| `aura_skull` | Soul Aura | Auras | 1000 | epic |
| `minime_shoulder` | Shoulder Minime | Minimes | 1000 | epic |
| `minime_head` | Head Minime | Minimes | 1000 | epic |
| `minime_buddy` | Buddy Minime | Minimes | 1400 | legendary |
| `minime_backpack` | Backpack Minime | Minimes | 1200 | legendary |

Rules:
1. `id` and `modelId` exactly as above, `category` exactly `"Auras"` or `"Minimes"` (the client finds the effect by the id; the category is what makes the two new sections show up in the client's Locker and shop by themselves).
2. `GET /shop` (category list) must now also return the sections `Auras` and `Minimes`, and `GET /shop?category=Auras` / `?category=Minimes` return their items.
3. Website: add the two sections to the shop page and its category filter, next to Headwear / Wings / Pets. Short descriptions you can use: Auras = "Animated particles around your player (flames, snow, stars, hearts...)"; Minimes = "A mini copy of you, with your own skin, that copies everything you do".
4. Crates may give them as prizes by `rarity`.
5. Nothing is removed from the other cosmetic categories.

## 3. Kill Effects: 9 new ones

The client now has 9 more 3D kill effects (33 in total). If the shop already has the category `Kill Effects`, add these 9 items to it, the same way as the existing ones (bought with `POST /shop/buy`, equipped with the normal equip endpoint, one equipped at a time). If the shop does **not** sell kill effects, skip this section: the client then lets everyone use all of them for free. Pictures: `images/killfx/<id>.png` (128x128, transparent). Data also in `killfx-catalogue.json`.

| id (= modelId) | name | price (coins) | rarity |
| --- | --- | --- | --- |
| `thunder` | Thunder Strike | 600 | rare |
| `anvil` | Anvil Drop | 600 | rare |
| `grave` | Rest in Peace | 800 | epic |
| `balloons` | Balloon Ride | 800 | epic |
| `ice` | Ice Shatter | 800 | epic |
| `glove` | Knockout | 1000 | epic |
| `portal` | Void Portal | 1000 | epic |
| `swords` | Sword Rain | 1200 | legendary |
| `bed` | Sweet Dreams | 1200 | legendary |

`id` and `modelId` must be exactly the lower-case id above and `category` exactly `"Kill Effects"`. The 24 existing kill effects stay as they are.

Quick check: `GET /shop?category=Emotes` returns exactly 30 items and the 5 free ones (`wave`, `clap`, `salute`, `sit`, `point`) come back `owned: true` for a brand new account; `GET /shop?category=Auras` returns 12 items and `?category=Minimes` 4; buying and equipping `aura_flame` makes it appear in the player's worn items; every other shop item is unchanged.
