package dev.kuboext.utilities;

import dev.solclient.mods.QuickPhrasesMod;
import dev.solclient.mods.SlotLockMod;
import dev.solclient.mods.AutoRespawnMod;
import dev.solclient.mods.SlowMouseMod;
import dev.solclient.mods.HotkeysMod;
import dev.solclient.mods.InventoryTweaksMod;
import dev.solclient.mods.ContainerPreviewMod;
import dev.solclient.mods.MonstersInTheClosetMod;
import dev.solclient.mods.ToolsItemsOptimizerMod;
import dev.solclient.mods.ChunkBordersMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Caos extension "Utilities": Quick Phrases, Slot Lock, Auto Respawn, Slow Mouse, Hotkeys, Inventory Tweaks, Container Preview, Monsters in the Closet, Tools Optimizer and Chunk Borders. */
public class UtilitiesExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new QuickPhrasesMod());
		api.addMod(new SlotLockMod());
		api.addMod(new AutoRespawnMod());
		api.addMod(new SlowMouseMod());
		api.addMod(new HotkeysMod());
		api.addMod(new InventoryTweaksMod());
		api.addMod(new ContainerPreviewMod());
		api.addMod(new MonstersInTheClosetMod());
		api.addMod(new ToolsItemsOptimizerMod());
		api.addMod(new ChunkBordersMod());
		ClientTickEvents.START_CLIENT_TICK.register(c -> SlotLockMod.blockDrop());
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> ContainerPreviewMod.addLines(stack, lines));
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient() && ToolsItemsOptimizerMod.instance != null && ToolsItemsOptimizerMod.instance.enabled) {
				ToolsItemsOptimizerMod.instance.onUse(player, hand);
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		api.onAttack(e -> {
			if (ToolsItemsOptimizerMod.instance != null && ToolsItemsOptimizerMod.instance.enabled) ToolsItemsOptimizerMod.instance.onAttack(e);
		});
	}
}
