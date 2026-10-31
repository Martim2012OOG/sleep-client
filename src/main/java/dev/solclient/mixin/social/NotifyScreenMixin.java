package dev.solclient.mixin.social;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notification cards (gui.Notify) also show on top of menus: the title screen, the Sleep menu, chat... */
@Mixin(Screen.class)
public abstract class NotifyScreenMixin {

	@Inject(method = "renderWithTooltip", at = @At("TAIL"), require = 0)
	private void kubo$notify(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().currentScreen) {
			dev.solclient.gui.Notify.draw(context);
		}
	}
}
