package dev.solclient.mixin.perf;

import dev.solclient.mods.ArmorStandPosesMod;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armor Stand Poses: draws the chosen Bedrock pose. Only the picture changes; the real armor stand is never touched. */
@Mixin(ArmorStandRenderer.class)
public class ArmorStandPoseMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V", at = @At("TAIL"), require = 0)
	private void sleep$pose(ArmorStand stand, ArmorStandRenderState state, float tickProgress, CallbackInfo ci) {
		float[][] p = ArmorStandPosesMod.poseFor(stand.getUUID());
		if (p == null) {
			return;
		}
		state.headPose = ArmorStandPosesMod.angle(p[0]);
		state.bodyPose = ArmorStandPosesMod.angle(p[1]);
		state.leftArmPose = ArmorStandPosesMod.angle(p[2]);
		state.rightArmPose = ArmorStandPosesMod.angle(p[3]);
		state.leftLegPose = ArmorStandPosesMod.angle(p[4]);
		state.rightLegPose = ArmorStandPosesMod.angle(p[5]);
		state.showArms = true;
	}
}
