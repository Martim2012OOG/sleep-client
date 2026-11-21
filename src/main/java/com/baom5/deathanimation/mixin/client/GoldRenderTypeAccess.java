package com.baom5.deathanimation.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(RenderLayer.class)
public interface GoldRenderTypeAccess {
   @Accessor("field_64013")
   RenderSetup deathanimations$setup();

   @Invoker("method_75940")
   static RenderLayer deathanimations$create(String name, RenderSetup setup) {
      throw new AssertionError();
   }
}
