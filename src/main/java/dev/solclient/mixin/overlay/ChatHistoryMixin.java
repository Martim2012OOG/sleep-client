package dev.solclient.mixin.overlay;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat History mod: leaving a server doesn't clear the chat (F3+D still does). */
@Mixin(InGameHud.class)
public abstract class ChatHistoryMixin {

	@Redirect(method = "clear", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;clear(Z)V"))
	private void plegumo$keepChat(ChatHud chat, boolean clearHistory) {
		if (!dev.solclient.mods.ChatHistoryMod.on()) {
			chat.clear(clearHistory);
		}
	}
}
