package com.baom5.deathanimation.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class MotionHistory {
   private static final Map<UUID, MotionHistory.Sample> SAMPLES = new HashMap<>();

   static void clear() {
      SAMPLES.clear();
   }

   static void forget(UUID player) {
      SAMPLES.remove(player);
   }

   static void record(AbstractClientPlayerEntity player) {
      long tick = player.getEntityWorld().getTime();
      MotionHistory.Sample old = SAMPLES.get(player.getUuid());
      Vec3d movement = old == null ? player.getVelocity() : player.getEntityPos().subtract(old.position);
      if (movement.lengthSquared() < 1.0E-4 && player.getVelocity().lengthSquared() > 1.0E-4) {
         movement = player.getVelocity();
      }

      boolean moving = movement.lengthSquared() > 1.0E-4;
      Map<EquipmentSlot, ItemStack> gear = old == null ? new EnumMap<>(EquipmentSlot.class) : old.gear;

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         ItemStack current = player.getEquippedStack(slot);
         if (!ItemStack.areEqual(gear.getOrDefault(slot, ItemStack.EMPTY), current)) {
            gear.put(slot, current.copy());
         }
      }

      SAMPLES.put(
         player.getUuid(),
         new MotionHistory.Sample(
            player.getEntityPos(),
            moving ? movement : (old == null ? Vec3d.ZERO : old.velocity),
            moving ? tick : (old == null ? tick : old.movingAt),
            tick,
            gear
         )
      );
      if (SAMPLES.size() > 256) {
         SAMPLES.entrySet().removeIf(entry -> tick - entry.getValue().seenAt > 40L);
      }
   }

   static Vec3d atDeath(AbstractClientPlayerEntity player, Vec3d fallback) {
      if (player instanceof PlayerDeathSnapshot.FrozenPlayer) {
         return player.getVelocity();
      }

      MotionHistory.Sample sample = SAMPLES.get(player.getUuid());
      return sample != null && player.getEntityWorld().getTime() - sample.movingAt <= 5L ? sample.velocity : fallback;
   }

   static ItemStack equipmentAtDeath(AbstractClientPlayerEntity player, EquipmentSlot slot) {
      if (player instanceof PlayerDeathSnapshot.FrozenPlayer) {
         return player.getEquippedStack(slot);
      }

      MotionHistory.Sample sample = SAMPLES.get(player.getUuid());
      return sample != null && player.getEntityWorld().getTime() - sample.seenAt <= 3L
         ? sample.gear.getOrDefault(slot, ItemStack.EMPTY)
         : player.getEquippedStack(slot);
   }

   @Environment(EnvType.CLIENT)
   private record Sample(Vec3d position, Vec3d velocity, long movingAt, long seenAt, Map<EquipmentSlot, ItemStack> gear) {
   }
}
