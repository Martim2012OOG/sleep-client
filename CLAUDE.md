# Kubo Client – rules for anyone (human or AI) changing this code

## Inventory sync – the server is always the boss
These rules exist because the old anti-ghost (Ghost Item Fix) broke inventories: thrown splash potions stayed as ghosts
in the hotbar and an identical potion became invisible until a slot was clicked.

1. **Never cancel, delay, filter or overwrite inventory packets from the server** (`ScreenHandlerSlotUpdateS2CPacket`,
   `InventoryS2CPacket`, cursor / slot updates, `SetPlayerInventory`, ...). No mixin may `ci.cancel()` or change
   them.
2. **Never change the client inventory ourselves** to "fix" something: no predicting counts, no `setStack`, no moving
   items, no real slot clicks (pick up / put back) from a background mod. Real clicks use the client's prediction and
   go wrong with lag (the server's answer arrives after the mod already decided).
3. The only allowed way to fix a desync is to **ask the server for the real inventory**:
   - a `ClickSlotC2SPacket` sent directly (not through `interactionManager.clickSlot`) with an **outdated revision**,
     action `CLONE`, a valid hotbar slot, empty `modifiedStacks` and `ItemStackHash.EMPTY` as cursor. In survival a
     middle-click does nothing, and the wrong revision makes the server send everything again.
   - `CloseHandledScreenC2SPacket` when an item is stuck on the cursor with no screen open (the server puts it back).
   Never send `PICKUP`/`QUICK_MOVE` on slot -999 for this (it would drop the cursor item).
4. Anything time-based must wait for the **real ping** (`PlayerListEntry.getLatency()`), never a fixed number of ticks.
5. Before shipping a change near inventories, test: 1 potion in the hotbar + 1 identical in the inventory; several
   identical stacks; many throws in a row; high ping (for example with a lag tool / a far server).

## General
- Fabric 1.21.11, yarn mappings. Check names against the yarn mappings before using them.
- Every new mod needs an icon (`assets/sol_client/textures/gui/icon/<id>.png` + `.mcmeta`, and the id in `Theme.ICONS`)
  and Portuguese (Portugal) translations in `assets/sol_client/lang/pt_pt.tsv`.
- Do not remove features unless asked.

## Extensions (since v87)
- Optional / heavy mods live in `extensions/<name>`. Their jars are nested inside the Kubo jar (`include project(...)` in build.gradle); nothing is downloaded. An extension does nothing until the player installs it in Mods > Extensions (installed ids: config/kubo-extensions.txt); installed ones show up as a folder in All Mods.
- The core must **never** reference an extension class. Only use the hooks in `dev.solclient.ext.KuboApi`; add a new hook there if needed.
- Extension mixins use their own package `dev.kuboext.<name>.mixin` and their own mixin json.
