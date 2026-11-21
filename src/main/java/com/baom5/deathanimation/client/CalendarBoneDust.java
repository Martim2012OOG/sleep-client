package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class CalendarBoneDust {
   private static final float START = 0.86F;
   private static final float WIND = 0.945F;
   private static final float DT = 0.008333334F;
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");
   private static final int[][] FACES = new int[][]{{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
   private static final float[][] NORMALS = new float[][]{
      {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, 0.0F, -1.0F}, {0.0F, 0.0F, 1.0F}
   };
   private final List<CalendarBoneDust.Grain> grains = new ArrayList<>();
   private final float[][] ground = new float[13][13];
   private final float duration;
   private float simulated;

   CalendarBoneDust(DeathScene scene) {
      this.duration = AnimationTimeline.ticks(scene.animation) / 20.0F;
      Random random = new Random(scene.seed ^ 2967392343L);
      this.box(random, 0.0F, 1.74F, 0.0F, 0.5F, 0.5F, 0.5F, true);
      this.box(random, 0.0F, 1.18F, 0.0F, 0.1F, 0.54F, 0.12F, false);
      this.box(random, 0.0F, 1.43F, 0.0F, 0.48F, 0.08F, 0.15F, false);

      for (int i = 0; i < 3; i++) {
         this.box(random, 0.0F, 1.05F + i * 0.12F, -0.055F, 0.46F, 0.055F, 0.15F, false);
      }

      for (int side : new int[]{-1, 1}) {
         this.box(random, side * 0.31F, 1.1F, 0.0F, 0.12F, 0.72F, 0.12F, false);
         this.box(random, side * 0.12F, 0.38F, 0.0F, 0.12F, 0.75F, 0.12F, false);
      }

      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));

      for (int x = 0; x < 13; x++) {
         for (int z = 0; z < 13; z++) {
            Vector3f point = facing.transform(new Vector3f((x - 6) * 0.5F, 0.0F, (z - 6) * 0.5F));
            this.ground[x][z] = scene.gui
               ? 0.0F
               : (float)(BodyFragment.support(scene.level, new Vec3d(scene.x + point.x, scene.floorY, scene.z + point.z), 3.0, scene.floorY) - scene.floorY);
         }
      }
   }

   private void box(Random random, float cx, float cy, float cz, float sx, float sy, float sz, boolean hollow) {
      int nx = Math.max(1, Math.round(sx / 0.07F));
      int ny = Math.max(1, Math.round(sy / 0.07F));
      int nz = Math.max(1, Math.round(sz / 0.07F));

      for (int x = 0; x < nx; x++) {
         for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
               if (!hollow || x <= 0 || x >= nx - 1 || y <= 0 || y >= ny - 1 || z <= 0 || z >= nz - 1) {
                  if (this.grains.size() >= 768) {
                     return;
                  }

                  this.grains
                     .add(
                        new CalendarBoneDust.Grain(
                           new Vector3f(cx + ((x + 0.5F) / nx - 0.5F) * sx, cy + ((y + 0.5F) / ny - 0.5F) * sy, cz + ((z + 0.5F) / nz - 0.5F) * sz), random
                        )
                     );
               }
            }
         }
      }
   }

   private float floor(float x, float z) {
      int ix = Math.clamp(Math.round(x * 2.0F) + 6, 0, 12);
      int iz = Math.clamp(Math.round(z * 2.0F) + 6, 0, 12);
      return this.ground[ix][iz];
   }

   private void advance(float progress) {
      float target = Math.max(0.0F, Math.min(1.0F, progress) - 0.86F) * this.duration;
      if (target + 0.008333334F < this.simulated) {
         this.simulated = 0.0F;

         for (CalendarBoneDust.Grain grain : this.grains) {
            grain.position.set(grain.origin);
            grain.velocity.zero();
            grain.landed = -1.0F;
         }
      }

      while (this.simulated + 0.008333334F <= target) {
         this.simulated += 0.008333334F;
         float p = 0.86F + this.simulated / this.duration;

         for (CalendarBoneDust.Grain grain : this.grains) {
            if (!(p < grain.release)) {
               Vector3f point = grain.position;
               Vector3f v = grain.velocity;
               float gust = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.945F, 0.97F));
               boolean wind = grain.landed >= 0.0F && this.simulated - grain.landed > 0.18F;
               if (wind) {
                  v.x = v.x + (7.5F + (float)Math.sin(grain.seed) * 1.4F) * gust * 0.008333334F;
                  v.z = v.z + (1.3F + (float)Math.cos(grain.seed) * 0.8F) * gust * 0.008333334F;
               }

               v.y = v.y + (-9.8F + (wind ? gust * (11.8F + 1.2F * (float)Math.sin(grain.seed + this.simulated * 5.0F)) : 0.0F)) * 0.008333334F;
               float oldX = point.x;
               float oldZ = point.z;
               point.add(v.x * 0.008333334F, v.y * 0.008333334F, v.z * 0.008333334F);
               float surface = this.floor(point.x, point.z) + grain.size * 0.5F;
               if (surface > point.y + 0.12F) {
                  point.x = oldX;
                  point.z = oldZ;
                  v.x *= 0.3F;
                  v.z *= 0.3F;
                  surface = this.floor(oldX, oldZ) + grain.size * 0.5F;
               }

               if (point.y <= surface) {
                  point.y = surface;
                  if (grain.landed < 0.0F) {
                     grain.landed = this.simulated;
                  }

                  v.y = Math.max(0.0F, -v.y * 0.12F);
                  float friction = wind && gust > 0.0F ? 0.985F : 0.8F;
                  v.x *= friction;
                  v.z *= friction;
               }
            }
         }
      }
   }

   static void submit(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      CalendarBoneDust dust = scene.calendarDust;
      if (dust != null && !(scene.progress() < 0.86F)) {
         float p = scene.progress();
         dust.advance(p);
         int light = scene.gui ? 15728880 : WorldRenderer.getLightmapCoordinates(scene.level, BlockPos.ofFloored(scene.x, scene.floorY + 1.0, scene.z));
         poses.push();
         poses.translate(scene.x - relativeTo.x, (scene.gui ? scene.y : scene.floorY) - relativeTo.y, scene.z - relativeTo.z);
         poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)));
         collector.submitCustom(
            poses,
            RenderLayers.itemEntityTranslucentCull(WHITE),
            (pose, vertices) -> {
               for (CalendarBoneDust.Grain grain : dust.grains) {
                  if (!(p < grain.release)) {
                     float fade = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.972F, 1.0F));
                     float alpha = AnimationTimeline.smooth(AnimationTimeline.phase(p, grain.release, grain.release + 0.006F)) * fade;
                     int color = Math.round(255.0F * alpha) << 24 | grain.color;
                     float radius = grain.size * 0.5F;

                     for (int f = 0; f < FACES.length; f++) {
                        for (int corner = 0; corner < 4; corner++) {
                           int bits = FACES[f][corner];
                           vertices.vertex(
                                 pose,
                                 grain.position.x + ((bits & 1) == 0 ? -radius : radius),
                                 grain.position.y + ((bits & 2) == 0 ? -radius : radius),
                                 grain.position.z + ((bits & 4) == 0 ? -radius : radius)
                              )
                              .color(color)
                              .texture(corner != 0 && corner != 3 ? 1.0F : 0.0F, corner < 2 ? 0.0F : 1.0F)
                              .overlay(655360)
                              .light(light)
                              .normal(pose, NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);
                        }
                     }
                  }
               }
            }
         );
         poses.pop();
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Grain {
      final Vector3f origin;
      final Vector3f position;
      final Vector3f velocity = new Vector3f();
      final float release;
      final float size;
      final float seed;
      final int color;
      float landed = -1.0F;

      Grain(Vector3f origin, Random random) {
         this.origin = origin;
         this.position = new Vector3f(origin);
         this.release = 0.86F + random.nextFloat() * 0.022F;
         this.size = 0.032F + random.nextFloat() * 0.025F;
         this.seed = random.nextFloat() * 6.28F;
         int shade = 205 + random.nextInt(45);
         this.color = shade << 16 | shade << 8 | shade - 12;
      }
   }
}
