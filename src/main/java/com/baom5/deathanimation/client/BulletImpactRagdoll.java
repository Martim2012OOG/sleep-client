package com.baom5.deathanimation.client;

import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class BulletImpactRagdoll {
   private static final float SCALE = 0.9375F;
   private final DeathScene scene;
   private final Vector3f[] centers = new Vector3f[6];
   private final Vector3f[] pivots = new Vector3f[6];
   private final DeathBodyRig.Geometry[] geometry = new DeathBodyRig.Geometry[6];
   private final BodyFragment[] pieces = new BodyFragment[6];
   private final Quaternionf facing;
   private JointedRagdoll solver;
   private BulletImpactRagdoll.Frame previous;
   private BulletImpactRagdoll.Frame current;
   private int simulated;
   private boolean kicked;
   private float landingAge = -1.0F;
   private Vec3d landingPoint;

   public static float impactPhase(DeathScene scene) {
      return AnimationCatalog.recipe(scene.animation).events().getFirst().p();
   }

   private float impactTick() {
      return impactPhase(this.scene) * AnimationTimeline.ticks(this.scene.animation);
   }

   BulletImpactRagdoll(DeathScene scene, BipedEntityModel<?> model) {
      this.scene = scene;
      this.facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));

      for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
         int i = part.ordinal();
         ModelPart bone = DeathBodyRig.part(model, part);
         ModelTransform rest = bone.getDefaultTransform();
         this.geometry[i] = DeathBodyRig.geometry(bone);
         this.pivots[i] = new Vector3f(rest.x(), rest.y(), rest.z()).div(16.0F);
         this.centers[i] = new Vector3f(this.pivots[i]).add(this.geometry[i].center());
      }

      this.reset();
   }

   private Vec3d rotate(Vec3d local) {
      return JointedRagdoll.rotate(this.facing, local);
   }

   private void reset() {
      this.simulated = 0;
      this.kicked = false;
      this.landingAge = -1.0F;
      this.landingPoint = null;

      for (int i = 0; i < 6; i++) {
         Vec3d local = new Vec3d(-this.centers[i].x * 0.9375F, (1.501 - this.centers[i].y) * 0.9375, this.centers[i].z * 0.9375F);
         Vec3d position = this.rotate(local).add(this.scene.x, this.scene.y, this.scene.z);
         this.pieces[i] = new BodyFragment(position, Vec3d.ZERO, new Vector3f(), new Vector3f(), this.geometry[i], 0.0F, 0.9375F);
         this.pieces[i].worldRotation(new Quaternionf(this.facing).rotateZ(3.1415927F));
         this.pieces[i].previousRotation.set(this.pieces[i].rotation);
      }

      this.solver = new JointedRagdoll(this.pieces, this.centers, this.pivots);
      this.current = this.capture();
      this.previous = this.current;
   }

   private void kick() {
      float[] mass = new float[]{5.0F, 30.0F, 4.0F, 4.0F, 9.0F, 9.0F};
      double deflection = ((this.scene.seed & 255L) / 255.0 - 0.5) * 0.025;

      for (int i = 0; i < 6; i++) {
         double side = i == 2 ? -0.055 : (i == 3 ? 0.055 : (i == 4 ? -0.015 : (i == 5 ? 0.015 : 0.0)));
         double forward = i == 0 ? -0.16 : (i >= 4 ? -0.055 : -0.1);
         double up = i == 0 ? 0.14 : (i >= 4 ? 0.045 : 0.085);
         this.solver.impulse(i, this.rotate(new Vec3d(side + deflection, up, forward).multiply(mass[i])), Vec3d.ZERO);
      }

      this.solver.impulse(1, this.rotate(new Vec3d(0.0, 0.8, -5.0)), this.rotate(new Vec3d(0.025, 0.22, 0.14)));
      this.kicked = true;
   }

   private void ensure(float elapsed) {
      if (elapsed < this.simulated - 1.001F && this.simulated > 0) {
         this.reset();
      }

      for (int target = Math.max(0, (int)Math.ceil(elapsed)); this.simulated < target; this.current = this.capture()) {
         this.previous = this.current;
         if (!this.kicked) {
            this.kick();
         }

         this.solver.tick(this.scene.level, this.scene.gui, this.scene.gui ? this.scene.y : this.scene.floorY);
         this.simulated++;
         int contact = this.solver.upperGroundContact();
         if (this.landingAge < 0.0F && this.simulated > 4 && contact >= 0) {
            this.landingAge = this.impactTick() + this.simulated;
            Box bounds = this.pieces[contact].bounds();
            this.landingPoint = new Vec3d(this.pieces[contact].position.x, bounds.minY, this.pieces[contact].position.z);
         }
      }
   }

   public void tick() {
      this.ensure(this.scene.age - this.impactTick());
   }

   private BulletImpactRagdoll.Frame capture() {
      Vec3d[] positions = new Vec3d[6];
      Quaternionf[] rotations = new Quaternionf[6];

      for (int i = 0; i < 6; i++) {
         positions[i] = this.pieces[i].position;
         rotations[i] = this.pieces[i].worldRotation();
      }

      return new BulletImpactRagdoll.Frame(positions, rotations);
   }

   public DeathBodyRig.Motion[] pose() {
      float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
      float elapsed = this.scene.age + partial * DeathAnimationConfig.INSTANCE.animationSpeed - this.impactTick();
      this.ensure(elapsed);
      float blend = this.simulated == 0 ? 1.0F : Math.clamp(elapsed - (this.simulated - 1), 0.0F, 1.0F);
      DeathBodyRig.Motion[] result = new DeathBodyRig.Motion[6];

      for (int i = 0; i < 6; i++) {
         Vec3d position = this.previous.positions[i].lerp(this.current.positions[i], blend).subtract(this.scene.x, this.scene.y, this.scene.z);
         Vector3f local = new Vector3f((float)position.x, (float)position.y, (float)position.z)
            .div(-0.9375F, -0.9375F, 0.9375F)
            .add(0.0F, 1.501F, 0.0F)
            .sub(this.centers[i]);
         Quaternionf worldRotation = new Quaternionf(this.previous.rotations[i]).slerp(this.current.rotations[i], blend);
         Vector3f angles = new Quaternionf().rotationZ(-3.1415927F).mul(worldRotation).getEulerAnglesZYX(new Vector3f());
         result[i] = new DeathBodyRig.Motion(true, local.x, local.y, local.z, angles.x, angles.y, angles.z);
      }

      return result;
   }

   public List<BodyFragment> pieces() {
      return this.kicked ? Arrays.asList(this.pieces) : List.of();
   }

   public float landingAge() {
      return this.landingAge;
   }

   public Vec3d landingPoint() {
      return this.landingPoint;
   }

   @Environment(EnvType.CLIENT)
   private record Frame(Vec3d[] positions, Quaternionf[] rotations) {
   }
}
