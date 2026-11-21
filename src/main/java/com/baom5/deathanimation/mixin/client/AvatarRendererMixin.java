package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathAnimationConfig;
import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class AvatarRendererMixin {
   @Inject(method = "method_4216", at = @At("RETURN"), cancellable = true)
   private void deathanimations$useGoldSkin(PlayerEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
      DeathScene scene = DeathEffectController.sceneFor(state);
      if (scene != null
         && (
            scene.animation == DeathAnimationConfig.Animation.EXPLOSION && scene.progress() >= 0.12F
               || scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION && scene.progress() >= 0.25F
         )) {
         cir.setReturnValue(Identifier.of("deathanimation", "textures/entity/white_skin.png"));
      }
   }
}
