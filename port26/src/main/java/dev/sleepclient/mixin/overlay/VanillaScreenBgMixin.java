package dev.sleepclient.mixin.overlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus outside a world (worlds, servers, options...) get the same background as the Plegumo home screen. */
@Mixin(Screen.class)
public abstract class VanillaScreenBgMixin {

	@Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
	private void plegumo$bg(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Screen s = (Screen) (Object) this;
		if (dev.sleepclient.gui.VanillaStyle.on() && net.minecraft.client.Minecraft.getInstance().level == null
				&& !(s instanceof dev.sleepclient.gui.UiScreen) && !(s instanceof dev.sleepclient.gui.SolMenuScreen)) {
			dev.sleepclient.gui.VanillaStyle.background(c, s.width, s.height);
		}
	}
}
