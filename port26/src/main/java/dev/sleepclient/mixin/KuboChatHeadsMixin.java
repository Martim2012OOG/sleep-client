package dev.sleepclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class KuboChatHeadsMixin {

	@ModifyVariable(method = {"addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V"}, at = @At("HEAD"), argsOnly = true, require = 0)
	private Component kubo$heads(Component msg) {
		try {
			return dev.sleepclient.mods.ChatHeadsMod.decorate(msg);
		} catch (RuntimeException e) {
			return msg;
		}
	}
}
