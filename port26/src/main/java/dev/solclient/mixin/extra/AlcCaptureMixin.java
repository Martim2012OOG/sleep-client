package dev.solclient.mixin.extra;

import dev.solclient.voice.persona.PersonaHook;
import org.lwjgl.openal.ALC11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the Personas mod reshape the microphone for every voice mod that records through OpenAL. Optional (require = 0). */
@Mixin(value = ALC11.class, remap = false)
public abstract class AlcCaptureMixin {

	@Inject(method = "nalcCaptureOpenDevice", at = @At("RETURN"), require = 0, remap = false)
	private static void kubo$opened(long name, int freq, int format, int size, CallbackInfoReturnable<Long> cir) {
		PersonaHook.opened(cir.getReturnValue(), freq, format);
	}

	@Inject(method = "nalcCaptureSamples", at = @At("RETURN"), require = 0, remap = false)
	private static void kubo$samples(long device, long buffer, int samples, CallbackInfo ci) {
		PersonaHook.samples(device, buffer, samples);
	}
}
