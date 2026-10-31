package dev.solclient.mixin;

import dev.solclient.mods.BlockSelectionMod;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

	/** drawBlockOutline(..., OutlineRenderState state, int color, float lineWidth) */
	@ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, require = 0)
	private int sol$outlineColour(int color) {
		return BlockSelectionMod.color(color);
	}

	@ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, require = 0)
	private float sol$outlineWidth(float width) {
		return BlockSelectionMod.width(width);
	}
}
