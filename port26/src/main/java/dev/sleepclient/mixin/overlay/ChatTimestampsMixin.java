package dev.sleepclient.mixin.overlay;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat Timestamps mod. */
@Mixin(ChatComponent.class)
public abstract class ChatTimestampsMixin {

	@ModifyVariable(method = {"addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V"}, at = @At("HEAD"), argsOnly = true, require = 0)
	private Component plegumo$time(Component message) {
		return dev.sleepclient.mods.ChatTimestampsMod.apply(message);
	}
}
