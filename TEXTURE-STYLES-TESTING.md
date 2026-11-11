# Texture Styles (v133)

New mod **Texture Styles** (Visual). Pick ready-made community styles PER ITEM; with the mod ON they are active.

- Categories: swords, pickaxes, axes, totems, pearls, crystals, obsidian, anchors, XP bottles, golden apples, mace & trident, bows, shields.
- Every style is a resource pack from Modrinth, searched live for the game version (sorted by downloads / relevance, text search). A few hand-picked ones show first (yellow dot).
- Nothing is bundled in Kubo: the player downloads each pack (SHA-1 checked, cached in `config/kubo-styles-cache/`), so each author's licence is respected.
- Only the files of the chosen category are copied into ONE generated pack `resourcepacks/kubo-styles-<hash>.zip` (plus the models / textures those files point to). The original packs are never changed.
- Choices are saved in `config/kubo-texture-styles.json`. They are applied when the screen is closed (if the mod is ON) or when the mod is turned ON. Turning it OFF removes the generated pack, choices stay.

## NOT tested (needs Minecraft; no Gradle / network here): run `./gradlew build` first
1. Mods > Texture Styles > Open: tabs, grid, scroll (scissor), search box, sort button, Done.
2. Choose a totem style + a sword style, close: pack appears ON in Options > Resource Packs, textures change.
3. Model-based packs (katana / knives): the sword shows the right model (models + textures are followed from the JSON files).
4. Turn the mod OFF: pack removed; ON again: the same pack comes back without downloading.
5. Offline: "Could not reach Modrinth", no crash.

## v134 change: inside the settings page, pictures only
- The "Open" button and the big window are gone. Texture Styles is drawn inside the mod's own settings page (`TextureStylesPanel`, an `Option.Custom` block).
- Pick an item chip, then pick a picture of that item's texture. No pack names / pack icons are shown; the author is only credited under the grid for the active style.
- The first time a picture is on screen the style is downloaded in the background (cached) and only the item's texture is read from it. Packs with no texture for that item disappear from the grid.
- Click a picture = used at once (the generated pack is rebuilt). First tile = vanilla.

## Speed fix (final v6)
- Cause of the slow pictures: Modrinth's file server answers HTTP 501 to "last N bytes" range requests (`bytes=-66000`), so `RemoteZip` always failed and every picture downloaded its whole pack. It now asks for one byte to learn the size and then uses explicit positions. Checked against live packs: table of contents in ~0.3 s, one texture in ~25 ms.
- The files of a whole page of packs are looked up with one request (`/v2/versions?ids=[...]`, from the search's `latest_version`) instead of one request per pack; the plain list of a category is cached on disk for 6 hours.
- The generated pack is built from ranged reads too (only the chosen item's files), and unchanged items are not fetched again.
- In a world, with the Sleep menu open, the game no longer reloads on every click: the pack is built in the background and turned on once, when the menu closes. NOT tested in game.
