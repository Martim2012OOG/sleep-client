package dev.sleepclient.mixin.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat History mod: the chat clear done while disconnecting is skipped too. */
@Mixin(Minecraft.class)
public abstract class ChatHistoryClientMixin {

	@Redirect(method = "disconnect", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private void plegumo$keepChat(ChatComponent chat, boolean clearHistory) {
		if (!dev.sleepclient.mods.ChatHistoryMod.on()) {
			chat.clearMessages(clearHistory);
		}
	}
}
