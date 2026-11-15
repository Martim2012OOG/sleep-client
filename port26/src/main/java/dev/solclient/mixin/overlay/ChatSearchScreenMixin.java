package dev.solclient.mixin.overlay;

import dev.solclient.chatx.ChatSearch;
import dev.solclient.mods.ChatSearchMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat Search: button above the chat, Ctrl+F, the chat box becomes the search box while searching. */
@Mixin(ChatScreen.class)
public abstract class ChatSearchScreenMixin {

	@Shadow
	protected EditBox input;

	private void plegumo$toggle() {
		if (ChatSearch.active) {
			input.setValue(ChatSearch.stop());
		} else {
			ChatSearch.start(input.getValue());
			input.setValue("");
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void plegumo$render(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!ChatSearchMod.on()) {
			return;
		}
		if (ChatSearch.active) {
			ChatSearch.update(input.getValue());
		}
		ChatSearch.render(c, ((net.minecraft.client.gui.screens.Screen) (Object) this).height, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$click(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (!ChatSearchMod.on() || click.button() != 0) {
			return;
		}
		int r = ChatSearch.click((int) click.x(), (int) click.y());
		switch (r) {
			case 1, 4 -> plegumo$toggle();
			case 2 -> ChatSearch.next(1);
			case 3 -> ChatSearch.next(-1);
			default -> {
				return;
			}
		}
		cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$key(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
		if (!ChatSearchMod.on()) {
			return;
		}
		int k = input.key();
		boolean ctrl = dev.solclient.Keys.down(341) || dev.solclient.Keys.down(345) || dev.solclient.Keys.down(343) || dev.solclient.Keys.down(347);
		if (ctrl && k == 70) { // Ctrl / Cmd + F
			plegumo$toggle();
			cir.setReturnValue(true);
			return;
		}
		if (!ChatSearch.active) {
			return;
		}
		if (k == 256) { // Esc: stop searching, keep the chat open
			plegumo$toggle();
			cir.setReturnValue(true);
		} else if (k == 257 || k == 335 || k == 265) { // Enter / up: older match
			ChatSearch.next(1);
			cir.setReturnValue(true);
		} else if (k == 264) { // down: newer match
			ChatSearch.next(-1);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "removed", at = @At("HEAD"), require = 0)
	private void plegumo$closed(CallbackInfo ci) {
		if (ChatSearch.active) {
			ChatSearch.stop();
		}
	}
}
