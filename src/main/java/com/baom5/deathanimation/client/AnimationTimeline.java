package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class AnimationTimeline {
   private AnimationTimeline() {
   }

   public static int ticks(DeathAnimationConfig.Animation animation) {
      if (animation == DeathAnimationConfig.Animation.PILOT_NESTED_DOLLS) {
         return 400;
      }

      if (animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
         return 320;
      }

      if (AnimationCatalog.contains(animation)) {
         return AnimationCatalog.recipe(animation).ticks();
      }

      return switch (animation) {
         case RAGDOLL -> 18;
         case PHYSICS_RAGDOLL, MOMENTUM_RAGDOLL -> 100;
         case GRAVE -> 32;
         case GHOST, HEAVEN -> 68;
         case BURN -> 68;
         case GORY_EXPLOSION -> 46;
         case EXPLOSION -> 48;
         case LIGHTNING -> 32;
         case FREEZE -> 50;
         case OPPONENT -> 44;
         case GOLD -> 140;
         case ANVIL -> 40;
         case AIR -> 64;
         case VOID, ENDER -> 48;
         case TOTEM -> 56;
         case DUST -> 52;
         case NONE -> 20;
         default -> 80;
      };
   }

   public static float phase(float progress, float start, float end) {
      return MathHelper.clamp((progress - start) / (end - start), 0.0F, 1.0F);
   }

   public static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   public static float easeOut(float t) {
      return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
   }

   public static float track(float time, float... keys) {
      if (keys.length >= 4 && keys.length % 2 == 0) {
         if (time <= keys[0]) {
            return keys[1];
         }

         for (int i = 2; i < keys.length; i += 2) {
            if (time <= keys[i]) {
               return MathHelper.lerp(smooth(phase(time, keys[i - 2], keys[i])), keys[i - 1], keys[i + 1]);
            }
         }

         return keys[keys.length - 1];
      } else {
         throw new IllegalArgumentException("Time/value pairs required");
      }
   }
}
