package com.baom5.deathanimation.client;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PaperAirplaneFold {
   static final float LAUNCH = 0.91F;
   static final float WORLD_FLIGHT_DISTANCE = 96.0F;
   static final int ALL_SOUND_CUES = 2047;
   private static final float[] FOLD_SOUNDS = new float[]{0.21F, 0.27F, 0.34F, 0.42F, 0.5F, 0.58F, 0.67F, 0.745F, 0.8F};

   private PaperAirplaneFold() {
   }

   public static DeathScene.Root root(DeathScene scene) {
      if (scene.progress() >= 0.15F) {
         return sheetRoot(scene);
      }

      float flatten = AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.02F, 0.12F));
      float floor = scene.gui ? 0.0F : (float)(scene.floorY - scene.y);
      return new DeathScene.Root(
         0.0F, floor + 0.25F * flatten, 0.0F, 0.0F, scene.sceneFacing, 0.0F, 1.0F - 0.15F * flatten, 1.0F - 0.1F * flatten, 1.0F - 0.99F * flatten
      );
   }

   static DeathScene.Root sheetRoot(DeathScene scene) {
      float p = scene.progress();
      float flight = AnimationTimeline.phase(p, 0.91F, 1.0F);
      float windup = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.88F, 0.905F));
      float distance = -1.2F * windup;
      if (p >= 0.91F) {
         float end = scene.gui ? 3.5F : 96.0F;
         distance = -1.2F + (end + 1.2F) * (0.3F * flight + 0.7F * flight * flight);
      }

      float floor = scene.gui ? 0.0F : (float)(scene.floorY - scene.y);
      float hover = 0.025F * (float)Math.sin(scene.age * 0.12F) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.825F, 0.84F)));
      float scale = 1.0F - (scene.gui ? 0.97F : 0.45F) * AnimationTimeline.smooth(flight);
      float y = floor + 0.25F + hover + 0.95F * (1.0F - scale);
      Vector3f offset = forward(scene).mul(distance);
      return new DeathScene.Root(offset.x, y, offset.z, 0.0F, scene.sceneFacing, 0.0F, scale, scale, scale);
   }

   static float paperAlpha(float p) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.055F, 0.13F)) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.94F, 1.0F)));
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.paperAirplane == null) {
         scene.paperAirplane = new PaperAirplaneFold.State(scene);
      }
   }

   public static void animate(DeathScene scene) {
      PaperAirplaneFold.State state = scene.paperAirplane;
      if (state != null && !scene.gui && DeathAnimationConfig.INSTANCE.visualEffects) {
         float p = scene.progress();
         int tick = (int)scene.age;
         if (!(p < 0.91F) && !(p > 0.995F) && tick != state.lastTrailTick && tick % 4 == 0) {
            state.lastTrailTick = tick;
            DeathScene.Root root = sheetRoot(scene);
            Vector3f tail = forward(scene).mul(-0.7F);
            Vector3f wind = forward(scene).mul(-0.16F);
            EffectParticles.add(
               scene,
               ParticleTypes.CLOUD,
               scene.x + root.x() + tail.x,
               scene.y + root.y() + 0.949999988079071,
               scene.z + root.z() + tail.z,
               wind.x,
               0.006,
               wind.z
            );
         }
      }
   }

   public static void effects(DeathScene scene) {
      PaperAirplaneFold.State state = scene.paperAirplane;
      if (state != null) {
         SoundEvent page = (SoundEvent)Registries.SOUND_EVENT.get(Identifier.ofVanilla("item.book.page_turn"));
         if (page == null) {
            page = SoundEvents.BLOCK_WOOL_PLACE;
         }

         for (int i = 0; i < FOLD_SOUNDS.length; i++) {
            sound(scene, state, 1 << i, FOLD_SOUNDS[i], page, 0.28F, 0.85F + i * 0.045F);
         }

         sound(scene, state, 512, 0.875F, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.3F, 0.7F);
         sound(scene, state, 1024, 0.91F, SoundEvents.ITEM_ELYTRA_FLYING, 0.48F, 1.15F);
      }
   }

   private static void sound(DeathScene scene, PaperAirplaneFold.State state, int bit, float at, SoundEvent event, float volume, float pitch) {
      if (!(scene.progress() < at) && (state.soundMask & bit) == 0) {
         state.soundMask |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level.playSoundClient(scene.x, scene.floorY + 1.35, scene.z, event, SoundCategory.PLAYERS, volume * (scene.gui ? 0.22F : 1.0F), pitch, false);
         }
      }
   }

   private static Vector3f world(DeathScene scene, float x, float z) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(x, 0.0F, z));
   }

   static Vector3f forward(DeathScene scene) {
      return world(scene, 0.0F, -1.0F);
   }

   public static void discard(DeathScene scene) {
      scene.paperAirplane = null;
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final PaperAirplaneMesh.Print print;
      int soundMask;
      int lastTrailTick = -1;

      State(DeathScene scene) {
         this.print = new PaperAirplaneMesh.Print(scene);
      }
   }
}
