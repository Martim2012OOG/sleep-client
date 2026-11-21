package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class HangingRagdoll {
   private static final float SCALE = 0.9375F;
   private static final Vec3d CRADLE_PIN = new Vec3d(-0.8, 3.6, 0.0);
   private static final double CORD = 0.85;
   private static final double BALL_HALF = 0.24;
   private final DeathScene scene;
   private final Quaternionf facing;
   private final Vector3f[] centers = new Vector3f[6];
   private final Vector3f[] pivots = new Vector3f[6];
   private final DeathBodyRig.Geometry[] geometry = new DeathBodyRig.Geometry[6];
   private final BodyFragment[] pieces = new BodyFragment[6];
   private final JointedRagdoll.Rigid[] balls = new JointedRagdoll.Rigid[4];
   private final List<HangingRagdoll.Impact> contacts = new ArrayList<>();
   private JointedRagdoll solver;
   private HangingRagdoll.Frame previous;
   private HangingRagdoll.Frame current;
   private HangingRagdoll.Sample sampled;
   private float sampledTime = 0.0F / 0.0F;
   private int simulated;
   private double time;
   private double lastSound = -100.0;
   private Vec3d headTarget;
   private Vec3d headVelocity = Vec3d.ZERO;
   private HangingRagdoll.SoundImpact pendingSound;

   public static boolean supports(DeathAnimationConfig.Animation animation) {
      return false;
   }

   HangingRagdoll(DeathScene scene, BipedEntityModel<?> model) {
      this.scene = scene;
      this.facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));

      for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
         int i = part.ordinal();
         ModelPart bone = DeathBodyRig.part(model, part);
         ModelTransform initial = bone.getDefaultTransform();
         this.geometry[i] = DeathBodyRig.geometry(bone);
         this.pivots[i] = new Vector3f(initial.x(), initial.y(), initial.z()).div(16.0F);
         this.centers[i] = new Vector3f(this.pivots[i]).add(this.geometry[i].center());
      }

      this.reset();
   }

   private Vec3d world(Vec3d local) {
      return JointedRagdoll.rotate(this.facing, local).add(this.scene.x, this.scene.floorY, this.scene.z);
   }

   private Vec3d ballPin(int i) {
      return this.world(new Vec3d(-0.28 + i * 0.52, 3.6, 0.0));
   }

   private Vector3f crown() {
      return new Vector3f(0.0F, -this.pieces[0].halfExtents().y, 0.0F);
   }

   private static Vector3f ballTop() {
      return new Vector3f(0.0F, 0.24F, 0.0F);
   }

   private void reset() {
      this.simulated = 0;
      this.time = 0.0;
      this.pendingSound = null;
      this.lastSound = -100.0;
      this.contacts.clear();
      this.sampled = null;
      this.sampledTime = 0.0F / 0.0F;
      Quaternionf initialRotation = new Quaternionf(this.facing);
      Vector3f neutralHead = new Vector3f(-this.centers[0].x * 0.9375F, (1.501F - this.centers[0].y) * 0.9375F, this.centers[0].z * 0.9375F);
      float headHalf = 0.0F;

      for (Vector3f vertex : this.geometry[0].vertices()) {
         headHalf = Math.max(headHalf, Math.abs(vertex.y) * 0.9375F);
      }

      Vec3d neutralCrown = new Vec3d(neutralHead.x, neutralHead.y + headHalf, neutralHead.z);
      this.headTarget = this.world(CRADLE_PIN.add(-Math.sin(0.65) * 0.85, -Math.cos(0.65) * 0.85, 0.0));

      for (int i = 0; i < 6; i++) {
         Vec3d neutral = new Vec3d(-this.centers[i].x * 0.9375F, (1.501 - this.centers[i].y) * 0.9375, this.centers[i].z * 0.9375F);
         Vec3d position = this.headTarget.add(JointedRagdoll.rotate(initialRotation, neutral.subtract(neutralCrown)));
         this.pieces[i] = new BodyFragment(position, Vec3d.ZERO, new Vector3f(), new Vector3f(), this.geometry[i], 0.0F, 0.9375F);
         this.pieces[i].worldRotation(new Quaternionf(initialRotation).rotateZ(3.1415927F));
         this.pieces[i].previousRotation.set(this.pieces[i].rotation);
      }

      for (int i = 0; i < 4; i++) {
         this.balls[i] = new JointedRagdoll.Rigid(this.ballPin(i).add(0.0, -1.0899999999999999, 0.0), new Vector3f(0.24F), 61.0F);
      }

      this.solver = new JointedRagdoll(this.pieces, this.centers, this.pivots, this);
      this.current = this.capture();
      this.previous = this.current;
   }

   private void ensure(float renderTime) {
      if (renderTime < this.simulated - 1.001F) {
         this.reset();
      }

      for (int target = Math.max(0, (int)Math.ceil(renderTime)); this.simulated < target; this.sampledTime = 0.0F / 0.0F) {
         this.previous = this.current;
         this.solver.tick(this.scene.level, this.scene.gui, this.scene.floorY);
         this.simulated++;
         this.current = this.capture();
      }
   }

   public void tick() {
      this.ensure(this.scene.age);
   }

   private HangingRagdoll.Sample sample() {
      float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
      float renderTime = this.scene.age + partial * DeathAnimationConfig.INSTANCE.animationSpeed;
      this.ensure(renderTime);
      if (this.sampled != null && this.sampledTime == renderTime) {
         return this.sampled;
      }

      float blend = Math.clamp(renderTime - (this.simulated - 1), 0.0F, 1.0F);
      Vec3d[] positions = new Vec3d[10];
      Quaternionf[] rotations = new Quaternionf[10];

      for (int i = 0; i < 10; i++) {
         positions[i] = this.previous.positions[i].lerp(this.current.positions[i], blend);
         rotations[i] = new Quaternionf(this.previous.rotations[i]).slerp(this.current.rotations[i], blend);
      }

      Vec3d top = positions[0].add(JointedRagdoll.rotate(rotations[0], new Vec3d(0.0, this.crown().y, 0.0)));
      Vec3d target = onCord(top, this.world(CRADLE_PIN), 0.85);
      positions[0] = positions[0].add(target.subtract(top));

      for (int i = 0; i < 4; i++) {
         Vec3d offset = JointedRagdoll.rotate(rotations[6 + i], new Vec3d(0.0, 0.24, 0.0));
         Vec3d end = positions[6 + i].add(offset);
         positions[6 + i] = positions[6 + i].add(onCord(end, this.ballPin(i), 0.85).subtract(end));
      }

      this.sampledTime = renderTime;
      return this.sampled = new HangingRagdoll.Sample(positions, rotations);
   }

   private HangingRagdoll.Frame capture() {
      Vec3d[] positions = new Vec3d[10];
      Quaternionf[] rotations = new Quaternionf[10];

      for (int i = 0; i < 6; i++) {
         positions[i] = this.pieces[i].position;
         rotations[i] = this.pieces[i].worldRotation();
      }

      for (int i = 0; i < 4; i++) {
         positions[i + 6] = this.balls[i].position;
         rotations[i + 6] = new Quaternionf(this.balls[i].orientation);
      }

      return new HangingRagdoll.Frame(positions, rotations);
   }

   public DeathBodyRig.Motion[] pose() {
      HangingRagdoll.Sample state = this.sample();
      DeathBodyRig.Motion[] result = new DeathBodyRig.Motion[6];

      for (int i = 0; i < 6; i++) {
         Vec3d position = state.positions[i].subtract(this.scene.x, this.scene.y, this.scene.z);
         Vector3f local = new Vector3f((float)position.x, (float)position.y, (float)position.z)
            .div(-0.9375F, -0.9375F, 0.9375F)
            .add(0.0F, 1.501F, 0.0F)
            .sub(this.centers[i]);
         Vector3f rotation = new Quaternionf().rotationZ(-3.1415927F).mul(state.rotations[i]).getEulerAnglesZYX(new Vector3f());
         result[i] = new DeathBodyRig.Motion(true, local.x, local.y, local.z, rotation.x, rotation.y, rotation.z);
      }

      return result;
   }

   void predict(float dt, JointedRagdoll.Rigid[] bodies) {
      this.time += dt;
      this.contacts.clear();

      for (JointedRagdoll.Rigid body : bodies) {
         body.velocity = body.velocity.multiply(Math.pow(0.975, dt));
         body.omega = body.omega.multiply(Math.pow(0.985, dt));
      }

      for (JointedRagdoll.Rigid ball : this.balls) {
         ball.velocity = ball.velocity.multiply(Math.pow(0.975, dt));
         ball.predict();
      }
   }

   void project(JointedRagdoll.Rigid[] bodies) {
      for (int i = 0; i < 4; i++) {
         constrain(this.balls[i], ballTop(), this.ballPin(i), 0.85, Vec3d.ZERO, true);

         for (JointedRagdoll.Rigid body : bodies) {
            this.collide(body, this.balls[i], true);
         }

         for (int j = i + 1; j < 4; j++) {
            this.collide(this.balls[i], this.balls[j], true);
         }
      }

      constrain(bodies[0], this.crown(), this.world(CRADLE_PIN), 0.85, this.headVelocity, true);
   }

   void closeAttachment(JointedRagdoll.Rigid head) {
      Vec3d point = head.point(this.crown());
      Vec3d target = onCord(point, this.world(CRADLE_PIN), 0.85);
      head.position = head.position.add(target.subtract(point));

      for (int i = 0; i < 4; i++) {
         Vec3d end = this.balls[i].point(ballTop());
         this.balls[i].position = this.balls[i].position.add(onCord(end, this.ballPin(i), 0.85).subtract(end));
      }
   }

   void reconstruct() {
      for (JointedRagdoll.Rigid ball : this.balls) {
         ball.reconstruct();
      }
   }

   boolean canRest(JointedRagdoll.Rigid[] bodies) {
      Vec3d cord = bodies[0].point(this.crown()).subtract(this.world(CRADLE_PIN));
      if (cord.x * cord.x + cord.z * cord.z > 1.0E-4) {
         return false;
      }

      for (int i = 0; i < 4; i++) {
         cord = this.balls[i].point(ballTop()).subtract(this.ballPin(i));
         if (cord.x * cord.x + cord.z * cord.z > 1.0E-4 || this.balls[i].velocity.lengthSquared() > 1.0E-6 || this.balls[i].omega.lengthSquared() > 1.0E-6) {
            return false;
         }
      }

      for (JointedRagdoll.Rigid body : bodies) {
         if (body.velocity.lengthSquared() > 1.0E-6 || body.omega.lengthSquared() > 1.0E-6) {
            return false;
         }
      }

      return true;
   }

   void solveVelocity(JointedRagdoll.Rigid[] bodies) {
      for (HangingRagdoll.Impact hit : this.contacts) {
         Vec3d ra = hit.point.subtract(hit.a.position);
         Vec3d rb = hit.point.subtract(hit.b.position);
         double closing = hit.a.at(ra).subtract(hit.b.at(rb)).dotProduct(hit.normal);
         double desired = -hit.incoming * 0.92;
         if (closing < desired) {
            double weight = hit.a.weight(ra, hit.normal) + hit.b.weight(rb, hit.normal);
            Vec3d impulse = hit.normal.multiply((desired - closing) / weight);
            hit.a.impulse(impulse, ra);
            hit.b.impulse(impulse.multiply(-1.0), rb);
         }
      }

      for (int i = 0; i < 4; i++) {
         for (JointedRagdoll.Rigid body : bodies) {
            this.collide(body, this.balls[i], false);
         }

         for (int j = i + 1; j < 4; j++) {
            this.collide(this.balls[i], this.balls[j], false);
         }

         constrain(this.balls[i], ballTop(), this.ballPin(i), 0.85, Vec3d.ZERO, false);
      }

      constrain(bodies[0], this.crown(), this.world(CRADLE_PIN), 0.85, this.headVelocity, false);
   }

   private void collide(JointedRagdoll.Rigid a, JointedRagdoll.Rigid b, boolean project) {
      if (a.bounds().expand(0.002).intersects(b.bounds().expand(0.002))) {
         if (project) {
            Vec3d penetration = BodyFragment.contact(b.position, b.axes(), b.half, a.position, a.axes(), a.half, 0.002);
            if (penetration != null && penetration.lengthSquared() > 1.0E-12) {
               Vec3d normal = penetration.normalize();
               Vec3d point = a.support(normal.multiply(-1.0)).add(b.support(normal)).multiply(0.5);
               double closing = a.at(point.subtract(a.position)).subtract(b.at(point.subtract(b.position))).dotProduct(normal);
               if (closing < -0.012 && this.contacts.stream().noneMatch(h -> h.a == a && h.b == b)) {
                  this.contacts.add(new HangingRagdoll.Impact(a, b, normal, point, closing));
                  if (this.time - this.lastSound > 6.0 && (this.pendingSound == null || -closing > this.pendingSound.speed)) {
                     this.pendingSound = new HangingRagdoll.SoundImpact(point, -closing);
                     this.lastSound = this.time;
                  }
               }
            }
         }

         JointedRagdoll.contact(a, b, null, project, true);
      }
   }

   private static Vec3d onCord(Vec3d point, Vec3d pin, double length) {
      Vec3d direction = point.subtract(pin);
      return direction.lengthSquared() < 1.0E-12 ? pin.add(0.0, -length, 0.0) : pin.add(direction.normalize().multiply(length));
   }

   private static void constrain(JointedRagdoll.Rigid body, Vector3f local, Vec3d pin, double length, Vec3d pinVelocity, boolean project) {
      Vec3d point = body.point(local);
      Vec3d direction = point.subtract(pin);
      if (direction.lengthSquared() < 1.0E-12) {
         direction = new Vec3d(0.0, -1.0, 0.0);
      }

      Vec3d axis = direction.normalize();
      solveAxis(body, local, pin.add(axis.multiply(length)), pinVelocity, axis, project);
   }

   private static void solveAxis(JointedRagdoll.Rigid body, Vector3f local, Vec3d target, Vec3d pinVelocity, Vec3d axis, boolean project) {
      Vec3d point = body.point(local);
      Vec3d r = point.subtract(body.position);
      double error = project ? point.subtract(target).dotProduct(axis) : body.at(r).subtract(pinVelocity).dotProduct(axis);
      Vec3d impulse = axis.multiply(-error / body.weight(r, axis));
      if (project) {
         body.project(impulse, r);
      } else {
         body.impulse(impulse, r);
      }
   }

   public HangingRagdoll.SoundImpact pollImpact() {
      HangingRagdoll.SoundImpact hit = this.pendingSound;
      this.pendingSound = null;
      return hit;
   }

   public boolean animateProp(AnimationCatalog.Cue cue, Entity entity, DeathScene.Root key, float visible) {
      if (cue.hangingRole() == null) {
         return false;
      }

      HangingRagdoll.Sample state = this.sample();
      Quaternionf rotation = new Quaternionf(this.facing);
      int index = cue.hangingIndex() == null ? 0 : Math.clamp(cue.hangingIndex().intValue(), 0, 3);
      Vector3f scale = new Vector3f(key.sx(), key.sy(), key.sz()).mul(visible);
      Vec3d from = null;
      Vec3d to = null;
      Vec3d position;
      switch (cue.hangingRole()) {
         case "headRope":
            to = state.positions[0].add(JointedRagdoll.rotate(state.rotations[0], new Vec3d(0.0, this.crown().y, 0.0)));
            from = this.world(CRADLE_PIN);
            position = from.add(to).multiply(0.5);
            break;
         case "ballRope":
            from = this.ballPin(index);
            to = state.positions[index + 6].add(JointedRagdoll.rotate(state.rotations[index + 6], new Vec3d(0.0, 0.24, 0.0)));
            position = from.add(to).multiply(0.5);
            break;
         case "ball":
            position = state.positions[index + 6];
            rotation.set(state.rotations[index + 6]);
            break;
         default:
            position = this.world(new Vec3d(key.x(), key.y(), key.z()));
      }

      if (from != null) {
         Vec3d line = to.subtract(from);
         float length = (float)line.length();
         if (length > 1.0E-6F) {
            rotation.rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float)line.x, (float)line.y, (float)line.z).normalize());
         }

         scale.set(0.024F * visible, Math.max(1.0E-4F, length) * visible, 0.024F * visible);
      }

      Vector3f offset = cue.block() != null ? rotation.transform(new Vector3f(scale).mul(-0.5F)) : new Vector3f();
      ((DisplayEntity)entity).setTransformation(new AffineTransformation(offset, rotation, scale, new Quaternionf()));
      entity.setPosition(position);
      entity.lastX = entity.lastRenderX = position.x;
      entity.lastY = entity.lastRenderY = position.y;
      entity.lastZ = entity.lastRenderZ = position.z;
      entity.tick();
      return true;
   }

   @Environment(EnvType.CLIENT)
   private record Frame(Vec3d[] positions, Quaternionf[] rotations) {
   }

   @Environment(EnvType.CLIENT)
   private record Impact(JointedRagdoll.Rigid a, JointedRagdoll.Rigid b, Vec3d normal, Vec3d point, double incoming) {
   }

   @Environment(EnvType.CLIENT)
   private record Sample(Vec3d[] positions, Quaternionf[] rotations) {
   }

   @Environment(EnvType.CLIENT)
   public record SoundImpact(Vec3d point, double speed) {
   }
}
