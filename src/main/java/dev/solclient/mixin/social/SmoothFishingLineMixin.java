package dev.solclient.mixin.social;

import dev.solclient.mods.SmoothFishingLineMod;
import net.minecraft.client.render.entity.FishingBobberEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The line is a loop of 16 pieces (i <= 16, percentage(i, 16), percentage(i + 1, 16)); every 16 in it is replaced by the
 * same bigger number, so the curve is the same, only smoother.
 * The vanilla line colour is plain black (-16777216): that number is replaced by the colour chosen in the mod
 * (one colour, rainbow, gradient...).
 */
@Mixin(FishingBobberEntityRenderer.class)
public abstract class SmoothFishingLineMixin {

	@ModifyConstant(method = "method_72983", constant = @Constant(intValue = 16), require = 0)
	private static int kubo$smoothLine(int vanilla) {
		return SmoothFishingLineMod.pieces();
	}

	/** The colour is written either in the lambda or in the helper that draws one piece: both are tried. */
	@ModifyConstant(method = {"method_72983", "renderFishingLine"}, constant = @Constant(intValue = -16777216), require = 0)
	private static int kubo$lineColour(int vanilla) {
		return SmoothFishingLineMod.nextColour(vanilla);
	}
}
