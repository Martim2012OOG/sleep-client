package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.ChatBubblesMod;
import dev.sleepclient.social.SolarBadge;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds chat messages to the Chat Bubbles mod. */
@Mixin(ChatListener.class)
public class ChatBubbleMessageMixin {

	@Inject(method = "showMessageToPlayer", at = @At("RETURN"), require = 0)
	private void plegumo$playerChat(ChatType.Bound params, PlayerChatMessage message, Component decorated, com.mojang.authlib.GameProfile sender,
			boolean onlyShowSecureChat, Instant time, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && message != null) {
			try {
				ChatBubblesMod.onPlayerChat(SolarBadge.idOf(sender), message.decoratedContent().getString());
			} catch (RuntimeException ignored) {
			}
		}
	}

	@Inject(method = "handleSystemMessage", at = @At("HEAD"), require = 0)
	private void plegumo$gameChat(Component message, boolean overlay, CallbackInfo ci) {
		if (!overlay && message != null) {
			try {
				ChatBubblesMod.onGameMessage(message.getString());
			} catch (RuntimeException ignored) {
			}
		}
	}
}
