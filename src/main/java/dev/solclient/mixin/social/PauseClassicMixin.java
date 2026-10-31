package dev.solclient.mixin.social;

import dev.solclient.gui.PauseClassic;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Classic + Sleep" pause menu: Minecraft's real pause screen (so Essential and Mod Menu keep their buttons) plus Sleep's
 * buttons, your player and the logo. Only when Settings > Menus > Pause Menu Style says so.
 */
@Mixin(GameMenuScreen.class)
public abstract class PauseClassicMixin extends Screen {

	protected PauseClassicMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 0)
	private void kubo$classic(CallbackInfo ci) {
		try {
			if (!((GameMenuScreen) (Object) this).shouldShowMenu()) {
				return; // F3 + Esc
			}
			PauseClassic.build(this, this.width, this.height, this.children(), b -> this.addDrawableChild(b), d -> this.addDrawable(d));
		} catch (Throwable t) {
			System.err.println("[Sleep] classic pause menu: " + t);
		}
	}
}
