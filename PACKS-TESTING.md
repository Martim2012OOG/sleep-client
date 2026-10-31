# Resource packs (Kubo + Modrinth): what was tested and what needs the game

## What changed (v105)
- Menu > Resource Packs now has a source switch at the top: **Modrinth** (as before: filters, install) | **Kubo** (new, packs of the site).
- Kubo source: `GET /resource-packs[?q=]` (public, no session). Grid with icon, name, owner and tags + search (same search box, 350 ms debounce).
  Detail: banner on top, description, credits, social buttons (Discord / YouTube / Twitter / Website), Install button.
  A social value that is a URL (or a bare address like `discord.gg/x`) opens in the system browser; anything else (a Discord user name, `@handle`) is copied to the clipboard.
- Install: async download with progress bar to `.minecraft/resourcepacks/kubo-<id>-<version>.zip` (written as `.part`, moved only after the SHA-1 matches;
  wrong SHA-1 deletes the file and shows the error as a tooltip on the red "Retry" button). Then `POST /resource-packs/<id>/download` (counter only, errors ignored),
  then the pack is put on top of the enabled packs and resources reload (same calls as the Pack Profiles mod: scanPacks / setEnabledProfiles / refreshResourcePacks).
- Button states: Install / % / Retry / Update / Activate / Active. Update = a newer version, or the same file with a different SHA-1 from the site.
  "Older version" is known from `config/kubo-resource-packs.json` (id -> last installed file), not guessed from file names, so `faithful` never mixes up with `faithful-32x`.
  Old version files are NOT deleted (only replaced in the enabled list).
- Icons / banners: downloaded and decoded off the render thread (PNG, JPG, first frame of GIF; WebP is not supported -> letter placeholder), scaled to 128 / 640 px wide,
  cached by address (max 80), all freed when the screen closes.
- Capes: `/players/lookup` now sends and reads UUIDs as lower case without hyphens (`UuidKey`). Nothing else in the cape code was changed (see CAPES-TESTING.md).

## Tested without Minecraft
Classes `KuboPackLogicTest` and `UuidKeyTest` (`./gradlew test`). They were also run here with a tiny JUnit stand-in on JDK 21: 13 / 13 passed.
- file name `kubo-<id>-<version>.zip`, and an id / version like `../../x` can never leave the folder
- only https downloads, SHA-1 must be 40 hex digits; real SHA-1 of a file (known value of "abc")
- install state: not installed / installed / update (new version, same version with another hash) / recorded file deleted by hand / recorded name with `..`
- links: URL opens, bare address gets https, Discord user names (even `cool.dev`) are copied, `javascript:` / `file:` / `ftp:` never open
- UUID keys: any case, with or without hyphens, rejects names and wrong lengths

## NOT tested (needs Minecraft; the project could not be compiled here: no Gradle / no network)
**Please run `./gradlew build` first.** The new/changed Minecraft code only reuses calls that already exist elsewhere in Kubo, and has no syntax errors,
but it was never compiled against the 1.21.11 yarn mappings.
1. The switch Modrinth | Kubo, search box on both, layout at a small and a big GUI scale.
2. Kubo grid: icons, tags, scrolling; detail: banner (cropped, not stretched), description, credits, scrolling.
3. Install a real pack: progress bar, file name in `resourcepacks/`, pack appears ON in Options > Resource Packs and the textures change. Check it is on top (highest priority).
4. Break the SHA-1 (edit the download or the value): file must disappear, red "Retry", tooltip says the SHA-1 does not match.
5. Close and open the screen: the pack shows Active; turn it off in Options: it shows Activate; click it: it turns on again.
6. Publish a new version on the site: the button shows Update, installing it replaces the old version in the enabled list.
7. Social buttons: Discord invite opens the browser; a plain Discord user name is copied (note at the bottom); YouTube / Twitter / Website open.
8. Offline (no internet): "Could not reach the Kubo site" and no crash; the download counter failing must not show an error.
9. Capes in the world after the UUID change: yours and another player's, and on an offline-mode server.

## Animated Packs (v110): animated resource packs even when another mod blocks them
New mod **Animated Packs** (`AnimatedPacksMod`, on by default, Visual). Written from scratch, no third-party code.
It does not change how Minecraft draws; it puts other mods' "turn animations off" switches back to "animate":
- **Sodium**: "Animate only visible textures" is turned OFF (live, read by Sodium every tick).
- **Sodium Extra**: the whole Animations page (animations, water, lava, fire, portal, block animations, sculk sensor) is turned ON.
- Runs on the first tick and then every 2 s (also in menus), so it also wins when the other mod loads its config after Kubo,
  or when the player saves the other mod's settings screen.
- If the live switch can't be found (new mod version with other names) it fixes `config/sodium-options.json` / `config/sodium-extra-options.json`
  instead (needs a restart; the status line says so).
- Turning the mod off puts every switch back exactly as it was. Settings: Unblock Sodium / Unblock Sodium Extra / "Check now" button + status.

### Tested without Minecraft
Compiled against small stubs on JDK 21, and the reflection part was run against fake Sodium / Sodium Extra option classes:
only the right switches change (others untouched), a second pass keeps the first original value, restore puts everything back, a missing mod returns "not found".

### NOT tested (needs Minecraft + the real mods)
**Please run `./gradlew build` first** (never compiled against the real 1.21.11 mappings; the mod only uses Fabric Loader / Fabric API calls already used elsewhere in Kubo).
1. With Sodium installed and "Animate only visible textures" ON in its settings: open Kubo > Animated Packs is ON, the Sodium checkbox must show OFF and an animated pack (water, fire, lava, animated blocks / items) must keep animating when not in the centre of the screen.
2. With Sodium Extra: turn "Animations" OFF in its page, wait 2 s: it must come back ON and the textures animate.
3. Turn Animated Packs OFF in Kubo: the switches go back to what they were.
4. Without Sodium / Sodium Extra: status "No blocking mods found", nothing else happens.
5. Check the real field names in the current Sodium / Sodium Extra for 1.21.11 (`animateOnlyVisibleTextures`, `animationSettings`); if they changed, the file fallback is used.
