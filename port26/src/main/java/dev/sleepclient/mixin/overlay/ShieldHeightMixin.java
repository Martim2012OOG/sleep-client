package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** equipProgress is the 4th float argument of renderFirstPersonItem. */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
public class ShieldHeightMixin {

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0)
	private float sol$shield(float equip) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && p.isBlocking()) {
			return dev.sleepclient.mods.LowShieldMod.apply(OverlayMod.shieldEquip(equip));
		}
		return equip;
	}
}
