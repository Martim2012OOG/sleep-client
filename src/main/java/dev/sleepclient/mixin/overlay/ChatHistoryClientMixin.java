package dev.sleepclient.mixin.overlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat History mod: the chat clear done while disconnecting is skipped too. */
@Mixin(MinecraftClient.class)
public abstract class ChatHistoryClientMixin {

	@Redirect(method = "disconnect", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;clear(Z)V"))
	private void plegumo$keepChat(ChatHud chat, boolean clearHistory) {
		if (!dev.sleepclient.mods.ChatHistoryMod.on()) {
			chat.clear(clearHistory);
		}
	}
}
