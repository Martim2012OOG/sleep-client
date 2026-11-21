package dev.sleepclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.fx.ScaleFlag;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Baby Mode: draws the whole player smaller. */
@Mixin(LivingEntityRenderer.class)
public class KuboLivingRendererMixin {

	@Unique
	private static float kubo$next = 1F;
	@Unique
	private static int kubo$pushed;

	@ModifyVariable(method = "submit", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private LivingEntityRenderState kubo$state(LivingEntityRenderState state) {
		kubo$next = (Object) state instanceof ScaleFlag f ? f.kubo$scale() : 1F;
		return state;
	}

	@ModifyVariable(method = "submit", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private PoseStack kubo$push(PoseStack m) {
		if (kubo$next != 1F) {
			m.pushPose();
			m.scale(kubo$next, kubo$next, kubo$next);
			kubo$pushed++;
		}
		return m;
	}

	@ModifyVariable(method = "submit", at = @At("RETURN"), ordinal = 0, argsOnly = true, require = 0)
	private PoseStack kubo$pop(PoseStack m) {
		if (kubo$next != 1F && kubo$pushed > 0) {
			m.popPose();
			kubo$pushed--;
		}
		kubo$next = 1F;
		return m;
	}
}
