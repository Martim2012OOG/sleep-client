# Lovable prompt: Verity, the special pet (20 000 coins)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (base `/api/public/gc`).

---

Add a new shop item: **Verity**, a special pet.

- `id`: `verity`, `modelId`: `verity`, `name`: `Verity`, `category`: `Pets`
- `price`: `20000` coins
- `badge`: `SPECIAL`, `featured`: `true`
- `description`: "Your personal helper friend. Talks with AI, hears you in voice chat, has moods... don't make him angry. Needs a free Groq key (Locker > Verity > Edit)."
- `imageUrl`: the uploaded `images/cosmetics/verity.png` (in this zip)

In the shop, show items with `badge = "SPECIAL"` with a gold animated border and a "SPECIAL PET" ribbon, above the normal pets.
Buying, owning and equipping work like every other pet (`PUT /wardrobe/equip`, `GET` of a player's cosmetics lists `verity` while equipped), so other Caos players see him.

## Done when
- The shop lists Verity for 20 000 coins with the SPECIAL ribbon.
- After buying and equipping, `GET` of the player's cosmetics contains `verity`.
