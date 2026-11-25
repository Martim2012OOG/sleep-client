package dev.kuboext.streamer.mixin;

import dev.solclient.parkour.ParkourLive;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the parkour map, jumping on farmland never turns it into dirt (the map must never break). */
@Mixin(FarmlandBlock.class)
public class FarmlandTrampleMixin {

	@Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
	private static void kubo$noTrample(Entity entity, BlockState state, Level world, BlockPos pos, CallbackInfo ci) {
		if (entity != null && ParkourLive.protects(world)) {
			ci.cancel();
		}
	}
}
