package dev.solclient.mixin.social;

import dev.solclient.mods.ChatBubblesMod;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Bubbles: every line that lands in the chat window, whatever way it arrived (signed chat, system message or the
 * unsigned chat of cracked / plugin servers), so bubbles work on any server.
 */
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class ChatBubbleHudMixin {

	@Inject(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), require = 0)
	private void plegumo$bubbleLine(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
		if (message != null) {
			try {
				ChatBubblesMod.onChatLine(message.getString());
			} catch (RuntimeException ignored) {
			}
		}
	}
}
