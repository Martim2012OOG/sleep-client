package dev.solclient.mixin;

import dev.solclient.mods.FreelookMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

	/** While Freelook is active the mouse rotates the camera instead of the player. */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$freelook(double dx, double dy, CallbackInfo ci) {
		if ((Object) this == Minecraft.getInstance().player) {
			dev.solclient.mods.CinematicAfkMod.pokeLook(dx, dy); // moving the mouse ends the AFK camera
		}
		if (FreelookMod.active && (Object) this == Minecraft.getInstance().player) {
			if (FreelookMod.rotate(dx, dy)) {
				ci.cancel();
			}
		}
	}
}
