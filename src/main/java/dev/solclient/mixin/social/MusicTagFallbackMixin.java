package dev.solclient.mixin.social;

import dev.solclient.music.MusicTagDraw;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
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

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("TAIL"), require = 0)
	private void caos$musicTagFallback(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		if (!(state instanceof PlayerEntityRenderState ps) || ps.nameLabelPos != null || ps.invisible) {
			return;
		}
		var m = dev.solclient.mods.MusicShareMod.instance;
		if (m == null || !m.enabled) {
			return;
		}
		MusicTagDraw.submit(ps, matrices, queue, camera, new Vec3d(0, ps.height + 0.5 + m.tagLift(), 0), 0);
	}
}
