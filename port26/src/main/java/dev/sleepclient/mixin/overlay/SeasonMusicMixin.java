package dev.sleepclient.mixin.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Christmas / Halloween music in the menus (see SeasonMusic). */
@Mixin(Minecraft.class)
public abstract class SeasonMusicMixin {

	@Inject(method = "getSituationalMusic", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$seasonMusic(CallbackInfoReturnable<Music> cir) {
		try {
			Music m = dev.sleepclient.gui.SeasonMusic.current();
			if (m != null) {
				cir.setReturnValue(m);
			}
		} catch (Throwable ignored) {
		}
	}
}
