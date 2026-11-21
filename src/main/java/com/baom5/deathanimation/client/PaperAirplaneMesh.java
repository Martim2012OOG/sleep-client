package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class PaperAirplaneMesh {
   static final float WIDTH = 1.35F;
   static final float HEIGHT = 1.9F;
   private static final float HALF = 0.675F;
   private static final int PAPER = 0;
   private static final int SKIN = 1;
   private static final int OVERLAY = 2;
   private static final int CREASE = 3;
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");
   private static final float SIN_22 = (float)Math.sin(0.39269908169872414);
   private static final float COS_22 = (float)Math.cos(0.39269908169872414);
   private static final PaperAirplaneMesh.Fold[] FOLDS = new PaperAirplaneMesh.Fold[]{
      new PaperAirplaneMesh.Fold(0.31F, 0.37F, 0.0F, 1.9F, -0.675F, 1.2249999F, 1, -1.0F, 0),
      new PaperAirplaneMesh.Fold(0.39F, 0.45F, 0.0F, 1.9F, 0.675F, 1.2249999F, -1, 1.0F, 0),
      new PaperAirplaneMesh.Fold(0.47F, 0.53F, 0.0F, 1.9F, -SIN_22, 1.9F - COS_22, 1, -1.0F, 0),
      new PaperAirplaneMesh.Fold(0.55F, 0.61F, 0.0F, 1.9F, SIN_22, 1.9F - COS_22, -1, 1.0F, 0),
      new PaperAirplaneMesh.Fold(0.64F, 0.7F, 0.0F, 0.0F, 0.0F, 1.9F, 1, -1.0F, 0),
      new PaperAirplaneMesh.Fold(0.72F, 0.765F, 0.0F, 1.9F, -0.12F, 0.0F, 1, -0.5F, -1),
      new PaperAirplaneMesh.Fold(0.78F, 0.825F, 0.0F, 1.9F, -0.12F, 0.0F, 1, 0.5F, 1)
   };
   private static final Quaternionf FLIGHT_FRAME = flightFrame();
   private static final Quaternionf VERTICAL_FRAME = new Quaternionf().rotationX(-1.5707964F).mul(FLIGHT_FRAME);

   private PaperAirplaneMesh() {
   }

   private static Quaternionf flightFrame() {
      List<PaperAirplaneMesh.Panel> panels = new ArrayList<>();
      rectangle(panels, -0.675F, 0.0F, 0.675F, 1.9F, 0.0F, 0.0F, 1.0F, 1.0F, 0);

      for (PaperAirplaneMesh.Fold operation : FOLDS) {
         panels = fold(panels, operation, operation.turns * 3.1415927F);
      }

      Vector3f nose = marker(panels, 0.5F, 0.0F);
      Vector3f left = marker(panels, 0.0F, 1.0F);
      Vector3f right = marker(panels, 1.0F, 1.0F);
      Vector3f forward = new Vector3f(nose).sub(new Vector3f(left).add(right).mul(0.5F)).normalize();
      Vector3f span = new Vector3f(right).sub(left);
      span.sub(new Vector3f(forward).mul(span.dot(forward))).normalize();
      Vector3f up = new Vector3f(forward).cross(span).normalize();
      return new Quaternionf().setFromNormalized(new Matrix3f(span, up, forward).transpose()).normalize();
   }

   private static Vector3f marker(List<PaperAirplaneMesh.Panel> panels, float u, float v) {
      Vector3f point = new Vector3f();
      int count = 0;

      for (PaperAirplaneMesh.Panel panel : panels) {
         for (PaperAirplaneMesh.Vertex vertex : panel.vertices) {
            if (Math.abs(vertex.u - u) < 1.0E-5F && Math.abs(vertex.v - v) < 1.0E-5F) {
               point.add(vertex.position);
               count++;
            }
         }
      }

      if (count == 0) {
         throw new IllegalStateException("Missing paper airplane geometry marker");
      } else {
         return point.div(count);
      }
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD && scene.paperAirplane != null) {
         float p = scene.progress();
         float alpha = PaperAirplaneFold.paperAlpha(p);
         if (!(alpha < 0.001F)) {
            List<PaperAirplaneMesh.Panel> panels = scene.paperAirplane.print.pose(scene, p);
            DeathScene.Root root = PaperAirplaneFold.sheetRoot(scene);
            int light = scene.gui ? 15728880 : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, scene.floorY + 1.0, scene.z));
            poses.push();
            poses.translate(scene.x + root.x() - relativeTo.x, scene.y + root.y() - relativeTo.y, scene.z + root.z() - relativeTo.z);
            poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing + 180.0F)));
            poses.scale(root.sx(), root.sy(), root.sz());
            poses.translate(0.0F, 0.95F, 0.0F);
            poses.multiply(orientation(p));
            poses.translate(0.0F, -0.95F, 0.0F);
            boolean opaqueSheet = alpha >= 0.999F;
            RenderLayer paperType = opaqueSheet ? RenderLayers.entityCutout(WHITE) : RenderLayers.itemEntityTranslucentCull(WHITE);
            collector.submitCustom(
               poses,
               paperType,
               (pose, vertices) -> {
                  for (PaperAirplaneMesh.Panel panel : panels) {
                     if (panel.material == 0) {
                        emit(panel, pose, vertices, light, alpha, -132108, false, 0.0F);
                        emit(panel, pose, vertices, light, alpha, -1250590, true, 0.0F);
                     } else if (panel.material == 3 && p > 0.285F) {
                        emit(
                           panel,
                           pose,
                           vertices,
                           light,
                           alpha * 0.25F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.285F, 0.31F)),
                           -7499634,
                           false,
                           1.0E-5F
                        );
                     }
                  }
               }
            );
            Identifier skin = scene.avatar.getSkin().body().texturePath();
            RenderLayer printType = opaqueSheet ? RenderLayers.entityCutout(skin) : RenderLayers.itemEntityTranslucentCull(skin);
            collector.submitCustom(poses, printType, (pose, vertices) -> {
               for (PaperAirplaneMesh.Panel panel : panels) {
                  if (panel.material == 1 || panel.material == 2) {
                     emit(panel, pose, vertices, light, alpha, -1, false, panel.material == 1 ? 2.0E-5F : 4.0E-5F);
                  }
               }
            });
            poses.pop();
         }
      }
   }

   private static Quaternionf orientation(float p) {
      Quaternionf vertical = new Quaternionf().slerp(VERTICAL_FRAME, AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.825F, 0.84F)));
      float pitch = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.84F, 0.87F)) * 3.1415927F / 2.0F;
      return new Quaternionf().rotationX(pitch).mul(vertical);
   }

   private static void rectangle(
      List<PaperAirplaneMesh.Panel> panels, float left, float bottom, float right, float top, float u0, float v0, float u1, float v1, int material
   ) {
      float divisor = material != 1 && material != 2 ? 1.0F : 64.0F;
      List<PaperAirplaneMesh.Vertex> vertices = List.of(
         new PaperAirplaneMesh.Vertex(new Vector3f(left, bottom, 0.0F), u0 / divisor, v1 / divisor),
         new PaperAirplaneMesh.Vertex(new Vector3f(right, bottom, 0.0F), u1 / divisor, v1 / divisor),
         new PaperAirplaneMesh.Vertex(new Vector3f(right, top, 0.0F), u1 / divisor, v0 / divisor),
         new PaperAirplaneMesh.Vertex(new Vector3f(left, top, 0.0F), u0 / divisor, v0 / divisor)
      );
      PaperAirplaneMesh.Fold center = new PaperAirplaneMesh.Fold(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.9F, 1, 1.0F, 0);
      List<PaperAirplaneMesh.Vertex> leftHalf = clip(vertices, center, false);
      List<PaperAirplaneMesh.Vertex> rightHalf = clip(vertices, center, true);
      if (hasArea(leftHalf)) {
         panels.add(new PaperAirplaneMesh.Panel(leftHalf, material, -1, 0));
      }

      if (hasArea(rightHalf)) {
         panels.add(new PaperAirplaneMesh.Panel(rightHalf, material, 1, 0));
      }
   }

   private static List<PaperAirplaneMesh.Panel> fold(List<PaperAirplaneMesh.Panel> source, PaperAirplaneMesh.Fold fold, float angle) {
      if (Math.abs(angle) < 1.0E-6F) {
         return source;
      }

      List<PaperAirplaneMesh.Panel> result = new ArrayList<>(source.size() * 2);
      Vector3f axis = new Vector3f(fold.bx - fold.ax, fold.by - fold.ay, 0.0F).normalize();
      Quaternionf rotation = new Quaternionf().rotationAxis(angle, axis);
      Vector3f origin = new Vector3f(fold.ax, fold.ay, 0.0F);
      int packetLayers = source.stream()
         .filter(panelx -> fold.half == 0 || panelx.originalHalf == fold.half)
         .mapToInt(PaperAirplaneMesh.Panel::layers)
         .max()
         .orElse(0);
      float gap = Math.scalb(5.0E-4F, packetLayers) * 0.5F * (1.0F - (float)Math.cos(angle)) * (-fold.side * Math.signum(angle));

      for (PaperAirplaneMesh.Panel panel : source) {
         if (fold.half != 0 && panel.originalHalf != fold.half) {
            result.add(panel);
         } else {
            List<PaperAirplaneMesh.Vertex> fixed = clip(panel.vertices, fold, false);
            List<PaperAirplaneMesh.Vertex> moving = clip(panel.vertices, fold, true);
            if (hasArea(fixed)) {
               result.add(new PaperAirplaneMesh.Panel(fixed, panel.material, panel.originalHalf, panel.layers));
            }

            if (hasArea(moving)) {
               List<PaperAirplaneMesh.Vertex> rotated = new ArrayList<>(moving.size());

               for (PaperAirplaneMesh.Vertex vertex : moving) {
                  rotated.add(
                     new PaperAirplaneMesh.Vertex(
                        rotation.transform(new Vector3f(vertex.position).sub(origin)).add(origin).add(0.0F, 0.0F, gap), vertex.u, vertex.v
                     )
                  );
               }

               result.add(new PaperAirplaneMesh.Panel(rotated, panel.material, panel.originalHalf, panel.layers + 1));
            }
         }
      }

      return result;
   }

   private static boolean hasArea(List<PaperAirplaneMesh.Vertex> vertices) {
      if (vertices.size() < 3) {
         return false;
      }

      Vector3f origin = vertices.getFirst().position;

      for (int i = 1; i < vertices.size() - 1; i++) {
         if (new Vector3f(vertices.get(i).position).sub(origin).cross(new Vector3f(vertices.get(i + 1).position).sub(origin)).lengthSquared() > 1.0E-12F) {
            return true;
         }
      }

      return false;
   }

   private static List<PaperAirplaneMesh.Vertex> clip(List<PaperAirplaneMesh.Vertex> polygon, PaperAirplaneMesh.Fold fold, boolean moving) {
      List<PaperAirplaneMesh.Vertex> output = new ArrayList<>();

      for (int i = 0; i < polygon.size(); i++) {
         PaperAirplaneMesh.Vertex a = polygon.get(i);
         PaperAirplaneMesh.Vertex b = polygon.get((i + 1) % polygon.size());
         float da = fold.distance(a);
         float db = fold.distance(b);
         boolean inA = moving ? da >= -1.0E-6F : da <= 1.0E-6F;
         boolean inB = moving ? db >= -1.0E-6F : db <= 1.0E-6F;
         if (inA) {
            output.add(a);
         }

         if (inA != inB && Math.abs(da - db) > 1.0E-9F) {
            output.add(a.between(b, Math.clamp(da / (da - db), 0.0F, 1.0F)));
         }
      }

      return output;
   }

   private static void emit(
      PaperAirplaneMesh.Panel panel, Entry pose, VertexConsumer consumer, int light, float alpha, int color, boolean back, float paintOffset
   ) {
      if (!(alpha < 0.001F)) {
         List<PaperAirplaneMesh.Vertex> vertices = panel.vertices;
         Vector3f normal = new Vector3f();

         for (int i = 0; i < vertices.size(); i++) {
            Vector3f a = vertices.get(i).position;
            Vector3f b = vertices.get((i + 1) % vertices.size()).position;
            normal.add((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y));
         }

         if (!(normal.lengthSquared() < 1.0E-12F)) {
            normal.normalize();
            if (back) {
               normal.negate();
            }

            float separation = 5.0E-5F + paintOffset;
            int tint = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | color & 16777215;

            for (int i = 1; i < vertices.size() - 1; i++) {
               PaperAirplaneMesh.Vertex a = vertices.getFirst();
               PaperAirplaneMesh.Vertex b = vertices.get(back ? i + 1 : i);
               PaperAirplaneMesh.Vertex c = vertices.get(back ? i : i + 1);

               for (PaperAirplaneMesh.Vertex vertex : new PaperAirplaneMesh.Vertex[]{a, b, c, c}) {
                  Vector3f position = new Vector3f(vertex.position).fma(separation, normal);
                  consumer.vertex(pose, position).color(tint).texture(vertex.u, vertex.v).overlay(655360).light(light).normal(pose, normal);
               }
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Fold(float start, float end, float ax, float ay, float bx, float by, int side, float turns, int half) {
      float distance(PaperAirplaneMesh.Vertex vertex) {
         return this.side * ((this.by - this.ay) * (vertex.position.x - this.ax) - (this.bx - this.ax) * (vertex.position.y - this.ay));
      }
   }

   @Environment(EnvType.CLIENT)
   private record Panel(List<PaperAirplaneMesh.Vertex> vertices, int material, int originalHalf, int layers) {
   }

   @Environment(EnvType.CLIENT)
   static final class Print {
      private final PrintedPlayerPage page;
      private List<PaperAirplaneMesh.Panel> original;
      private float lastPhase = 0.0F / 0.0F;
      private List<PaperAirplaneMesh.Panel> lastPose = List.of();

      Print(DeathScene scene) {
         this.page = new PrintedPlayerPage(scene);
         this.rebuild();
      }

      private void rebuild() {
         List<PaperAirplaneMesh.Panel> panels = new ArrayList<>();

         for (PrintedPlayerPage.Patch patch : this.page.patches) {
            float divisor = patch.material() == 0 ? 1.0F : 64.0F;
            PaperAirplaneMesh.rectangle(
               panels,
               patch.left(),
               patch.bottom(),
               patch.right(),
               patch.top(),
               patch.u0() * divisor,
               patch.v0() * divisor,
               patch.u1() * divisor,
               patch.v1() * divisor,
               patch.material()
            );
         }

         PaperAirplaneMesh.rectangle(panels, -0.0025F, 0.0F, 0.0025F, 1.9F, 0.0F, 0.0F, 1.0F, 1.0F, 3);
         this.original = List.copyOf(panels);
         this.lastPhase = 0.0F / 0.0F;
      }

      List<PaperAirplaneMesh.Panel> pose(DeathScene scene, float p) {
         if (this.page.refresh(scene)) {
            this.rebuild();
         }

         if (p == this.lastPhase) {
            return this.lastPose;
         }

         List<PaperAirplaneMesh.Panel> panels = this.original;
         float crease = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.19F, 0.235F))
            * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.25F, 0.285F)));
         if (crease > 1.0E-5F) {
            panels = PaperAirplaneMesh.fold(panels, new PaperAirplaneMesh.Fold(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.9F, 1, -1.0F, 0), -crease * 3.1415927F);
         }

         for (PaperAirplaneMesh.Fold operation : PaperAirplaneMesh.FOLDS) {
            if (p <= operation.start) {
               break;
            }

            panels = PaperAirplaneMesh.fold(
               panels, operation, operation.turns * 3.1415927F * AnimationTimeline.smooth(AnimationTimeline.phase(p, operation.start, operation.end))
            );
         }

         this.lastPhase = p;
         this.lastPose = List.copyOf(panels);
         return this.lastPose;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Vertex(Vector3f position, float u, float v) {
      PaperAirplaneMesh.Vertex between(PaperAirplaneMesh.Vertex other, float t) {
         return new PaperAirplaneMesh.Vertex(
            new Vector3f(this.position).lerp(other.position, t), this.u + (other.u - this.u) * t, this.v + (other.v - this.v) * t
         );
      }
   }
}
