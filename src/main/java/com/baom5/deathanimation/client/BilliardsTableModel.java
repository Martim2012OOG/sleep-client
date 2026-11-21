package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Arrays;
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
final class BilliardsTableModel {
   static final float TOP = 0.95F;
   static final float HALF_LENGTH = 1.8F;
   static final float HALF_WIDTH = 0.9F;
   static final float POCKET_RADIUS = 0.18F;
   static final float BALL_RADIUS = 0.075F;
   static final float[][] POCKETS = new float[][]{{-1.82F, -0.92F}, {0.0F, -1.0F}, {1.82F, -0.92F}, {-1.82F, 0.92F}, {0.0F, 1.0F}, {1.82F, 0.92F}};
   private static final Identifier MATERIALS = Identifier.of("deathanimation:textures/entity/billiards/table_materials.png");
   private static final Identifier BALLS = Identifier.of("deathanimation:textures/entity/billiards/balls.png");
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");
   private static final int WOOD = 0;
   private static final int TRIM = 1;
   private static final int FELT = 2;
   private static final int CUSHION = 3;
   private static final int LEATHER = 4;
   private static final int BRASS = 5;
   private static final int IVORY = 6;
   private static final int NET = 7;
   private static final int METAL = 8;
   private static final int BLACK = 9;
   private static final BilliardsTableModel.Mesh TABLE = table();
   private static final List<BilliardsTableModel.SphereQuad> SPHERE = sphereMesh();

