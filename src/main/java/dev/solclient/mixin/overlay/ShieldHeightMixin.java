package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** equipProgress is the 4th float argument of renderFirstPersonItem. */
@Mixin(targets = "net.minecraft.client.render.item.HeldItemRenderer")
public class ShieldHeightMixin {

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0)
	private float sol$shield(float equip) {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		if (p != null && p.isBlocking()) {
			return dev.solclient.mods.LowShieldMod.apply(OverlayMod.shieldEquip(equip));
		}
		return equip;
	}
}
