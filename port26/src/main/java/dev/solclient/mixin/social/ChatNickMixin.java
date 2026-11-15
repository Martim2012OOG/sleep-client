package dev.solclient.mixin.social;

import dev.solclient.mods.NickHiderMod;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nick Hider in chat. */
@Mixin(ChatComponent.class)
public abstract class ChatNickMixin {

	@org.spongepowered.asm.mixin.injection.Inject(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$hideFiltered(Component message, net.minecraft.network.chat.MessageSignature sig, net.minecraft.client.multiplayer.chat.GuiMessageTag ind,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.solclient.mods.ChatMod.hide(message)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Component plegumo$hideNick(Component message) {
		dev.solclient.social.ChatSmooth.lastMessageAt = System.currentTimeMillis();
		Component t = dev.solclient.mods.NameColourMod.applyChat(NickHiderMod.apply(message));
		t = dev.solclient.mods.ChatMod.process(t, (ChatComponent) (Object) this);
		String ts = dev.solclient.mods.ChatMod.timestamp();
		if (ts != null && t != null) {
			t = Component.literal(ts).withStyle(net.minecraft.ChatFormatting.GRAY).append(t);
		}
		return t;
	}
}
