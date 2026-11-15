package dev.solclient.mixin.fightcam;

import dev.solclient.fightcam.Replay;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a replay is open the real entities are hidden: the recorded copies are drawn instead. */
@Mixin(EntityRenderDispatcher.class)
public abstract class ReplayHideMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$hideLive(Entity entity, @Coerce Object frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (Replay.active()) {
			cir.setReturnValue(false);
		}
	}
}
