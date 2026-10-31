package dev.solclient.mixin.fightcam;

import dev.solclient.fightcam.Replay;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers every block that changes in the client world, so the replay can show the world as it was. */
@Mixin(World.class)
public abstract class ReplayBlockMixin {

	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"), require = 0)
	private void kubo$recordBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		World self = (World) (Object) this;
		if (self.isClient() && !Replay.applying()) {
			try {
				Replay.onBlockChange(pos, self.getBlockState(pos), state);
			} catch (RuntimeException ignored) {
			}
		}
	}
}
