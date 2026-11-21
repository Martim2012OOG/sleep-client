package dev.sleepclient.mixin.perf;

import dev.sleepclient.GameFont;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hologram / leaderboard speed-up. Vanilla throws away the split-into-lines result of every Text Display on EVERY frame
 * (refreshData sets textLines = null), so each frame the game re-wraps and re-measures every line of every hologram. Servers
 * full of leaderboards then cost a lot of fps, mostly in the direction where the holograms are. We keep the last result and
 * only let it be recomputed when the text, the line width or the font really changed. Nothing is hidden or reduced.
 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayCacheMixin {

	@Shadow
	private Display.TextDisplay.CachedInfo clientDisplayCache;

	@Unique private Display.TextDisplay.CachedInfo sleep$keep;
	@Unique private Component sleep$text;
	@Unique private int sleep$width = -1;
	@Unique private Object sleep$font;

	@Inject(method = "updateRenderSubState", at = @At("HEAD"), require = 0)
	private void sleep$before(boolean lerp, float progress, CallbackInfo ci) {
		this.sleep$keep = this.clientDisplayCache;
	}

	@Inject(method = "updateRenderSubState", at = @At("TAIL"), require = 0)
	private void sleep$after(boolean lerp, float progress, CallbackInfo ci) {
		Display.TextDisplay self = (Display.TextDisplay) (Object) this;
		Component t = self.getText();
		int w = self.getLineWidth();
		Object font = GameFont.target;
		if (this.sleep$keep != null && w == this.sleep$width && font == this.sleep$font && t != null && t == this.sleep$text) {
			this.clientDisplayCache = this.sleep$keep; // same text, same width, same font: reuse
		} else {
			this.sleep$text = t;
			this.sleep$width = w;
			this.sleep$font = font;
		}
		this.sleep$keep = null;
	}
}
