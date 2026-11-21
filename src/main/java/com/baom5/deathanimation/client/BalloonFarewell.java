package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;

@Environment(EnvType.CLIENT)
public final class BalloonFarewell {
   private BalloonFarewell() {
   }

   public static Float propAlpha(BlockDisplayEntity prop) {
      DeathScene hover = DeathEffectController.hoverScene();
      if (owns(hover, prop)) {
         return AnimationCatalog.alpha(hover);
      }

      for (DeathScene scene : DeathEffectController.scenes()) {
         if (owns(scene, prop)) {
            return AnimationCatalog.alpha(scene);
         }
      }

      return null;
   }

   private static boolean owns(DeathScene scene, BlockDisplayEntity prop) {
      return scene != null && scene.animation == DeathAnimationConfig.Animation.BALLOON && scene.props.contains(prop);
   }

   @Environment(EnvType.CLIENT)
   public record FadingVertices(VertexConsumer target, float alpha) implements VertexConsumer {
      public VertexConsumer vertex(float x, float y, float z) {
         this.target.vertex(x, y, z);
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         this.target.color(red, green, blue, Math.round(alpha * this.alpha));
         return this;
      }

      public VertexConsumer color(int argb) {
         return this.color(argb >>> 16 & 0xFF, argb >>> 8 & 0xFF, argb & 0xFF, argb >>> 24);
      }

      public VertexConsumer texture(float u, float v) {
         this.target.texture(u, v);
         return this;
      }

      public VertexConsumer overlay(int u, int v) {
         this.target.overlay(u, v);
         return this;
      }

      public VertexConsumer light(int u, int v) {
         this.target.light(u, v);
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         this.target.normal(x, y, z);
         return this;
      }

      public VertexConsumer lineWidth(float width) {
         this.target.lineWidth(width);
         return this;
      }
   }
}
