package com.baom5.deathanimation.client;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class DeathAvatar extends OtherClientPlayerEntity {
   private CapturedPlayerSkin playerSkin;
   private SkinTextures fixedSkin;

   public DeathAvatar(ClientWorld level, AbstractClientPlayerEntity source, int id) {
      super(level, new GameProfile(UUID.randomUUID(), "DeathAnimations"));
      if (source instanceof DeathAvatar avatar) {
         this.playerSkin = avatar.playerSkin;
         this.fixedSkin = avatar.fixedSkin;
      } else if (source instanceof PlayerDeathSnapshot.FrozenPlayer frozen) {
         this.playerSkin = frozen.capturedSkin();
      } else {
         this.playerSkin = new CapturedPlayerSkin(source);
      }

      this.copyModelParts(source);
      this.setId(id);
      this.setPosition(source.getX(), source.getY(), source.getZ());
      this.setYaw(source.bodyYaw);
      this.bodyYaw = this.lastBodyYaw = source.bodyYaw;
      this.headYaw = this.lastHeadYaw = source.bodyYaw;
      this.setPitch(0.0F);
      this.setNoGravity(true);
      this.noClip = true;

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         this.equipStack(slot, MotionHistory.equipmentAtDeath(source, slot).copy());
      }

      this.setMainArm(source.getMainArm());
      this.syncOldPosition();
   }

   public void useSkin(SkinTextures skin) {
      if (skin != null) {
         this.fixedSkin = skin;
         this.playerSkin = null;
      }
   }

   public void usePlayerSkin(GameProfile profile, SkinTextures initial) {
      if (this.playerSkin != null && this.playerSkin.profile().id().equals(profile.id())) {
         this.playerSkin.remember(initial);
      } else {
         this.playerSkin = new CapturedPlayerSkin(profile, initial);
      }

      this.fixedSkin = null;
   }

   public void copyModelParts(AbstractClientPlayerEntity source) {
      int mask = 0;

      for (PlayerModelPart part : PlayerModelPart.values()) {
         if (source.isModelPartVisible(part)) {
            mask |= part.getBitFlag();
         }
      }

      this.getDataTracker().set(PLAYER_MODE_CUSTOMIZATION_ID, (byte)mask);
   }

   boolean showsSkinPart(PlayerModelPart part) {
      return ((Byte)this.getDataTracker().get(PLAYER_MODE_CUSTOMIZATION_ID) & part.getBitFlag()) != 0;
   }

   void skinPartsMask(byte mask) {
      this.getDataTracker().set(PLAYER_MODE_CUSTOMIZATION_ID, mask);
   }

   public void useReferenceSkin(SkinTextures skin) {
      this.useSkin(skin);

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         this.equipStack(slot, ItemStack.EMPTY);
      }

      this.getDataTracker().set(PLAYER_MODE_CUSTOMIZATION_ID, (byte)127);
   }

   public SkinTextures getSkin() {
      if (this.fixedSkin != null) {
         return this.fixedSkin;
      } else {
         return this.playerSkin != null ? this.playerSkin.get() : super.getSkin();
      }
   }

   public boolean canHit() {
      return false;
   }

   public boolean isSpectator() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   public void tick() {
      this.age++;
      this.deathTime = 0;
      this.setVelocity(Vec3d.ZERO);
      this.syncOldPosition();
   }

   public void syncOldPosition() {
      this.lastX = this.lastRenderX = this.getX();
      this.lastY = this.lastRenderY = this.getY();
      this.lastZ = this.lastRenderZ = this.getZ();
      this.lastYaw = this.getYaw();
      this.lastPitch = this.getPitch();
      this.lastBodyYaw = this.bodyYaw;
      this.lastHeadYaw = this.headYaw;
   }
}
