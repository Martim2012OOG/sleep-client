package dev.solclient.mixin.perf;

import dev.solclient.render.WorldScale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the world is being drawn, "the screen" is the smaller picture of {@link WorldScale}. */
@Mixin(MinecraftClient.class)
public class WorldScaleFramebufferMixin {

	@Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$scaled(CallbackInfoReturnable<Framebuffer> cir) {
		Framebuffer f = WorldScale.override();
		if (f != null) {
			cir.setReturnValue(f);
		}
	}
}
