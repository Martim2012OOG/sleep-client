package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathBodyRig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl.ModelCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ModelCommandRenderer.class)
public abstract class ModelFeatureRendererMixin {
   @Inject(method = "method_73000", at = @At("HEAD"))
   private void deathanimations$prepareRig(
      ModelCommand<?> submit, RenderLayer type, VertexConsumer vertices, OutlineVertexConsumerProvider outlines, Immediate crumbling, CallbackInfo ci
   ) {
      DeathBodyRig.prepareForRender(submit.model(), submit.state());
   }

   @Inject(method = "method_73000", at = @At("RETURN"))
   private void deathanimations$clearRig(
      ModelCommand<?> submit, RenderLayer type, VertexConsumer vertices, OutlineVertexConsumerProvider outlines, Immediate crumbling, CallbackInfo ci
   ) {
      DeathBodyRig.clear();
   }
}
