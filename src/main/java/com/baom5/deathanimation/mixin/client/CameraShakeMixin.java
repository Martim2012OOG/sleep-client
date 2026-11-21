package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import com.baom5.deathanimation.client.DominoBridgeCollapse;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
   @Shadow
   protected abstract void method_19322(Vec3d var1);

   @Inject(method = "method_19321", at = @At("TAIL"))
   private void deathanimations$shakeOnDominoImpact(World level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
      if (entity != null) {
         Camera camera = (Camera)(Object)this;
         Vec3d cameraPosition = camera.getCameraPos();
         Vec3d shake = Vec3d.ZERO;
         double strongest = 0.0;

         for (DeathScene scene : DeathEffectController.scenes()) {
            if (scene.level == level) {
               Vec3d candidate = DominoBridgeCollapse.cameraShake(scene);
               double magnitude = candidate.lengthSquared();
               if (!(magnitude <= strongest) && !(cameraPosition.squaredDistanceTo(scene.x, scene.floorY, scene.z) > 144.0)) {
                  strongest = magnitude;
                  shake = candidate;
               }
            }
         }

         if (strongest > 0.0) {
            this.method_19322(cameraPosition.add(shake));
         }
      }
   }
}
