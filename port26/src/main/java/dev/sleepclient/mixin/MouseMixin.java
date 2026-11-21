package dev.sleepclient.mixin;

import dev.sleepclient.mods.ZoomMod;
import dev.sleepclient.video.PanelInput;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		PanelInput.alive("mouse scroll");
		dev.sleepclient.mods.CinematicAfkMod.poke();
		if (PanelInput.onScroll(vertical)) { // aiming at a floating browser panel: the scroll is for the panel
			ci.cancel();
			return;
		}
		if (ZoomMod.onScroll(vertical)) {
			ci.cancel();
		}
	}

	/** Clicks on the floating browser panels do not reach the game (no attacking / placing while you click a page). */
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$panelButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
		PanelInput.alive("mouse button");
		if (dev.sleepclient.mods.TabListMod.onMouseButton(input.button(), action)) { // Tab held + quick clicks = free mouse over the tab list
			ci.cancel();
			return;
		}
		if (PanelInput.onMouseButton(input.button(), action)) {
			ci.cancel();
		}
	}
}
