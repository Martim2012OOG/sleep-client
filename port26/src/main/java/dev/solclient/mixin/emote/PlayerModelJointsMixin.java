package dev.solclient.mixin.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.emote.EmoteRig;
import dev.solclient.emote.LimbSplit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player model gets elbows and knees (see LimbSplit), and a held item follows the bent forearm. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelJointsMixin {

	@Inject(method = "createMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;Z)Lnet/minecraft/client/model/geom/builders/MeshDefinition;", at = @At("RETURN"), require = 0)
	private static void sol$joints(CubeDeformation dilation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
		try {
			LimbSplit.player(cir.getReturnValue(), dilation, slim);
		} catch (Throwable t) {
			System.err.println("[Caos] emote joints: " + t);
		}
	}

	@Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			at = @At("TAIL"), require = 0)
	private void sol$hand(AvatarRenderState state, HumanoidArm arm, PoseStack matrices, CallbackInfo ci) {
		EmoteRig.afterArm(((HumanoidModel<?>) (Object) this).getArm(arm), matrices);
	}
}
