package com.baom5.deathanimation.client;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;

@Environment(EnvType.CLIENT)
public final class AttackerTracker {
   private static final Map<UUID, AttackerTracker.Hit> HITS = new HashMap<>();
   private static ClientWorld observed;

   private AttackerTracker() {
   }

   public static void changeLevel(ClientWorld level) {
      if (observed != level) {
         HITS.clear();
         observed = level;
      }
   }

   public static void observe(EntityDamageS2CPacket packet) {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientWorld level = client.world;
      if (level != observed) {
         HITS.clear();
         observed = level;
      }

      if (level != null) {
         Entity victim = level.getEntityById(packet.entityId());
         DamageSource source = packet.createDamageSource(level);
         Entity entity = source.getAttacker();
         if (!(entity instanceof AbstractClientPlayerEntity)) {
            entity = level.getEntityById(packet.sourceCauseId());
         }

         if (victim instanceof AbstractClientPlayerEntity target
            && entity instanceof AbstractClientPlayerEntity attacker
            && !target.getUuid().equals(attacker.getUuid())) {
            HITS.put(
               target.getUuid(),
               new AttackerTracker.Hit(attacker.getUuid(), attacker.getGameProfile(), attacker.getSkin(), layerMask(attacker), level.getTime())
            );
         }

         HITS.entrySet().removeIf(entry -> level.getTime() - entry.getValue().time > 200L);
      }
   }

   public static SkinTextures skinFor(AbstractClientPlayerEntity victim) {
      if (victim.getEntityWorld() != observed) {
         HITS.clear();
         observed = (ClientWorld)victim.getEntityWorld();
      }

      AttackerTracker.Hit hit = HITS.get(victim.getUuid());
      if (hit != null && victim.getEntityWorld().getTime() - hit.time <= 200L) {
         ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
         PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(hit.attacker);
         return info == null ? hit.skin : info.getSkinTextures();
      } else {
         DamageSource damage = victim.getRecentDamageSource();
         if (damage != null && damage.getAttacker() instanceof AbstractClientPlayerEntity attacker) {
            return attacker.getSkin();
         } else {
            return victim.getAttacker() instanceof AbstractClientPlayerEntity attacker ? attacker.getSkin() : victim.getSkin();
         }
      }
   }

   public static SkinTextures skinFor(UUID victim, SkinTextures fallback) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && client.world == observed) {
         AttackerTracker.Hit hit = HITS.get(victim);
         if (hit != null && client.world.getTime() - hit.time <= 200L) {
            ClientPlayNetworkHandler connection = client.getNetworkHandler();
            PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(hit.attacker);
            return info == null ? hit.skin : info.getSkinTextures();
         } else {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public static void applySkin(UUID victim, DeathAvatar avatar, SkinTextures fallback) {
      MinecraftClient client = MinecraftClient.getInstance();
      AttackerTracker.Hit hit = client.world == observed ? HITS.get(victim) : null;
      if (hit != null && client.world != null && client.world.getTime() - hit.time <= 200L) {
         ClientPlayNetworkHandler connection = client.getNetworkHandler();
         PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(hit.attacker);
         avatar.usePlayerSkin(info == null ? hit.profile : info.getProfile(), hit.skin);
         avatar.skinPartsMask(hit.layers);
      } else {
         avatar.useSkin(fallback);
      }
   }

   private static byte layerMask(AbstractClientPlayerEntity player) {
      int mask = 0;

      for (PlayerModelPart part : PlayerModelPart.values()) {
         if (player.isModelPartVisible(part)) {
            mask |= part.getBitFlag();
         }
      }

      return (byte)mask;
   }

   @Environment(EnvType.CLIENT)
   private record Hit(UUID attacker, GameProfile profile, SkinTextures skin, byte layers, long time) {
   }
}
