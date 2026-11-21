package dev.sleepclient.mixin.emote;

import dev.sleepclient.emote.EmoteFlag;
import dev.sleepclient.emote.EmoteManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player model and armor models (all biped models) get the emote pose after the vanilla pose. */
@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelMixin {

	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart body;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	/** Elbows and knees for armour / biped models (the player model does its own, see PlayerModelJointsMixin). */
	@Inject(method = "createMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;F)Lnet/minecraft/client/model/geom/builders/MeshDefinition;", at = @At("RETURN"), require = 0)
	private static void sol$joints(net.minecraft.client.model.geom.builders.CubeDeformation d, float off, CallbackInfoReturnable<net.minecraft.client.model.geom.builders.MeshDefinition> cir) {
		try {
			dev.sleepclient.emote.LimbSplit.biped(cir.getReturnValue(), d, off);
		} catch (Throwable t) {
			System.err.println("[Sleep] emote joints: " + t);
		}
	}

	/** A held item follows the bent forearm. */
	@Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			at = @At("TAIL"), require = 0)
	private void sol$hand(HumanoidRenderState state, net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack matrices, CallbackInfo ci) {
		dev.sleepclient.emote.EmoteRig.afterArm(arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? rightArm : leftArm, matrices);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void sol$emote(HumanoidRenderState state, CallbackInfo ci) {
		boolean posed = false;
		if ((Object) state instanceof EmoteFlag flag) {
			if (flag.sol$isEmoting()) {
				posed = true;
				EmoteManager.applyTo(head, body, rightArm, leftArm, rightLeg, leftLeg);
				EmoteManager.applyItems(state);
			} else if (flag.sol$remoteEmote() != null && flag.sol$remoteEmote() == EmoteManager.previewRemote()) {
				posed = true;
				EmoteManager.applyPreview(flag.sol$remoteEmote(), head, body, rightArm, leftArm, rightLeg, leftLeg);
				EmoteManager.applyRemoteItems(flag.sol$remoteEmote(), state);
			} else if (flag.sol$remoteEmote() != null) {
				posed = true;
				EmoteManager.applyRemote(flag.sol$remoteEmote(), head, body, rightArm, leftArm, rightLeg, leftLeg);
				EmoteManager.applyRemoteItems(flag.sol$remoteEmote(), state);
			}
		}
		if (!posed) {
			// no emote: the elbows and knees go straight (they are child parts the game does not reset, so without this a limb stays bent forever)
			dev.sleepclient.emote.EmoteRig.resetBends(rightArm, leftArm, rightLeg, leftLeg);
		}
	}
}
