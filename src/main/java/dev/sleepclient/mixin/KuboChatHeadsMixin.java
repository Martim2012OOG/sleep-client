package dev.sleepclient.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class KuboChatHeadsMixin {

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true, require = 0)
	private Text kubo$heads(Text msg) {
		try {
			return dev.sleepclient.mods.ChatHeadsMod.decorate(msg);
		} catch (RuntimeException e) {
			return msg;
		}
	}
}
