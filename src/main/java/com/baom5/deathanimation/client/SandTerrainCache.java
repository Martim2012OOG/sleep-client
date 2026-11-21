package com.baom5.deathanimation.client;

import com.google.common.collect.UnmodifiableIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
final class SandTerrainCache {
   private final Long2ObjectOpenHashMap<SandTerrainCache.Cell> cells = new Long2ObjectOpenHashMap();
   private final ArrayDeque<SandTerrainCache.Cell> spare = new ArrayDeque<>();

   void beginStep() {
      ObjectIterator var1 = this.cells.values().iterator();

      while (var1.hasNext()) {
         SandTerrainCache.Cell cell = (SandTerrainCache.Cell)var1.next();
         cell.shapes.clear();
         this.spare.addLast(cell);
      }

      this.cells.clear();
   }

   void discard() {
      this.beginStep();
      this.spare.clear();
   }

   Vec3d move(World level, Box box, Vec3d motion) {
      if (motion.lengthSquared() == 0.0) {
         return motion;
      }

      int x = (int)Math.floor((box.minX + box.maxX) * 0.5);
      int y = (int)Math.floor((box.minY + box.maxY) * 0.5);
      int z = (int)Math.floor((box.minZ + box.maxZ) * 0.5);
      double minX = Math.min(box.minX, box.minX + motion.x);
      double minY = Math.min(box.minY, box.minY + motion.y);
      double minZ = Math.min(box.minZ, box.minZ + motion.z);
      double maxX = Math.max(box.maxX, box.maxX + motion.x);
      double maxY = Math.max(box.maxY, box.maxY + motion.y);
      double maxZ = Math.max(box.maxZ, box.maxZ + motion.z);
      if (!(minX < x - 0.5) && !(minY < y - 0.5) && !(minZ < z - 0.5) && !(maxX > x + 1.5) && !(maxY > y + 1.5) && !(maxZ > z + 1.5)) {
         long key = BlockPos.asLong(x, y, z);
         SandTerrainCache.Cell cell = (SandTerrainCache.Cell)this.cells.get(key);
         if (cell == null) {
            cell = this.spare.pollFirst();
            if (cell == null) {
               cell = new SandTerrainCache.Cell();
            }

            for (VoxelShape shape : level.getBlockCollisions(null, new Box(x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 1.5, z + 1.5))) {
               cell.shapes.add(shape);
            }

            this.cells.put(key, cell);
         }

         return collide(motion, box, cell.shapes);
      } else {
         return Entity.adjustMovementForCollisions(null, motion, box, level, List.of());
      }
   }

   static Vec3d collide(Vec3d motion, Box box, List<VoxelShape> shapes) {
      if (shapes.isEmpty()) {
         return motion;
      }

      double x = 0.0;
      double y = 0.0;
      double z = 0.0;
      UnmodifiableIterator var9 = Direction.getCollisionOrder(motion).iterator();

      while (var9.hasNext()) {
         Axis axis = (Axis)var9.next();
         double requested = motion.getComponentAlongAxis(axis);
         if (requested != 0.0) {
            double allowed = VoxelShapes.calculateMaxOffset(axis, box, shapes, requested);
            if (axis == Axis.X) {
               x = allowed;
               if (allowed != 0.0) {
                  box = box.offset(allowed, 0.0, 0.0);
               }
            } else if (axis == Axis.Y) {
               y = allowed;
               if (allowed != 0.0) {
                  box = box.offset(0.0, allowed, 0.0);
               }
            } else {
               z = allowed;
               if (allowed != 0.0) {
                  box = box.offset(0.0, 0.0, allowed);
               }
            }
         }
      }

      return x == motion.x && y == motion.y && z == motion.z ? motion : new Vec3d(x, y, z);
   }

   @Environment(EnvType.CLIENT)
   private static final class Cell {
      final ArrayList<VoxelShape> shapes = new ArrayList<>();
   }
}
