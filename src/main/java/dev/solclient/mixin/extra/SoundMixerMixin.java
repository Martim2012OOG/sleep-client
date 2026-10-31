package dev.solclient.mixin.extra;

import dev.solclient.mods.SoundMixerMod;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sound Mixer: its own volume for footsteps, explosions, hits, rain... on top of Minecraft's categories. */
@Mixin(SoundSystem.class)
public class SoundMixerMixin {

	@Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$mixer(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		float k = SoundMixerMod.factor(sound);
		if (k != 1F) {
			cir.setReturnValue(Math.max(0F, Math.min(1F, cir.getReturnValueF() * k)));
		}
	}
}
