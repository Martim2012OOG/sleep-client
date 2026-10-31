package dev.solclient.mixin;

import dev.solclient.mods.ZoomMod;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$zoom(net.minecraft.client.render.Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		float m = ZoomMod.fovMultiplier() * dev.solclient.mods.FovChangerMod.multiplier() * dev.solclient.mods.CinematicAfkMod.fovMultiplier();
		float v = cir.getReturnValueF() * m;
		if (changingFov) {
			dev.solclient.mods.WaypointsMod.worldFov = v; // real projection FOV for the waypoint markers
		}
		if (m != 1F) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noTotemPop(net.minecraft.item.ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.solclient.ext.KuboApi.replaceTotem(stack) || dev.solclient.mods.TotemAnimationMod.hidden()) {
			ci.cancel();
		}
	}
}
