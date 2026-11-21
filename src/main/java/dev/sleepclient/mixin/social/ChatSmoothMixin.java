package dev.sleepclient.mixin.social;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth chat: when a message arrives the older lines glide up into place instead of jumping. */
@Mixin(ChatHud.class)
public abstract class ChatSmoothMixin {

	@org.spongepowered.asm.mixin.Unique
	private boolean sleep$pushed;

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"), require = 0)
	private void sleep$slideIn(DrawContext context, TextRenderer font, int tick, int mx, int my, boolean focused, boolean other, CallbackInfo ci) {
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
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(0F, dy);
		sleep$pushed = true;
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"), require = 0)
	private void sleep$slideOut(DrawContext context, TextRenderer font, int tick, int mx, int my, boolean focused, boolean other, CallbackInfo ci) {
		if (sleep$pushed) {
			sleep$pushed = false;
			context.getMatrices().popMatrix();
		}
	}
}
