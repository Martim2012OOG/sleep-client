package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.AttackerTracker;
import com.baom5.deathanimation.client.PlayerDeathDetection;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {
   @Inject(method = "method_34083", at = @At("HEAD"))
   private void deathanimations$title(TitleS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.matchText(packet.text(), "title");
      }
   }

   @Inject(method = "method_34082", at = @At("HEAD"))
   private void deathanimations$subtitle(SubtitleS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.matchText(packet.text(), "subtitle");
      }
   }

   @Inject(method = "method_34076", at = @At("HEAD"))
   private void deathanimations$actionbar(OverlayMessageS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.matchText(packet.text(), "action_bar");
      }
   }

   @Inject(method = "method_11118", at = @At("HEAD"))
   private void deathanimations$score(ScoreboardScoreUpdateS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.matchText(Text.literal(packet.scoreHolderName()), "scoreboard_line");
         packet.display().ifPresent(text -> PlayerDeathDetection.matchText(text, "scoreboard_display"));
      }
   }

   @Inject(method = "method_11144", at = @At("HEAD"))
   private void deathanimations$objective(ScoreboardObjectiveUpdateS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread() && packet.getMode() != 1) {
         PlayerDeathDetection.matchText(packet.getDisplayName(), "scoreboard_objective");
      }
   }

   @Inject(method = "method_45725", at = @At("HEAD"))
   private void deathanimations$tabRemoval(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.playerInfoRemoved(packet.profileIds());
      }
   }

   @Inject(method = "method_48295", at = @At("HEAD"))
   private void deathanimations$hurt(DamageTiltS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.damaged(packet.id());
      }
   }

   @Inject(method = "method_11113", at = @At("HEAD"))
   private void deathanimations$gameModes(PlayerListS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread() && packet.getActions().contains(Action.UPDATE_GAME_MODE)) {
         for (Entry entry : packet.getEntries()) {
            PlayerDeathDetection.gameMode(entry.profileId(), entry.gameMode());
         }
      }
   }

   @Inject(method = "method_11085", at = @At("HEAD"))
   private void deathanimations$localGameMode(GameStateChangeS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread() && packet.getReason() == GameStateChangeS2CPacket.GAME_MODE_CHANGED) {
         PlayerDeathDetection.localGameMode(GameMode.byIndex((int)packet.getValue()));
      }
   }

   @Inject(method = "method_49034", at = @At("TAIL"))
   private void deathanimations$rememberAttacker(EntityDamageS2CPacket packet, CallbackInfo ci) {
      AttackerTracker.observe(packet);
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         PlayerDeathDetection.damaged(packet);
      }
   }

   @Inject(method = "method_11148", at = @At("HEAD"))
   private void deathanimations$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.world != null) {
         PlayerDeathDetection.status(((EntityEventPacketAccess)packet).deathanimations$entityId(), packet.getStatus());
      }
   }

   @Inject(method = "method_34075", at = @At("HEAD"))
   private void deathanimations$combatDeath(DeathMessageS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.combatKill(packet.playerId());
      }
   }

   @Inject(method = "method_11122", at = @At("HEAD"))
   private void deathanimations$health(HealthUpdateS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.health(packet.getHealth());
      }
   }

   @Inject(method = "method_11093", at = @At("HEAD"))
   private void deathanimations$beforeMetadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.world != null) {
         PlayerDeathDetection.observe(client.world.getEntityById(packet.id()));
      }
   }

   @Inject(method = "method_11093", at = @At("TAIL"))
   private void deathanimations$afterMetadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null) {
         PlayerDeathDetection.observe(level.getEntityById(packet.id()));
      }
   }

   @Inject(method = "method_11095", at = @At("HEAD"))
   private void deathanimations$beforeRemoval(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.world != null) {
         IntListIterator var4 = packet.getEntityIds().iterator();

         while (var4.hasNext()) {
            int id = (Integer)var4.next();
            PlayerDeathDetection.removed(client.world.getEntityById(id));
         }
      }
   }

   @Inject(method = "method_11117", at = @At("HEAD"))
   private void deathanimations$beforeRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.world != null) {
         PlayerDeathDetection.respawn(client.world.getRegistryKey().equals(packet.commonPlayerSpawnInfo().dimension()));
      }
   }

   @Inject(method = "method_43596", at = @At("HEAD"))
   private void deathanimations$deathMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PlayerDeathDetection.systemMessage(packet.content());
      }
   }
}
