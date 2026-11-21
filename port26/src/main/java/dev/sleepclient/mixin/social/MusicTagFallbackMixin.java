package dev.sleepclient.mixin.social;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.music.MusicTagDraw;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Servers with their own name tags (holograms, ranks in a text display) hide the real name tag: the player has no label and the normal music
 * line never ran. Here the line is drawn anyway, a little above where the real tag would be, so it does not sit on the server's tags.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MusicTagFallbackMixin {

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void sleep$musicTagFallback(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState ps) || ps.nameTagAttachment != null || ps.isInvisible) {
			return;
		}
		var m = dev.sleepclient.mods.MusicShareMod.instance;
		if (m == null || !m.enabled) {
			return;
		}
		MusicTagDraw.submit(ps, matrices, queue, camera, new Vec3(0, ps.boundingBoxHeight + 0.5 + m.tagLift(), 0), 0);
	}
}
