package dev.sleepclient.mixin.overlay;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.mods.CustomSkyMod;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Sky. The overworld sky pass calls renderTopSky(int) first; when a photo is loaded we skip the vanilla
 * sky disc and instead run the vanilla renderEndSky() with our own texture + cube buffer swapped in for that one call.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRenderingMixin {

	@Shadow
	@Final
	@Mutable
	private AbstractTexture endSkyTexture;

	@Shadow
	@Final
	@Mutable
	private GpuBuffer endSkyBuffer;

	@Shadow
	public abstract void renderEndSky();

	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$customSky(int color, CallbackInfo ci) {
		if (!CustomSkyMod.prepare()) {
			return;
		}
		AbstractTexture oldTexture = this.endSkyTexture;
		GpuBuffer oldBuffer = this.endSkyBuffer;
		this.endSkyTexture = CustomSkyMod.texture();
		this.endSkyBuffer = CustomSkyMod.buffer();
		try {
			this.renderEndSky();
		} finally {
			this.endSkyTexture = oldTexture;
			this.endSkyBuffer = oldBuffer;
		}
		ci.cancel();
	}

	/** The dark disc under the horizon would paint over the photo. */
	@Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noDarkDisc(CallbackInfo ci) {
		if (CustomSkyMod.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noGlow(PoseStack matrices, float solarAngle, int color, CallbackInfo ci) {
		if (CustomSkyMod.hideGlow()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noCelestials(PoseStack matrices, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float alpha, float starBrightness, CallbackInfo ci) {
		if (CustomSkyMod.hideCelestials()) {
			ci.cancel();
		}
	}
}
