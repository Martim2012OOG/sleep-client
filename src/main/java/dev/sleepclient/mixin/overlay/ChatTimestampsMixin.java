package dev.sleepclient.mixin.overlay;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat Timestamps mod. */
@Mixin(ChatHud.class)
public abstract class ChatTimestampsMixin {

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true, require = 0)
	private Text plegumo$time(Text message) {
		return dev.sleepclient.mods.ChatTimestampsMod.apply(message);
	}
}
