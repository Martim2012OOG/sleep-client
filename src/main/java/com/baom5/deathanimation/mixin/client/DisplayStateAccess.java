package com.baom5.deathanimation.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.RenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(DisplayEntity.class)
public interface DisplayStateAccess {
   @Invoker("method_49774")
   RenderState deathanimations$freshRenderState();

   @Invoker("method_49776")
   void deathanimations$refreshSubState(boolean var1, float var2);

   @Accessor("field_43184")
   void deathanimations$setRenderState(RenderState var1);

   @Accessor("field_43185")
   void deathanimations$setDirty(boolean var1);
}
