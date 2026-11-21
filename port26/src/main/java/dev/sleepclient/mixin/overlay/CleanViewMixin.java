package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.CleanViewMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View: drops the effect particles that come out of your own body. */
@Mixin(ClientLevel.class)
public abstract class CleanViewMixin {

	@Inject(method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$cleanView(ParticleOptions effect, boolean force, boolean canSpawnOnMinimal, double x, double y, double z, double vx, double vy, double vz,
			CallbackInfo ci) {
		if (CleanViewMod.hides(effect, x, y, z)) {
			ci.cancel();
		}
	}
}
