package dev.solclient.mixin.overlay;

import dev.solclient.mods.BossBarMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class BossBarMixin {

	@Unique
	private boolean plegumo$pushed;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$before(DrawContext c, CallbackInfo ci) {
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
			float cx = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2F;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(cx, m.offset.f());
			c.getMatrices().scale(s, s);
			c.getMatrices().translate(-cx, 0);
			plegumo$pushed = true;
		}
	}

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void plegumo$after(DrawContext c, CallbackInfo ci) {
		if (plegumo$pushed) {
			c.getMatrices().popMatrix();
			plegumo$pushed = false;
		}
	}
}
