package dev.solclient.mixin.perf;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.solclient.render.WorldScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the world is being drawn, "the screen" is the smaller picture of {@link WorldScale}. */
@Mixin(Minecraft.class)
public class WorldScaleFramebufferMixin {

	@Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$scaled(CallbackInfoReturnable<RenderTarget> cir) {
		RenderTarget f = WorldScale.override();
		if (f != null) {
			cir.setReturnValue(f);
		}
	}
}
