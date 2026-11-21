package dev.sleepclient.mixin;

import dev.sleepclient.video.PanelInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you type into a floating browser panel the keyboard belongs to the page (or to its address bar), not to the game. */
@Mixin(KeyboardHandler.class)
public class KuboKeyboardMixin {

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$panelKey(long window, int action, KeyEvent input, CallbackInfo ci) {
		PanelInput.alive("key");
		if (PanelInput.onKey(action, input.key(), input)) {
			ci.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$panelChar(long window, CharacterEvent input, CallbackInfo ci) {
		PanelInput.alive("char");
		if (PanelInput.onChar(input.codepoint(), input)) {
			ci.cancel();
		}
	}
}
