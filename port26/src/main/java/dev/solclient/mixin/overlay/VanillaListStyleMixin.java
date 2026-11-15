package dev.solclient.mixin.overlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lists (worlds, servers...): a soft dark panel instead of the dirt-style strip and its separator lines. */
@Mixin(AbstractSelectionList.class)
public abstract class VanillaListStyleMixin {

	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$listBg(GuiGraphicsExtractor c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on()) {
			return;
		}
		AbstractWidget w = (AbstractWidget) (Object) this;
		c.fill(w.getX(), w.getY(), w.getRight(), w.getBottom(), 0x66080A0F);
		ci.cancel();
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$separators(GuiGraphicsExtractor c, CallbackInfo ci) {
		if (!dev.solclient.gui.VanillaStyle.on()) {
			return;
		}
		AbstractWidget w = (AbstractWidget) (Object) this;
		c.fill(w.getX(), w.getY() - 1, w.getRight(), w.getY(), 0x22FFFFFF);
		c.fill(w.getX(), w.getBottom(), w.getRight(), w.getBottom() + 1, 0x22FFFFFF);
		ci.cancel();
	}
}
