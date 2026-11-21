package com.baom5.deathanimation.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class AcrobaticRagdoll {
   private final DeathScene scene;
   private JointedRagdoll ragdoll;
   private BodyFragment[] pieces;
   private AcrobaticRagdoll.Frame previous;
   private float previousAge = -1.0F;
   private float interruptedAtAge = -1.0F;
   private boolean absorbed;
   private Vector3f[] ragdollFrameRenderCenter;

   static boolean supports(DeathAnimationConfig.Animation animation) {
      return animation == DeathAnimationConfig.Animation.BACKFLIP
         || animation == DeathAnimationConfig.Animation.STRETCH_SNAP
         || animation == DeathAnimationConfig.Animation.TORNADO
         || animation == DeathAnimationConfig.Animation.BAN_HAMMER
         || animation == DeathAnimationConfig.Animation.WHIRLPOOL;
   }

   private float handoffProgress() {
      return switch (this.scene.animation) {
         case TORNADO -> 0.98F;
         case BAN_HAMMER -> 0.64F;
         case WHIRLPOOL -> 0.99F;
         default -> 0.9F;
      };
   }

   private boolean waitingForAuthoredFinish() {
      return this.scene.animation != DeathAnimationConfig.Animation.BACKFLIP && this.ragdoll == null && this.scene.progress() < this.handoffProgress();
   }

   AcrobaticRagdoll(DeathScene scene) {
      this.scene = scene;
   }

   boolean active() {
      return this.ragdoll != null || this.absorbed;
   }

   boolean absorbed() {
      return this.absorbed;
   }

   float interruptedAtAge() {
      return this.interruptedAtAge;
   }

   boolean sleeping() {
      return this.ragdoll != null && this.ragdoll.sleeping();
   }

   List<BodyFragment> pieces() {
      return this.pieces != null && !this.absorbed ? List.of(this.pieces) : List.of();
   }

   void tick(BipedEntityModel<?> model) {
      if (this.previousAge >= 0.0F && this.scene.age < this.previousAge - 1.0F) {
         this.reset();
      }

      this.previousAge = this.scene.age;
      if (!this.absorbed) {
         if (this.scene.animation == DeathAnimationConfig.Animation.WHIRLPOOL) {
            if (this.scene.progress() >= 0.99F) {
               this.absorb();
            }
         } else if (this.ragdoll == null) {
            AcrobaticRagdoll.Frame now = this.capture(model);
            AcrobaticRagdoll.Collision collision = this.waitingForAuthoredFinish() ? null : this.detect(now);
            float p = this.scene.progress();
            if (collision != null) {
               this.activate(model, now);
               Vec3d impulse = collision.impulse.lengthSquared() < 1.0E-8 ? Vec3d.ZERO : collision.impulse;
               this.ragdoll.impulse(collision.part, impulse, collision.point.subtract(this.pieces[collision.part].position));
            } else if (p >= this.handoffProgress()) {
               this.activate(model, now);
            }

            this.previous = now;
         } else {
            this.ragdoll
               .tick(this.scene.level, this.scene.gui || !DeathAnimationConfig.INSTANCE.physicsDebris, this.scene.gui ? this.scene.y : this.scene.floorY);
         }
      }
   }

   boolean interact(AbstractClientPlayerEntity player) {
      if (this.scene.animation != DeathAnimationConfig.Animation.WHIRLPOOL && !this.scene.gui && !this.absorbed && !this.waitingForAuthoredFinish()) {
         BipedEntityModel<?> model = this.model();
         if (model == null) {
            return false;
         }

         AcrobaticRagdoll.Frame frame = this.ragdoll == null ? this.capture(model) : null;
         Vec3d start = player.getEyePos();
         Vec3d direction = player.getRotationVector().normalize();
         int part = -1;
         double nearest = 4.5;

         for (int i = 0; i < 6; i++) {
            Box bounds = this.ragdoll == null ? frame.bounds[i] : this.pieces[i].bounds();
            Double t = ray(bounds.expand(0.035), start, direction, 4.5);
            if (t != null && t < nearest) {
               nearest = t;
               part = i;
            }
         }

         if (part < 0) {
            return false;
         }

         Vec3d point = start.add(direction.multiply(nearest));
         BlockHitResult obstruction = this.scene.level.raycast(new RaycastContext(start, point, ShapeType.OUTLINE, FluidHandling.NONE, player));
         if (obstruction.getType() == Type.BLOCK && obstruction.getPos().squaredDistanceTo(start) < point.squaredDistanceTo(start) - 1.0E-4) {
            return false;
         }

         if (this.ragdoll == null) {
            this.activate(model, frame);
         }

         this.ragdoll.impulse(part, direction.multiply(3.2), point.subtract(this.pieces[part].position));
         return true;
      } else {
         return false;
      }
   }

   void applyImpulse(Vec3d impulse, Vec3d point) {
      if (this.scene.animation != DeathAnimationConfig.Animation.WHIRLPOOL
         && !this.absorbed
         && impulse != null
         && !(impulse.lengthSquared() < 1.0E-8)
         && !this.waitingForAuthoredFinish()) {
         BipedEntityModel<?> model = this.model();
         if (model != null) {
            int part = 1;
            if (this.ragdoll == null) {
               this.activate(model, this.capture(model));
            } else {
               double distance = 1.0 / 0.0;

               for (int i = 0; i < 6; i++) {
                  double d = this.pieces[i].position.squaredDistanceTo(point);
                  if (d < distance) {
                     distance = d;
                     part = i;
                  }
               }
            }

            this.ragdoll.impulse(part, impulse, point.subtract(this.pieces[part].position));
         }
      }
   }

   DeathBodyRig.Motion[] pose(BipedEntityModel<?> model) {
      if (this.absorbed) {
         DeathBodyRig.Motion[] hidden = new DeathBodyRig.Motion[6];

         for (int i = 0; i < hidden.length; i++) {
            hidden[i] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F);
         }

         return hidden;
      } else if (this.ragdoll == null) {
         DeathBodyRig.Motion[] motions = new DeathBodyRig.Motion[6];

         for (int i = 0; i < 6; i++) {
            motions[i] = AnimationCatalog.pose(this.scene, i);
         }

         return motions;
      } else {
         float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
         DeathBodyRig.Motion[] result = new DeathBodyRig.Motion[6];

         for (int i = 0; i < 6; i++) {
            Vec3d world = this.pieces[i].previous.lerp(this.pieces[i].position, partial);
            Vector3f local = this.toModel(world).sub(this.ragdollFrameRenderCenter[i]);
            Vector3f rotation = new Quaternionf()
               .rotationZYX(this.pieces[i].previousRotation.z, this.pieces[i].previousRotation.y, this.pieces[i].previousRotation.x)
               .slerp(new Quaternionf().rotationZYX(this.pieces[i].rotation.z, this.pieces[i].rotation.y, this.pieces[i].rotation.x), partial)
               .getEulerAnglesZYX(new Vector3f());
            result[i] = new DeathBodyRig.Motion(true, local.x, local.y, local.z, rotation.x, rotation.y, rotation.z);
         }

         return result;
      }
   }

   Box bounds() {
      if (this.absorbed) {
         return new Box(
            this.scene.x + WhirlpoolMotion.centerX(this.scene) - 0.2,
            this.scene.floorY - 2.8,
            this.scene.z + WhirlpoolMotion.centerZ(this.scene) - 0.2,
            this.scene.x + WhirlpoolMotion.centerX(this.scene) + 0.2,
            this.scene.floorY,
            this.scene.z + WhirlpoolMotion.centerZ(this.scene) + 0.2
         );
      }

      if (this.pieces == null) {
         return new Box(this.scene.x - 2.0, this.scene.y - 1.0, this.scene.z - 2.0, this.scene.x + 2.0, this.scene.y + 3.0, this.scene.z + 2.0);
      }

      Box bounds = this.pieces[0].bounds();

      for (int i = 1; i < 6; i++) {
         bounds = bounds.union(this.pieces[i].bounds());
      }

      return bounds.union(new Box(this.scene.x - 2.0, this.scene.y - 1.0, this.scene.z - 2.0, this.scene.x + 2.0, this.scene.y + 3.0, this.scene.z + 2.0))
         .expand(0.15);
   }

   private void activate(BipedEntityModel<?> model, AcrobaticRagdoll.Frame frame) {
      if (this.ragdoll == null) {
         this.interruptedAtAge = this.scene.age;
         this.pieces = new BodyFragment[6];
         this.ragdollFrameRenderCenter = frame.renderCenters;

         for (int i = 0; i < 6; i++) {
            this.pieces[i] = new BodyFragment(
               frame.centers[i],
               frame.velocities[i],
               new Vector3f(),
               new Vector3f(),
               frame.geometry[i],
               (float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw),
               0.9375F
            );
            this.pieces[i].rotation.set(frame.localRotations[i]);
            this.pieces[i].previousRotation.set(frame.localRotations[i]);
         }

         this.ragdoll = new JointedRagdoll(this.pieces, frame.anchors, 1.0F, frame.angularVelocities);
         if (this.scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER) {
            BanHammer.strike(this.scene, this.ragdoll);
         }
      }
   }

   private AcrobaticRagdoll.Frame capture(BipedEntityModel<?> model) {
      DeathScene.Root root = this.scene.root();
      Quaternionf bodyRotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw()))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));
      Quaternionf actorFacing = new Quaternionf().rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw));
      Quaternionf base = new Quaternionf(actorFacing).rotateZ(3.1415927F);
      DeathBodyRig.Geometry[] geometry = new DeathBodyRig.Geometry[6];
      Vec3d[] centers = new Vec3d[6];
      Quaternionf[] rotations = new Quaternionf[6];
      Vector3f[] localRotations = new Vector3f[6];
      Vector3f[] renderCenters = new Vector3f[6];
      Vec3d[] pivots = new Vec3d[6];
      Box[] bounds = new Box[6];
      DeathBodyRig.Motion[] motions = new DeathBodyRig.Motion[6];

      for (int i = 0; i < 6; i++) {
         DeathBodyRig.Part part = DeathBodyRig.Part.values()[i];
         ModelPart bone = DeathBodyRig.part(model, part);
         ModelTransform initial = bone.getDefaultTransform();
         geometry[i] = DeathBodyRig.geometry(bone);
         motions[i] = AnimationCatalog.pose(this.scene, i);
         renderCenters[i] = new Vector3f(initial.x(), initial.y(), initial.z()).div(16.0F).add(geometry[i].center());
         Quaternionf localRotation = new Quaternionf()
            .rotationZYX(initial.roll() + motions[i].zRot(), initial.yaw() + motions[i].yRot(), initial.pitch() + motions[i].xRot());
         Vector3f center = new Vector3f(geometry[i].center());
         localRotation.transform(center).add(initial.x() / 16.0F + motions[i].x(), initial.y() / 16.0F + motions[i].y(), initial.z() / 16.0F + motions[i].z());
         Quaternionf desired = new Quaternionf(bodyRotation).mul(actorFacing).rotateZ(3.1415927F).mul(localRotation).normalize();
         Quaternionf actorLocal = new Quaternionf(base).invert().mul(desired).normalize();
         rotations[i] = desired;
         localRotations[i] = actorLocal.getEulerAnglesZYX(new Vector3f());
         centers[i] = this.worldPoint(center, root, actorFacing, bodyRotation);
         Vector3f pivot = new Vector3f(initial.x() / 16.0F + motions[i].x(), initial.y() / 16.0F + motions[i].y(), initial.z() / 16.0F + motions[i].z());
         pivots[i] = this.worldPoint(pivot, root, actorFacing, bodyRotation);
         bounds[i] = this.bounds(centers[i], geometry[i], desired);
      }

      Vector3f[][] partAnchors = new Vector3f[6][2];

      for (int i = 0; i < 6; i++) {
         if (i != 1) {
            Vec3d parentDelta = pivots[i].subtract(centers[1]);
            Vec3d childDelta = pivots[i].subtract(centers[i]);
            partAnchors[i][0] = new Quaternionf(rotations[1])
               .conjugate()
               .transform(new Vector3f((float)parentDelta.x, (float)parentDelta.y, (float)parentDelta.z));
            partAnchors[i][1] = new Quaternionf(rotations[i])
               .conjugate()
               .transform(new Vector3f((float)childDelta.x, (float)childDelta.y, (float)childDelta.z));
         }
      }

      Vec3d[] velocities = new Vec3d[6];
      Vec3d[] angularVelocities = new Vec3d[6];
      Vec3d inherited = this.scene.inheritedVelocity.lengthSquared() > 0.36
         ? this.scene.inheritedVelocity.normalize().multiply(0.6)
         : this.scene.inheritedVelocity;

      for (int i = 0; i < 6; i++) {
         if (this.previous == null) {
            velocities[i] = inherited;
         } else {
            velocities[i] = centers[i].subtract(this.previous.centers[i]).add(inherited);
         }

         if (this.previous == null) {
            angularVelocities[i] = Vec3d.ZERO;
         } else {
            angularVelocities[i] = this.angularVelocity(this.previous.rotations[i], rotations[i]);
         }
      }

      return new AcrobaticRagdoll.Frame(geometry, centers, rotations, localRotations, renderCenters, pivots, bounds, velocities, angularVelocities, partAnchors);
   }

   private AcrobaticRagdoll.Collision detect(AcrobaticRagdoll.Frame frame) {
      if (this.previous == null) {
         return null;
      }

      for (int i = 0; i < 6; i++) {
         Box swept = this.previous.bounds[i].union(frame.bounds[i]).expand(0.015);

         for (VoxelShape shape : this.scene.level.getBlockCollisions(null, swept)) {
            for (Box block : shape.getBoundingBoxes()) {
               if (!(block.maxY <= this.scene.floorY + 0.035) && block.intersects(swept)) {
                  Vec3d v = frame.velocities[i];
                  return new AcrobaticRagdoll.Collision(i, frame.centers[i], v.lengthSquared() < 0.015 ? Vec3d.ZERO : v.normalize().multiply(-2.4));
               }
            }
         }

         if (!this.scene.gui) {
            for (Entity entity : this.scene
               .level
               .getEntitiesByClass(
                  Entity.class,
                  swept,
                  candidate -> candidate != this.scene.avatar
                     && !(candidate instanceof DeathAvatar)
                     && candidate.getId() != this.scene.sourceId
                     && !this.scene.props.contains(candidate)
               )) {
               Box other = entity.getBoundingBox();
               boolean hit = frame.bounds[i].intersects(other)
                  && (
                     !this.previous.bounds[i].intersects(other)
                        || entity.getVelocity().lengthSquared() > 0.0064
                        || frame.centers[i].squaredDistanceTo(this.previous.centers[i]) > 0.0064
                  );
               if (hit) {
                  Vec3d normal = frame.centers[i].subtract(other.getCenter());
                  if (normal.lengthSquared() < 1.0E-6) {
                     normal = frame.velocities[i].multiply(-1.0);
                  }

                  if (normal.lengthSquared() < 1.0E-6) {
                     normal = new Vec3d(1.0, 0.0, 0.0);
                  }

                  return new AcrobaticRagdoll.Collision(i, frame.centers[i], normal.normalize().multiply(3.0));
               }
            }
         }
      }

      return null;
   }

   private Vec3d worldPoint(Vector3f model, DeathScene.Root root, Quaternionf facing, Quaternionf body) {
      Vector3f point = new Vector3f(-model.x, 1.501F - model.y, model.z).mul(0.9375F);
      facing.transform(point);
      point.mul(root.sx(), root.sy(), root.sz());
      point.y = point.y - root.pivotY();
      body.transform(point);
      point.y = point.y + root.pivotY();
      return new Vec3d(this.scene.x + root.x() + point.x, this.scene.y + root.y() + point.y, this.scene.z + root.z() + point.z);
   }

   private Box bounds(Vec3d center, DeathBodyRig.Geometry geometry, Quaternionf rotation) {
      Vector3f low = new Vector3f(1.0F / 0.0F);
      Vector3f high = new Vector3f(-1.0F / 0.0F);
      Vector3f v = new Vector3f();

      for (Vector3f vertex : geometry.vertices()) {
         rotation.transform(vertex, v).mul(0.9375F);
         low.min(v);
         high.max(v);
      }

      return new Box(center.x + low.x, center.y + low.y, center.z + low.z, center.x + high.x, center.y + high.y, center.z + high.z);
   }

   private Vec3d angularVelocity(Quaternionf before, Quaternionf after) {
      Quaternionf delta = new Quaternionf(after).mul(new Quaternionf(before).conjugate()).normalize();
      if (delta.w < 0.0F) {
         delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
      }

      double sin = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
      if (sin < 1.0E-7) {
         return Vec3d.ZERO;
      }

      double angle = 2.0 * Math.atan2(sin, Math.max(-1.0F, Math.min(1.0F, delta.w)));
      double scale = angle / sin;
      Vec3d omega = new Vec3d(delta.x * scale, delta.y * scale, delta.z * scale);
      return omega.lengthSquared() > 2.25 ? omega.normalize().multiply(1.5) : omega;
   }

   private Vector3f toModel(Vec3d world) {
      Vector3f local = new Vector3f((float)(world.x - this.scene.x), (float)(world.y - this.scene.y), (float)(world.z - this.scene.z));
      new Quaternionf().rotationY((float)Math.toRadians(this.scene.avatar.bodyYaw - 180.0F)).transform(local);
      return new Vector3f(-local.x / 0.9375F, 1.501F - local.y / 0.9375F, local.z / 0.9375F);
   }

   private BipedEntityModel<?> model() {
      return MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
            && living.getModel() instanceof BipedEntityModel<?> model
         ? model
         : null;
   }

   private void absorb() {
      this.absorbed = true;
      this.ragdoll = null;
      this.pieces = null;
      this.previous = null;
      this.ragdollFrameRenderCenter = null;
   }

   private void reset() {
      this.ragdoll = null;
      this.pieces = null;
      this.previous = null;
      this.ragdollFrameRenderCenter = null;
      this.interruptedAtAge = -1.0F;
      this.absorbed = false;
   }

   private static Double ray(Box box, Vec3d start, Vec3d direction, double max) {
      double lo = 0.0;
      double hi = max;
      double[] o = new double[]{start.x, start.y, start.z};
      double[] d = new double[]{direction.x, direction.y, direction.z};
      double[] mn = new double[]{box.minX, box.minY, box.minZ};
      double[] mx = new double[]{box.maxX, box.maxY, box.maxZ};

      for (int i = 0; i < 3; i++) {
         if (Math.abs(d[i]) < 1.0E-9) {
            if (o[i] < mn[i] || o[i] > mx[i]) {
               return null;
            }
         } else {
            double a = (mn[i] - o[i]) / d[i];
            double b = (mx[i] - o[i]) / d[i];
            if (a > b) {
               double t = a;
               a = b;
               b = t;
            }

            lo = Math.max(lo, a);
            hi = Math.min(hi, b);
            if (lo > hi) {
               return null;
            }
         }
      }

      return lo;
   }

   @Environment(EnvType.CLIENT)
   private record Collision(int part, Vec3d point, Vec3d impulse) {
   }

   @Environment(EnvType.CLIENT)
   private record Frame(
      DeathBodyRig.Geometry[] geometry,
      Vec3d[] centers,
      Quaternionf[] rotations,
      Vector3f[] localRotations,
      Vector3f[] renderCenters,
      Vec3d[] pivots,
      Box[] bounds,
      Vec3d[] velocities,
      Vec3d[] angularVelocities,
      Vector3f[][] anchors
   ) {
   }
}
