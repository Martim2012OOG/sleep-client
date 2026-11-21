package dev.sleepclient.mixin;

import dev.sleepclient.video.PanelInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you type into a floating browser panel the keyboard belongs to the page (or to its address bar), not to the game. */
@Mixin(Keyboard.class)
public class KuboKeyboardMixin {

	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$panelKey(long window, int action, KeyInput input, CallbackInfo ci) {
		PanelInput.alive("key");
		if (PanelInput.onKey(action, input.key(), input)) {
			ci.cancel();
		}
	}

	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$panelChar(long window, CharInput input, CallbackInfo ci) {
		PanelInput.alive("char");
		if (PanelInput.onChar(input.codepoint(), input)) {
			ci.cancel();
		}
	}
}
