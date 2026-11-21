package dev.sleepclient.mixin;

import dev.sleepclient.mods.ZoomMod;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$zoom(net.minecraft.client.render.Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		double[] cut = dev.sleepclient.ext.KuboApi.camera();
		if (cut != null && cut.length > 5 && cut[5] > 0) {
			cir.setReturnValue((float) cut[5]); // a cut-scene sets its own lens
			return;
		}
		float m = ZoomMod.fovMultiplier() * dev.sleepclient.mods.FovChangerMod.multiplier() * dev.sleepclient.mods.CinematicAfkMod.fovMultiplier();
		float v = cir.getReturnValueF() * m;
		if (changingFov) {
			dev.sleepclient.mods.WaypointsMod.worldFov = v; // real projection FOV for the waypoint markers
		}
		if (m != 1F) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noTotemPop(net.minecraft.item.ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.sleepclient.ext.KuboApi.replaceTotem(stack) || dev.sleepclient.mods.TotemAnimationMod.hidden()) {
			ci.cancel();
		}
	}
}
