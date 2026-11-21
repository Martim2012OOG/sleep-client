package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.BossBarMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossBarMixin {

	@Unique
	private boolean plegumo$pushed;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$before(GuiGraphicsExtractor c, CallbackInfo ci) {
		plegumo$pushed = false;
		if (!BossBarMod.on()) {
			return;
		}
		BossBarMod m = BossBarMod.instance;
		if (m.hide.on()) {
			ci.cancel();
			return;
		}
		float s = m.scale.f() / 100F;
		if (s != 1F || m.offset.f() != 0) {
			float cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2F;
			c.pose().pushMatrix();
			c.pose().translate(cx, m.offset.f());
			c.pose().scale(s, s);
			c.pose().translate(-cx, 0);
			plegumo$pushed = true;
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void plegumo$after(GuiGraphicsExtractor c, CallbackInfo ci) {
		if (plegumo$pushed) {
			c.pose().popMatrix();
			plegumo$pushed = false;
		}
	}
}
