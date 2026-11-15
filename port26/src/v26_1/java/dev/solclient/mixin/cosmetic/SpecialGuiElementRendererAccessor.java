package dev.solclient.mixin.cosmetic;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PictureInPictureRenderer.class)
public interface SpecialGuiElementRendererAccessor {

	@Accessor("bufferSource")
	MultiBufferSource.BufferSource plegumo$vertexConsumers();
}
