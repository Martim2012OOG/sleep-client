package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class WashingMachineCycle {
   static final float CENTER_Y = 1.35F;
   static final float CENTER_Z = 0.65F;
   static final float FRONT = -0.48F;
   static final float BACK = 1.62F;
   static final float RADIUS = 1.025F;
   static final float CAPTURE = 0.43F;
   static final float EJECT = 0.87F;
   private static final float CABINET_BOTTOM_OFFSET = 0.0F;

   private WashingMachineCycle() {
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.washer == null) {
         WashingMachineCycle.State state = new WashingMachineCycle.State();
         scene.washer = state;
         state.groundY = sampleGround(scene) - 0.0;
         part(scene, state, add, "washer_cabinet", 0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);
         part(scene, state, add, "washer_drum", 0.0F, 1.35F, -0.38F, 2.0F, 2.0F, 2.0F);
         part(scene, state, add, "washer_door", -1.15F, 1.35F, -0.62F, 2.0F, 2.0F, 2.0F);
         animate(scene);
      }
   }

   private static void part(
      DeathScene scene, WashingMachineCycle.State state, Consumer<Entity> add, String model, float x, float y, float z, float sx, float sy, float sz
   ) {
      ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display != null) {
         display.setItemDisplayContext(ItemDisplayContext.FIXED);
         ItemStack stack = new ItemStack(Items.IRON_INGOT);
         stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "mass/" + model));
         display.setItemStack(stack);
         display.setBrightness(new Brightness(15, 15));
         add.accept(display);
         state.parts.add(display);
         transform(scene, display, x, y, z, sx, sy, sz, new Quaternionf());
      }
   }

   public static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      float entry = smooth(phase(p, 0.16F, 0.39F));
      Vector3f position = world(scene, 0.0F, MathHelper.lerp(entry, -1.85F, 0.65F));
      float bend = -14.0F * (float)Math.sin(3.141592653589793 * entry);
      float lift = 0.45F * smooth(phase(p, 0.16F, 0.25F));
      return new DeathScene.Root(
         position.x, (scene.gui ? 0.0F : (float)(scene.floorY - scene.y)) + lift, position.z, bend, scene.sceneFacing + 180.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.9F
      );
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float entry = phase(scene.progress(), 0.16F, 0.39F);
      float pull = (float)Math.sin(3.141592653589793 * entry);
      float lag = (float)Math.sin(scene.age * 0.6F + part) * pull;
      float pitch = part == 0 ? -0.15F * pull : (part != 2 && part != 3 ? (part >= 4 ? lag * 0.15F : 0.0F) : -0.85F * pull + lag * 0.12F);
      float roll = part == 2 ? -0.12F * pull : (part == 3 ? 0.12F * pull : 0.0F);
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, pitch, 0.0F, roll);
   }

   public static void animate(DeathScene scene) {
      WashingMachineCycle.State state = scene.washer;
      if (state != null && state.parts.size() >= 3) {
         float p = scene.progress();
         if (p < 0.43F) {
            state.ejected = false;
            state.boosted = false;
            state.landedAge = -1.0F;
         }

         transform(scene, state.parts.get(0), 0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F, new Quaternionf());
         transform(scene, state.parts.get(1), 0.0F, 1.35F, -0.38F, 2.0F, 2.0F, 2.0F, new Quaternionf().rotationZ((float)Math.toRadians(spinDegrees(p))));
         float open = smooth(phase(p, 0.04F, 0.14F)) * (1.0F - smooth(phase(p, 0.39F, 0.45F))) + smooth(phase(p, 0.81F, 0.86F));
         transform(
            scene,
            state.parts.get(2),
            -1.15F,
            1.35F,
            -0.62F,
            2.0F,
            2.0F,
            2.0F,
            new Quaternionf().rotationY((float)Math.toRadians(108.0F * MathHelper.clamp(open, 0.0F, 1.0F)))
         );
      }
   }

   public static void effects(DeathScene scene) {
      WashingMachineCycle.State state = scene.washer;
      if (state != null) {
         float p = scene.progress();
         sound(scene, state, 1, 0.04F, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.5F, 0.92F);
         sound(scene, state, 2, 0.39F, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.54F, 1.12F);
         sound(scene, state, 4, 0.46F, SoundEvents.BLOCK_PISTON_EXTEND, 0.32F, 0.72F);
         sound(scene, state, 8, 0.8F, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.44F, 1.6F);
         sound(scene, state, 16, 0.81F, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.56F, 0.85F);
         sound(scene, state, 32, 0.87F, SoundEvents.BLOCK_PISTON_CONTRACT, 0.42F, 0.85F);
         int batch = (int)(scene.age / 12.0F);
         if (p > 0.46F && p < 0.8F && batch > state.lastWashSound) {
            state.lastWashSound = batch;
            if (DeathAnimationConfig.INSTANCE.sounds) {
               Vector3f center = world(scene, 0.0F, 0.65F);
               scene.level
                  .playSoundClient(
                     scene.x + center.x,
                     groundY(scene) + 1.350000023841858,
                     scene.z + center.z,
                     SoundEvents.ENTITY_GENERIC_SPLASH,
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.06F : 0.17F,
                     0.85F,
                     false
                  );
            }
         }

         int tick = (int)scene.age;
         if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && state.particleTick != tick && tick % 3 == 0) {
            state.particleTick = tick;
            if (p >= 0.16F && p < 0.39F) {
               Vector3f point = world(scene, (float)Math.sin(tick) * 0.25F, -1.4F);
               Vector3f velocity = world(scene, 0.0F, 0.15F);
               EffectParticles.add(scene, ParticleTypes.CLOUD, scene.x + point.x, groundY(scene) + 1.2, scene.z + point.z, velocity.x, 0.005, velocity.z);
            }

            if (p >= 0.46F && p < 0.8F) {
               for (int i = 0; i < 4; i++) {
                  double angle = tick * 0.45 + i * 3.141592653589793 / 2.0;
                  Vector3f point = world(scene, (float)Math.cos(angle) * 0.66F, -0.39F);
                  double y = groundY(scene) + 1.350000023841858 + Math.sin(angle) * 0.66;
                  EffectParticles.add(scene, ParticleTypes.BUBBLE_POP, scene.x + point.x, y, scene.z + point.z, 0.0, 0.012, 0.0);
                  if (i == 0) {
                     EffectParticles.add(scene, ParticleTypes.SPLASH, scene.x + point.x, y, scene.z + point.z, 0.0, 0.04, 0.0);
                  }
               }
            }

            if (state.landedAge >= 0.0F && scene.age - state.landedAge < 40.0F) {
               List<BodyFragment> pieces = scene.pieces();
               if (pieces.isEmpty()) {
                  return;
               }

               Vec3d center = pieces.get(Math.min(1, pieces.size() - 1)).position;
               double angle = tick * 0.8;
               EffectParticles.add(
                  scene, ParticleTypes.END_ROD, center.x + Math.cos(angle) * 0.45, center.y + 0.25, center.z + Math.sin(angle) * 0.45, 0.0, 0.025, 0.0
               );
               if (tick % 6 == 0) {
                  EffectParticles.add(scene, ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.35, center.z, 0.0, 0.02, 0.0);
               }
            }
         }
      }
   }

   static Vec3d initialImpulse(DeathScene scene) {
      return Vec3d.ZERO;
   }

   static void applyDrumMotion(DeathScene scene, JointedRagdoll ragdoll, BodyFragment[] pieces) {
      WashingMachineCycle.State state = scene.washer;
      if (state != null) {
         float p = scene.progress();
         if (p >= 0.87F && !state.ejected) {
            state.ejected = true;
         }

         if (state.ejected) {
            if (!state.boosted) {
               boolean clear = true;

               for (BodyFragment piece : pieces) {
                  Vector3f local = local(scene, piece.position);
                  Box box = piece.bounds();
                  float yaw = (float)Math.toRadians(scene.sceneFacing);
                  double extent = Math.abs(Math.sin(yaw)) * box.getLengthX() * 0.5 + Math.abs(Math.cos(yaw)) * box.getLengthZ() * 0.5;
                  if (local.z + extent >= -0.6299999952316284) {
                     clear = false;
                  }
               }

               Vector3f velocity = world(scene, 0.0F, -0.45F);
               velocity.y = clear ? 0.3F : 0.065F;

               for (int i = 0; i < pieces.length; i++) {
                  ragdoll.guideVelocity(i, new Vec3d(velocity.x, velocity.y, velocity.z), clear ? 1.0F : 0.75F);
               }

               if (clear) {
                  state.boosted = true;
               }
            }
         } else {
            float speed = spinSpeed(p);
            if (!(speed <= 0.001F)) {
               for (int i = 0; i < pieces.length; i++) {
                  Vector3f local = local(scene, pieces[i].position);
                  float dx = local.x;
                  float dy = local.y - 1.35F;
                  Vector3f velocity = world(scene, -dy * 0.55F * speed, 0.0F);
                  velocity.y = dx * 0.55F * speed + 0.055F;
                  Vector3f axial = world(scene, 0.0F, (0.65F - local.z) * 0.12F);
                  velocity.add(axial);
                  ragdoll.guideVelocity(i, new Vec3d(velocity.x, velocity.y, velocity.z), 0.28F);
               }
            }
         }
      }
   }

   static void contain(DeathScene scene, JointedRagdoll ragdoll, BodyFragment[] pieces) {
      WashingMachineCycle.State state = scene.washer;
      if (state != null) {
         float yaw = (float)Math.toRadians(scene.sceneFacing);
         float c = Math.abs((float)Math.cos(yaw));
         float s = Math.abs((float)Math.sin(yaw));

         for (BodyFragment piece : pieces) {
            Vector3f local = local(scene, piece.position);
            Box bounds = piece.bounds();
            float hx = (float)(c * bounds.getLengthX() + s * bounds.getLengthZ()) * 0.5F;
            float hz = (float)(s * bounds.getLengthX() + c * bounds.getLengthZ()) * 0.5F;
            float hy = (float)bounds.getLengthY() * 0.5F;
            Vector3f corrected = new Vector3f(local);
            corrected.z = Math.min(corrected.z, 1.62F - hz - 0.015F);
            if (!state.ejected) {
               corrected.z = Math.max(corrected.z, -0.48F + hz + 0.015F);
            }

            if (!state.ejected || local.z - hz > -0.48F) {
               float margin = (float)Math.sqrt(hx * hx + hy * hy);
               float allowed = Math.max(0.12F, 1.025F - margin - 0.012F);
               float dx = corrected.x;
               float dy = corrected.y - 1.35F;
               float radius = (float)Math.sqrt(dx * dx + dy * dy);
               if (radius > allowed) {
                  corrected.x = dx * allowed / radius;
                  corrected.y = 1.35F + dy * allowed / radius;
               }
            }

            Vector3f delta = new Vector3f(corrected).sub(local);
            if (delta.lengthSquared() > 1.0E-10F) {
               new Quaternionf().rotationY(yaw).transform(delta);
               Vec3d shift = new Vec3d(delta.x, delta.y, delta.z);
               piece.position = piece.position.add(shift);
               piece.previous = piece.previous.add(shift);
            }
         }

         if (state.boosted && state.landedAge < 0.0F && ragdoll.upperGroundContact() >= 0 && local(scene, pieces[1].position).z < -0.88F) {
            state.landedAge = scene.age;
         }
      }
   }

   static List<Box> colliders(DeathScene scene) {
      List<Box> boxes = new ArrayList<>();
      boxes.add(new Box(scene.x - 8.0, scene.floorY - 1.0, scene.z - 8.0, scene.x + 8.0, scene.floorY, scene.z + 8.0));
      if (!scene.gui) {
         for (VoxelShape shape : scene.level
            .getBlockCollisions(null, new Box(scene.x - 7.0, scene.floorY - 0.5, scene.z - 7.0, scene.x + 7.0, scene.floorY + 4.0, scene.z + 7.0))) {
            boxes.addAll(shape.getBoundingBoxes());
         }
      }

      int count = 18;

      for (int i = 0; i < count; i++) {
         float x0 = -1.18F + i * 2.36F / count;
         float x1 = -1.18F + (i + 1) * 2.36F / count;
         float nearest = x0 <= 0.0F && x1 >= 0.0F ? 0.0F : Math.min(Math.abs(x0), Math.abs(x1));
         float half = (float)Math.sqrt(Math.max(0.0F, 1.050625F - nearest * nearest));
         addPanel(boxes, scene, x0, x1, 0.18F, 1.35F - half, -0.48F, 1.62F, false);
         addPanel(boxes, scene, x0, x1, 1.35F + half, 2.65F, -0.48F, 1.62F, false);
      }

      addPanel(boxes, scene, -1.18F, 1.18F, 0.18F, 2.65F, 1.62F, 1.78F, true);
      if (scene.progress() < 0.86F) {
         addPanel(boxes, scene, -1.18F, 1.18F, 0.18F, 2.65F, -0.62F, -0.48F, true);
      }

      return boxes;
   }

   private static Vector3f world(DeathScene scene, float x, float z) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(x, 0.0F, z));
   }

   private static Vector3f local(DeathScene scene, Vec3d point) {
      return new Quaternionf()
         .rotationY((float)Math.toRadians(-scene.sceneFacing))
         .transform(new Vector3f((float)(point.x - scene.x), (float)(point.y - groundY(scene)), (float)(point.z - scene.z)));
   }

   private static void addPanel(List<Box> output, DeathScene scene, float x0, float x1, float y0, float y1, float z0, float z1, boolean splitX) {
      float length = splitX ? x1 - x0 : z1 - z0;
      int count = Math.max(1, (int)Math.ceil(length / 0.18F));
      float seg = length / count;
      float halfX = splitX ? seg * 0.5F : (x1 - x0) * 0.5F;
      float halfZ = splitX ? (z1 - z0) * 0.5F : seg * 0.5F;
      float facing = (float)Math.toRadians(scene.sceneFacing);
      float cos = Math.abs((float)Math.cos(facing));
      float sin = Math.abs((float)Math.sin(facing));

      for (int i = 0; i < count; i++) {
         float x = splitX ? x0 + (i + 0.5F) * seg : (x0 + x1) * 0.5F;
         float z = splitX ? (z0 + z1) * 0.5F : z0 + (i + 0.5F) * seg;
         Vector3f center = new Quaternionf().rotationY(facing).transform(new Vector3f(x, 0.0F, z));
         float extentX = cos * halfX + sin * halfZ;
         float extentZ = sin * halfX + cos * halfZ;
         output.add(
            new Box(
               scene.x + center.x - extentX,
               groundY(scene) + y0,
               scene.z + center.z - extentZ,
               scene.x + center.x + extentX,
               groundY(scene) + y1,
               scene.z + center.z + extentZ
            )
         );
      }
   }

   private static void sound(DeathScene scene, WashingMachineCycle.State state, int bit, float phase, SoundEvent event, float volume, float pitch) {
      if (!(scene.progress() < phase) && (state.soundMask & bit) == 0) {
         state.soundMask |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level
               .playSoundClient(scene.x, scene.floorY + 0.9, scene.z + 0.62, event, SoundCategory.PLAYERS, volume * (scene.gui ? 0.25F : 1.0F), pitch, false);
         }
      }
   }

   private static float spinDegrees(float p) {
      float t = phase(p, 0.46F, 0.8F);
      return 1800.0F * (t * t * (3.0F - 2.0F * t));
   }

   private static float spinSpeed(float p) {
      return smooth(phase(p, 0.46F, 0.55F)) * (1.0F - smooth(phase(p, 0.75F, 0.8F)));
   }

   private static void transform(DeathScene scene, ItemDisplayEntity display, float x, float y, float z, float sx, float sy, float sz, Quaternionf rotation) {
      Vector3f pos = new Vector3f(x, y, z);
      new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(pos);
      rotation.premul(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)));
      display.setTransformation(new AffineTransformation(new Vector3f(), rotation, new Vector3f(sx, sy, sz), new Quaternionf()));
      display.setPosition(scene.x + pos.x, (scene.gui ? scene.y : groundY(scene)) + pos.y, scene.z + pos.z);
      display.lastX = display.lastRenderX = display.getX();
      display.lastY = display.lastRenderY = display.getY();
      display.lastZ = display.lastRenderZ = display.getZ();
      DisplayStateAccess access = (DisplayStateAccess)display;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }

   private static float smooth(float v) {
      v = MathHelper.clamp(v, 0.0F, 1.0F);
      return v * v * (3.0F - 2.0F * v);
   }

   private static float phase(float p, float a, float b) {
      return MathHelper.clamp((p - a) / (b - a), 0.0F, 1.0F);
   }

   private static double sampleGround(DeathScene scene) {
      if (scene.gui) {
         return scene.y;
      }

      double highest = scene.floorY;

      for (float dx : new float[]{-1.34F, 0.0F, 1.34F}) {
         for (float dz : new float[]{-1.22F, 0.0F, 1.22F}) {
            Vector3f offset = world(scene, dx, 0.65F + dz);
            Vec3d sample = new Vec3d(scene.x + offset.x, scene.floorY, scene.z + offset.z);
            highest = Math.max(highest, BodyFragment.support(scene.level, sample, 4.0, scene.floorY));
         }
      }

      return highest;
   }

   private static double groundY(DeathScene scene) {
      if (scene.gui) {
         return scene.y;
      }

      WashingMachineCycle.State state = scene.washer;
      return state != null && !Double.isNaN(state.groundY) ? state.groundY : sampleGround(scene) - 0.0;
   }

   public static void discard(DeathScene scene) {
      scene.washer = null;
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final List<ItemDisplayEntity> parts = new ArrayList<>();
      double groundY = 0.0 / 0.0;
      int soundMask;
      int lastWashSound;
      boolean ejected;
      boolean boosted;
      float landedAge = -1.0F;
      int particleTick = -1;
   }
}
