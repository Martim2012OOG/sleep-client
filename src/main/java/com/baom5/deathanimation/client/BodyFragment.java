package com.baom5.deathanimation.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class BodyFragment {
   public Vec3d position;
   public Vec3d previous;
   public Vec3d velocity;
   public final Vector3f rotation = new Vector3f();
   public final Vector3f previousRotation = new Vector3f();
   final Vector3f spin;
   private final Vector3f restingRotation;
   private final DeathBodyRig.Geometry geometry;
   private final float yaw;
   private final float scale;
   private final Vector3f halfExtents = new Vector3f();
   private int stillTicks;
   private int bounces;
   private boolean sleeping;
   boolean jointed;

   public BodyFragment(Vec3d position, Vec3d velocity, Vector3f spin, Vector3f restingRotation, DeathBodyRig.Geometry geometry, float yaw, float scale) {
      this.position = this.previous = position;
      this.velocity = velocity;
      this.spin = spin;
      this.restingRotation = restingRotation;
      this.geometry = geometry;
      this.yaw = yaw;
      this.scale = scale;

      for (Vector3f vertex : geometry.vertices()) {
         this.halfExtents.max(new Vector3f(Math.abs(vertex.x), Math.abs(vertex.y), Math.abs(vertex.z)).mul(scale));
      }
   }

   public void tick(ClientWorld level, boolean stage, double floorY, float speed) {
      this.previous = this.position;
      this.previousRotation.set(this.rotation);
      if (!stage) {
         this.collidePlayers(level);
      }

      if (!this.sleeping) {
         int steps = Math.max(1, (int)Math.ceil(speed * 2.0F));
         float dt = speed / steps;

         for (int i = 0; i < steps; i++) {
            this.step(level, stage, floorY, dt);
         }
      }
   }

   private void step(ClientWorld level, boolean stage, double floorY, float dt) {
      this.velocity = this.velocity.add(0.0, -0.055 * dt, 0.0);
      this.rotation.fma(dt, this.spin);
      Box box = this.bounds();
      double under = stage ? floorY : floorBelow(level, this.position, box.getLengthY() * 0.5 + 0.35);
      if (Double.isFinite(under) && box.minY < under && this.position.y >= under - box.getLengthY() * 0.5) {
         this.position = this.position.add(0.0, under - box.minY, 0.0);
         box = this.bounds();
      }

      Vec3d motion = this.velocity.multiply(dt);
      Vec3d allowed;
      if (stage) {
         allowed = new Vec3d(motion.x, Math.max(motion.y, floorY - box.minY), motion.z);
      } else {
         allowed = Entity.adjustMovementForCollisions(null, motion, box, level, List.of());
      }

      this.position = this.position.add(allowed);
      boolean ground = motion.y < 0.0 && allowed.y > motion.y + 1.0E-5;
      if (Math.abs(allowed.x - motion.x) > 1.0E-5) {
         this.velocity = new Vec3d(-this.velocity.x * 0.12, this.velocity.y, this.velocity.z);
      }

      if (Math.abs(allowed.z - motion.z) > 1.0E-5) {
         this.velocity = new Vec3d(this.velocity.x, this.velocity.y, -this.velocity.z * 0.12);
      }

      if (ground) {
         double rebound = this.bounces++ == 0 && this.velocity.y < -0.16 ? -this.velocity.y * 0.15 : 0.0;
         this.velocity = new Vec3d(this.velocity.x * 0.48, rebound, this.velocity.z * 0.48);
         this.spin.mul(0.35F);
         if (!this.jointed) {
            this.rotation.lerp(this.restingRotation, 0.65F);
         }

         double support = stage ? floorY : support(level, this.position, this.bounds().getLengthY(), floorY);
         Box settled = this.bounds();
         this.position = this.position.add(0.0, support - settled.minY, 0.0);
         if (this.velocity.lengthSquared() < 8.0E-4) {
            this.stillTicks++;
         } else {
            this.stillTicks = 0;
         }

         if (!this.jointed && this.stillTicks >= 3) {
            this.sleeping = true;
            this.velocity = Vec3d.ZERO;
            this.rotation.set(this.restingRotation);
            this.position = this.position.add(0.0, support - this.bounds().minY, 0.0);
         }
      }
   }

   public boolean sleeping() {
      return this.sleeping;
   }

   void sleep() {
      this.sleeping = true;
      this.velocity = Vec3d.ZERO;
      this.spin.zero();
      this.previous = this.position;
      this.previousRotation.set(this.rotation);
   }

   void wake() {
      this.sleeping = false;
      this.stillTicks = 0;
   }

   Quaternionf worldRotation() {
      return new Quaternionf().rotationY(this.yaw).rotateZ(3.1415927F).rotateZYX(this.rotation.z, this.rotation.y, this.rotation.x);
   }

   void worldRotation(Quaternionf orientation) {
      new Quaternionf().rotationY(this.yaw).rotateZ(3.1415927F).invert().mul(orientation).normalize().getEulerAnglesZYX(this.rotation);
   }

   Vector3f halfExtents() {
      return new Vector3f(this.halfExtents);
   }

   Vec3d anchor(Vector3f local) {
      Vector3f offset = this.worldRotation().transform(new Vector3f(local).mul(this.scale));
      return this.position.add(offset.x, offset.y, offset.z);
   }

   void turn(Vec3d worldAxis, float radians) {
      if (!(worldAxis.lengthSquared() < 1.0E-10) && Float.isFinite(radians)) {
         Vec3d axis = worldAxis.normalize();
         Quaternionf rotationWorld = new Quaternionf().rotationAxis(radians, (float)axis.x, (float)axis.y, (float)axis.z).mul(this.worldRotation());
         new Quaternionf().rotationY(this.yaw).rotateZ(3.1415927F).invert().mul(rotationWorld).normalize().getEulerAnglesZYX(this.rotation);
      }
   }

   void correct(Vec3d amount, ClientWorld level, boolean stage, double floor) {
      if (amount.lengthSquared() > 0.04) {
         amount = amount.normalize().multiply(0.2);
      }

      Box box = this.bounds();
      Vec3d allowed = stage
         ? new Vec3d(amount.x, Math.max(amount.y, floor - box.minY), amount.z)
         : Entity.adjustMovementForCollisions(null, amount, box, level, List.of());
      this.position = this.position.add(allowed);
      double support = stage ? floor : floorBelow(level, this.position, this.bounds().getLengthY() * 0.5 + 0.25);
      if (Double.isFinite(support) && this.bounds().minY < support && this.position.y >= support - 0.05) {
         this.position = this.position.add(0.0, support - this.bounds().minY, 0.0);
      }
   }

   void collidePlayers(ClientWorld level) {
      Box box = this.bounds();

      for (AbstractClientPlayerEntity player : level.getPlayers()) {
         if (!(player instanceof DeathAvatar) && !player.isDead() && !player.isSpectator()) {
            Box other = player.getBoundingBox();
            if (box.intersects(other)
               && contact(
                     this.position,
                     this.axes(),
                     this.halfExtents,
                     other.getCenter(),
                     new Vec3d[]{new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), new Vec3d(0.0, 0.0, 1.0)},
                     new Vector3f((float)other.getLengthX() / 2.0F, (float)other.getLengthY() / 2.0F, (float)other.getLengthZ() / 2.0F)
                  )
                  != null) {
               double dx = this.position.x - player.getX();
               double dz = this.position.z - player.getZ();
               double length = Math.sqrt(dx * dx + dz * dz);
               if (length < 0.001) {
                  dx = 1.0;
                  dz = 0.0;
                  length = 1.0;
               }

               double push = Math.min(
                  0.15, Math.min(Math.min(box.maxX - other.minX, other.maxX - box.minX), Math.min(box.maxZ - other.minZ, other.maxZ - box.minZ)) + 0.005
               );
               Vec3d offset = new Vec3d(dx / length * push, 0.0, dz / length * push);
               this.position = this.position.add(Entity.adjustMovementForCollisions(null, offset, box, level, List.of()));
               this.velocity = this.velocity.add(offset.multiply(0.25)).add(player.getVelocity().multiply(0.25, 0.0, 0.25));
               this.wake();
               box = this.bounds();
            }
         }
      }
   }

   static void collidePair(BodyFragment a, BodyFragment b, ClientWorld level, boolean stage, double floor) {
      Box aa = a.bounds();
      Box bb = b.bounds();
      if (aa.intersects(bb)) {
         Vec3d penetration = contact(a.position, a.axes(), a.halfExtents, b.position, b.axes(), b.halfExtents);
         if (penetration != null) {
            Vec3d normal = penetration.normalize();
            Vec3d shift = normal.multiply(Math.min(0.12, penetration.length() * 0.5 + 1.0E-4));
            a.correct(shift.multiply(-1.0), level, stage, floor);
            b.correct(shift, level, stage, floor);
            double impact = b.velocity.subtract(a.velocity).dotProduct(normal);
            if (impact < -0.01) {
               a.velocity = a.velocity.add(normal.multiply(impact * 0.55));
               b.velocity = b.velocity.subtract(normal.multiply(impact * 0.55));
               a.wake();
               b.wake();
            }
         }
      }
   }

   private Vec3d[] axes() {
      Quaternionf q = this.worldRotation();
      Vec3d[] result = new Vec3d[3];

      for (int i = 0; i < 3; i++) {
         Vector3f axis = new Vector3f(i == 0 ? 1.0F : 0.0F, i == 1 ? 1.0F : 0.0F, i == 2 ? 1.0F : 0.0F);
         q.transform(axis);
         result[i] = new Vec3d(axis.x, axis.y, axis.z);
      }

      return result;
   }

   static Vec3d contact(Vec3d pa, Vec3d[] aa, Vector3f ha, Vec3d pb, Vec3d[] ab, Vector3f hb) {
      return contact(pa, aa, ha, pb, ab, hb, 0.0);
   }

   static Vec3d contact(Vec3d pa, Vec3d[] aa, Vector3f ha, Vec3d pb, Vec3d[] ab, Vector3f hb, double margin) {
      Vec3d delta = pb.subtract(pa);
      Vec3d best = null;
      double smallest = 1.0 / 0.0;

      for (int i = 0; i < 15; i++) {
         Vec3d axis = i < 3 ? aa[i] : (i < 6 ? ab[i - 3] : aa[(i - 6) / 3].crossProduct(ab[(i - 6) % 3]));
         if (!(axis.lengthSquared() < 1.0E-8)) {
            axis = axis.normalize();
            double ra = Math.abs(axis.dotProduct(aa[0])) * ha.x + Math.abs(axis.dotProduct(aa[1])) * ha.y + Math.abs(axis.dotProduct(aa[2])) * ha.z;
            double rb = Math.abs(axis.dotProduct(ab[0])) * hb.x + Math.abs(axis.dotProduct(ab[1])) * hb.y + Math.abs(axis.dotProduct(ab[2])) * hb.z;
            double distance = delta.dotProduct(axis);
            double overlap = ra + rb + margin - Math.abs(distance);
            if (overlap <= 1.0E-5) {
               return null;
            }

            if (overlap < smallest) {
               smallest = overlap;
               best = axis.multiply(distance < 0.0 ? -1.0 : 1.0);
            }
         }
      }

      return best == null ? null : best.multiply(smallest);
   }

   private static double floorBelow(ClientWorld level, Vec3d position, double distance) {
      BlockHitResult hit = level.raycast(
         new RaycastContext(position.add(0.0, distance, 0.0), position.add(0.0, -distance, 0.0), ShapeType.COLLIDER, FluidHandling.NONE, ShapeContext.absent())
      );
      return hit.getType() == Type.BLOCK ? hit.getPos().y : 0.0 / 0.0;
   }

   Box bounds() {
      Quaternionf transform = this.worldRotation();
      Vector3f v = new Vector3f();
      double x = 0.0;
      double y = 0.0;
      double z = 0.0;

      for (Vector3f vertex : this.geometry.vertices()) {
         transform.transform(vertex, v).mul(this.scale);
         x = Math.max(x, Math.abs(v.x));
         y = Math.max(y, Math.abs(v.y));
         z = Math.max(z, Math.abs(v.z));
      }

      return new Box(this.position.x - x, this.position.y - y, this.position.z - z, this.position.x + x, this.position.y + y, this.position.z + z);
   }

   public static double support(ClientWorld level, Vec3d position, double height, double fallback) {
      Vec3d from = position.add(0.0, height * 0.5 + 0.25, 0.0);
      BlockHitResult hit = level.raycast(
         new RaycastContext(from, from.add(0.0, -height - 1.0, 0.0), ShapeType.COLLIDER, FluidHandling.NONE, ShapeContext.absent())
      );
      return hit.getType() == Type.BLOCK ? hit.getPos().y : fallback;
   }
}
