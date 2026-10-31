package dev.solclient.mixin.splash;

import dev.solclient.gui.SplashLogo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animated Plegumo logo on the Mojang loading screen. Turned off in Plegumo > Settings > Loading Screen
 * (separately for the game start and for resource pack reloads). Its own mixin config (required=false).
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

	@Shadow private long reloadCompleteTime;
	@Shadow private long reloadStartTime;
	@Shadow @Final private boolean reloading;

	private boolean plegumo$hid;

	/** Reload (resource pack added / changed): push everything the loading screen draws far off-screen. */
	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void plegumo$hideStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		plegumo$hid = false;
		boolean hide = reloading && dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.hideReloadScreen.on();
		SplashLogo.quietReload = hide;
		if (hide) {
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(-100000F, -100000F);
			plegumo$hid = true;
		}
	}

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void plegumo$hideEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (plegumo$hid) {
			plegumo$hid = false;
			context.getMatrices().popMatrix();
		}
	}

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void plegumo$logo(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			drawLogo(context);
		} catch (Throwable t) {
			// never crash the loading screen (e.g. maximising the window while it loads)
			if (!warned) {
				warned = true;
				System.err.println("[Sleep] loading screen logo: " + t);
			}
		}
	}

	private static boolean warned;

	private void drawLogo(DrawContext context) {
		if (!reloading && dev.solclient.mods.CinematicIntroMod.blackLoading()) {
			// Cinematic Intro: plain black loading screen (the intro video comes right after)
			long t = Util.getMeasuringTimeMs();
			float d = reloadCompleteTime > -1L ? (t - reloadCompleteTime) / 1000F : -1F;
			float a = d >= 1F ? 1F - MathHelper.clamp(d - 1F, 0F, 1F) : 1F;
			context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), ((int) (a * 255) << 24));
		}
		if (!SplashLogo.enabled(reloading)) {
			return;
		}
		// same fade as the vanilla overlay
		long now = Util.getMeasuringTimeMs();
		float done = reloadCompleteTime > -1L ? (now - reloadCompleteTime) / 1000F : -1F;
		float start = reloadStartTime > -1L ? (now - reloadStartTime) / 500F : -1F;
		float alpha;
		if (done >= 1F) {
			alpha = 1F - MathHelper.clamp(done - 1F, 0F, 1F);
		} else if (reloading) {
			alpha = MathHelper.clamp(start, 0F, 1F);
		} else {
			alpha = 1F;
		}
		SplashLogo.draw(context, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha);
	}
}
