package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.FullbrightMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright: whoever reads the gamma option (the lightmap) gets a higher value. The saved video setting is never
 * changed. Hooking the option itself instead of one call inside LightmapTextureManager.update keeps working when
 * Minecraft reshuffles the lightmap code (the old redirect failed silently on 1.21.11).
 */
@Mixin(SimpleOption.class)
public class FullbrightMixin {

	/** Set while this hook runs: whatever it calls can never come back into it (no loop with other mods). */
	@Unique
	private static boolean kubo$busy;

	/**
	 * Safe during start-up and with other mods: every option's getValue passes here, so first a plain reference check
	 * against the gamma option (remembered when the options were made, see FullbrightGammaCaptureMixin) - no
	 * getGamma() call, no settings read. Anything else, or the game still starting, leaves at once.
	 */
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$fullbright(CallbackInfoReturnable<Object> cir) {
		Object gammaOption = FullbrightMod.gammaOption;
		if (gammaOption == null || (Object) this != gammaOption || kubo$busy || FullbrightMod.saving) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.options == null || dev.sleepclient.SleepClient.settings == null) {
			return; // still starting
		}
		kubo$busy = true;
		try {
			// the brightness slider in the video settings keeps showing the real value
			if (mc.currentScreen instanceof GameOptionsScreen) {
				return;
			}
			Double g = FullbrightMod.gamma();
			if (g != null) {
				cir.setReturnValue(g);
			}
		} finally {
			kubo$busy = false;
		}
	}
}
