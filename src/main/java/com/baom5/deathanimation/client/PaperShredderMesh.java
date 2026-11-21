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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class PaperShredderMesh {
   private static final int STRIPS = 22;
   private static final int NODES = 33;
   private static final float SEGMENT = 0.059375F;
   private static final float STEP = 0.25F;
   private static final float DT = 0.0125F;
   private static final float WIDTH = 0.05836364F;
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");
   private final PrintedPlayerPage print;
   private final PaperShredderMesh.Ribbon[] ribbons = new PaperShredderMesh.Ribbon[22];
   private final float[][] ground = new float[9][9];
   private float simulated;

   PaperShredderMesh(DeathScene scene) {
      this.print = new PrintedPlayerPage(scene);
      this.reset();
      Quaternionf rotation = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing + 180.0F));

      for (int i = 0; i < 9; i++) {
         for (int j = 0; j < 9; j++) {
            if (!scene.gui) {
               Vector3f offset = rotation.transform(new Vector3f((i - 4) * 0.5F, 0.0F, (j - 4) * 0.5F));
               double support = BodyFragment.support(scene.level, new Vec3d(scene.x + offset.x, scene.floorY, scene.z + offset.z), 2.0, scene.floorY);
               this.ground[i][j] = (float)(support - scene.floorY);
            }
         }
      }
   }

   private void reset() {
      this.simulated = 0.0F;

      for (int i = 0; i < 22; i++) {
         this.ribbons[i] = new PaperShredderMesh.Ribbon(i);
      }
   }

   void advance(DeathScene scene) {
      float target = Math.min(scene.progress(), 0.98F) * AnimationTimeline.ticks(scene.animation);
      if (target + 0.25F < this.simulated) {
         this.reset();
      }

      while (this.simulated + 0.25F <= target) {
         this.simulated += 0.25F;
         this.step(this.simulated / AnimationTimeline.ticks(scene.animation), this.simulated / 20.0F);
      }
   }

   private void step(float p, float time) {
      for (int lane = 0; lane < 22; lane++) {
         PaperShredderMesh.Ribbon ribbon = this.ribbons[lane];
         ribbon.length = PaperShredderStrip.outputLength(p - ribbon.delay);
         if (!(ribbon.length <= 0.0F)) {
            int active = Math.min(32, (int)(ribbon.length / 0.059375F));
            ribbon.released = p - ribbon.delay >= 0.8041579F;
            if (ribbon.released) {
               active = 32;
            }

            for (int i = ribbon.active + 1; i <= active; i++) {
               ribbon.position[i].set(ribbon.x, 1.239F, 0.001F);
               ribbon.previous[i].set(ribbon.x, 1.244F, 0.0F);
            }

            ribbon.active = active;

            for (int i = 0; i <= active; i++) {
               Vector3f a = ribbon.position[i];
               Vector3f old = ribbon.previous[i];
               float vx = (a.x - old.x) * 0.987F;
               float vy = (a.y - old.y) * 0.987F;
               float vz = (a.z - old.z) * 0.987F;
               old.set(a);
               float airborne = MathHelper.clamp((a.y - this.floor(a.x, a.z)) / 0.5F, 0.0F, 1.0F);
               float wind = (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.86F, 0.96F))) * airborne;
               a.add(
                  vx + (float)Math.sin(time * 3.1F + ribbon.seed + i * 0.33F) * 0.75F * 0.0125F * 0.0125F * wind,
                  vy - 0.0015312501F,
                  vz + (0.75F + 0.65F * (float)Math.sin(time * 2.2F + ribbon.seed + i * 0.44F)) * 0.0125F * 0.0125F * wind
               );
            }

            for (int iteration = 0; iteration < 7; iteration++) {
               for (int i = 0; i < active; i++) {
                  constrain(ribbon.position[i], ribbon.position[i + 1], 0.059375F, 0.5F);
               }

               for (int i = 0; i < active - 1; i++) {
                  constrain(ribbon.position[i], ribbon.position[i + 2], 0.11696875F, 0.035F);
               }

               if (!ribbon.released) {
                  anchor(ribbon);
               }

               for (int i = 0; i <= active; i++) {
                  this.collide(ribbon.position[i], ribbon.previous[i], lane);
               }
            }
         }
      }
   }

   private static void constrain(Vector3f a, Vector3f b, float rest, float weight) {
      float dx = b.x - a.x;
      float dy = b.y - a.y;
      float dz = b.z - a.z;
      float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(distance < 1.0E-7F)) {
         float correction = (distance - rest) / distance * weight;
         a.add(dx * correction, dy * correction, dz * correction);
         b.add(-dx * correction, -dy * correction, -dz * correction);
      }
   }

   private static void anchor(PaperShredderMesh.Ribbon ribbon) {
      Vector3f tail = ribbon.position[ribbon.active];
      float rest = Math.max(0.0F, ribbon.length - ribbon.active * 0.059375F);
      float dx = tail.x - ribbon.x;
      float dy = tail.y - 1.24F;
      float dz = tail.z;
      float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length < 1.0E-6F) {
         tail.set(ribbon.x, 1.24F - rest, 0.0F);
      } else {
         tail.set(ribbon.x + dx * rest / length, 1.24F + dy * rest / length, dz * rest / length);
      }
   }

   private float floor(float x, float z) {
      int ix = MathHelper.clamp(Math.round(x * 2.0F) + 4, 0, 8);
      int iz = MathHelper.clamp(Math.round(z * 2.0F) + 4, 0, 8);
      return this.ground[ix][iz];
   }

   private void collide(Vector3f point, Vector3f old, int lane) {
      float floor = this.floor(point.x, point.z) + 0.01F + lane * 6.0E-4F;
      if (point.y < floor) {
         float dx = point.x - old.x;
         float dz = point.z - old.z;
         point.y = floor;
         old.set(point.x - dx * 0.48F, point.y, point.z - dz * 0.48F);
      }

      if (point.y < 1.24F && Math.abs(point.x) < 0.84F && point.z < -0.31F && point.z > -0.46F) {
         point.z = -0.31F;
      }

      if (point.y < 1.24F && Math.abs(point.z) < 0.42F && Math.abs(point.x) > 0.72F && Math.abs(point.x) < 0.89F) {
         point.x = Math.copySign(0.72F, point.x);
      }
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP && scene.paperShredder != null) {
         PaperShredderMesh mesh = scene.paperShredder.mesh;
         mesh.print.refresh(scene);
         mesh.advance(scene);
         float p = scene.progress();
         float alpha = PaperShredderStrip.paperAlpha(p);
         if (!(alpha < 0.001F)) {
            List<PaperShredderMesh.Quad> quads = mesh.geometry(p);
            int light = scene.gui ? 15728880 : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, scene.floorY + 1.0, scene.z));
            poses.push();
            poses.translate(scene.x - relativeTo.x, (scene.gui ? scene.y : scene.floorY) - relativeTo.y, scene.z - relativeTo.z);
            poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing + 180.0F)));
            collector.submitCustom(poses, RenderLayers.entityTranslucent(WHITE), (pose, vertices) -> {
               for (PaperShredderMesh.Quad quad : quads) {
                  if (quad.material == 0) {
                     emit(quad, pose, vertices, light, alpha, false);
                     emit(quad, pose, vertices, light, alpha, true);
                  }
               }
            });
            Identifier skin = scene.avatar.getSkin().body().texturePath();
            collector.submitCustom(poses, RenderLayers.entityTranslucent(skin), (pose, vertices) -> {
               for (PaperShredderMesh.Quad quad : quads) {
                  if (quad.material != 0) {
                     emit(quad, pose, vertices, light, alpha, false);
                  }
               }
            });
            poses.pop();
         }
      }
   }

   private List<PaperShredderMesh.Quad> geometry(float p) {
      ArrayList<PaperShredderMesh.Quad> quads = new ArrayList<>();
      float top = 1.9F - PaperShredderStrip.consumed(p);
      if (top > 1.0E-4F) {
         float angle = 3.1415927F * PaperShredderStrip.turn(p);
         float cos = (float)Math.cos(angle);
         float sin = (float)Math.sin(angle);
         float cy = PaperShredderStrip.pageCenterY(p);
         PaperShredderMesh.Surface sheet = (x, y) -> new Vector3f(x * cos - (y - 0.95F) * sin, cy + x * sin + (y - 0.95F) * cos, 0.0F);
         this.addArtwork(quads, -0.675F, 0.0F, 0.675F, top, sheet);
      }

      for (PaperShredderMesh.Ribbon ribbon : this.ribbons) {
         if (ribbon.active >= 0) {
            for (int segment = 0; segment <= ribbon.active && (segment != ribbon.active || !ribbon.released); segment++) {
               float s0 = segment * 0.059375F;
               float s1 = segment == ribbon.active ? ribbon.length : (segment + 1) * 0.059375F;
               if (!(s1 - s0 < 1.0E-5F)) {
                  Vector3f a = ribbon.position[segment];
                  Vector3f b = segment == ribbon.active ? new Vector3f(ribbon.x, 1.24F, 0.0F) : ribbon.position[segment + 1];
                  Vector3f widthA = widthAt(ribbon, segment);
                  Vector3f widthB = widthAt(ribbon, segment + 1);
                  float start = s0;
                  float end = s1;
                  PaperShredderMesh.Surface strip = (x, y) -> {
                     float t = MathHelper.clamp((1.9F - y - start) / (end - start), 0.0F, 1.0F);
                     Vector3f point = new Vector3f(a).lerp(b, t).fma(-(x - ribbon.sourceX), new Vector3f(widthA).lerp(widthB, t));
                     point.y = Math.max(point.y, this.floor(point.x, point.z) + 0.008F + ribbon.delay * 2.0F);
                     return point;
                  };
                  this.addArtwork(quads, ribbon.sourceX - 0.02918182F, 1.9F - s1, ribbon.sourceX + 0.02918182F, 1.9F - s0, strip);
               }
            }
         }
      }

      return quads;
   }

   private static Vector3f widthAt(PaperShredderMesh.Ribbon ribbon, int node) {
      if (node > ribbon.active) {
         return new Vector3f(1.0F, 0.0F, 0.0F);
      }

      Vector3f previous = ribbon.position[Math.max(0, node - 1)];
      Vector3f next = node < ribbon.active ? ribbon.position[node + 1] : (ribbon.released ? ribbon.position[node] : new Vector3f(ribbon.x, 1.24F, 0.0F));
      Vector3f tangent = new Vector3f(previous).sub(next);
      if (tangent.lengthSquared() < 1.0E-8F) {
         tangent.set(0.0F, -1.0F, 0.0F);
      } else {
         tangent.normalize();
      }

      float curl = 0.32F * (float)Math.sin(node * 0.43F + ribbon.seed);
      Vector3f width = new Vector3f((float)Math.cos(curl), 0.0F, (float)Math.sin(curl));
      width.fma(-width.dot(tangent), tangent);
      if (width.lengthSquared() < 0.001F) {
         width.set(0.0F, 0.0F, 1.0F).fma(-tangent.z, tangent);
      }

      return width.normalize();
   }

   private void addArtwork(List<PaperShredderMesh.Quad> quads, float left, float bottom, float right, float top, PaperShredderMesh.Surface surface) {
      for (PrintedPlayerPage.Patch patch : this.print.patches) {
         float x0 = Math.max(left, patch.left());
         float y0 = Math.max(bottom, patch.bottom());
         float x1 = Math.min(right, patch.right());
         float y1 = Math.min(top, patch.top());
         if (!(x1 <= x0) && !(y1 <= y0)) {
            float u0 = MathHelper.lerp((x0 - patch.left()) / (patch.right() - patch.left()), patch.u0(), patch.u1());
            float u1 = MathHelper.lerp((x1 - patch.left()) / (patch.right() - patch.left()), patch.u0(), patch.u1());
            float v0 = MathHelper.lerp((patch.top() - y0) / (patch.top() - patch.bottom()), patch.v0(), patch.v1());
            float v1 = MathHelper.lerp((patch.top() - y1) / (patch.top() - patch.bottom()), patch.v0(), patch.v1());
            quads.add(
               new PaperShredderMesh.Quad(
                  new Vector3f[]{surface.point(x0, y0), surface.point(x1, y0), surface.point(x1, y1), surface.point(x0, y1)},
                  new float[]{u0, u1, u1, u0},
                  new float[]{v0, v0, v1, v1},
                  patch.material()
               )
            );
         }
      }
   }

   private static void emit(PaperShredderMesh.Quad quad, Entry pose, VertexConsumer consumer, int light, float alpha, boolean back) {
      Vector3f[] points = quad.position;
      Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[3]).sub(points[0]));
      if (!(normal.lengthSquared() < 1.0E-12F)) {
         normal.normalize();
         if (back) {
            normal.negate();
         }

         int rgb = quad.material == 0 ? (back ? 15132127 : 16776695) : 16777215;
         int color = Math.round(alpha * 255.0F) << 24 | rgb;
         float separation = 4.0E-4F + quad.material * 3.0E-4F;

         for (int i = 0; i < 4; i++) {
            int index = back ? 3 - i : i;
            consumer.vertex(pose, new Vector3f(points[index]).fma(separation, normal))
               .color(color)
               .texture(quad.u[index], quad.v[index])
               .overlay(655360)
               .light(light)
               .normal(pose, normal);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Quad(Vector3f[] position, float[] u, float[] v, int material) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Ribbon {
      final Vector3f[] position = new Vector3f[33];
      final Vector3f[] previous = new Vector3f[33];
      final float sourceX;
      final float x;
      final float delay;
      final float seed;
      int active = -1;
      float length;
      boolean released;

      Ribbon(int lane) {
         this.sourceX = -0.675F + (lane + 0.5F) * 1.35F / 22.0F;
         this.x = -this.sourceX;
         this.delay = lane * 3.0E-4F;
         this.seed = lane * 1.719F;

         for (int j = 0; j < 33; j++) {
            this.position[j] = new Vector3f(this.x, 1.24F, 0.0F);
            this.previous[j] = new Vector3f(this.position[j]);
         }
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   private interface Surface {
      Vector3f point(float var1, float var2);
   }
}