   private BilliardsTableModel() {
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT && !(scene.progress() >= 1.0F)) {
         float p = scene.progress();
         int light = scene.gui ? 15728880 : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, scene.floorY + 1.0, scene.z));
         poses.push();
         poses.translate(scene.x - relativeTo.x, (scene.gui ? scene.y : scene.floorY) - relativeTo.y, scene.z - relativeTo.z);
         poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)));
         collector.submitCustom(poses, RenderLayers.entityCutoutNoCull(MATERIALS), (pose, vertices) -> TABLE.emit(pose, vertices, light));
         BilliardsTableModel.Mesh cue = BankShot.cueMesh(p);
         if (cue != null) {
            collector.submitCustom(poses, RenderLayers.entityCutoutNoCull(MATERIALS), (pose, vertices) -> cue.emit(pose, vertices, light));
         }

         List<BilliardsTableModel.Ball> balls = BankShot.balls(p);
         collector.submitCustom(poses, RenderLayers.entityCutoutNoCull(BALLS), (pose, vertices) -> {
            for (BilliardsTableModel.Ball ball : balls) {
               emitBall(pose, vertices, ball, light);
            }
         });
         collector.submitCustom(poses, RenderLayers.entityTranslucent(WHITE), (pose, vertices) -> {
            for (BilliardsTableModel.Ball ball : balls) {
               gleam(pose, vertices, ball, light);
            }
         });
         poses.pop();
      }
   }

   private static BilliardsTableModel.Mesh table() {
      BilliardsTableModel.Mesh m = new BilliardsTableModel.Mesh();
      felt(m);

      for (int side : new int[]{-1, 1}) {
         for (float[] span : new float[][]{{-1.61F, -0.2F}, {0.2F, 1.61F}}) {
            float center = (span[0] + span[1]) * 0.5F;
            float length = span[1] - span[0];
            m.bevelBox(center, 1.055F, side * 1.065F, length, 0.15F, 0.25F, 0.025F, 0);
            m.box(center, 1.137F, side * 1.1F, length - 0.035F, 0.012F, 0.025F, 1);
            m.box(center, 0.72F, side * 1.06F, length, 0.45F, 0.19F, 0);
            m.box(center, 0.53F, side * 1.065F, length, 0.025F, 0.2F, 5);
            m.box(center, 0.62F, side * 1.165F, length - 0.09F, 0.12F, 0.013F, 1);
            m.cushion(span[0], span[1], side, false);
         }
      }

      for (int side : new int[]{-1, 1}) {
         m.bevelBox(side * 1.965F, 1.055F, 0.0F, 0.25F, 0.15F, 1.46F, 0.025F, 0);
         m.box(side * 2.0F, 1.137F, 0.0F, 0.025F, 0.012F, 1.43F, 1);
         m.box(side * 1.96F, 0.72F, 0.0F, 0.19F, 0.45F, 1.46F, 0);
         m.box(side * 1.965F, 0.53F, 0.0F, 0.2F, 0.025F, 1.46F, 5);
         m.box(side * 2.065F, 0.62F, 0.0F, 0.013F, 0.12F, 1.35F, 1);
         m.cushion(-0.73F, 0.73F, side, true);
      }

      for (int side : new int[]{-1, 1}) {
         for (float x : new float[]{-1.35F, -0.9F, -0.45F, 0.45F, 0.9F, 1.35F}) {
            m.diamond(x, 1.132F, side * 1.055F, 0.024F, 0.039F, 6);
         }

         for (float z : new float[]{-0.45F, 0.0F, 0.45F}) {
            m.diamond(side * 1.955F, 1.132F, z, 0.039F, 0.024F, 6);
         }
      }

      for (float[] pocket : POCKETS) {
         m.ring(pocket[0], pocket[1], 0.97499996F, 1.025F, 0.18F, 0.245F, 4);
         m.ring(pocket[0], pocket[1], 0.956F, 0.971F, 0.243F, 0.26200002F, 1);
         m.tube(pocket[0], pocket[1], 0.43F, 0.976F, 0.145F, 0.18F, 4, true);
         m.tube(pocket[0], pocket[1], 0.28F, 0.43F, 0.115F, 0.145F, 7, true);
         m.disk(pocket[0], 0.278F, pocket[1], 0.115F, 9);
      }

      m.bevelBox(0.0F, 0.8F, 0.0F, 3.21F, 0.15F, 1.53F, 0.04F, 0);

      for (float x : new float[]{-1.43F, 1.43F}) {
         for (float z : new float[]{-0.59F, 0.59F}) {
            m.taperedLeg(x, z, 0.055F, 0.79F, 0.12F, 0.18F, 0);
            m.bevelBox(x, 0.75F, z, 0.39F, 0.13F, 0.39F, 0.035F, 1);
            m.bevelBox(x, 0.07F, z, 0.32F, 0.14F, 0.32F, 0.035F, 9);

            for (int side : new int[]{-1, 1}) {
               Vector3f a = new Vector3f(x - 0.075F, 0.23F, z + side * 0.125F);
               Vector3f b = new Vector3f(x + 0.075F, 0.23F, z + side * 0.125F);
               Vector3f c = new Vector3f(x + 0.075F, 0.61F, z + side * 0.155F);
               Vector3f d = new Vector3f(x - 0.075F, 0.61F, z + side * 0.155F);
               if (side > 0) {
                  m.face(a, b, c, d, 1);
               } else {
                  m.face(b, a, d, c, 1);
               }
            }
         }
      }

      m.box(0.0F, 0.26F, -0.59F, 2.86F, 0.1F, 0.11F, 0);
      m.box(0.0F, 0.26F, 0.59F, 2.86F, 0.1F, 0.11F, 0);
      m.box(0.0F, 0.26F, 0.0F, 0.12F, 0.1F, 1.18F, 0);

      for (int side : new int[]{-1, 1}) {
         m.bevelBox(0.9F, 0.64F, side * 1.185F, 0.29F, 0.12F, 0.035F, 0.008F, 8);
      }

      return m;
   }

   private static void felt(BilliardsTableModel.Mesh m) {
      int rows = 120;

      for (int row = 0; row < 120; row++) {
         float z0 = -0.9F + 1.8F * row / 120.0F;
         float z1 = -0.9F + 1.8F * (row + 1) / 120.0F;
         List<float[]> spans = new ArrayList<>();
         spans.add(new float[]{-1.8F, 1.8F});

         for (float[] pocket : POCKETS) {
            float dz = Math.max(z0 - pocket[1], Math.max(pocket[1] - z1, 0.0F));
            if (!(dz >= 0.18F)) {
               float dx = (float)Math.sqrt(0.0324F - dz * dz);
               List<float[]> cut = new ArrayList<>();

               for (float[] span : spans) {
                  if (!(pocket[0] + dx <= span[0]) && !(pocket[0] - dx >= span[1])) {
                     if (pocket[0] - dx > span[0]) {
                        cut.add(new float[]{span[0], pocket[0] - dx});
                     }

                     if (pocket[0] + dx < span[1]) {
                        cut.add(new float[]{pocket[0] + dx, span[1]});
                     }
                  } else {
                     cut.add(span);
                  }
               }

               spans = cut;
            }
         }

         for (float[] span : spans) {
            m.box((span[0] + span[1]) * 0.5F, 0.91499996F, (z0 + z1) * 0.5F, span[1] - span[0], 0.07F, z1 - z0, 2);
         }
      }
   }

   private static List<BilliardsTableModel.SphereQuad> sphereMesh() {
      List<BilliardsTableModel.SphereQuad> result = new ArrayList<>();

      for (int latitude = 0; latitude < 20; latitude++) {
         for (int longitude = 0; longitude < 32; longitude++) {
            Vector3f[] normals = new Vector3f[4];
            float[] u = new float[4];
            float[] v = new float[4];

            for (int corner = 0; corner < 4; corner++) {
               u[corner] = (longitude + (corner != 1 && corner != 2 ? 0 : 1)) / 32.0F;
               v[corner] = (latitude + (corner >= 2 ? 1 : 0)) / 20.0F;
               double polar = v[corner] * 3.141592653589793;
               double azimuth = u[corner] * 3.141592653589793 * 2.0;
               normals[corner] = new Vector3f(
                  (float)(Math.sin(polar) * Math.cos(azimuth)), (float)Math.cos(polar), (float)(Math.sin(polar) * Math.sin(azimuth))
               );
            }

            result.add(new BilliardsTableModel.SphereQuad(normals, u, v));
         }
      }

      return List.copyOf(result);
   }

   private static void emitBall(Entry pose, VertexConsumer target, BilliardsTableModel.Ball ball, int light) {
      for (BilliardsTableModel.SphereQuad quad : SPHERE) {
         for (int corner = 0; corner < 4; corner++) {
            Vector3f normal = ball.rotation.transform(new Vector3f(quad.normals[corner]));
            Vector3f point = new Vector3f(normal).mul(0.075F).add(ball.position);
            target.vertex(pose, point)
               .color(-1)
               .texture((ball.number % 4 * 256 + 0.5F + quad.u[corner] * 255.0F) / 1024.0F, (ball.number / 4 * 128 + 0.5F + quad.v[corner] * 127.0F) / 512.0F)
               .overlay(655360)
               .light(light)
               .normal(pose, normal);
         }
      }
   }

   private static void gleam(Entry pose, VertexConsumer target, BilliardsTableModel.Ball ball, int light) {
      boolean aboveFelt = ball.position.y >= 1.02F;

      for (float[] pocket : POCKETS) {
         if (Math.hypot(ball.position.x - pocket[0], ball.position.z - pocket[1]) < 0.2199999988079071) {
            aboveFelt = false;
         }
      }

      if (aboveFelt) {
         for (int i = 0; i < 16; i++) {
            double a = i * 3.141592653589793 / 8.0;
            double b = (i + 1) * 3.141592653589793 / 8.0;
            Vector3f center = new Vector3f(ball.position.x, 0.951F, ball.position.z);
            Vector3f p = new Vector3f(center).add(0.075F * (float)Math.cos(a), 0.0F, 0.075F * (float)Math.sin(a));
            Vector3f q = new Vector3f(center).add(0.075F * (float)Math.cos(b), 0.0F, 0.075F * (float)Math.sin(b));

            for (Vector3f point : new Vector3f[]{center, q, p, p}) {
               target.vertex(pose, point).color(671088640).texture(0.5F, 0.5F).overlay(655360).light(light).normal(pose, 0.0F, 1.0F, 0.0F);
            }
         }
      }

      Vector3f normal = new Vector3f(-0.36F, 0.87F, -0.34F).normalize();
      Vector3f tangent = new Vector3f(normal).cross(0.0F, 0.0F, 1.0F).normalize();
      Vector3f bitangent = new Vector3f(tangent).cross(normal).normalize();

      for (int i = 0; i < 12; i++) {
         float a = i * 3.1415927F / 6.0F;
         float b = (i + 1) * 3.1415927F / 6.0F;
         Vector3f center = new Vector3f(normal).mul(0.07515F).add(ball.position);
         Vector3f p = new Vector3f(normal)
            .add(new Vector3f(tangent).mul(0.14F * (float)Math.cos(a)))
            .add(new Vector3f(bitangent).mul(0.24F * (float)Math.sin(a)))
            .normalize()
            .mul(0.075225F)
            .add(ball.position);
         Vector3f q = new Vector3f(normal)
            .add(new Vector3f(tangent).mul(0.14F * (float)Math.cos(b)))
            .add(new Vector3f(bitangent).mul(0.24F * (float)Math.sin(b)))
            .normalize()
            .mul(0.075225F)
            .add(ball.position);

         for (Vector3f point : new Vector3f[]{center, p, q, q}) {
            target.vertex(pose, point).color(1711276031).texture(0.5F, 0.5F).overlay(655360).light(light).normal(pose, normal);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   record Ball(int number, Vector3f position, Quaternionf rotation) {
   }

   @Environment(EnvType.CLIENT)
   static final class Mesh {
      private final List<BilliardsTableModel.Quad> quads = new ArrayList<>();

      private void face(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int material) {
         Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
         if (!(normal.lengthSquared() < 1.0E-12F)) {
            this.quads.add(new BilliardsTableModel.Quad(a, b, c, d, normal.normalize(), material));
         }
      }

      void box(float x, float y, float z, float w, float h, float d, int material) {
         this.loft(
            new float[][]{{x - w / 2.0F, z - d / 2.0F}, {x + w / 2.0F, z - d / 2.0F}, {x + w / 2.0F, z + d / 2.0F}, {x - w / 2.0F, z + d / 2.0F}},
            y - h / 2.0F,
            new float[]{y + h / 2.0F, y + h / 2.0F, y + h / 2.0F, y + h / 2.0F},
            material
         );
      }

      void loft(float[][] polygon, float bottom, float[] heights, int material) {
         int n = polygon.length;
         float area = 0.0F;

         for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += polygon[i][0] * polygon[j][1] - polygon[j][0] * polygon[i][1];
         }

         if (area < 0.0F) {
            float[][] reversed = new float[n][];
            float[] reversedHeights = new float[n];

            for (int i = 0; i < n; i++) {
               reversed[i] = polygon[n - 1 - i];
               reversedHeights[i] = heights[n - 1 - i];
            }

            polygon = reversed;
            heights = reversedHeights;
         }

         Vector3f[] low = new Vector3f[n];
         Vector3f[] high = new Vector3f[n];

         for (int i = 0; i < n; i++) {
            low[i] = new Vector3f(polygon[i][0], bottom, polygon[i][1]);
            high[i] = new Vector3f(polygon[i][0], heights[i], polygon[i][1]);
         }

         for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            this.face(low[i], high[i], high[j], low[j], material);
         }

         for (int i = 1; i < n - 1; i++) {
            this.face(high[0], high[i + 1], high[i], high[i], material);
            this.face(low[0], low[i], low[i + 1], low[i + 1], material);
         }
      }

      void bevelBox(float x, float y, float z, float w, float h, float d, float bevel, int material) {
         float a = w / 2.0F;
         float b = d / 2.0F;
         float c = Math.min(bevel, Math.min(a, b) * 0.45F);
         float[][] outline = new float[][]{
            {x - a + c, z - b},
            {x + a - c, z - b},
            {x + a, z - b + c},
            {x + a, z + b - c},
            {x + a - c, z + b},
            {x - a + c, z + b},
            {x - a, z + b - c},
            {x - a, z - b + c}
         };
         float[] heights = new float[8];
         Arrays.fill(heights, y + h / 2.0F);
         this.loft(outline, y - h / 2.0F, heights, material);
      }

      void taperedLeg(float x, float z, float bottom, float top, float lowRadius, float highRadius, int material) {
         Vector3f[] low = new Vector3f[8];
         Vector3f[] high = new Vector3f[8];

         for (int i = 0; i < 8; i++) {
            double angle = 0.39269908169872414 + i * 3.141592653589793 / 4.0;
            low[i] = new Vector3f(x + (float)Math.cos(angle) * lowRadius, bottom, z + (float)Math.sin(angle) * lowRadius);
            high[i] = new Vector3f(x + (float)Math.cos(angle) * highRadius, top, z + (float)Math.sin(angle) * highRadius);
         }

         for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            this.face(low[i], high[i], high[j], low[j], material);
         }
      }

      void cushion(float start, float end, int side, boolean endRail) {
         float back = endRail ? 1.88F : 0.98F;
         float nose = endRail ? 1.8F : 0.9F;
         float[][] polygon = new float[][]{{start, back}, {end, back}, {end - 0.055F, nose}, {start + 0.055F, nose}};

         for (float[] v : polygon) {
            if (endRail) {
               float along = v[0];
               v[0] = side * v[1];
               v[1] = along;
            } else {
               v[1] *= side;
            }
         }

         this.loft(polygon, 0.95F, new float[]{1.06F, 1.06F, 1.025F, 1.025F}, 3);
      }

      void diamond(float x, float y, float z, float rx, float rz, int material) {
         this.face(new Vector3f(x - rx, y, z), new Vector3f(x, y, z + rz), new Vector3f(x + rx, y, z), new Vector3f(x, y, z - rz), material);
      }

      void disk(float x, float y, float z, float r, int material) {
         for (int i = 0; i < 24; i++) {
            double a = i * 3.141592653589793 / 12.0;
            double b = (i + 1) * 3.141592653589793 / 12.0;
            Vector3f center = new Vector3f(x, y, z);
            Vector3f p = new Vector3f(x + r * (float)Math.cos(a), y, z + r * (float)Math.sin(a));
            Vector3f q = new Vector3f(x + r * (float)Math.cos(b), y, z + r * (float)Math.sin(b));
            this.face(center, q, p, p, material);
         }
      }

      void tube(float x, float z, float bottom, float top, float lower, float upper, int material, boolean inside) {
         for (int i = 0; i < 24; i++) {
            double a = i * 3.141592653589793 / 12.0;
            double b = (i + 1) * 3.141592653589793 / 12.0;
            Vector3f p = new Vector3f(x + upper * (float)Math.cos(a), top, z + upper * (float)Math.sin(a));
            Vector3f q = new Vector3f(x + upper * (float)Math.cos(b), top, z + upper * (float)Math.sin(b));
            Vector3f r = new Vector3f(x + lower * (float)Math.cos(b), bottom, z + lower * (float)Math.sin(b));
            Vector3f s = new Vector3f(x + lower * (float)Math.cos(a), bottom, z + lower * (float)Math.sin(a));
            if (inside) {
               this.face(p, s, r, q, material);
            } else {
               this.face(p, q, r, s, material);
            }
         }
      }

      void ring(float x, float z, float bottom, float top, float inner, float outer, int material) {
         this.tube(x, z, bottom, top, inner, inner, material, true);
         this.tube(x, z, bottom, top, outer, outer, material, false);

         for (int i = 0; i < 24; i++) {
            double a = i * 3.141592653589793 / 12.0;
            double b = (i + 1) * 3.141592653589793 / 12.0;
            this.face(
               new Vector3f(x + outer * (float)Math.cos(a), top, z + outer * (float)Math.sin(a)),
               new Vector3f(x + inner * (float)Math.cos(a), top, z + inner * (float)Math.sin(a)),
               new Vector3f(x + inner * (float)Math.cos(b), top, z + inner * (float)Math.sin(b)),
               new Vector3f(x + outer * (float)Math.cos(b), top, z + outer * (float)Math.sin(b)),
               material
            );
         }
      }

      void rod(Vector3f from, Vector3f to, float tipRadius, float buttRadius, int material) {
         Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(to).sub(from).normalize());
         float length = from.distance(to);

         for (int i = 0; i < 12; i++) {
            float a = i * 3.1415927F / 6.0F;
            float b = (i + 1) * 3.1415927F / 6.0F;
            Vector3f p = rotation.transform(new Vector3f(tipRadius * (float)Math.cos(a), 0.0F, tipRadius * (float)Math.sin(a))).add(from);
            Vector3f q = rotation.transform(new Vector3f(tipRadius * (float)Math.cos(b), 0.0F, tipRadius * (float)Math.sin(b))).add(from);
            Vector3f r = rotation.transform(new Vector3f(buttRadius * (float)Math.cos(b), length, buttRadius * (float)Math.sin(b))).add(from);
            Vector3f s = rotation.transform(new Vector3f(buttRadius * (float)Math.cos(a), length, buttRadius * (float)Math.sin(a))).add(from);
            this.face(p, q, r, s, material);
         }
      }

      void emit(Entry pose, VertexConsumer target, int light) {
         for (BilliardsTableModel.Quad face : this.quads) {
            float u0 = (face.material % 4 * 64 + 1) / 256.0F;
            float v0 = (face.material / 4 * 64 + 1) / 256.0F;
            Vector3f[] points = new Vector3f[]{face.a, face.b, face.c, face.d};

            for (int corner = 0; corner < 4; corner++) {
               target.vertex(pose, points[corner])
                  .color(-1)
                  .texture(u0 + (corner != 1 && corner != 2 ? 0.0F : 0.2421875F), v0 + (corner >= 2 ? 0.2421875F : 0.0F))
                  .overlay(655360)
                  .light(light)
                  .normal(pose, face.normal);
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal, int material) {
   }

   @Environment(EnvType.CLIENT)
   private record SphereQuad(Vector3f[] normals, float[] u, float[] v) {
   }
}
