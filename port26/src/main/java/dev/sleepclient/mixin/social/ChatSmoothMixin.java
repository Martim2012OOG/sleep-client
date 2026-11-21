package dev.sleepclient.mixin.social;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth chat: when a message arrives the older lines glide up into place instead of jumping. */
@Mixin(ChatComponent.class)
public abstract class ChatSmoothMixin {

	@org.spongepowered.asm.mixin.Unique
	private boolean sleep$pushed;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), require = 0)
	private void sleep$slideIn(GuiGraphicsExtractor context, Font font, int tick, int mx, int my, net.minecraft.client.gui.components.ChatComponent.DisplayMode mode, boolean other, CallbackInfo ci) {
		sleep$pushed = false;
		var s = dev.sleepclient.SleepClient.settings;
		if (s != null && !s.buttonAnimations.on()) {
			return;
		}
		float age = (System.currentTimeMillis() - dev.sleepclient.social.ChatSmooth.lastMessageAt) / 1000F;
		if (age < 0F || age > 0.28F) {
			return;
		}
		float k = 1F - age / 0.28F;
		float dy = k * k * k * 10F; // one line height, easing out
		context.pose().pushMatrix();
		context.pose().translate(0F, dy);
		sleep$pushed = true;
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"), require = 0)
	private void sleep$slideOut(GuiGraphicsExtractor context, Font font, int tick, int mx, int my, net.minecraft.client.gui.components.ChatComponent.DisplayMode mode, boolean other, CallbackInfo ci) {
		if (sleep$pushed) {
			sleep$pushed = false;
			context.pose().popMatrix();
		}
	}
}
