package dev.sleepclient.mixin.overlay;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat History mod: leaving a server doesn't clear the chat (F3+D still does). */
@Mixin(Gui.class)
public abstract class ChatHistoryMixin {

	@Redirect(method = "onDisconnected", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private void plegumo$keepChat(ChatComponent chat, boolean clearHistory) {
		if (!dev.sleepclient.mods.ChatHistoryMod.on()) {
			chat.clearMessages(clearHistory);
		}
	}
}
