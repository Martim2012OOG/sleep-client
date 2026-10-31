package dev.solclient.mixin.overlay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lists (worlds, servers...): a soft dark panel instead of the dirt-style strip and its separator lines. */
@Mixin(EntryListWidget.class)
public abstract class VanillaListStyleMixin {

	@Inject(method = "drawMenuListBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$listBg(DrawContext c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on()) {
			return;
		}
		ClickableWidget w = (ClickableWidget) (Object) this;
		c.fill(w.getX(), w.getY(), w.getRight(), w.getBottom(), 0x66080A0F);
		ci.cancel();
	}

	@Inject(method = "drawHeaderAndFooterSeparators", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$separators(DrawContext c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on()) {
			return;
		}
		ClickableWidget w = (ClickableWidget) (Object) this;
		c.fill(w.getX(), w.getY() - 1, w.getRight(), w.getY(), 0x22FFFFFF);
		c.fill(w.getX(), w.getBottom(), w.getRight(), w.getBottom() + 1, 0x22FFFFFF);
		ci.cancel();
	}
}
