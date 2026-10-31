# Browser = floating panels (replaces the old 2D browser window)

The Browser mod now puts real browser panels **in the world** (Chromium via MCEF, already bundled in Kubo).

## Controls
- **B** (option "New Panel Key"): new panel in front of you. Option "Close All Key" is unbound by default.
- **Middle button** on a panel + turn your head: move it (scroll = closer / further). It turns to face you.
- **Left drag on the title bar**: move it too.
- **Right button on a bottom corner** + drag: scale (keeps the shape, hold **Shift** for free).
- **Scroll on the title bar / frame**: bigger / smaller.
- **Left click on the page**: click + typing mode (Esc or a click elsewhere leaves it). Ctrl+V / A / C / X work.
- **Title bar**: `<` back, `>` forward, `R` reload, address box (click, type, Enter), `X` close (Shift+X closes all).
- **Mouse side buttons**: back / forward.
- Typing mode silences every other Kubo hotkey (Keys.down returns false) and releases held movement keys.

## Options
New Panel Key, Close All Key, Home Page, Max Panels (4), Spawn Distance, Spawn Width, Handle Colour, "Close All" button.
Removed (no longer make sense): Open Window, Keep On Screen After Closing, Pinned Opacity, saved window position/rotation.

## Code
- `video/Panels`: list, create/close, browser lifecycle, world rendering (BEFORE_ENTITIES, same hook as Watch Party).
- `video/PanelInput`: aiming, clicks, move/scale, typing, address bar.
- `video/PanelBox`: geometry + title-bar layout.
- `mixin/MouseMixin` (+button hook) and new `mixin/KuboKeyboardMixin` (both `require = 0`).
- Deleted: `BrowserScreen`, `BrowserWindow`, `BrowserFrame`. Kept: `Mcef`, `BrowserTexture`, `BrowserKeys`.

## NOT compiled against Minecraft yet
Type-checked only against hand-written stubs. If the build complains, the likely spots are the vanilla signatures in the two
mixins (`Mouse.onMouseButton(long, MouseInput, int)`, `Keyboard.onKey(long, int, KeyInput)`, `Keyboard.onChar(long, CharInput)`),
`KeyBinding.unpressAll()` and `Quaternionf.rotationY` in `Panels.text(...)`.
