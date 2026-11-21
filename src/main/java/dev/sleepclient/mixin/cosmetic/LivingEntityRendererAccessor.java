package dev.sleepclient.mixin.cosmetic;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

	@Invoker("addFeature")
	boolean plegumo$addFeature(FeatureRenderer<?, ?> feature);
}
