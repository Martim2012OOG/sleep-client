package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class GlintMixin {

	@Inject(method = "hasGlint", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$glint(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && dev.solclient.mods.TweaksPlusMod.shiny((ItemStack) (Object) this)) {
			cir.setReturnValue(true); // Shiny Potions (PvP Tweaks)
			return;
		}
		if (cir.getReturnValueZ() && (!OverlayMod.glintAllowed((ItemStack) (Object) this) || !dev.solclient.mods.EnchantmentGlintMod.allowed((ItemStack) (Object) this))) {
			cir.setReturnValue(false);
		}
	}
}
