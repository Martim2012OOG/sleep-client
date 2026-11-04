package dev.solclient.mixin.splash;

import dev.solclient.gui.SplashLogo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourceReload;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
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

	@Shadow @Final private MinecraftClient client;
	@Shadow @Final private ResourceReload reload;
	@Shadow private float progress;

	private static boolean fast(boolean reloading) {
		return reloading && dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.hideReloadScreen.on();
	}

	/**
	 * Instant pack reload (idea from Resource Pack Optimizer by ravineclaw, MIT): when packs are added / removed the
	 * red Mojang screen, its 0.5 s fade-in, the 1 s minimum and the 1 s fade-out are skipped. You keep seeing the
	 * game and only a thin progress bar runs along the top until the reload really finishes.
	 */
	@Inject(method = "isInGracePeriod", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$noMinimum(CallbackInfoReturnable<Boolean> cir) {
		if (fast(reloading)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$fastReload(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashLogo.quietReload = false;
		if (!fast(reloading)) {
			return;
		}
		ci.cancel();
		long now = Util.getMeasuringTimeMs();
		if (reloadStartTime == -1L) {
			reloadStartTime = now;
		}
		if (client.currentScreen != null) {
			client.currentScreen.renderWithTooltip(context, mouseX, mouseY, delta);
		} else {
			client.inGameHud.renderDeferredSubtitles();
		}
		if (reloadCompleteTime > -1L) {
			// finished (tick() already ran the vanilla finish handler): give the screen back right away
			client.setOverlay(null);
			return;
		}
		progress = MathHelper.clamp(Math.max(progress, progress * 0.8F + reload.getProgress() * 0.2F), 0.0F, 1.0F);
		int w = context.getScaledWindowWidth();
		context.createNewRootLayer();
		context.fill(0, 0, w, 2, 0x60000000);
		context.fill(0, 0, MathHelper.ceil(w * progress), 2, 0xDCFFFFFF);
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
