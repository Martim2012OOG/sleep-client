package dev.solclient.mixin.splash;

import dev.solclient.gui.SplashLogo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
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
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {

	@Shadow private long fadeOutStart;
	@Shadow private long fadeInStart;
	@Shadow @Final private boolean fadeIn;

	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private ReloadInstance reload;
	@Shadow private float currentProgress;

	private static boolean fast(boolean reloading) {
		return reloading && dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.hideReloadScreen.on();
	}

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void caos$passive(CallbackInfo ci) {
		dev.solclient.FastReload.overlay = fast(fadeIn) ? this : null;
	}

	/** Instant reload: the game keeps running behind it (no pause in singleplayer). */
	@Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true, require = 0)
	private void caos$noPause(CallbackInfoReturnable<Boolean> cir) {
		if (fast(fadeIn)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Instant pack reload (idea from Resource Pack Optimizer by ravineclaw, MIT): when packs are added / removed the
	 * red Mojang screen, its 0.5 s fade-in, the 1 s minimum and the 1 s fade-out are skipped. You keep seeing the
	 * game and only a thin progress bar runs along the top until the reload really finishes.
	 */
	@Inject(method = "isReadyToFadeOut", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$noMinimum(CallbackInfoReturnable<Boolean> cir) {
		if (fast(fadeIn)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$fastReload(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashLogo.quietReload = false;
		if (!fast(fadeIn)) {
			return;
		}
		ci.cancel();
		long now = Util.getMillis();
		if (fadeInStart == -1L) {
			fadeInStart = now;
		}
		if (dev.solclient.compat.Mc.screen(minecraft) != null) {
			dev.solclient.compat.Mc.screen(minecraft).extractRenderStateWithTooltipAndSubtitles(context, mouseX, mouseY, delta);
		} else {
			dev.solclient.compat.Mc.extractDeferredSubtitles(minecraft);
		}
		if (fadeOutStart > -1L) {
			// finished (tick() already ran the vanilla finish handler): give the screen back right away
			dev.solclient.compat.Mc.setOverlay(minecraft, null);
			return;
		}
		currentProgress = Mth.clamp(Math.max(currentProgress, currentProgress * 0.8F + reload.getActualProgress() * 0.2F), 0.0F, 1.0F);
		int w = context.guiWidth();
		context.nextStratum();
		int acc = dev.solclient.gui.Theme.accent() & 0xFFFFFF, end = Mth.ceil(w * currentProgress);
		context.fill(0, 0, w, 2, 0x60000000);
		context.fill(0, 0, end, 2, 0xDCFFFFFF);
		context.fillGradient(0, 2, end, 6, 0x60000000 | acc, acc); // a soft glow under the line
		// a light that runs along the bar, and a bright tip
		int run = (int) ((now % 1100L) / 1100F * (end + 80)) - 40;
		context.fill(Math.max(0, run), 0, Math.min(end, run + 40), 2, 0xFF000000 | acc);
		context.fill(Math.max(0, end - 6), 0, end, 2, 0xFFFFFFFF);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void plegumo$logo(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			drawLogo(context);
		} catch (Throwable t) {
			// never crash the loading screen (e.g. maximising the window while it loads)
			if (!warned) {
				warned = true;
				System.err.println("[Caos] loading screen logo: " + t);
			}
		}
	}

	private static boolean warned;

	private void drawLogo(GuiGraphicsExtractor context) {
		if (!fadeIn && dev.solclient.mods.CinematicIntroMod.blackLoading()) {
			// Cinematic Intro: plain black loading screen (the intro video comes right after)
			long t = Util.getMillis();
			float d = fadeOutStart > -1L ? (t - fadeOutStart) / 1000F : -1F;
			float a = d >= 1F ? 1F - Mth.clamp(d - 1F, 0F, 1F) : 1F;
			context.fill(0, 0, context.guiWidth(), context.guiHeight(), ((int) (a * 255) << 24));
		}
		if (!SplashLogo.enabled(fadeIn)) {
			return;
		}
		// same fade as the vanilla overlay
		long now = Util.getMillis();
		float done = fadeOutStart > -1L ? (now - fadeOutStart) / 1000F : -1F;
		float start = fadeInStart > -1L ? (now - fadeInStart) / 500F : -1F;
		float alpha;
		if (done >= 1F) {
			alpha = 1F - Mth.clamp(done - 1F, 0F, 1F);
		} else if (fadeIn) {
			alpha = Mth.clamp(start, 0F, 1F);
		} else {
			alpha = 1F;
		}
		SplashLogo.draw(context, context.guiWidth(), context.guiHeight(), alpha);
	}
}
