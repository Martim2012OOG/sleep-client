package dev.solclient.mixin.overlay;

import dev.solclient.mods.HideInventoryModelMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Inventory Model: skips the player drawing in the inventory screens. */
@Mixin(InventoryScreen.class)
public abstract class HideInventoryModelMixin {

	@Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true,
			require = 0)
	private static void kubo$hideModel(DrawContext c, int x1, int y1, int x2, int y2, int size, float scale, float mouseX, float mouseY, LivingEntity entity,
			CallbackInfo ci) {
		if (HideInventoryModelMod.hides()) {
			ci.cancel();
		}
	}
}
