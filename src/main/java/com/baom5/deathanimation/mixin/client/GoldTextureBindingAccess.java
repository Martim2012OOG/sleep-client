package com.baom5.deathanimation.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/class_12247$class_12338")
public interface GoldTextureBindingAccess {
   @Accessor("comp_5228")
   Identifier deathanimations$texture();
}
