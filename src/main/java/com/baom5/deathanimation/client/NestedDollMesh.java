package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class NestedDollMesh {
   private static final Identifier PAINT = Identifier.of("deathanimation:textures/entity/nested_doll.png");
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");
   private static final float WALL = 0.006F;
   private static final float DEPTH = 0.82F;
   private static final int RINGS = 48;
   private static final int SEGMENTS = 32;
   private static final float[][] PROFILE = new float[][]{
      {0.0F, 0.2F},
      {0.1F, 0.285F},
      {0.27F, 0.34F},
      {0.42F, 0.325F},
      {0.53F, 0.28F},
      {0.63F, 0.205F},
      {0.78F, 0.2F},
      {0.9F, 0.17F},
      {0.98F, 0.09F},
      {1.0F, 0.025F}
   };
   private static final NestedDollMesh.Half LEFT = build(-1);
   private static final NestedDollMesh.Half RIGHT = build(1);

   private NestedDollMesh() {
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      if (scene.nestedDoll != null) {
         NestedDollClose state = scene.nestedDoll;
         int light = scene.gui ? 15728880 : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, scene.floorY + 1.0, scene.z));

         for (NestedDollClose.Shell shell : state.visibleShells()) {
            NestedDollMesh.Half mesh = shell.side() < 0 ? LEFT : RIGHT;
            poses.push();
            poses.translate(scene.x - relativeTo.x, state.shellBaseY() - relativeTo.y, scene.z - relativeTo.z);
            poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)));
            poses.translate(state.shellX(shell), 0.0F, 0.0F);
            float scale = state.shellScale(shell);
            poses.scale(scale, scale, scale);
            collector.submitCustom(poses, RenderLayers.entityCutout(PAINT), (pose, vertices) -> emit(mesh.painted, pose, vertices, light, -1));
            collector.submitCustom(poses, RenderLayers.entityCutout(WHITE), (pose, vertices) -> emit(mesh.wood, pose, vertices, light, -4552359));
            poses.pop();
         }
      }
   }

   private static NestedDollMesh.Half build(int side) {
      List<NestedDollMesh.Face> outside = new ArrayList<>();
      List<NestedDollMesh.Face> inside = new ArrayList<>();
      NestedDollMesh.Vertex[][] outer = new NestedDollMesh.Vertex[49][33];
      NestedDollMesh.Vertex[][] inner = new NestedDollMesh.Vertex[49][33];

      for (int row = 0; row <= 48; row++) {
         for (int col = 0; col <= 32; col++) {
            float y = row / 48.0F;
            double theta = (side < 0 ? -3.141592653589793 : 0.0) + 3.141592653589793 * col / 32.0;
            outer[row][col] = point(y, radius(y), theta);
            inner[row][col] = point(0.006F + 0.988F * y, radius(y) - 0.006F, theta);
         }
      }

      for (int row = 0; row < 48; row++) {
         for (int col = 0; col < 32; col++) {
            face(outside, outer[row][col], outer[row + 1][col], outer[row + 1][col + 1], outer[row][col + 1]);
            face(inside, inner[row][col + 1], inner[row + 1][col + 1], inner[row + 1][col], inner[row][col]);
         }
      }

      for (int row = 0; row < 48; row++) {
         for (int col : new int[]{0, 32}) {
            facing(inside, new Vector3f(-side, 0.0F, 0.0F), outer[row][col], inner[row][col], inner[row + 1][col], outer[row + 1][col]);
         }
      }

      for (int col = 0; col < 32; col++) {
         facing(outside, new Vector3f(0.0F, -1.0F, 0.0F), outer[0][col + 1], outer[0][col], center(0.0F), center(0.0F));
         facing(inside, new Vector3f(0.0F, 1.0F, 0.0F), inner[0][col + 1], inner[0][col], center(0.006F), center(0.006F));
         facing(outside, new Vector3f(0.0F, 1.0F, 0.0F), outer[48][col], outer[48][col + 1], center(1.0F), center(1.0F));
         facing(inside, new Vector3f(0.0F, -1.0F, 0.0F), inner[48][col], inner[48][col + 1], center(0.994F), center(0.994F));
      }

      for (int row : new int[]{0, 48}) {
         facing(inside, new Vector3f(-side, 0.0F, 0.0F), outer[row][0], outer[row][32], inner[row][32], inner[row][0]);
      }

      return new NestedDollMesh.Half(List.copyOf(outside), List.copyOf(inside));
   }

   private static NestedDollMesh.Vertex center(float y) {
      return new NestedDollMesh.Vertex(new Vector3f(0.0F, y, 0.0F), 0.5F, 1.0F - y);
   }

   private static NestedDollMesh.Vertex point(float y, float radius, double theta) {
      float x = Math.abs(Math.sin(theta)) < 1.0E-7 ? 0.0F : (float)Math.sin(theta) * radius;
      return new NestedDollMesh.Vertex(
         new Vector3f(x, y, -((float)Math.cos(theta)) * radius * 0.82F), (float)((theta + 3.141592653589793) / 6.283185307179586), 1.0F - y
      );
   }

   private static float radius(float y) {
      for (int i = 1; i < PROFILE.length; i++) {
         if (y <= PROFILE[i][0]) {
            float[] a = PROFILE[i - 1];
            float[] b = PROFILE[i];
            return a[1] + (b[1] - a[1]) * (y - a[0]) / (b[0] - a[0]);
         }
      }

      return PROFILE[PROFILE.length - 1][1];
   }

   private static Vector3f normal(NestedDollMesh.Vertex a, NestedDollMesh.Vertex b, NestedDollMesh.Vertex c) {
      return new Vector3f(b.position).sub(a.position).cross(new Vector3f(c.position).sub(a.position)).normalize();
   }

   private static void face(List<NestedDollMesh.Face> faces, NestedDollMesh.Vertex a, NestedDollMesh.Vertex b, NestedDollMesh.Vertex c, NestedDollMesh.Vertex d) {
      faces.add(new NestedDollMesh.Face(new NestedDollMesh.Vertex[]{a, b, c, d}, normal(a, b, c)));
   }

   private static void facing(
      List<NestedDollMesh.Face> faces, Vector3f direction, NestedDollMesh.Vertex a, NestedDollMesh.Vertex b, NestedDollMesh.Vertex c, NestedDollMesh.Vertex d
   ) {
      if (normal(a, b, c).dot(direction) < 0.0F) {
         face(faces, c, b, a, d);
      } else {
         face(faces, a, b, c, d);
      }
   }

   private static void emit(List<NestedDollMesh.Face> faces, Entry pose, VertexConsumer vertices, int light, int color) {
      for (NestedDollMesh.Face face : faces) {
         for (NestedDollMesh.Vertex vertex : face.vertices) {
            vertices.vertex(pose, vertex.position.x, vertex.position.y, vertex.position.z)
               .color(color)
               .texture(vertex.u, vertex.v)
               .overlay(655360)
               .light(light)
               .normal(pose, face.normal);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Face(NestedDollMesh.Vertex[] vertices, Vector3f normal) {
   }

   @Environment(EnvType.CLIENT)
   private record Half(List<NestedDollMesh.Face> painted, List<NestedDollMesh.Face> wood) {
   }

   @Environment(EnvType.CLIENT)
   private record Vertex(Vector3f position, float u, float v) {
   }
}
