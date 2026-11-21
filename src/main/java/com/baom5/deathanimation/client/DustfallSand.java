package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class DustfallSand {
   private static final int[][] FACES = new int[][]{{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
   private static final float CELL = 0.065F;
   private static final float STEP = 0.33333334F;
   private static final float DT = 0.016666668F;
   private static final Identifier TEXTURE = Identifier.of("minecraft:textures/block/sand.png");
   private static final float[][] BODY = new float[][]{
      {0.0F, 1.640625F, 0.0F, 0.46875F, 0.46875F, 0.46875F},
      {0.0F, 1.0546875F, 0.0F, 0.46875F, 0.703125F, 0.234375F},
      {-0.3515625F, 1.0546875F, 0.0F, 0.234375F, 0.703125F, 0.234375F},
      {0.3515625F, 1.0546875F, 0.0F, 0.234375F, 0.703125F, 0.234375F},
      {-0.1171875F, 0.3515625F, 0.0F, 0.234375F, 0.703125F, 0.234375F},
      {0.1171875F, 0.3515625F, 0.0F, 0.234375F, 0.703125F, 0.234375F}
   };
   private final boolean sandScene = ThreadLocalRandom.current().nextBoolean();
   private final List<DustfallSand.Grain> grains = new ArrayList<>();
   private final SandContactGrid contacts = new SandContactGrid();
   private final SandContactGrid interactionGrid = new SandContactGrid();
   private final SandTerrainCache terrain = new SandTerrainCache();
   private final SandContactGrid.PairConsumer contactPairs = (a, b) -> contact(this.grains.get(a), this.grains.get(b));
   private boolean interactionGridDirty = true;
   private Box cachedBounds;
   private DustfallSand.RenderSnapshot renderSnapshot;
   private Vec3d nearestHit;
   private double nearestDistance;
   private final Vector3f motionScratch = new Vector3f();
   private final Vector3f actualScratch = new Vector3f();
   private long cleanupTick = -9223372036854775808L;
   private boolean initialized;
   private float simulated;
   private boolean collapseSound;
   private Box interactionBounds;
   private final Map<UUID, Boolean> swinging = new HashMap<>();

   boolean sandScene() {
      return this.sandScene;
   }

   private double base(DeathScene scene) {
      return scene.gui ? scene.y : scene.floorY;
   }

   DeathScene.Root root(DeathScene scene) {
      return new DeathScene.Root(0.0F, (float)(this.base(scene) - scene.y), 0.0F, 0.0F, scene.sceneFacing, 0.0F);
   }

   boolean originalVisible(float p) {
      return p < 0.205F;
   }

   DeathBodyRig.Motion[] originalPose() {
      DeathBodyRig.Motion[] pose = new DeathBodyRig.Motion[6];

      for (int i = 0; i < 6; i++) {
         pose[i] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      return pose;
   }

   private static List<float[]> bodyShape(DeathScene scene) {
      if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         ArrayList var18 = new ArrayList();

         for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
            ModelPart bone = DeathBodyRig.part(model, part);
            ModelTransform pose = bone.getDefaultTransform();
            DeathBodyRig.Geometry geometry = DeathBodyRig.geometry(bone);
            Vector3f center = geometry.center();
            Vector3f half = new Vector3f();

            for (Vector3f v : geometry.vertices()) {
               half.max(new Vector3f(Math.abs(v.x), Math.abs(v.y), Math.abs(v.z)));
            }

            center.add(pose.x() / 16.0F, pose.y() / 16.0F, pose.z() / 16.0F);
            center.set(-center.x * 0.9375F, (1.501F - center.y) * 0.9375F, center.z * 0.9375F);
            half.mul(0.9375F);
            EquipmentSlot slot = part == DeathBodyRig.Part.HEAD ? EquipmentSlot.HEAD : (part.ordinal() < 4 ? EquipmentSlot.CHEST : EquipmentSlot.LEGS);
            if (!scene.avatar.getEquippedStack(slot).isEmpty()) {
               half.add(0.022F, 0.012F, 0.022F);
            }

            var18.add(new float[]{center.x, center.y, center.z, half.x * 2.0F, half.y * 2.0F, half.z * 2.0F});
         }

         return var18;
      } else {
         return Arrays.asList(BODY);
      }
   }

   void spawn(DeathScene scene, Consumer<Entity> add) {
      if (this.sandScene) {
         this.initialized = true;
         Random random = new Random(scene.seed ^ 1511510029L);
         Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(180.0F - scene.avatar.bodyYaw));

         for (float[] b : bodyShape(scene)) {
            int nx = (int)Math.ceil(b[3] / 0.065F);
            int ny = (int)Math.ceil(b[4] / 0.065F);
            int nz = (int)Math.ceil(b[5] / 0.065F);
            Vector3f half = new Vector3f(b[3] / nx, b[4] / ny, b[5] / nz).mul(0.5F);

            for (int x = 0; x < nx; x++) {
               for (int y = 0; y < ny; y++) {
                  for (int z = 0; z < nz; z++) {
                     Vector3f point = new Vector3f(
                        b[0] - b[3] / 2.0F + (x + 0.5F) * b[3] / nx, b[1] - b[4] / 2.0F + (y + 0.5F) * b[4] / ny, b[2] - b[5] / 2.0F + (z + 0.5F) * b[5] / nz
                     );
                     float release = 0.34F + point.y * 0.075F + random.nextFloat() * 0.085F;
                     facing.transform(point);
                     this.grains.add(new DustfallSand.Grain(point, new Vector3f(half), release, 0.88F + random.nextFloat() * 0.12F));
                  }
               }
            }
         }
      }
   }

   void animate(DeathScene scene) {
      if (this.sandScene && !this.grains.isEmpty()) {
         this.prune(scene);
         if (!this.grains.isEmpty()) {
            float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
            float target = scene.age + partial * DeathAnimationConfig.INSTANCE.animationSpeed;
            if (target + 0.33333334F < this.simulated) {
               this.simulated = 0.0F;
               this.interactionBounds = null;
               this.swinging.clear();
               this.cleanupTick = -9223372036854775808L;
               this.changed();
               this.contacts.clear();
               this.interactionGrid.clear();
               this.terrain.discard();

               for (DustfallSand.Grain g : this.grains) {
                  g.position.set(g.origin);
                  g.previous.set(g.origin);
                  g.velocity.zero();
                  g.released = false;
                  g.grounded = false;
                  g.support = null;
                  g.landedTick = -1.0;
                  g.wake();
               }
            }

            while (this.simulated + 0.33333334F <= target) {
               this.simulated += 0.33333334F;
               this.step(scene, this.simulated / AnimationTimeline.ticks(scene.animation));
            }
         }
      }
   }

   private void step(DeathScene scene, float progress) {
      if (!scene.gui && progress >= 1.0F) {
         this.walkThrough(scene);
      }

      this.terrain.beginStep();
      boolean active = false;

      for (DustfallSand.Grain g : this.grains) {
         g.previous.set(g.position);
         if (progress >= g.release && !g.released) {
            g.released = true;
            g.wake();
         }

         if (g.sleeping && g.support != null && g.support.released && !g.support.sleeping) {
            g.wake();
         }

         if (g.released && !g.sleeping) {
            active = true;
            g.grounded = false;
            g.support = null;
            g.velocity.y -= 0.16350001F;
            g.velocity.mul(0.998F);
            this.move(scene, g, this.motionScratch.set(g.velocity).mul(0.016666668F));
         }
      }

      if (active) {
         this.changed();

         for (int iteration = 0; iteration < 4; iteration++) {
            this.contacts.clear();

            for (int i = 0; i < this.grains.size(); i++) {
               DustfallSand.Grain a = this.grains.get(i);
               this.contacts.add(i, a.position.x, a.position.y, a.position.z, a.released && !a.sleeping);
            }

            this.contacts.pairs(this.contactPairs);
         }

         for (DustfallSand.Grain g : this.grains) {
            if (g.released && !g.sleeping) {
               Vector3f desired = this.motionScratch.set(g.position).sub(g.previous);
               g.position.set(g.previous);
               this.move(scene, g, desired);
               Vector3f actual = this.actualScratch.set(g.position).sub(g.previous).div(0.016666668F);
               g.velocity.lerp(actual, 0.85F);
               if (g.velocity.lengthSquared() < 0.0036F) {
                  g.still++;
               } else {
                  g.still = 0;
               }

               if (g.still >= 35 && (g.grounded || g.support != null)) {
                  g.sleeping = true;
                  g.velocity.zero();
                  landed(scene, g);
               }
            }
         }

         this.interactionBounds = this.bounds(scene);
      }
   }

   private void changed() {
      this.renderSnapshot = null;
      this.cachedBounds = null;
      this.interactionGridDirty = true;
   }

   private void ensureInteractionGrid() {
      if (this.interactionGridDirty) {
         this.interactionGrid.clear();

         for (int i = 0; i < this.grains.size(); i++) {
            DustfallSand.Grain g = this.grains.get(i);
            if (g.released) {
               this.interactionGrid.add(i, g.position.x, g.position.y, g.position.z, false);
            }
         }

         this.interactionGridDirty = false;
      }
   }

   private void query(DeathScene scene, Box bounds, IntConsumer visit) {
      this.ensureInteractionGrid();
      this.interactionGrid
         .query(
            bounds.minX - scene.x,
            bounds.minY - this.base(scene),
            bounds.minZ - scene.z,
            bounds.maxX - scene.x,
            bounds.maxY - this.base(scene),
            bounds.maxZ - scene.z,
            visit
         );
   }

   private void prune(DeathScene scene) {
      if (!scene.gui) {
         long tick = scene.level.getTime();
         if (tick != this.cleanupTick) {
            this.cleanupTick = tick;
            boolean removed = this.grains.removeIf(grainx -> {
               if (!(grainx.landedTick < 0.0) && !(tick - grainx.landedTick < 120.0)) {
                  grainx.retired = true;
                  return true;
               } else {
                  return false;
               }
            });
            if (removed) {
               this.contacts.clear();
               this.interactionGrid.clear();
               this.changed();
               this.terrain.discard();

               for (DustfallSand.Grain grain : this.grains) {
                  if (grain.support != null && grain.support.retired) {
                     grain.support = null;
                     grain.wake();
                  }
               }

               this.interactionBounds = null;
               if (this.grains.isEmpty()) {
                  this.contacts.discard();
                  this.interactionGrid.discard();
                  this.swinging.clear();
               }
            }
         }
      }
   }

   boolean finished() {
      return this.sandScene && this.initialized && this.grains.isEmpty();
   }

   void discard() {
      this.grains.clear();
      this.contacts.discard();
      this.interactionGrid.discard();
      this.terrain.discard();
      this.swinging.clear();
      this.interactionBounds = null;
      this.changed();
   }

   private void move(DeathScene scene, DustfallSand.Grain g, Vector3f motion) {
      if (scene.gui) {
         g.position.add(motion);
         if (g.position.y < g.radius) {
            g.position.y = g.radius;
            g.grounded = true;
            landed(scene, g);
            g.velocity.y = 0.0F;
            g.velocity.x *= 0.8F;
            g.velocity.z *= 0.8F;
         }
      } else {
         Box box = this.box(scene, g);
         Vec3d allowed = this.terrain.move(scene.level, box, new Vec3d(motion.x, motion.y, motion.z));
         g.position.add((float)allowed.x, (float)allowed.y, (float)allowed.z);
         if (Math.abs(allowed.y - motion.y) > 1.0E-7) {
            if (motion.y < 0.0F) {
               g.grounded = true;
               landed(scene, g);
            }

            g.velocity.y = 0.0F;
            g.velocity.x *= 0.8F;
            g.velocity.z *= 0.8F;
         }

         if (Math.abs(allowed.x - motion.x) > 1.0E-7) {
            g.velocity.x = 0.0F;
         }

         if (Math.abs(allowed.z - motion.z) > 1.0E-7) {
            g.velocity.z = 0.0F;
         }
      }
   }

   private static void landed(DeathScene scene, DustfallSand.Grain grain) {
      if (grain.landedTick < 0.0) {
         grain.landedTick = scene.level.getTime();
      }
   }

   private static void contact(DustfallSand.Grain a, DustfallSand.Grain b) {
      if (a.released && !a.sleeping || b.released && !b.sleeping) {
         float dx = a.position.x - b.position.x;
         float dy = a.position.y - b.position.y;
         float dz = a.position.z - b.position.z;
         float rr = a.radius + b.radius;
         float d2 = dx * dx + dy * dy + dz * dz;
         if (!(d2 < 1.0E-12F)) {
            if (d2 <= (rr + 0.001F) * (rr + 0.001F)) {
               if (dy > rr * 0.55F && (!b.released || b.sleeping || b.still > 12)) {
                  a.support = b;
               }

               if (dy < -rr * 0.55F && (!a.released || a.sleeping || a.still > 12)) {
                  b.support = a;
               }
            }

            if (!(d2 >= rr * rr) && (a.released && !a.sleeping || b.released && !b.sleeping)) {
               float distance = (float)Math.sqrt(d2);
               dx /= distance;
               dy /= distance;
               dz /= distance;
               if (a.released && b.released && (a.velocity.lengthSquared() > 0.04F || b.velocity.lengthSquared() > 0.04F)) {
                  a.wake();
                  b.wake();
               }

               float wa = a.released && !a.sleeping ? 1.0F : 0.0F;
               float wb = b.released && !b.sleeping ? 1.0F : 0.0F;
               if (wa + wb != 0.0F) {
                  float correction = (rr - distance) / (wa + wb);
                  a.position.add(dx * correction * wa, dy * correction * wa, dz * correction * wa);
                  b.position.add(-dx * correction * wb, -dy * correction * wb, -dz * correction * wb);
                  float speed = (a.velocity.x - b.velocity.x) * dx + (a.velocity.y - b.velocity.y) * dy + (a.velocity.z - b.velocity.z) * dz;
                  if (speed < 0.0F) {
                     float impulse = -speed / (wa + wb);
                     a.velocity.add(dx * impulse * wa, dy * impulse * wa, dz * impulse * wa);
                     b.velocity.add(-dx * impulse * wb, -dy * impulse * wb, -dz * impulse * wb);
                     a.velocity.mul(0.97F);
                     b.velocity.mul(0.97F);
                  }
               }
            }
         }
      }
   }

   private void walkThrough(DeathScene scene) {
      if (this.interactionBounds == null) {
         this.interactionBounds = this.bounds(scene);
      }

      for (AbstractClientPlayerEntity player : scene.level.getPlayers()) {
         if (!(player instanceof DeathAvatar) && player.isAlive() && !player.isSpectator()) {
            Vec3d motion = player.getVelocity();
            Vec3d observed = player.getEntityPos().subtract(new Vec3d(player.lastRenderX, player.lastRenderY, player.lastRenderZ));
            if (observed.horizontalLengthSquared() > motion.horizontalLengthSquared()) {
               motion = observed;
            }

            float vx = (float)Math.clamp(motion.x * 20.0, -8.0, 8.0);
            float vz = (float)Math.clamp(motion.z * 20.0, -8.0, 8.0);
            float speed = Math.min(8.0F, (float)Math.sqrt(vx * vx + vz * vz));
            boolean swing = player.getHandSwingProgress(1.0F) > 0.0F;
            boolean prior = this.swinging.getOrDefault(player.getUuid(), false);
            this.swinging.put(player.getUuid(), swing);
            if (player != MinecraftClient.getInstance().player && swing && !prior) {
               this.interact(scene, player);
            }

            Box bounds = player.getBoundingBox().stretch(-vx * 0.016666668F, 0.0, -vz * 0.016666668F).expand(0.045);
            if (bounds.intersects(this.interactionBounds)) {
               this.query(scene, bounds, index -> {
                  DustfallSand.Grain g = this.grains.get(index);
                  if (bounds.contains(scene.x + g.position.x, this.base(scene) + g.position.y, scene.z + g.position.z)) {
                     float dx = (float)(scene.x + g.position.x - player.getX());
                     float dz = (float)(scene.z + g.position.z - player.getZ());
                     float length = (float)Math.sqrt(dx * dx + dz * dz);
                     if (length < 0.001F) {
                        dx = 1.0F;
                        dz = 0.0F;
                        length = 1.0F;
                     }

                     float push = 0.35F + speed * 0.55F;
                     g.wake();
                     g.velocity.x = g.velocity.x + (dx / length * push + vx * 0.55F - g.velocity.x) * 0.25F;
                     g.velocity.z = g.velocity.z + (dz / length * push + vz * 0.55F - g.velocity.z) * 0.25F;
                     g.velocity.y = Math.max(g.velocity.y, 0.1F + speed * 0.12F);
                  }
               });
            }
         }
      }
   }

   boolean interact(DeathScene scene, AbstractClientPlayerEntity player) {
      if (this.sandScene && !scene.gui && player.isAlive() && !player.isSpectator() && !(scene.progress() < 1.0F)) {
         Vec3d eye = player.getEyePos();
         Vec3d direction = player.getRotationVector().normalize();
         Vec3d end = eye.add(direction.multiply(4.5));
         if (this.interactionBounds != null
            && !this.interactionBounds.expand(0.04).contains(eye)
            && this.interactionBounds.expand(0.04).raycast(eye, end).isEmpty()) {
            return false;
         }

         this.nearestHit = null;
         this.nearestDistance = 1.0 / 0.0;
         this.query(scene, new Box(eye, end).expand(0.06), index -> {
            DustfallSand.Grain g = this.grains.get(index);
            Optional<Vec3d> hit = this.box(scene, g).expand(0.018).raycast(eye, end);
            if (hit.isPresent() && eye.squaredDistanceTo(hit.get()) < this.nearestDistance) {
               this.nearestHit = hit.get();
               this.nearestDistance = eye.squaredDistanceTo(this.nearestHit);
            }
         });
         Vec3d nearest = this.nearestHit;
         double distance = this.nearestDistance;
         if (nearest == null) {
            return false;
         }

         BlockHitResult obstruction = scene.level.raycast(new RaycastContext(eye, nearest, ShapeType.OUTLINE, FluidHandling.NONE, player));
         if (obstruction.getType() == Type.BLOCK && eye.squaredDistanceTo(obstruction.getPos()) < distance - 1.0E-4) {
            return false;
         }

         float strength = 0.45F + 0.55F * player.getAttackCooldownProgress(0.0F);
         float movement = Math.min(1.0F, (float)player.getVelocity().horizontalLength() * 3.0F);
         this.query(
            scene,
            new Box(nearest.x - 0.4, nearest.y - 0.4, nearest.z - 0.4, nearest.x + 0.4, nearest.y + 0.4, nearest.z + 0.4),
            index -> {
               DustfallSand.Grain g = this.grains.get(index);
               double dx = scene.x + g.position.x - nearest.x;
               double dy = this.base(scene) + g.position.y - nearest.y;
               double dz = scene.z + g.position.z - nearest.z;
               double d = dx * dx + dy * dy + dz * dz;
               if (!(d > 0.16)) {
                  float force = (float)(1.0 - Math.sqrt(d) / 0.4) * (strength + movement * 0.3F);
                  float radialX = (float)(scene.x + g.position.x - nearest.x);
                  float radialZ = (float)(scene.z + g.position.z - nearest.z);
                  g.wake();
                  g.velocity
                     .add(
                        ((float)direction.x * 3.0F + radialX * 4.0F) * force,
                        (1.4F + Math.max(0.0F, (float)direction.y)) * force,
                        ((float)direction.z * 3.0F + radialZ * 4.0F) * force
                     );
               }
            }
         );
         return true;
      } else {
         return false;
      }
   }

   private Box box(DeathScene scene, DustfallSand.Grain g) {
      double x = scene.x + g.position.x;
      double y = this.base(scene) + g.position.y;
      double z = scene.z + g.position.z;
      double r = g.radius;
      return new Box(x - r, y - r, z - r, x + r, y + r, z + r);
   }

   Box bounds(DeathScene scene) {
      if (this.cachedBounds != null) {
         return this.cachedBounds;
      }

      double minX = -0.6;
      double minY = 0.0;
      double minZ = -0.6;
      double maxX = 0.6;
      double maxY = 2.0;
      double maxZ = 0.6;

      for (DustfallSand.Grain g : this.grains) {
         minX = Math.min(minX, g.position.x - g.radius);
         minY = Math.min(minY, g.position.y - g.radius);
         minZ = Math.min(minZ, g.position.z - g.radius);
         maxX = Math.max(maxX, g.position.x + g.radius);
         maxY = Math.max(maxY, g.position.y + g.radius);
         maxZ = Math.max(maxZ, g.position.z + g.radius);
      }

      return this.cachedBounds = new Box(scene.x + minX, this.base(scene) + minY, scene.z + minZ, scene.x + maxX, this.base(scene) + maxY, scene.z + maxZ);
   }

   void effects(DeathScene scene) {
      if (this.sandScene && !this.collapseSound && !(scene.progress() < 0.34F)) {
         this.collapseSound = true;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level
               .playSoundClient(
                  scene.x, this.base(scene) + 0.7, scene.z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, scene.gui ? 0.12F : 0.42F, 0.85F, false
               );
         }
      }
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      DustfallSand sand = scene.dustfallSand;
      if (sand != null && sand.sandScene && !(scene.progress() < 0.205F)) {
         if (scene.gui || EffectParticles.near(scene.x, sand.base(scene), scene.z)) {
            sand.animate(scene);
            if (!sand.grains.isEmpty()) {
               int light = scene.gui
                  ? 15728880
                  : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, sand.base(scene) + 0.5, scene.z));
               DustfallSand.RenderSnapshot snapshot = sand.snapshot();
               double now = (float)scene.level.getTime() + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
               double cameraX = relativeTo.x - scene.x;
               double cameraY = relativeTo.y - sand.base(scene);
               double cameraZ = relativeTo.z - scene.z;
               poses.push();
               poses.translate(scene.x - relativeTo.x, sand.base(scene) - relativeTo.y, scene.z - relativeTo.z);
               submitPass(poses, collector, snapshot, light, now, false, scene.gui, cameraX, cameraY, cameraZ);
               if (now > snapshot.earliestLanding + 100.0) {
                  submitPass(poses, collector, snapshot, light, now, true, scene.gui, cameraX, cameraY, cameraZ);
               }

               poses.pop();
            }
         }
      }
   }

   private DustfallSand.RenderSnapshot snapshot() {
      if (this.renderSnapshot != null) {
         return this.renderSnapshot;
      }

      float[] vertices = new float[this.grains.size() * 7];
      double[] landed = new double[this.grains.size()];
      double earliest = 1.0 / 0.0;
      int n = 0;
      int index = 0;

      for (DustfallSand.Grain g : this.grains) {
         vertices[n++] = g.position.x;
         vertices[n++] = g.position.y;
         vertices[n++] = g.position.z;
         vertices[n++] = g.half.x;
         vertices[n++] = g.half.y;
         vertices[n++] = g.half.z;
         vertices[n++] = g.shade;
         landed[index++] = g.landedTick;
         if (g.landedTick >= 0.0) {
            earliest = Math.min(earliest, g.landedTick);
         }
      }

      return this.renderSnapshot = new DustfallSand.RenderSnapshot(vertices, landed, earliest);
   }

   private static void submitPass(
      MatrixStack poses,
      OrderedRenderCommandQueue collector,
      DustfallSand.RenderSnapshot snapshot,
      int light,
      double now,
      boolean fading,
      boolean gui,
      double cameraX,
      double cameraY,
      double cameraZ
   ) {
      collector.submitCustom(
         poses,
         fading ? RenderLayers.entityTranslucent(TEXTURE) : RenderLayers.entityCutoutNoCull(TEXTURE),
         (pose, out) -> {
            float[] vertices = snapshot.vertices;
            int i = 0;

            for (int index = 0; i < vertices.length; index++) {
               int alpha = SandRenderMath.alpha(now, snapshot.landed[index]);
               if (alpha != 0 && fading == alpha < 255) {
                  float x = vertices[i];
                  float y = vertices[i + 1];
                  float z = vertices[i + 2];
                  int visible = !gui && !fading
                     ? SandRenderMath.visibleFaces(cameraX, cameraY, cameraZ, x, y, z, vertices[i + 3], vertices[i + 4], vertices[i + 5])
                     : 63;

                  for (int face = 0; face < 6; face++) {
                     if ((visible & 1 << face) != 0) {
                        int axis = face / 2;
                        int side = face % 2 == 0 ? -1 : 1;
                        float shade = vertices[i + 6] * (axis == 1 ? (side > 0 ? 1.0F : 0.65F) : (axis == 0 ? 0.82F : 0.9F));
                        int color = alpha << 24 | (int)(255.0F * shade) * 65793;

                        for (int v = 0; v < 4; v++) {
                           int corner = FACES[face][v];
                           out.vertex(
                                 pose,
                                 x + ((corner & 1) == 0 ? -1 : 1) * vertices[i + 3],
                                 y + ((corner & 2) == 0 ? -1 : 1) * vertices[i + 4],
                                 z + ((corner & 4) == 0 ? -1 : 1) * vertices[i + 5]
                              )
                              .color(color)
                              .texture(v != 1 && v != 2 ? 0.0F : 1.0F, v >= 2 ? 1.0F : 0.0F)
                              .overlay(OverlayTexture.DEFAULT_UV)
                              .light(light)
                              .normal(pose, axis == 0 ? side : 0.0F, axis == 1 ? side : 0.0F, axis == 2 ? side : 0.0F);
                        }
                     }
                  }
               }

               i += 7;
            }
         }
      );
   }

   @Environment(EnvType.CLIENT)
   private static final class Grain {
      final Vector3f origin;
      final Vector3f position;
      final Vector3f previous;
      final Vector3f velocity = new Vector3f();
      final Vector3f half;
      final float release;
      final float radius;
      final float shade;
      boolean released;
      boolean sleeping;
      boolean grounded;
      boolean retired;
      DustfallSand.Grain support;
      int still;
      double landedTick = -1.0;

      Grain(Vector3f origin, Vector3f half, float release, float shade) {
         this.origin = origin;
         this.position = new Vector3f(origin);
         this.previous = new Vector3f(origin);
         this.half = half;
         this.radius = Math.max(half.x, Math.max(half.y, half.z));
         this.release = release;
         this.shade = shade;
      }

      void wake() {
         this.sleeping = false;
         this.still = 0;
      }
   }

   @Environment(EnvType.CLIENT)
   private record RenderSnapshot(float[] vertices, double[] landed, double earliestLanding) {
   }
}
