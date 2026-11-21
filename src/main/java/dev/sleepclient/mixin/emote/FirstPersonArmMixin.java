package dev.sleepclient.mixin.emote;

import dev.sleepclient.emote.EmoteRig;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own arm in first person is never bent by an emote: the elbow goes straight before it is drawn. */
@Mixin(PlayerEntityRenderer.class)
public abstract class FirstPersonArmMixin {

	@Inject(method = "renderArm", at = @At("HEAD"), require = 0)
	private void sol$straight(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
		EmoteRig.resetBends(arm);
	}
}
