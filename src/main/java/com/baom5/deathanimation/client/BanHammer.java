package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class BanHammer {
   static final float IMPACT = 0.64F;
   private static final float SCALE = 0.82F;
   private static final float SWING_START = 0.06F;
   private static final float FACE_X = -0.9375F;
   private static final float FACE_Y = 2.240625F;
   private static final float PIVOT_LOCAL_Y = -0.34375F;
   private static final float CONTACT_ANGLE = 1.5707964F;
   private static final int SWING_SAMPLES = 240;
   private static final float START_ANGULAR_SPEED = 0.05F;
   private static final float[] GRAVITY_SWING = gravitySwing();

   private BanHammer() {
   }

   static DeathScene.Root swingPose(DeathScene scene, float progress) {
      float t = MathHelper.clamp((progress - 0.06F) / 0.58F, 0.0F, 1.0F);
      float radians = gravityAngle(t);
      float pivotX = 2.1191874F;
      float pivotY = contactHeight(scene) - -0.76875F;
      float localX = 0.34375F * (float)Math.sin(radians);
      float localY = -0.34375F * (float)Math.cos(radians);
      float fade = 1.0F - MathHelper.clamp((progress - 0.76F) / 0.050000012F, 0.0F, 1.0F);
      float scale = Math.max(1.0E-4F, 0.82F * fade);
      float x = pivotX - localX * scale;
      float y = pivotY - localY * scale;
      return new DeathScene.Root(x, y, 0.0F, 0.0F, 0.0F, (float)Math.toDegrees(radians), scale, scale, scale);
   }

   private static float contactHeight(DeathScene scene) {
      if (Float.isFinite(scene.banHammerCrown)) {
         return scene.banHammerCrown;
      }

      float crown = 1.9052343F;
      if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         DeathBodyRig.Geometry head = DeathBodyRig.geometry(model.head);
         float top = 1.0F / 0.0F;

         for (Vector3f point : head.vertices()) {
            top = Math.min(top, point.y + head.center().y + model.head.getDefaultTransform().y() / 16.0F);
         }

         if (Float.isFinite(top)) {
            crown = (1.501F - top) * 0.9375F;
         }
      }

      if (!scene.avatar.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
         crown = Math.max(crown, 1.9345312F);
      }

      return scene.banHammerCrown = crown;
   }

   static Vec3d contactPoint(DeathScene scene) {
      return new Vec3d(scene.x, scene.floorY + contactHeight(scene), scene.z);
   }

   private static float gravityAngle(float t) {
      float sample = t * 240.0F;
      int index = Math.min(239, (int)sample);
      return MathHelper.lerp(sample - index, GRAVITY_SWING[index], GRAVITY_SWING[index + 1]);
   }

   private static float impactAngularSpeed() {
      float durationSeconds = 0.58F * AnimationCatalog.recipe(DeathAnimationConfig.Animation.BAN_HAMMER).ticks() / 20.0F;
      return (GRAVITY_SWING[240] - GRAVITY_SWING[239]) * 240.0F / durationSeconds;
   }

   private static float[] gravitySwing() {
      float target = 1.5707964F;
      double low = 0.0;
      double high = 100.0;

      for (int i = 0; i < 48; i++) {
         double mid = (low + high) * 0.5;
         if (simulateGravity(mid)[240] < target) {
            low = mid;
         } else {
            high = mid;
         }
      }

      return simulateGravity((low + high) * 0.5);
   }

   private static float[] simulateGravity(double gravity) {
      float[] angles = new float[241];
      double dt = 0.004166666666666667;
      double angle = 0.0;
      double velocity = 0.05000000074505806;

      for (int i = 1; i <= 240; i++) {
         velocity += gravity * Math.sin(angle) * dt;
         angle += velocity * dt;
         angles[i] = (float)angle;
      }

      return angles;
   }

   static void strike(DeathScene scene, JointedRagdoll ragdoll) {
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      Vector3f radius = new Vector3f(-2.1191874F, -0.76875F, 0.0F);
      Vector3f direction = new Vector3f(-radius.y, radius.x, 0.0F).normalize();
      Vector3f velocity = facing.transform(new Vector3f(direction).mul(0.2F));
      float[] masses = new float[]{5.0F, 30.0F, 4.0F, 4.0F, 9.0F, 9.0F};

      for (int i = 0; i < masses.length; i++) {
         ragdoll.impulse(i, new Vec3d(velocity.x, velocity.y, velocity.z).multiply(masses[i]), Vec3d.ZERO);
      }

      float strength = 10.0F + Math.min(4.0F, impactAngularSpeed() * 0.5F);
      Vector3f hit = facing.transform(new Vector3f(direction).mul(strength));
      Vector3f offset = facing.transform(new Vector3f(0.0F, 0.12F, -0.04F));
      ragdoll.impulse(0, new Vec3d(hit.x, hit.y, hit.z), new Vec3d(offset.x, offset.y, offset.z));
   }
}
