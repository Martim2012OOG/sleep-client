package com.baom5.deathanimation.mixin.client;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(RenderSetup.class)
public interface GoldRenderSetupAccess {
   @Accessor("field_63987")
   Map<String, ?> deathanimations$textures();
}
