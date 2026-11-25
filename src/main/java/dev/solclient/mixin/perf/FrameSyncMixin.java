package dev.solclient.mixin.perf;

import dev.solclient.mods.InputOptimizerMod;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Input Optimizer: blocks until the GPU finished the frame so input is never read against a queued, stale frame. */
@Mixin(MinecraftClient.class)
public class FrameSyncMixin {
	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void caos$frameSync(boolean tick, CallbackInfo ci) {
		if (InputOptimizerMod.syncFrames()) GL11.glFinish();
	}
}
