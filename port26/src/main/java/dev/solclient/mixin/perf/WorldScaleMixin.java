package dev.solclient.mixin.perf;

import dev.solclient.render.WorldScale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sharp menus, fast world (see {@link WorldScale}): the world is drawn into a smaller picture. */
@Mixin(GameRenderer.class)
public class WorldScaleMixin {

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void kubo$frameStart(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
		WorldScale.reset();
	}

	@Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
	private void kubo$worldStart(DeltaTracker tickCounter, CallbackInfo ci) {
		WorldScale.begin();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
	private void kubo$worldEnd(DeltaTracker tickCounter, CallbackInfo ci) {
		WorldScale.end();
	}
}
