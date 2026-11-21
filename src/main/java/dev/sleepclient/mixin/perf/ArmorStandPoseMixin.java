package dev.sleepclient.mixin.perf;

import dev.sleepclient.mods.ArmorStandPosesMod;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armor Stand Poses: draws the chosen Bedrock pose. Only the picture changes; the real armor stand is never touched. */
@Mixin(ArmorStandEntityRenderer.class)
public class ArmorStandPoseMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/ArmorStandEntity;Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;F)V", at = @At("TAIL"), require = 0)
	private void sleep$pose(ArmorStandEntity stand, ArmorStandEntityRenderState state, float tickProgress, CallbackInfo ci) {
		float[][] p = ArmorStandPosesMod.poseFor(stand.getUuid());
		if (p == null) {
			return;
		}
		state.headRotation = ArmorStandPosesMod.angle(p[0]);
		state.bodyRotation = ArmorStandPosesMod.angle(p[1]);
		state.leftArmRotation = ArmorStandPosesMod.angle(p[2]);
		state.rightArmRotation = ArmorStandPosesMod.angle(p[3]);
		state.leftLegRotation = ArmorStandPosesMod.angle(p[4]);
		state.rightLegRotation = ArmorStandPosesMod.angle(p[5]);
		state.showArms = true;
	}
}
