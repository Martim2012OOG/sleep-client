package com.baom5.deathanimation.client;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;

@Environment(EnvType.CLIENT)
final class EffectParticles {
   private static final int GLOBAL_LIMIT = 2048;
   private static final int CELL_LIMIT = 256;
   private static final Long2IntOpenHashMap CELLS = new Long2IntOpenHashMap();
   private static ClientWorld level;
   private static long tick = -9223372036854775808L;
   private static int emitted;

   private EffectParticles() {
   }

   static boolean near(double x, double y, double z) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null) {
         return false;
      }

      double distance = Math.min(128, Math.max(48, client.options.getClampedViewDistance() * 16));
      return client.gameRenderer.getCamera().getCameraPos().squaredDistanceTo(x, y, z) <= distance * distance;
   }

   private static boolean reserve(double x, double y, double z) {
      ClientWorld current = MinecraftClient.getInstance().world;
      if (current != null && near(x, y, z)) {
         if (level != current || tick != current.getTime()) {
            level = current;
            tick = current.getTime();
            emitted = 0;
            CELLS.clear();
         }

         if (emitted >= 2048) {
            return false;
         }

         long key = ((long)Math.floor(x / 8.0) & 2097151L) << 42 | ((long)Math.floor(y / 8.0) & 2097151L) << 21 | (long)Math.floor(z / 8.0) & 2097151L;
         int count = CELLS.get(key);
         if (count >= 256) {
            return false;
         }

         CELLS.put(key, count + 1);
         emitted++;
         return true;
      } else {
         return false;
      }
   }

   static Particle create(ParticleEffect type, double x, double y, double z, double vx, double vy, double vz) {
      return reserve(x, y, z) ? MinecraftClient.getInstance().particleManager.addParticle(type, x, y, z, vx, vy, vz) : null;
   }

   static void add(DeathScene scene, ParticleEffect type, double x, double y, double z, double vx, double vy, double vz) {
      if (!scene.gui && MinecraftClient.getInstance().world == scene.level && reserve(x, y, z)) {
         scene.level.addParticleClient(type, x, y, z, vx, vy, vz);
      }
   }

   static void clear() {
      level = null;
      tick = -9223372036854775808L;
      emitted = 0;
      CELLS.clear();
   }
}
