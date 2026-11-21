package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class WhirlpoolMotion {
   static final float SWALLOW_PROGRESS = 0.99F;
   private static final float CENTER_X = 0.0F;
   private static final float OUTER_RADIUS = 2.6F;
   private static final float CENTER_Z = -2.6F;
   private static final float ORBIT_TURNS = 6.25F;
   private static final float RADIAL_START = 0.0F;
   private static final float RADIAL_END = 0.965F;
   private static final float ANGLE_START = 0.0F;
   private static final float ANGLE_END = 1.0F;
   private static final float SINK_START = 0.8F;
   private static final float SINK_END = 0.975F;
   private static final float SINK_DISTANCE = 3.25F;
   private static final float TAIL_RADIANS_PER_AGE_TICK = 0.7F;

   private WhirlpoolMotion() {
   }

   static DeathScene.Root root(DeathScene scene) {
      WhirlpoolMotion.Flow flow = flow(scene, scene.progress());
      float p = flow.progress;
      float q = flow.tightening;
      float angle = flow.angle;
      float dive = 18.0F * smooth(phase(p, 0.8F, 0.975F));
      float ground = (float)(scene.floorY - scene.y);
      float sink = descentAt(p);
      float pitch = 10.0F + 14.0F * q + dive;
      float roll = -12.0F - 14.0F * q;
      return new DeathScene.Root(flow.x, ground - sink, flow.z, pitch, (float)Math.toDegrees(angle - 1.5707963267948966), roll, 1.0F, 1.0F, 1.0F, 0.9F);
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      WhirlpoolMotion.Flow flow = flow(scene, scene.progress());
      float q = flow.tightening;
      float angle = flow.angle;
      float phase = angle * 0.35F - flow.angularStep * 1.4F;
      float pulse = (float)Math.sin(phase);
      float late = smooth(phase(flow.progress, 0.78F, 0.96F));
      float rx = 0.0F;
      float ry = 0.0F;
      float rz = 0.0F;
      switch (part) {
         case 0:
            rx = (float)Math.toRadians(-7.0F - 8.0F * q - 4.0F * late);
            ry = (float)Math.toRadians(-5.0F * pulse);
            rz = (float)Math.toRadians(5.0F * (float)Math.cos(phase - 0.35F));
            break;
         case 1:
            rx = (float)Math.toRadians(4.0F + 8.0F * q + 8.0F * late);
            rz = (float)Math.toRadians(4.0F * pulse * q);
            break;
         case 2:
         case 3: {
            float side = part == 2 ? -1.0F : 1.0F;
            rx = 0.45F + 0.35F * q + 0.12F * pulse;
            ry = side * (0.08F + 0.18F * q);
            rz = side * (0.35F + 0.18F * q + 0.1F * (float)Math.cos(phase - side * 0.45F));
            break;
         }
         case 4:
         case 5: {
            float side = part == 4 ? -1.0F : 1.0F;
            rx = 0.28F + 0.22F * q + 0.16F * late + 0.2F * (float)Math.sin(phase - side * 0.5F);
            ry = side * (0.04F + 0.08F * q);
            rz = side * (0.16F + 0.28F * q) + 0.08F * pulse;
         }
      }

      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, rx, ry, rz);
   }

   static WhirlpoolMotion.Flow flow(DeathScene scene, float progress) {
      float p = MathHelper.clamp(progress, 0.0F, 1.0F);
      float q = radialTightening(p);
      float orbit = phase(p, 0.0F, 1.0F);
      float duration = AnimationCatalog.recipe(DeathAnimationConfig.Animation.WHIRLPOOL).ticks();
      float speed = DeathAnimationConfig.INSTANCE.animationSpeed;
      float currentAge = Math.max(scene.age, p * duration);
      float previousAge = Math.max(0.0F, currentAge - speed);
      float previousP = MathHelper.clamp(previousAge / duration, 0.0F, 1.0F);
      float angle = orbitAngle(orbit) + tailAngle(currentAge, duration);
      float radius = 2.6F * (1.0F - q);
      float previousOrbit = phase(previousP, 0.0F, 1.0F);
      float previousAngle = orbitAngle(previousOrbit) + tailAngle(previousAge, duration);
      float previousQ = radialTightening(previousP);
      float previousRadius = 2.6F * (1.0F - previousQ);
      float angularStep = Math.max(0.0F, angle - previousAngle);
      float radialStep = Math.max(0.0F, previousRadius - radius);
      float tighteningStep = Math.max(0.0F, q - previousQ);
      float sinkStep = Math.max(0.0F, descentAt(p) - descentAt(previousP));
      float x = 0.0F + radius * (float)Math.sin(angle);
      float z = -2.6F + radius * (float)Math.cos(angle);
      return new WhirlpoolMotion.Flow(p, q, orbit, angle, radius, x, z, angularStep, radialStep, tighteningStep, sinkStep);
   }

   static float outerRadius() {
      return 2.6F;
   }

   static float centerX(DeathScene scene) {
      return offsetX(scene, 0.0F, -2.6F);
   }

   static float centerZ(DeathScene scene) {
      return offsetZ(scene, 0.0F, -2.6F);
   }

   static float offsetX(DeathScene scene, float x, float z) {
      float facing = (float)Math.toRadians(scene.sceneFacing);
      return x * (float)Math.cos(facing) + z * (float)Math.sin(facing);
   }

   static float offsetZ(DeathScene scene, float x, float z) {
      float facing = (float)Math.toRadians(scene.sceneFacing);
      return -x * (float)Math.sin(facing) + z * (float)Math.cos(facing);
   }

   static float funnelFade(DeathScene scene) {
      float end = AnimationCatalog.recipe(DeathAnimationConfig.Animation.WHIRLPOOL).ticks();
      return 1.0F - smooth(phase(scene.age, end + 2.0F, end + 18.0F));
   }

   private static float orbitAngle(float orbit) {
      return (float)(39.269908169872416 * (0.28F * orbit + 0.72F * orbit * orbit));
   }

   private static float tailAngle(float age, float duration) {
      return Math.max(0.0F, age - duration) * 0.7F;
   }

   private static float radialTightening(float progress) {
      float linear = phase(progress, 0.0F, 0.965F);
      return smooth(linear);
   }

   private static float sinkAt(float progress) {
      return 3.25F * smooth(phase(progress, 0.8F, 0.975F));
   }

   private static float descentAt(float progress) {
      return 0.28F * radialTightening(progress) + sinkAt(progress);
   }

   private static float phase(float value, float start, float end) {
      return MathHelper.clamp((value - start) / Math.max(1.0E-4F, end - start), 0.0F, 1.0F);
   }

   private static float smooth(float value) {
      return value * value * (3.0F - 2.0F * value);
   }

   @Environment(EnvType.CLIENT)
   record Flow(
      float progress,
      float tightening,
      float orbit,
      float angle,
      float radius,
      float x,
      float z,
      float angularStep,
      float radialStep,
      float tighteningStep,
      float sinkStep
   ) {
   }
}
