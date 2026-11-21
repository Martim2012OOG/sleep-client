# Lovable prompt: Kubo emotes + new cosmetics (no pictures needed)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`).
Everything is in `lovable-pack.zip`: this prompt, `emotes-catalogue.json` (758 emotes), `cosmetics-catalogue.json` (one entry per item, colours grouped) and `images/cosmetics/*.png` (pictures, 96x96, transparent) to upload as the item pictures.

---

The Kubo client now draws every emote **by itself**: a grey mannequin doing the emote (like Lunar). So **emotes must NOT need a picture any more**: no `imageUrl`, no upload, no AI-generated logo. Please remove any "image required" check for items of category `Emotes` (in the database, the admin screens and the shop API), and never try to generate or fetch pictures for them.

## 1. Emotes in the shop

Create one shop item per entry of the attached `emotes-catalogue.json` (`emotes[]`). The client already contains the animation of each one, so the site only sells and remembers who owns what.

| field in the file | what it is | use it as |
| --- | --- | --- |
| `id` | lower case slug, e.g. `backflip` | the item `id` **and** `modelId` (must be exactly this: the client matches emotes by it) |
| `name` | display name | `name` |
| `tags` | `dance`, `multiverse`, `sport`, `movement`, `funny`, `horror`, `couple`, `nature`, `prop` | shown as small chips; used for filters |
| `price` | coins | `price` / `finalPrice` (keep the discounts you already have) |
| `rarity` | `common` / `rare` / `epic` | `rarity` (used by crates) |
| `free` | true for 16 basic emotes | give them to **every** user automatically (owned from the start) |
| `loop` | the emote repeats until stopped | informational |

Rules:
- `category` is exactly `"Emotes"` for all of them. They stay in `GET /shop?category=Emotes` and in `GET /shop` (category `All`), with the same item fields as every other item (`id, name, category, modelId, price, finalPrice, discountPct, owned, equipped, featured, colors, badge`). `imageUrl` may be `null` or missing.
- `modelId` = `id`. Do not rename ids later: players' wheels and other players' emotes use them.
- **Do not create any item that is not in the file.** 42 emotes were removed on purpose (not suitable for under 18) and the client has them hidden; if you ever see an id that is not in the file, ignore it.
- Buying works as for every other item (`POST /shop/buy`, 402 when there are not enough coins, the item becomes `owned: true`). Nothing to equip on the server: the client keeps the emote wheel on the player's PC.
- Crates (`POST /shop/crate/open`) may give emotes as prizes: use `rarity`. If the player already owns it, give back 40 % of the price as coins (as before).
- Shop page of the website: show emotes as a list/grid with the **name + tags + price** (no picture). If you want a preview, a coloured tile with the first letter is enough.

## 2. The new 3D cosmetics (hats, wings, tails, pets)

The client now ships 167 cosmetics. Create the shop items from `cosmetics-catalogue.json` (`cosmetics[]`):
- `id` → item `id` and `modelId` (exactly as given; the client loads the 3D model by it).
- `category` is one of `Headwear`, `Wings`, `Tails`, `Bodywear`, `Pets`; keep these names (the Locker filters by them). Add `Tails` as a new category if it does not exist.
- `price` and `rarity` are suggestions: change them freely.
- `live_pet: true` means the pet walks and flies around the player (client side only, nothing to do on the site).
- A picture is optional: the client draws its own 3D picture when `imageUrl` is empty. If you want pictures on the website, upload what you like (the files `src/main/resources/assets/sleep_client/textures/cosmetics/thumb/<id>.png` of the client repo are ready-made 96x96 pictures with transparent background).
- When a player wears one, `GET /players/lookup` must return it in `cosmetics` by category as already done for the other wearable items (one item per category).

## 2b. Items with several colours (ONE item + colour picker)

Entries of `cosmetics-catalogue.json` that have a `variants` array (Among Us Hat, Halo, Bucket Head, Short Hat, Mini Crewmate, Elven Ranger Wings, Ram Horns, Fox Ears) are **a single shop item**, not one item per colour:
- Create ONE item (`id` = the entry id, which is also the first variant) and keep the variants as its colours: `variants[] = { id, name, hex, image }`. In the item page, show a row of colour dots (`hex`) like the Laby shop: clicking a dot changes the preview picture (`image`) and the chosen colour.
- Buying the item gives **all** its colours.
- Equip: accept an optional `variantId` in `PUT /wardrobe/equip` (`{ "itemId": "...", "variantId": "..." }`, `variantId` must be one of the item's variants, default the first) and remember it per user and item.
- `GET /players/lookup` keeps returning `cosmetics` as it does now (category → item id) and adds a second map `cosmeticVariants` (category → `variantId`) for the items that have colours.
- `GET /shop` items with colours get `variants: [{ id, name, hex }]` and `variantId` (the colour the user picked, or the first).
(The client already lets the player pick the colour in the Locker and keeps it on the player's PC; reading `variantId` / `cosmeticVariants` from the site will be added on the client later.)

## 3. Quick check

1. `GET /shop?category=Emotes` returns 758 items, each with `modelId` = `id`, no picture required, and the 16 free ones come back with `owned: true` for a brand new account.
2. Buy an emote with enough coins: `owned` becomes true and the coins go down; with too few coins: 402 and nothing changes.
3. `GET /shop` still returns every old item unchanged.
