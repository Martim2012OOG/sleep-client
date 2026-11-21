package dev.kuboext.streamer.mixin;

import dev.sleepclient.parkour.ParkourLive;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the parkour map, jumping on farmland never turns it into dirt (the map must never break). */
@Mixin(FarmlandBlock.class)
public class FarmlandTrampleMixin {

	@Inject(method = "setToDirt", at = @At("HEAD"), cancellable = true)
	private static void kubo$noTrample(Entity entity, BlockState state, World world, BlockPos pos, CallbackInfo ci) {
		if (entity != null && ParkourLive.protects(world)) {
			ci.cancel();
		}
	}
}
