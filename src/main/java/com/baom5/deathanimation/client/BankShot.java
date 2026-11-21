package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class BankShot {
   static final float CUE_CONTACT = 0.35F;
   static final float BALL_CONTACT = 0.43F;
   static final float POCKET_ENTRY = 0.9F;
   private static final float R = 0.075F;
   private static final Vector3f START = new Vector3f(-0.3F, 0.0F, 0.0F);
   private static final Vector3f POCKET = new Vector3f(1.82F, 0.0F, 0.92F);
   private static final float BANK_Z = -0.825F;
   private static final Vector3f BANK = new Vector3f(START.x + (POCKET.x - START.x) * -0.825F / (-1.65F - POCKET.z), 0.0F, -0.825F);
   private static final Vector3f IN = new Vector3f(BANK).sub(START).normalize();
   private static final Vector3f OUT = new Vector3f(POCKET).sub(BANK).normalize();
   private static final float FIRST = START.distance(BANK);
   private static final float SECOND = BANK.distance(POCKET);
   static final float BANK_CONTACT = 0.43F + 0.46999997F * FIRST / (FIRST + SECOND);
   private static final float WHITE_START = 0.72F;

   private BankShot() {
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
   }

   static void animate(DeathScene scene) {
   }

   static DeathScene.Root root(DeathScene scene) {
      float curl = AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.08F, 0.31F));
      float size = 1.0F - 0.94F * curl;
      float y = 0.95F + (0.075F - 0.9F * size) * curl;
      Vector3f offset = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(START).mul(curl));
      return new DeathScene.Root(offset.x, (scene.gui ? 0.0F : (float)(scene.floorY - scene.y)) + y, offset.z, 0.0F, scene.sceneFacing, 0.0F, size, size, size);
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float curl = AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.08F, 0.31F));

      float pitch = switch (part) {
         case 0 -> 30.0F;
         default -> 20.0F;
         case 2, 3 -> -100.0F;
         case 4, 5 -> -95.0F;
      };
      float roll = part == 2 ? -18.0F : (part == 3 ? 18.0F : 0.0F);
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, (float)Math.toRadians(pitch * curl), 0.0F, (float)Math.toRadians(roll * curl));
   }

   static Vector3f shotPosition(float p) {
      float travel = Math.clamp((p - 0.43F) / 0.46999997F, 0.0F, 1.0F) * (FIRST + SECOND);
      Vector3f position = travel <= FIRST ? new Vector3f(IN).mul(travel).add(START) : new Vector3f(OUT).mul(travel - FIRST).add(BANK);
      float drop = AnimationTimeline.phase(p, 0.9F, 0.985F);
      position.y = 1.025F - 0.65F * drop * drop;
      return position;
   }

   private static Quaternionf roll(Vector3f direction, float distance) {
      return new Quaternionf().rotationAxis(distance / 0.075F, direction.z, 0.0F, -direction.x);
   }

   private static Quaternionf shotRotation(float p) {
      float travel = Math.clamp((p - 0.43F) / 0.46999997F, 0.0F, 1.0F) * (FIRST + SECOND);
      Quaternionf first = roll(IN, Math.min(travel, FIRST));
      return travel <= FIRST ? first : roll(OUT, travel - FIRST).mul(first);
   }

   static List<BilliardsTableModel.Ball> balls(float p) {
      List<BilliardsTableModel.Ball> balls = new ArrayList<>(16);
      int[] numbers = new int[]{1, 10, 2, 3, 8, 11, 4, 12, 5, 13, 6, 14, 7, 15};
      int next = 0;

      for (int row = 0; row < 5; row++) {
         for (int column = 0; column <= row && next < numbers.length; column++) {
            float x = -0.8F - row * 0.134F;
            float z = -0.15F + (column - row * 0.5F) * 0.154F;
            balls.add(new BilliardsTableModel.Ball(numbers[next++], new Vector3f(x, 1.025F, z), new Quaternionf().rotateX(0.5F).rotateY(0.35F)));
         }
      }

      float whiteTravel = AnimationTimeline.phase(p, 0.35F, 0.43F) * 0.57000005F;
      Vector3f white = new Vector3f(IN).mul(-0.72F + whiteTravel).add(START);
      white.y = 1.025F;
      balls.add(new BilliardsTableModel.Ball(0, white, roll(IN, whiteTravel)));
      if (p >= 0.3F && p < 0.985F) {
         balls.add(new BilliardsTableModel.Ball(9, shotPosition(p), shotRotation(p)));
      }

      return balls;
   }

   static BilliardsTableModel.Mesh cueMesh(float p) {
      if (!(p < 0.22F) && !(p >= 0.405F)) {
         float wind = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.22F, 0.29F));
         float strike = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.29F, 0.35F));
         float distance = 0.915F + 0.12F * wind - 0.24F * strike;
         Vector3f tip = new Vector3f(IN).mul(-distance).add(START);
         tip.y = 1.025F;
         Vector3f ferrule = new Vector3f(tip).sub(new Vector3f(IN).mul(0.045F)).add(0.0F, 0.006F, 0.0F);
         Vector3f back = new Vector3f(tip).sub(new Vector3f(IN).mul(1.12F)).add(0.0F, 0.4F, 0.0F);
         Vector3f grip = new Vector3f(tip).lerp(back, 0.76F);
         BilliardsTableModel.Mesh mesh = new BilliardsTableModel.Mesh();
         mesh.rod(tip, ferrule, 0.01F, 0.011F, 3);
         mesh.rod(ferrule, new Vector3f(tip).lerp(back, 0.09F), 0.011F, 0.012F, 6);
         mesh.rod(new Vector3f(tip).lerp(back, 0.09F), grip, 0.012F, 0.018F, 1);
         mesh.rod(grip, back, 0.018F, 0.022F, 0);
         return mesh;
      } else {
         return null;
      }
   }

   static void effects(DeathScene scene) {
      float p = scene.progress();
      float[] times = new float[]{0.35F, 0.43F, BANK_CONTACT, 0.92499995F};

      for (int i = 0; i < times.length; i++) {
         int bit = 1 << i;
         if (!(p < times[i]) && (scene.sounded & bit) == 0) {
            scene.sounded |= bit;
            scene.emitted |= bit;
            Vector3f local = i == 0
               ? new Vector3f(IN).mul(-0.72F).add(START)
               : (i == 1 ? new Vector3f(START) : (i == 2 ? new Vector3f(BANK) : new Vector3f(POCKET)));
            local.y = 1.025F;
            new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(local);
            if (DeathAnimationConfig.INSTANCE.sounds) {
               scene.level
                  .playSoundClient(
                     scene.x + local.x,
                     scene.floorY + local.y,
                     scene.z + local.z,
                     i == 3 ? SoundEvents.BLOCK_WOOL_PLACE : (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.12F : 0.48F,
                     i == 2 ? 0.85F : (i == 3 ? 0.65F : 1.25F),
                     false
                  );
            }

            if (i == 2 && !scene.gui && DeathAnimationConfig.INSTANCE.visualEffects) {
               EffectParticles.add(scene, ParticleTypes.CRIT, scene.x + local.x, scene.floorY + local.y, scene.z + local.z, 0.0, 0.015, 0.0);
            }
         }
      }
   }
}
