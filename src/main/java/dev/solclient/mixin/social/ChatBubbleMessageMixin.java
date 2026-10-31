package dev.solclient.mixin.social;

import dev.solclient.mods.ChatBubblesMod;
import dev.solclient.social.SolarBadge;
import java.time.Instant;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds chat messages to the Chat Bubbles mod. */
@Mixin(MessageHandler.class)
public class ChatBubbleMessageMixin {

	@Inject(method = "processChatMessageInternal", at = @At("RETURN"), require = 0)
	private void plegumo$playerChat(MessageType.Parameters params, SignedMessage message, Text decorated, com.mojang.authlib.GameProfile sender,
			boolean onlyShowSecureChat, Instant time, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && message != null) {
			try {
				ChatBubblesMod.onPlayerChat(SolarBadge.idOf(sender), message.getContent().getString());
			} catch (RuntimeException ignored) {
			}
		}
	}

	@Inject(method = "onGameMessage", at = @At("HEAD"), require = 0)
	private void plegumo$gameChat(Text message, boolean overlay, CallbackInfo ci) {
		if (!overlay && message != null) {
			try {
				ChatBubblesMod.onGameMessage(message.getString());
			} catch (RuntimeException ignored) {
			}
		}
	}
}
