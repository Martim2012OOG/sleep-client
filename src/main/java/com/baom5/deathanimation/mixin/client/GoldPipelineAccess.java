package com.baom5.deathanimation.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(RenderPipelines.class)
public interface GoldPipelineAccess {
   @Invoker("method_67887")
   static RenderPipeline deathanimations$register(RenderPipeline pipeline) {
      throw new AssertionError();
   }
}
