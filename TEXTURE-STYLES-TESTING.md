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
