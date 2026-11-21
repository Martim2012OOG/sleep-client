package dev.sleepclient.mixin;

import dev.sleepclient.mods.ZoomMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x: o calculo do FOV passou do GameRenderer.getFov para Camera.calculateFov(float). Mesma logica do zoom/FOV changer. */
@Mixin(net.minecraft.client.Camera.class)
public class CameraFovMixin {

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		double[] cut = dev.sleepclient.ext.KuboApi.camera();
		if (cut != null && cut.length > 5 && cut[5] > 0) {
			cir.setReturnValue((float) cut[5]); // a cut-scene sets its own lens
			return;
		}
		float m = ZoomMod.fovMultiplier() * dev.sleepclient.mods.FovChangerMod.multiplier() * dev.sleepclient.mods.CinematicAfkMod.fovMultiplier();
		float v = cir.getReturnValueF() * m;
		dev.sleepclient.mods.WaypointsMod.worldFov = v; // FOV real da projecao para os marcadores de waypoints
		if (m != 1F) {
			cir.setReturnValue(v);
		}
	}
}
