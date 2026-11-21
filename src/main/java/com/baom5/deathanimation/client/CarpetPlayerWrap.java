package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.ModelPart.Quad;
import net.minecraft.client.model.ModelPart.Vertex;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class CarpetPlayerWrap {
   private static final float SCALE = 0.9375F;

   private CarpetPlayerWrap() {
   }

   public static boolean active(DeathBodyRig.Pose pose) {
      return pose != null && pose.scene() != null && pose.scene().animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL;
   }

   static DeathScene.Root root(DeathScene scene) {
      return new DeathScene.Root(0.0F, scene.gui ? 0.0F : (float)(scene.floorY - scene.y), 0.0F, 0.0F, scene.sceneFacing, 0.0F);
   }

   public static void render(BipedEntityModel<?> model, DeathBodyRig.Pose rig, MatrixStack poses, VertexConsumer out, int light, int overlay, int color) {
      DeathScene scene = rig.scene();
      if (!(scene.progress() >= 0.84F)) {
         for (DeathBodyRig.Part bodyPart : DeathBodyRig.Part.values()) {
            ModelPart part = DeathBodyRig.part(model, bodyPart);
            if (rig.original(bodyPart).visible()) {
               ModelTransform initial = part.getDefaultTransform();
               part.visible = true;
               part.setOrigin(initial.x(), initial.y(), initial.z());
               part.setAngles(0.0F, 0.0F, 0.0F);
               part.forEachCuboid(
                  new MatrixStack(),
                  (transform, path, index, cube) -> {
                     if (PlayerSkinLayers.visibleChildPath(part, path)) {
                        for (Quad face : cube.sides) {
                           if (face.vertices().length == 4) {
                              Vector3f[] vertices = new Vector3f[4];
                              float[] u = new float[4];
                              float[] v = new float[4];

                              for (int i = 0; i < 4; i++) {
                                 Vertex vertex = face.vertices()[i];
                                 vertices[i] = transform.getPositionMatrix().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ()));
                                 u[i] = vertex.u();
                                 v[i] = vertex.v();
                              }

                              for (int row = 0; row < 12; row++) {
                                 for (int col = 0; col < 8; col++) {
                                    float[] a = new float[]{col / 8.0F, (col + 1) / 8.0F, (col + 1) / 8.0F, col / 8.0F};
                                    float[] b = new float[]{row / 12.0F, row / 12.0F, (row + 1) / 12.0F, (row + 1) / 12.0F};
                                    Vector3f[] mapped = new Vector3f[4];
                                    float[] us = new float[4];
                                    float[] vs = new float[4];
                                    boolean covered = true;

                                    for (int i = 0; i < 4; i++) {
                                       Vector3f point = new Vector3f(vertices[0])
                                          .lerp(vertices[1], a[i])
                                          .lerp(new Vector3f(vertices[3]).lerp(vertices[2], a[i]), b[i]);
                                       covered &= covered(scene, point);
                                       mapped[i] = deform(scene, point);
                                       us[i] = (u[0] * (1.0F - a[i]) + u[1] * a[i]) * (1.0F - b[i]) + (u[3] * (1.0F - a[i]) + u[2] * a[i]) * b[i];
                                       vs[i] = (v[0] * (1.0F - a[i]) + v[1] * a[i]) * (1.0F - b[i]) + (v[3] * (1.0F - a[i]) + v[2] * a[i]) * b[i];
                                    }

                                    if (!covered) {
                                       Vector3f normal = new Vector3f(mapped[1]).sub(mapped[0]).cross(new Vector3f(mapped[2]).sub(mapped[0]));
                                       if (!(normal.lengthSquared() < 1.0E-14F)) {
                                          normal.normalize();

                                          for (int i = 0; i < 4; i++) {
                                             out.vertex(poses.peek(), mapped[i])
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
                  }
               );
            }
         }
      }
   }

   private static Vector3f laid(DeathScene scene, Vector3f model) {
      float lay = AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.0F, 0.18F));
      Vector3f point = new Vector3f(-model.x * 0.9375F, (1.501F - model.y) * 0.9375F, model.z * 0.9375F);
      Quaternionf rotation = new Quaternionf().slerp(new Quaternionf().rotationZ(-1.5707964F).rotateY(1.5707964F), lay);
      return rotation.transform(point).add(-0.85F * lay, 0.32F * lay, 0.0F);
   }

   private static float front(DeathScene scene) {
      return -2.0F + 4.0F * AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.08F, 0.84F));
   }

   private static boolean covered(DeathScene scene, Vector3f model) {
      Vector3f point = laid(scene, model);
      float q = Math.max(0.0F, front(scene) - point.x);
      float outer = 0.16F + 0.095F * (front(scene) + 2.0F);
      float radius = Math.max(0.16F, outer - 0.095F * q);
      return -Math.log(radius / outer) / 0.0949999988079071 > 1.5707963267948966;
   }

   private static Vector3f deform(DeathScene scene, Vector3f model) {
      Vector3f point = laid(scene, model);
      float front = front(scene);
      float q = Math.max(0.0F, front - point.x);
      float flatten = AnimationTimeline.smooth(AnimationTimeline.phase(front - point.x, -0.16F, 0.0F));
      float thickness = 0.07F + (point.y - 0.07F) * (1.0F - 0.982F * flatten);
      if (q > 0.0F) {
         float outer = 0.16F + 0.095F * (front + 2.0F);
         float radius = Math.max(0.16F, outer - 0.095F * q);
         float theta = (float)(-Math.log(radius / outer) / 0.0949999988079071);
         float tangent = (float)Math.atan2(0.0949999988079071 * Math.cos(theta) + Math.sin(theta), -0.0949999988079071 * Math.sin(theta) + Math.cos(theta));
         float inset = 0.012F + Math.clamp(thickness - 0.07F, -0.004F, 0.012F);
         point.set(
            front + radius * (float)Math.sin(theta) - inset * (float)Math.sin(tangent),
            0.03125F + outer - radius * (float)Math.cos(theta) + inset * (float)Math.cos(tangent),
            point.z
         );
      } else {
         point.y = thickness;
      }

      return point.set(-point.x / 0.9375F, 1.501F - point.y / 0.9375F, point.z / 0.9375F);
   }
}
