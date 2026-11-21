package com.baom5.deathanimation.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class SandContactGrid {
   static final float WIDTH = 0.14F;
   private final Long2ObjectOpenHashMap<SandContactGrid.Cell> cells = new Long2ObjectOpenHashMap();
   private final ArrayList<SandContactGrid.Cell> occupied = new ArrayList<>();
   private final ArrayDeque<SandContactGrid.Cell> spare = new ArrayDeque<>();

   static int coordinate(double value) {
      return (int)Math.floor(value / 0.14000000059604645);
   }

   private static long key(int x, int y, int z) {
      return (x & 2097151L) << 42 | (y & 2097151L) << 21 | z & 2097151L;
   }

   void clear() {
      for (SandContactGrid.Cell cell : this.occupied) {
         cell.size = 0;
         cell.moving = false;
         this.spare.addLast(cell);
      }

      this.occupied.clear();
      this.cells.clear();
   }

   void discard() {
      this.clear();
      this.spare.clear();
   }

   int occupiedCells() {
      return this.occupied.size();
   }

   void add(int index, double x, double y, double z, boolean moving) {
      int cx = coordinate(x);
      int cy = coordinate(y);
      int cz = coordinate(z);
      long key = key(cx, cy, cz);
      SandContactGrid.Cell cell = (SandContactGrid.Cell)this.cells.get(key);
      if (cell == null) {
         cell = this.spare.pollFirst();
         if (cell == null) {
            cell = new SandContactGrid.Cell();
         }

         cell.x = cx;
         cell.y = cy;
         cell.z = cz;
         this.cells.put(key, cell);
         this.occupied.add(cell);
      }

      cell.add(index, moving);
   }

   void pairs(SandContactGrid.PairConsumer consumer) {
      for (SandContactGrid.Cell cell : this.occupied) {
         if (cell.moving) {
            for (int a = 0; a < cell.size; a++) {
               for (int b = 0; b < a; b++) {
                  consumer.accept(cell.indices[a], cell.indices[b]);
               }
            }
         }

         for (int dx = 0; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dy >= 0 && (dy != 0 || dz > 0)) {
                     SandContactGrid.Cell other = (SandContactGrid.Cell)this.cells.get(key(cell.x + dx, cell.y + dy, cell.z + dz));
                     if (other != null && (cell.moving || other.moving)) {
                        for (int a = 0; a < cell.size; a++) {
                           for (int b = 0; b < other.size; b++) {
                              consumer.accept(cell.indices[a], other.indices[b]);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   void query(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, IntConsumer consumer) {
      int x0 = coordinate(minX);
      int y0 = coordinate(minY);
      int z0 = coordinate(minZ);
      int x1 = coordinate(maxX);
      int y1 = coordinate(maxY);
      int z1 = coordinate(maxZ);
      double volume = (1.0 + x1 - x0) * (1.0 + y1 - y0) * (1.0 + z1 - z0);
      if (volume > this.occupied.size() * 2.0) {
         for (SandContactGrid.Cell cell : this.occupied) {
            if (cell.x >= x0 && cell.x <= x1 && cell.y >= y0 && cell.y <= y1 && cell.z >= z0 && cell.z <= z1) {
               for (int i = 0; i < cell.size; i++) {
                  consumer.accept(cell.indices[i]);
               }
            }
         }
      } else {
         for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
               for (int z = z0; z <= z1; z++) {
                  SandContactGrid.Cell cell = (SandContactGrid.Cell)this.cells.get(key(x, y, z));
                  if (cell != null) {
                     for (int i = 0; i < cell.size; i++) {
                        consumer.accept(cell.indices[i]);
                     }
                  }
               }
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Cell {
      int x;
      int y;
      int z;
      int size;
      boolean moving;
      int[] indices = new int[8];

      void add(int index, boolean active) {
         if (this.size == this.indices.length) {
            this.indices = Arrays.copyOf(this.indices, this.size * 2);
         }

         this.indices[this.size++] = index;
         this.moving |= active;
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   interface PairConsumer {
      void accept(int var1, int var2);
   }
}
