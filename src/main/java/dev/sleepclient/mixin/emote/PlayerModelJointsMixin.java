package dev.sleepclient.mixin.emote;

import dev.sleepclient.emote.EmoteRig;
import dev.sleepclient.emote.LimbSplit;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player model gets elbows and knees (see LimbSplit), and a held item follows the bent forearm. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelJointsMixin {

	@Inject(method = "getTexturedModelData(Lnet/minecraft/client/model/Dilation;Z)Lnet/minecraft/client/model/ModelData;", at = @At("RETURN"), require = 0)
	private static void sol$joints(Dilation dilation, boolean slim, CallbackInfoReturnable<ModelData> cir) {
		try {
			LimbSplit.player(cir.getReturnValue(), dilation, slim);
		} catch (Throwable t) {
			System.err.println("[Sleep] emote joints: " + t);
		}
	}

	@Inject(method = "setArmAngle(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
			at = @At("TAIL"), require = 0)
	private void sol$hand(PlayerEntityRenderState state, Arm arm, MatrixStack matrices, CallbackInfo ci) {
		EmoteRig.afterArm(((BipedEntityModel<?>) (Object) this).getArm(arm), matrices);
	}
}
