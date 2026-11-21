package dev.sleepclient.mixin.fightcam;

import dev.sleepclient.fightcam.Capture;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the recorder grabs the picture: right after the world is drawn (World only: no HUD, no menus) or at the very end
 * of the frame (Game + HUD, like Medal). Grabbing only starts an asynchronous GPU copy, so it costs almost nothing here.
 */
@Mixin(GameRenderer.class)
public abstract class CaptureMixin {

	@Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
	private void kubo$captureWorld(CallbackInfo ci) {
		Capture.onFrame(false);
	}

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void kubo$captureFull(CallbackInfo ci) {
		Capture.onFrame(true);
		dev.sleepclient.gui.PhotoModeScreen.onFrameEnd();
	}
}
