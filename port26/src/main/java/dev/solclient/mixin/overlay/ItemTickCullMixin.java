package dev.solclient.mixin.overlay;

import dev.solclient.mods.PerformanceMod;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crowd Guard, part 2: thousands of dropped items (a big TNT blast, a mob farm) cost far more in the game's TICK than in
 * drawing: every item moves and collides with blocks, on your computer too. The items that Crowd Guard hides (far away
 * or beyond the nearest ones) are simply not ticked on the client. The server still owns them: nothing changes in the
 * world, you can still pick them up, and they come back to life the moment they are near you again.
 */
@Mixin(ItemEntity.class)
public abstract class ItemTickCullMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$cullTick(CallbackInfo ci) {
		try {
			if (PerformanceMod.cullTick((net.minecraft.world.entity.Entity) (Object) this)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
