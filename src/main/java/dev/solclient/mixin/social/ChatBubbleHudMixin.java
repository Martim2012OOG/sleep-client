package dev.solclient.mixin.social;

import dev.solclient.mods.ChatBubblesMod;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Bubbles: every line that lands in the chat window, whatever way it arrived (signed chat, system message or the
 * unsigned chat of cracked / plugin servers), so bubbles work on any server.
 */
@Mixin(value = ChatHud.class, priority = 500)
public abstract class ChatBubbleHudMixin {

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), require = 0)
	private void plegumo$bubbleLine(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
		if (message != null) {
			try {
				ChatBubblesMod.onChatLine(message.getString());
			} catch (RuntimeException ignored) {
			}
		}
	}
}
