package dev.kuboext.far_horizons.mixin;

import dev.solclient.mods.FarHorizonsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Far Horizons: pushes the fog out to the LOD distance (the only int argument of applyFog is the view distance). */
/** priority 5000 = applied after Sodium / Iris / other mods, so the last word on the distance is Caos's. */
@Mixin(targets = "net.minecraft.client.render.fog.FogRenderer", priority = 5000)
public abstract class LodFogMixin {

	@ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private int kubo$lodFog(int viewDistance) {
		return FarHorizonsMod.fogDistance(viewDistance);
	}
}
