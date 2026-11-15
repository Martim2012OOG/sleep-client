package dev.solclient.mixin.social;

import dev.solclient.mods.NickHiderMod;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nick Hider in chat. */
@Mixin(ChatHud.class)
public abstract class ChatNickMixin {

	@org.spongepowered.asm.mixin.injection.Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$hideFiltered(Text message, net.minecraft.network.message.MessageSignatureData sig, net.minecraft.client.gui.hud.MessageIndicator ind,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.solclient.mods.ChatMod.hide(message)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Text plegumo$hideNick(Text message) {
		dev.solclient.social.ChatSmooth.lastMessageAt = System.currentTimeMillis();
		Text t = dev.solclient.mods.NameColourMod.applyChat(NickHiderMod.apply(message));
		t = dev.solclient.mods.ChatMod.process(t, (ChatHud) (Object) this);
		String ts = dev.solclient.mods.ChatMod.timestamp();
		if (ts != null && t != null) {
			t = Text.literal(ts).formatted(net.minecraft.util.Formatting.GRAY).append(t);
		}
		return t;
	}
}
