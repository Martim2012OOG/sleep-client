package dev.solclient.mixin.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.emote.EmoteRig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own arm in first person is never bent by an emote: the elbow goes straight before it is drawn. */
@Mixin(AvatarRenderer.class)
public abstract class FirstPersonArmMixin {

	@Inject(method = "renderHand", at = @At("HEAD"), require = 0)
	private void sol$straight(PoseStack matrices, SubmitNodeCollector queue, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
		EmoteRig.resetBends(arm);
	}
}
