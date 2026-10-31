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

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private Text plegumo$hideNick(Text message) {
		Text t = dev.solclient.mods.NameColourMod.applyChat(NickHiderMod.apply(message));
		String ts = dev.solclient.mods.ChatMod.timestamp();
		if (ts != null && t != null) {
			t = Text.literal(ts).formatted(net.minecraft.util.Formatting.GRAY).append(t);
		}
		return t;
	}
}
