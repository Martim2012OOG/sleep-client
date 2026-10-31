package dev.solclient.mixin.emote;

import dev.solclient.emote.EmoteFlag;
import dev.solclient.emote.EmoteManager;
import dev.solclient.emote.EmoteRig;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes move their props: the rightItem / leftItem track of the emote file is applied to the prop in that hand. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemEmoteMixin {

	@Inject(method = "renderItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/model/ModelWithArms;setArmAngle(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
			shift = At.Shift.AFTER), require = 0)
	private void sol$prop(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		if ((Object) state instanceof EmoteFlag flag) {
			float[] o = new float[6];
			if (EmoteManager.itemPose(flag, arm == Arm.RIGHT, o)) {
				EmoteRig.applyItem(matrices, o);
			}
		}
	}
}
