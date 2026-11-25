package dev.solclient.mixin.social;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notification cards (gui.Notify) also show on top of menus: the title screen, the Caos menu, chat... */
@Mixin(Screen.class)
public abstract class NotifyScreenMixin {

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"), require = 0)
	private void kubo$notify(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this == dev.solclient.compat.Mc.screen(net.minecraft.client.Minecraft.getInstance())) {
			dev.solclient.gui.Notify.draw(context);
		}
	}
}
