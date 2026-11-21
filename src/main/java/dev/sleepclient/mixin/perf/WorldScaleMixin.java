package dev.sleepclient.mixin.perf;

import dev.sleepclient.render.WorldScale;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sharp menus, fast world (see {@link WorldScale}): the world is drawn into a smaller picture. */
@Mixin(GameRenderer.class)
public class WorldScaleMixin {

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void kubo$frameStart(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		WorldScale.reset();
	}

	@Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
	private void kubo$worldStart(RenderTickCounter tickCounter, CallbackInfo ci) {
		WorldScale.begin();
	}

	@Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
	private void kubo$worldEnd(RenderTickCounter tickCounter, CallbackInfo ci) {
		WorldScale.end();
	}
}
