package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk builder threads call this, so no thread checks here. */
@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public class FoliageMixin {

	@Inject(method = "getRenderShape", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$foliage(CallbackInfoReturnable<RenderShape> cir) {
		if (OverlayMod.isHiddenFoliage((BlockState) (Object) this)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}
