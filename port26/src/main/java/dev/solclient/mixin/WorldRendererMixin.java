package dev.solclient.mixin;

import dev.solclient.mods.BlockSelectionMod;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

	/** drawBlockOutline(..., OutlineRenderState state, int color, float lineWidth) */
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, require = 0)
	private int sol$outlineColour(int color) {
		return BlockSelectionMod.color(color);
	}

	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, require = 0)
	private float sol$outlineWidth(float width) {
		return BlockSelectionMod.width(width);
	}
}
