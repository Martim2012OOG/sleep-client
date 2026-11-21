package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.FullbrightMod;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: remembers which option is the gamma one, once, when the options are made (see FullbrightMixin). */
@Mixin(Options.class)
public class FullbrightGammaCaptureMixin {

	@Shadow
	@Final
	private OptionInstance<Double> gamma;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void kubo$rememberGamma(CallbackInfo ci) {
		FullbrightMod.gammaOption = gamma;
	}

	/** While options.txt is written the real brightness is saved (the boosted 15.0 is not a valid value and broke it). */
	@Inject(method = "save", at = @At("HEAD"), require = 0)
	private void kubo$writeStart(CallbackInfo ci) {
		FullbrightMod.saving = true;
	}

	@Inject(method = "save", at = @At("RETURN"), require = 0)
	private void kubo$writeEnd(CallbackInfo ci) {
		FullbrightMod.saving = false;
	}
}
