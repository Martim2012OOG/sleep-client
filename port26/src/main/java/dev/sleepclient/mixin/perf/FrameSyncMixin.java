package dev.sleepclient.mixin.perf;

import dev.sleepclient.mods.InputOptimizerMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Input Optimizer: blocks until the GPU finished the frame so input is never read against a queued, stale frame. */
@Mixin(Minecraft.class)
public class FrameSyncMixin {
	@Inject(method = "runTick", at = @At("RETURN"), require = 0)
	private void sleep$frameSync(boolean tick, CallbackInfo ci) {
		if (InputOptimizerMod.syncFrames()) GL11.glFinish();
	}
}
