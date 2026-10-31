package dev.solclient.mixin.overlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.MusicSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Christmas / Halloween music in the menus (see SeasonMusic). */
@Mixin(MinecraftClient.class)
public abstract class SeasonMusicMixin {

	@Inject(method = "getMusicInstance", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$seasonMusic(CallbackInfoReturnable<MusicSound> cir) {
		try {
			MusicSound m = dev.solclient.gui.SeasonMusic.current();
			if (m != null) {
				cir.setReturnValue(m);
			}
		} catch (Throwable ignored) {
		}
	}
}
