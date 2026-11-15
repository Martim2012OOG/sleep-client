package dev.solclient.mixin.overlay;

import dev.solclient.mods.AspectRatioMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Aspect Ratio: the projection of the 3D world uses the chosen ratio instead of the window's. */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer")
public abstract class AspectRatioMixin {

	@ModifyArg(method = "getProjectionMatrix", index = 1, require = 0,
			at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"))
	private float kubo$aspect(float aspect) {
		return AspectRatioMod.apply(aspect);
	}
}
