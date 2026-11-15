package dev.solclient.mixin.perf;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mac only: Minecraft draws at the full Retina resolution (2880x1800 on a MacBook Air = 4x the pixels of the screen size),
 * which is the main reason it runs slowly on Apple M1/M2 GPUs. Unless "Sharp Retina Resolution" is on, the window is made
 * at the normal screen resolution instead: about 4x fewer pixels to draw. The hint does nothing on Windows / Linux.
 */
@Mixin(com.mojang.blaze3d.platform.Window.class)
public class RetinaMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false),
			require = 0)
	private void kubo$noRetina(CallbackInfo ci) {
		try {
			if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
				return;
			}
			if (!dev.solclient.mods.PerformanceMod.sharpRetinaSaved()) {
				GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW.GLFW_FALSE);
				System.out.println("[Sleep] Mac: drawing at screen resolution (Sharp Retina Resolution is off)");
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] retina hint: " + t);
		}
	}
}
