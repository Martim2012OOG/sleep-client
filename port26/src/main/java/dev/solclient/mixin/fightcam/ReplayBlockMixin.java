package dev.solclient.mixin.fightcam;

import dev.solclient.fightcam.Replay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers every block that changes in the client world, so the replay can show the world as it was. */
@Mixin(Level.class)
public abstract class ReplayBlockMixin {

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), require = 0)
	private void kubo$recordBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		Level self = (Level) (Object) this;
		if (self.isClientSide() && !Replay.applying()) {
			try {
				Replay.onBlockChange(pos, self.getBlockState(pos), state);
			} catch (RuntimeException ignored) {
			}
		}
	}
}
