package dev.solclient.mixin.emote;

import dev.solclient.emote.EmoteFlag;
import dev.solclient.emote.EmoteManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player model and armor models (all biped models) get the emote pose after the vanilla pose. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {

	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart body;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	/** Elbows and knees for armour / biped models (the player model does its own, see PlayerModelJointsMixin). */
	@Inject(method = "getModelData(Lnet/minecraft/client/model/Dilation;F)Lnet/minecraft/client/model/ModelData;", at = @At("RETURN"), require = 0)
	private static void sol$joints(net.minecraft.client.model.Dilation d, float off, CallbackInfoReturnable<net.minecraft.client.model.ModelData> cir) {
		try {
			dev.solclient.emote.LimbSplit.biped(cir.getReturnValue(), d, off);
		} catch (Throwable t) {
			System.err.println("[Caos] emote joints: " + t);
		}
	}

	/** A held item follows the bent forearm. */
	@Inject(method = "setArmAngle(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
			at = @At("TAIL"), require = 0)
	private void sol$hand(BipedEntityRenderState state, net.minecraft.util.Arm arm, net.minecraft.client.util.math.MatrixStack matrices, CallbackInfo ci) {
		dev.solclient.emote.EmoteRig.afterArm(arm == net.minecraft.util.Arm.RIGHT ? rightArm : leftArm, matrices);
	}

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void sol$emote(BipedEntityRenderState state, CallbackInfo ci) {
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
			dev.solclient.emote.EmoteRig.resetBends(rightArm, leftArm, rightLeg, leftLeg);
		}
	}
}
