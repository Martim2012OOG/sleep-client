package dev.sleepclient.mixin.extra;

import dev.sleepclient.mods.SoundMixerMod;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sound Mixer: its own volume for footsteps, explosions, hits, rain... on top of Minecraft's categories. */
@Mixin(SoundEngine.class)
public class SoundMixerMixin {

	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$mixer(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		float k = SoundMixerMod.factor(sound);
		if (k != 1F) {
			cir.setReturnValue(Math.max(0F, Math.min(1F, cir.getReturnValueF() * k)));
		}
	}
}
