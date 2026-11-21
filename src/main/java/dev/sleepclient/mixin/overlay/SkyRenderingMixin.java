package dev.sleepclient.mixin.overlay;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.sleepclient.mods.CustomSkyMod;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
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
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

	@Shadow
	@Final
	@Mutable
	private AbstractTexture endSkyTexture;

	@Shadow
	@Final
	@Mutable
	private GpuBuffer endSkyVertexBuffer;

	@Shadow
	public abstract void renderEndSky();

	@Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$customSky(int color, CallbackInfo ci) {
		if (!CustomSkyMod.prepare()) {
			return;
		}
		AbstractTexture oldTexture = this.endSkyTexture;
		GpuBuffer oldBuffer = this.endSkyVertexBuffer;
		this.endSkyTexture = CustomSkyMod.texture();
		this.endSkyVertexBuffer = CustomSkyMod.buffer();
		try {
			this.renderEndSky();
		} finally {
			this.endSkyTexture = oldTexture;
			this.endSkyVertexBuffer = oldBuffer;
		}
		ci.cancel();
	}

	/** The dark disc under the horizon would paint over the photo. */
	@Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noDarkDisc(CallbackInfo ci) {
		if (CustomSkyMod.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noGlow(MatrixStack matrices, float solarAngle, int color, CallbackInfo ci) {
		if (CustomSkyMod.hideGlow()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noCelestials(MatrixStack matrices, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float alpha, float starBrightness, CallbackInfo ci) {
		if (CustomSkyMod.hideCelestials()) {
			ci.cancel();
		}
	}
}
