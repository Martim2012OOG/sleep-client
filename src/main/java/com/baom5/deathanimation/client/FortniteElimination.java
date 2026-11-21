package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class FortniteElimination {
   private FortniteElimination() {
   }

   public static Vector3f beamCenter(DeathScene scene) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(0.0F, (float)(scene.floorY - scene.y), 0.85F));
   }

   public static Vector3f intake(DeathScene scene) {
      return beamCenter(scene).add(0.0F, 3.05F, 0.0F);
   }

   public static Vector3f dissolvingPart(DeathScene scene, int alternatingIndex) {
      float p = scene.progress();
      int part = p < 0.43F ? 4 + (alternatingIndex & 1) : (p < 0.61F ? 2 + (alternatingIndex & 1) : (p < 0.64F ? 1 : 0));
      return Acrobatics.partCenter(scene, part);
   }
}
