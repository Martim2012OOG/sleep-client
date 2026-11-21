package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.ModelPart.Quad;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class NestedPlayerShell {
   private NestedPlayerShell() {
   }

   public static boolean active(DeathBodyRig.Pose pose) {
      return pose != null && pose.renderKey() instanceof PlayerActors.Binding binding && "nested-player".equals(binding.cue().actor());
   }

   public static void render(BipedEntityModel<?> model, DeathBodyRig.Pose rig, MatrixStack poses, VertexConsumer out, int light, int overlay, int color) {
      PlayerActors.Binding binding = (PlayerActors.Binding)rig.renderKey();
      float scale = binding.cue().keys().getFirst().sx();
      int level = Math.round(-((float)(Math.log(scale) / Math.log(2.0))));
      float stage = 0.02F + level * 0.06F;
      float f = AnimationTimeline.phase(rig.scene().progress(), stage, stage + 0.06F);
      float open = level >= 15
         ? 0.0F
         : AnimationTimeline.smooth(AnimationTimeline.phase(f, 0.0F, 0.35F)) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(f, 0.74F, 0.96F)));
      float lift = 1.3333334F * open;

      for (DeathBodyRig.Part body : DeathBodyRig.Part.values()) {
         ModelPart part = DeathBodyRig.part(model, body);
         if (rig.original(body).visible()) {
            ModelTransform initial = part.getDefaultTransform();
            part.visible = true;
            part.setOrigin(initial.x(), initial.y(), initial.z());
            part.setAngles(0.0F, 0.0F, 0.0F);
            part.forEachCuboid(
               new MatrixStack(),
               (transform, path, index, cube) -> {
                  if (PlayerSkinLayers.visibleChildPath(part, path)) {
                     float centerY = 0.0F;
                     int count = 0;

                     for (Quad face : cube.sides) {
                        for (net.minecraft.client.model.ModelPart.Vertex vertex : face.vertices()) {
                           centerY += transform.getPositionMatrix().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ())).y;
                           count++;
                        }
                     }

                     boolean cubeUpper = centerY / Math.max(1, count) < 0.75F;

                     for (Quad face : cube.sides) {
                        List<NestedPlayerShell.Vertex> polygon = new ArrayList<>();

                        for (net.minecraft.client.model.ModelPart.Vertex vertex : face.vertices()) {
                           polygon.add(
                              new NestedPlayerShell.Vertex(
                                 transform.getPositionMatrix().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ())),
                                 vertex.u(),
                                 vertex.v()
                              )
                           );
                        }

                        for (boolean upper : new boolean[]{true, false}) {
                           if (!polygon.stream().allMatch(vertexx -> Math.abs(vertexx.position.y - 0.75F) < 1.0E-6F) || upper == cubeUpper) {
                              List<NestedPlayerShell.Vertex> clipped = clip(polygon, upper);
                              if (clipped.size() >= 3) {
                                 Vector3f normal = new Vector3f(clipped.get(1).position)
                                    .sub(clipped.getFirst().position)
                                    .cross(new Vector3f(clipped.get(2).position).sub(clipped.getFirst().position));
                                 if (!(normal.lengthSquared() < 1.0E-12F)) {
                                    normal.normalize();

                                    for (int triangle = 1; triangle < clipped.size() - 1; triangle++) {
                                       for (int corner : new int[]{0, triangle, triangle + 1, triangle + 1}) {
                                          NestedPlayerShell.Vertex vertex = clipped.get(corner);
                                          Vector3f point = new Vector3f(vertex.position);
                                          if (upper) {
                                             point.y -= lift;
                                          }

                                          out.vertex(poses.peek(), point)
                                             .color(color)
                                             .texture(vertex.u, vertex.v)
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

   private static List<NestedPlayerShell.Vertex> clip(List<NestedPlayerShell.Vertex> input, boolean upper) {
      List<NestedPlayerShell.Vertex> result = new ArrayList<>();

      for (int i = 0; i < input.size(); i++) {
         NestedPlayerShell.Vertex a = input.get(i);
         NestedPlayerShell.Vertex b = input.get((i + 1) % input.size());
         float da = a.position.y - 0.75F;
         float db = b.position.y - 0.75F;
         boolean insideA = upper ? da <= 0.0F : da >= 0.0F;
         boolean insideB = upper ? db <= 0.0F : db >= 0.0F;
         if (insideA) {
            result.add(a);
         }

         if (insideA != insideB) {
            result.add(a.between(b, da / (da - db)));
         }
      }

      return result;
   }

   @Environment(EnvType.CLIENT)
   private record Vertex(Vector3f position, float u, float v) {
      NestedPlayerShell.Vertex between(NestedPlayerShell.Vertex other, float t) {
         return new NestedPlayerShell.Vertex(
            new Vector3f(this.position).lerp(other.position, t), this.u + (other.u - this.u) * t, this.v + (other.v - this.v) * t
         );
      }
   }
}
