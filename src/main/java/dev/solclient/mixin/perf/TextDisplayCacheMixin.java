package dev.solclient.mixin.perf;

import dev.solclient.GameFont;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
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
@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class TextDisplayCacheMixin {

	@Shadow
	private DisplayEntity.TextDisplayEntity.TextLines textLines;

	@Unique private DisplayEntity.TextDisplayEntity.TextLines sleep$keep;
	@Unique private Text sleep$text;
	@Unique private int sleep$width = -1;
	@Unique private Object sleep$font;

	@Inject(method = "refreshData", at = @At("HEAD"), require = 0)
	private void sleep$before(boolean lerp, float progress, CallbackInfo ci) {
		this.sleep$keep = this.textLines;
	}

	@Inject(method = "refreshData", at = @At("TAIL"), require = 0)
	private void sleep$after(boolean lerp, float progress, CallbackInfo ci) {
		DisplayEntity.TextDisplayEntity self = (DisplayEntity.TextDisplayEntity) (Object) this;
		Text t = self.getText();
		int w = self.getLineWidth();
		Object font = GameFont.target;
		if (this.sleep$keep != null && w == this.sleep$width && font == this.sleep$font && t != null && (t == this.sleep$text || t.equals(this.sleep$text))) {
			this.textLines = this.sleep$keep; // same text, same width, same font: reuse
		} else {
			this.sleep$text = t;
			this.sleep$width = w;
			this.sleep$font = font;
		}
		this.sleep$keep = null;
	}
}
