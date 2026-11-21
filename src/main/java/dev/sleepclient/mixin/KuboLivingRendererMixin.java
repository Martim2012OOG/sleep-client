package dev.sleepclient.mixin;

import dev.sleepclient.fx.ScaleFlag;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
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

	@ModifyVariable(method = "render", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private LivingEntityRenderState kubo$state(LivingEntityRenderState state) {
		kubo$next = (Object) state instanceof ScaleFlag f ? f.kubo$scale() : 1F;
		return state;
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private MatrixStack kubo$push(MatrixStack m) {
		if (kubo$next != 1F) {
			m.push();
			m.scale(kubo$next, kubo$next, kubo$next);
			kubo$pushed++;
		}
		return m;
	}

	@ModifyVariable(method = "render", at = @At("RETURN"), ordinal = 0, argsOnly = true, require = 0)
	private MatrixStack kubo$pop(MatrixStack m) {
		if (kubo$next != 1F && kubo$pushed > 0) {
			m.pop();
			kubo$pushed--;
		}
		kubo$next = 1F;
		return m;
	}
}
