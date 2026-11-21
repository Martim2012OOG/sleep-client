package dev.sleepclient.mixin.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.emote.EmoteFlag;
import dev.sleepclient.emote.EmoteManager;
import dev.sleepclient.emote.EmoteRig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes move their props: the rightItem / leftItem track of the emote file is applied to the prop in that hand. */
@Mixin(ItemInHandLayer.class)
public abstract class HeldItemEmoteMixin {

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			shift = At.Shift.AFTER), require = 0)
	private void sol$prop(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack stack, HumanoidArm arm, PoseStack matrices,
			SubmitNodeCollector queue, int light, CallbackInfo ci) {
		if ((Object) state instanceof EmoteFlag flag) {
			float[] o = new float[6];
			if (EmoteManager.itemPose(flag, arm == HumanoidArm.RIGHT, o)) {
				EmoteRig.applyItem(matrices, o);
			}
		}
	}
}
