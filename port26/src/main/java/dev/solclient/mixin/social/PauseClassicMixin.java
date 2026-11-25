package dev.solclient.mixin.social;

import dev.solclient.gui.PauseClassic;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Classic + Caos" pause menu: Minecraft's real pause screen (so Essential and Mod Menu keep their buttons) plus Caos's
 * buttons, your player and the logo. Only when Settings > Menus > Pause Menu Style says so.
 */
@Mixin(PauseScreen.class)
public abstract class PauseClassicMixin extends Screen {

	protected PauseClassicMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 0)
	private void kubo$classic(CallbackInfo ci) {
		try {
			if (!((PauseScreen) (Object) this).showsPauseMenu()) {
				return; // F3 + Esc
			}
			PauseClassic.build(this, this.width, this.height, this.children(), b -> this.addRenderableWidget(b), d -> this.addRenderableOnly(d));
		} catch (Throwable t) {
			System.err.println("[Caos] classic pause menu: " + t);
		}
	}
}
