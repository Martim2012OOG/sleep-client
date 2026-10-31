# Shop capes: what was tested and what needs the game

Tested without Minecraft (`./gradlew test`, class `CapeMediaLogicTest`):
- only https media is accepted; formats are told apart (PNG / JPG / GIF / WebP / video)
- a plain PNG atlas keeps the old cape renderer (`capeUrl`); panels, JPG atlas, GIF and video go through `CapeMedia`
- front falls back to back; one shared key per cape (one decoder / texture for all players wearing it)
- the page that lays the cape atlas out (panel boxes, one decoder when both sides are the same video, one clock when they differ, escaped addresses)
- picture composition: cropped to fill (no stretching), 2:1 atlas

NOT tested (needs Minecraft, please check in game):
1. Panels cape with two PNG/JPG pictures: back reads correctly from behind, front (inside) is not mirrored.
2. Video cape (MP4 H.264 and WebM): plays muted, loops, both sides move together. First time only: the video engine (MCEF, ~100 MB) downloads, the video starts by itself afterwards.
3. The browser texture is the right way up (not upside down) on the cape.
4. Another player with the cape (needs a second client): visible on them, including on an offline server.
5. Unequip: the personal cape comes back, the video stops (the page closes ~4 s after nobody sees it).
6. Several players with different video capes: at most 3 play at once, the rest wait.
7. FPS with 1-3 animated capes in view.
Setting: Kubo > "Animated Capes (video)" turns animated capes off.
Not provided (by the site's contract): frame-exact sync between different clients.

## v43: Capes & Skins tabs, owned capes, 3D preview (needs the game)
- Menu > Capes & Skins now has two tabs: Skins | Capes. Capes lists every cape you bought, with your player in 3D (back view) and Equip / Unequip.
- Shop picture of a cape (flat image): cropped onto the cape panels. If the picture is already a real cape atlas (2:1, multiple of 64, empty corner) it is used as it is.
- Check in game: 1) bought cape appears in the Capes tab; 2) picking it turns the preview to show the cape; 3) Equip puts it on you at once (also in the world);
  4) Unequip removes it; 5) the Locker > Inventory > Cosmetics preview shows capes too.
- Title screen (no world): the 2D fallback preview does not draw capes, only the picture in the corner.

## v44: No Hurt Cam fix + new mods (needs the game)
- No Hurt Cam: 3 layers (tilt method under 2 names + hurtTime = 0 while the world view is set up). Test: enable it, get hit, no tilt.
- New: TPS Display, Smooth Camera, Aspect Ratio, No Render, Hit Bubbles, Flying Particles, Fly Cubes, Line Glyphs.
- Aspect Ratio and No Render hook renderer methods that could not be checked for 1.21.11 here: if they do nothing, the log says which mixin was skipped.

## v45: Crystal Tweaks + Inventory Tweaks (needs the game)
- Crystal Tweaks: Bounce 0% = crystal stays still. Inventory Tweaks: open inventory, press R (changeable) = sorts the 27 backpack slots.

## v46: Spotify mod (needs the game + a Spotify app)
- Mod "Spotify": settings -> create a free app at developer.spotify.com/dashboard, add Redirect URI http://127.0.0.1:8898/callback, paste its Client ID, press "Connect Spotify".
- Music plays in the Spotify app (PC/phone); the mod shows cover/title/artist/progress and controls it. Controls and playlists need Spotify Premium.
- Move it with the HUD editor like any other HUD.

## v47: Browser window in the game (needs the game; first use downloads the browser engine ~100 MB)
- Mod "Browser": press B (changeable) -> a Chromium window. Drag toolbar/border = move; drag a corner = resize (Shift keeps shape); drag the round handle = rotate (Shift = 15 deg steps);
  scroll on the frame = bigger/smaller; click/type/scroll inside the page. P = keep on screen after Esc, X = close.
- Keyboard/mouse go to the page through standard CefBrowser AWT events: if a key or the wheel direction is wrong, tell me which.
- Native PC apps cannot be embedded; use their web version (Spotify Web, Discord, WhatsApp Web...).

## v48: browser engine download progress
- In the browser window: stage text + bar with % (or a moving block when the engine gives no number), "About 100 MB, only the first time", warning if nothing moves for 45 s, error text + "Try again" (click the page).
- With the window closed: a small bar at the top of the screen while the engine downloads (only if the Browser mod is on).
- If the bar says "Waiting to start" or the engine is missing, tell me what it shows.

## v105: lookup keys (needs the game)
- `/players/lookup` now sends UUIDs as lower case without hyphens and reads the answer keys the same way (any case / hyphens accepted). Before, only two spellings were tried.
- Checked in the code for the shop-cape task (not re-tested in game): GET /shop?category=Capes, POST /shop/buy, PUT /wardrobe/equip, DELETE /wardrobe/equip/Capes, lookup of every visible player
  (not only friends) with the offline name -> account UUID bridge (`Presence.backendId`), `capeUrl` + `capeMedia` (atlas / panels, image / GIF / video), cache per cape.
- Video capes use ONE shared Chromium page per cape (MCEF), never one per player: 512x256, 24 fps, at most 3 at the same time, closed when nobody sees them. Not ffmpeg.
- To check in game: capes still show on other players after this change (a bad key would make every cape disappear).

## v122: HD atlas capes + video capes (needs the game)
Test in game: 1) PNG 64x32; 2) PNG 2048x1024 (shop atlas like the lightning / galaxy ones); 3) GIF 2:1; 4) MP4 2:1; 5) WebM 2:1;
6) another player seeing your cape on an offline server; 7) unequip: cape disappears, video decoder stops after ~4 s.
First MP4/WebM: ffmpeg is found or downloaded once (log line "[Kubo] cape video: ..."), then the video starts alone. Max 4 decoders.
Panels-layout videos still use the browser fallback (MCEF).
