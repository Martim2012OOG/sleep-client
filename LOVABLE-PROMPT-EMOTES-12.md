# Lovable prompt: ONLY these 12 emotes in the shop (replace the old emote list)

Paste everything below the line into Lovable, in the project that hosts the Caos API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`). The data is also in `emotes-catalogue.json` (attach it).

---

The Caos client now ships **only 12 emotes** (the old 758 were removed). Please change the shop so that category `Emotes` contains **exactly** these items and nothing else:

| id (= modelId) | name | tags | price (coins) | rarity | free | loop |
| --- | --- | --- | --- | --- | --- | --- |
| `amogus` | Amogus | funny | 150 | common | no | yes |
| `ascend` | Ascend | funny | 300 | epic | no | no |
| `becomehelicopter` | Become Helicopter | funny | 150 | common | no | yes |
| `brakedance` | Brakedance | dance | 250 | rare | no | yes |
| `californiagirls` | California Girls | dance | 250 | rare | no | yes |
| `coolsit` | Cool Sit | movement | 0 | common | yes | yes |
| `frontflip` | Front Flip | sport | 200 | rare | no | no |
| `getthegriddy` | Get the Griddy | dance | 200 | rare | no | yes |
| `headpat` | Headpat | couple | 0 | common | yes | yes |
| `helicopterhelicopter` | Helicopter Helicopter | funny | 150 | common | no | yes |
| `hug` | Hug | couple | 0 | common | yes | yes |
| `lean` | Lean | funny | 150 | common | no | yes |

Rules:
1. **Remove every other item of category `Emotes`** from the shop and from `GET /shop`, `GET /shop?category=Emotes`, admin screens and crates. Do not delete what players already own in the database, just stop returning the old ids. (If a player owned an old emote, ignore it; the client no longer has it.)
2. `id` and `modelId` must be exactly the lower-case id above (the client matches emotes by it). `category` is exactly `"Emotes"`. No picture needed: `imageUrl` may be `null` (the client draws a 3D mannequin by itself). Remove any "image required" check for emotes.
3. Items with `free = yes` are owned by **every** user from the start (`owned: true`, price 0). The others are bought with the normal `POST /shop/buy` (402 when there are not enough coins; afterwards `owned: true`). Keep the discounts you already have.
4. Crates (`POST /shop/crate/open`) may give these emotes as prizes using `rarity`; if the player already owns it, give back 40 % of the price as coins (as before).
5. **It must be easy to add more emotes later**: keep one place (a JSON/seed file or the admin "add item" screen) where I can add a new emote by giving `id`, `name`, `tags`, `price`, `rarity`, `free`, `loop`. New emotes need nothing else on the site: the client will contain the animation by the same `id`.
6. Shop page of the website: show emotes as a grid/list with name, tags and price (a coloured tile with the first letter is enough as a picture).

Quick check: `GET /shop?category=Emotes` returns exactly 12 items; the 3 free ones come back with `owned: true` for a brand new account; buying a paid one lowers the coins and sets `owned: true`; `GET /shop` still returns every non-emote item unchanged.
