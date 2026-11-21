package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.AnimationCatalog;
import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class LevelRendererMixin {
   @Inject(method = "method_72917", at = @At("HEAD"))
   private void deathanimations$sampleFrame(CallbackInfo ci) {
      for (DeathScene scene : DeathEffectController.scenes()) {
         AnimationCatalog.animateFrame(scene);
      }
   }
}
