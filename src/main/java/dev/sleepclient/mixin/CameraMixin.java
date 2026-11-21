package dev.sleepclient.mixin;

import dev.sleepclient.mods.FreelookMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {

	@ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), require = 0)
	private void sol$freelookRotation(Args args) {
		if (FreelookMod.active) {
			boolean front = MinecraftClient.getInstance().options.getPerspective().isFrontView();
			args.set(0, FreelookMod.yaw + (front ? 180F : 0F));
			args.set(1, front ? -FreelookMod.pitch : FreelookMod.pitch);
		}
	}

	/** A short shake from effects (Mace Effects...): a tiny wobble added to the rotation. */
	@ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), require = 0)
	private void sol$shake(Args args) {
		if (dev.sleepclient.fx.CameraShake.active()) {
			float yaw = args.get(0);
			float pitch = args.get(1);
			args.set(0, yaw + dev.sleepclient.fx.CameraShake.yaw());
			args.set(1, pitch + dev.sleepclient.fx.CameraShake.pitch());
		}
	}
}
