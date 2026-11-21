package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk builder threads call this, so no thread checks here. */
@Mixin(targets = "net.minecraft.block.AbstractBlock$AbstractBlockState")
public class FoliageMixin {

	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true, require = 0)
	private void sol$foliage(CallbackInfoReturnable<BlockRenderType> cir) {
		if (OverlayMod.isHiddenFoliage((BlockState) (Object) this)) {
			cir.setReturnValue(BlockRenderType.INVISIBLE);
		}
	}
}
