package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.ModelPart.Quad;
import net.minecraft.client.model.ModelPart.Vertex;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PlayerDeflate {
   private PlayerDeflate() {
   }

   public static DeathScene.Root root(DeathScene scene) {
      float floor = scene.gui ? 0.0F : (float)(scene.floorY - scene.y);
      float bottom = Acrobatics.minimumY(scene, DeathScene.Root.STILL);
      return new DeathScene.Root(0.0F, floor + 0.014F - bottom, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
   }

   private static float loss(float p, int part) {
      return AnimationTimeline.smooth(
         AnimationTimeline.phase(
            p, part == 1 ? 0.16F : (part == 0 ? 0.27F : (part < 4 ? 0.32F : 0.43F)), part == 1 ? 0.78F : (part == 0 ? 0.84F : (part < 4 ? 0.88F : 0.92F))
         )
      );
   }

   private static float puff(float p) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.025F, 0.14F)) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.16F, 0.29F)));
   }

   public static Vector3f referenceCenter(DeathScene scene, DeathBodyRig.Part part) {
      return MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
            && living.getModel() instanceof BipedEntityModel<?> model
         ? DeathBodyRig.geometry(DeathBodyRig.part(model, part)).center()
         : new Vector3f(0.0F, part == DeathBodyRig.Part.HEAD ? -0.25F : 0.375F, 0.0F);
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int index) {
      float p = scene.progress();
      float inflate = puff(p);
      float pressure = loss(p, index);
      float collapse = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.44F, 0.94F));
      float torsoLoss = loss(p, 1);
      float legLoss = loss(p, 4);
      float armLoss = loss(p, 2);
      float headLoss = loss(p, 0);
      float torsoY = 1.0F - 0.26F * torsoLoss;
      float legY = 1.0F - 0.28F * legLoss;
      float headY = 1.0F - 0.2F * headLoss;
      float armY = 1.0F - 0.27F * armLoss;
      float legAngle = 1.66F * collapse;
      float torsoAngle = 0.24F * torsoLoss + 1.06F * collapse;
      float hipY = 0.75F + 0.68F * collapse;
      float hipLimit = 1.5F - 0.75F * legY * Math.max(0.0F, (float)Math.cos(legAngle)) - 0.125F * (1.0F - 0.58F * legLoss) * (float)Math.sin(legAngle);
      hipY = Math.min(hipY, hipLimit);
      Vector3f hip = new Vector3f(0.0F, hipY, 0.12F * collapse);
      Quaternionf chestRotation = new Quaternionf().rotationX(torsoAngle);
      Vector3f shoulder = chestRotation.transform(new Vector3f(0.0F, -0.75F * torsoY, 0.0F)).add(hip);
      float sx = 1.0F;
      float sy = 1.0F;
      float sz = 1.0F;
      float rx = 0.0F;
      float side = index != 2 && index != 4 ? -1.0F : 1.0F;
      Vector3f target;
      switch (index) {
         case 0:
            rx = torsoAngle + 0.5F * headLoss;
            target = new Quaternionf().rotationX(rx).transform(new Vector3f(0.0F, -0.25F * headY, 0.0F)).add(shoulder);
            sx = 1.0F + 0.05F * inflate - 0.1F * headLoss;
            sy = headY;
            sz = 1.0F + 0.1F * inflate - 0.44F * headLoss;
            break;
         case 1:
            rx = torsoAngle;
            target = chestRotation.transform(new Vector3f(0.0F, 0.375F * torsoY, 0.0F)).add(shoulder);
            sx = 1.0F + 0.1F * inflate - 0.18F * torsoLoss;
            sy = torsoY;
            sz = 1.0F + 0.18F * inflate - 0.72F * torsoLoss;
            break;
         case 2:
         case 3:
            rx = 0.1F * armLoss - (index == 2 ? 1.46F : 1.54F) * collapse;
            Vector3f joint = chestRotation.transform(new Vector3f(0.0F, 0.125F * torsoY, 0.0F)).add(shoulder);
            joint.x = side * (0.25F * (1.0F - 0.18F * torsoLoss) + 0.085F * (1.0F - 0.3F * armLoss));
            target = new Quaternionf().rotationX(rx).transform(new Vector3f(0.0F, 0.375F * armY, 0.0F)).add(joint);
            sx = 1.0F + 0.04F * inflate - 0.3F * armLoss;
            sy = armY;
            sz = 1.0F + 0.08F * inflate - 0.63F * armLoss;
            break;
         case 4:
         case 5:
            rx = legAngle;
            target = new Quaternionf().rotationX(rx).transform(new Vector3f(side * 0.125F, 0.375F * legY, 0.0F)).add(hip);
            sx = 1.0F + 0.025F * inflate - 0.2F * legLoss;
            sy = legY;
            sz = 1.0F + 0.06F * inflate - 0.58F * legLoss;
            break;
         default:
            throw new IllegalArgumentException("Unknown body part " + index);
      }

      DeathBodyRig.Part part = DeathBodyRig.Part.values()[index];
      Vector3f center = referenceCenter(scene, part);
      if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         ModelTransform initial = DeathBodyRig.part(model, part).getDefaultTransform();
         center.add(initial.x() / 16.0F, initial.y() / 16.0F, initial.z() / 16.0F);
      }

      return new DeathBodyRig.Motion(true, target.x - center.x, target.y - center.y, target.z - center.z, rx, 0.0F, 0.0F, -1.0F, 1.0F, sx, sy, sz);
   }

   public static void renderPart(
      DeathScene scene, DeathBodyRig.Part bodyPart, ModelPart part, MatrixStack poses, VertexConsumer out, int light, int overlay, int color
   ) {
      float p = scene.progress();
      float pressure = loss(p, bodyPart.ordinal());
      Vector3f center = referenceCenter(scene, bodyPart);
      part.forEachCuboid(
         new MatrixStack(),
         (transform, path, cubeIndex, cube) -> {
            if (PlayerSkinLayers.visibleChildPath(part, path)) {
               for (Quad polygon : cube.sides) {
                  if (polygon.vertices().length == 4) {
                     Vector3f[] points = new Vector3f[4];
                     float[] u = new float[4];
                     float[] v = new float[4];

                     for (int i = 0; i < 4; i++) {
                        Vertex vertex = polygon.vertices()[i];
                        points[i] = transform.getPositionMatrix().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ()));
                        u[i] = vertex.u();
                        v[i] = vertex.v();
                     }

                     for (int row = 0; row < 8; row++) {
                        for (int col = 0; col < 4; col++) {
                           Vector3f[] deformed = new Vector3f[4];
                           float[] us = new float[4];
                           float[] vs = new float[4];
                           float[] xs = new float[]{col / 4.0F, (col + 1) / 4.0F, (col + 1) / 4.0F, col / 4.0F};
                           float[] ys = new float[]{row / 8.0F, row / 8.0F, (row + 1) / 8.0F, (row + 1) / 8.0F};

                           for (int i = 0; i < 4; i++) {
                              float a = xs[i];
                              float b = ys[i];
                              Vector3f point = new Vector3f(points[0]).lerp(points[1], a).lerp(new Vector3f(points[3]).lerp(points[2], a), b);
                              deformed[i] = deform(point, center, bodyPart.ordinal(), pressure, puff(p));
                              us[i] = (u[0] * (1.0F - a) + u[1] * a) * (1.0F - b) + (u[3] * (1.0F - a) + u[2] * a) * b;
                              vs[i] = (v[0] * (1.0F - a) + v[1] * a) * (1.0F - b) + (v[3] * (1.0F - a) + v[2] * a) * b;
                           }

                           Vector3f normal = new Vector3f(deformed[1]).sub(deformed[0]).cross(new Vector3f(deformed[2]).sub(deformed[0]));
                           if (!(normal.lengthSquared() < 1.0E-12F)) {
                              normal.normalize();

                              for (int i = 0; i < 4; i++) {
                                 out.vertex(poses.peek(), deformed[i])
                                    .color(color)
                                    .texture(us[i], vs[i])
                                    .overlay(overlay)
                                    .light(light)
                                    .normal(poses.peek(), normal);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }

   private static Vector3f deform(Vector3f point, Vector3f center, int part, float pressure, float inflate) {
      float length = part == 0 ? 0.5F : 0.75F;
      float t = Math.clamp((point.y - center.y) / length + 0.5F, 0.0F, 1.0F);
      float envelope = (float)Math.sin(3.141592653589793 * t);
      float pleat = (float)Math.sin(t * 3.141592653589793 * 6.0);
      float crossSection = 1.0F + 0.09F * inflate * envelope - 0.16F * pressure * envelope;
      point.x = center.x + (point.x - center.x) * crossSection;
      point.z = center.z + (point.z - center.z) * crossSection;
      point.z += pressure * (part == 1 ? 0.04F : 0.018F) * pleat * envelope;
      point.x = point.x + pressure * 0.012F * (float)Math.sin(t * 3.141592653589793 * 4.0) * envelope;
      return point;
   }

   public static void effects(DeathScene scene) {
      float p = scene.progress();
      if (!(p < 0.19F) && !(p > 0.96F)) {
         float pressure = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.19F, 0.94F));
         float weakening = 1.0F - 0.82F * pressure;
         int soundInterval = pressure < 0.72F ? 6 : 9;
         if ((int)scene.age % soundInterval == 0 && DeathAnimationConfig.INSTANCE.sounds) {
            float volume = 0.22F * weakening * (scene.gui ? 0.3F : 1.0F);
            scene.level
               .playSoundClient(
                  scene.x, scene.floorY + 0.92, scene.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, volume, 1.02F - 0.24F * pressure, false
               );
         }

         float leakRate = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.18F, 0.38F))
            * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.97F)));
         if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && leakRate > 0.01F && (int)scene.age % 4 == 0) {
            float collapse = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.56F, 0.91F));
            float y = (float)scene.floorY + 1.03F - 0.7F * collapse;
            float side = (int)(scene.age / 4.0F) % 2 == 0 ? 1.0F : -1.0F;
            double speed = 0.006 + 0.01 * leakRate;
            EffectParticles.add(scene, ParticleTypes.CLOUD, scene.x + side * 0.28, y, scene.z + 0.12, side * speed, 0.006 + 0.006 * leakRate, 0.008);
            if (leakRate > 0.35F) {
               EffectParticles.add(scene, ParticleTypes.CLOUD, scene.x - side * 0.24, y - 0.22, scene.z - 0.1, -side * speed, 0.005, -0.006);
            }
         }
      }
   }
}
