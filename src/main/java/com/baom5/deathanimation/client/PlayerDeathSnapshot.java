package com.baom5.deathanimation.client;

import com.mojang.authlib.GameProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
record PlayerDeathSnapshot(
   GameProfile profile,
   int entityId,
   String name,
   Vec3d position,
   Vec3d velocity,
   float yaw,
   byte layers,
   Arm arm,
   CapturedPlayerSkin skin,
   Map<EquipmentSlot, ItemStack> equipment,
   PlayerDeathSnapshot.PoseSnapshot pose,
   float observedHealth
) {
   static PlayerDeathSnapshot capture(AbstractClientPlayerEntity player, PlayerDeathSnapshot previous) {
      boolean alive = !player.isDead();
      CapturedPlayerSkin skin = previous == null ? new CapturedPlayerSkin(player) : previous.skin;
      skin.remember(player.getSkin());
      Map<EquipmentSlot, ItemStack> equipment = previous == null ? new EnumMap<>(EquipmentSlot.class) : previous.equipment;
      if (alive || previous == null) {
         EnumMap<EquipmentSlot, ItemStack> changed = null;

         for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack current = player.getEquippedStack(slot);
            ItemStack item = current.isEmpty() ? MotionHistory.equipmentAtDeath(player, slot) : current;
            if (!ItemStack.areEqual(equipment.getOrDefault(slot, ItemStack.EMPTY), item)) {
               if (changed == null) {
                  changed = new EnumMap<>(EquipmentSlot.class);
                  changed.putAll(equipment);
               }

               changed.put(slot, item.copy());
            }
         }

         if (changed != null) {
            equipment = Collections.unmodifiableMap(changed);
         }
      }

      int mask = 0;

      for (PlayerModelPart part : PlayerModelPart.values()) {
         if (player.isModelPartVisible(part)) {
            mask |= part.getBitFlag();
         }
      }

      Vec3d motion = MotionHistory.atDeath(player, player.getVelocity());
      if (!finite(motion)) {
         motion = Vec3d.ZERO;
      }

      return new PlayerDeathSnapshot(
         player.getGameProfile(),
         player.getId(),
         player.getName().getString(),
         player.getEntityPos(),
         motion,
         player.bodyYaw,
         (byte)mask,
         player.getMainArm(),
         skin,
         equipment,
         PlayerDeathSnapshot.PoseSnapshot.capture(player),
         player.getHealth()
      );
   }

   static boolean finite(Vec3d v) {
      return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   AbstractClientPlayerEntity materialize(ClientWorld level) {
      return new PlayerDeathSnapshot.FrozenPlayer(level, this);
   }

   @Environment(EnvType.CLIENT)
   static final class FrozenPlayer extends OtherClientPlayerEntity {
      private final CapturedPlayerSkin captured;
      private final PlayerDeathSnapshot.PoseSnapshot poseSnapshot;

      FrozenPlayer(ClientWorld level, PlayerDeathSnapshot s) {
         super(level, s.profile);
         this.captured = s.skin;
         this.poseSnapshot = s.pose;
         this.setId(s.entityId);
         this.setPosition(s.position);
         this.setVelocity(s.velocity);
         this.lastX = this.lastRenderX = s.position.x;
         this.lastY = this.lastRenderY = s.position.y;
         this.lastZ = this.lastRenderZ = s.position.z;
         this.setYaw(s.yaw);
         this.bodyYaw = this.lastBodyYaw = s.yaw;
         this.headYaw = this.lastHeadYaw = s.pose.headYaw;
         this.setPitch(s.pose.pitch);
         this.lastPitch = s.pose.pitch;
         this.setPose(s.pose.pose);
         this.setSneaking(s.pose.shift);
         this.setSprinting(s.pose.sprinting);
         this.setSwimming(s.pose.swimming);
         this.setFlag(7, s.pose.flying);
         this.age = s.pose.age;
         this.handSwingProgress = this.lastHandSwingProgress = s.pose.attack;
         this.preferredHand = s.pose.swingingArm;
         this.limbAnimator.updateLimbs(s.pose.walkPosition, 1.0F, 1.0F);
         this.limbAnimator.setSpeed(s.pose.walkSpeed);
         this.getDataTracker().set(PLAYER_MODE_CUSTOMIZATION_ID, s.layers);
         this.setMainArm(s.arm);
         this.setCustomName(Text.literal(s.name));

         for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.equipStack(slot, s.equipment.getOrDefault(slot, ItemStack.EMPTY).copy());
         }
      }

      public SkinTextures getSkin() {
         return this.captured == null ? super.getSkin() : this.captured.get();
      }

      CapturedPlayerSkin capturedSkin() {
         return this.captured;
      }

      public boolean isSpectator() {
         return false;
      }

      public float getLeaningPitch(float tickProgress) {
         return this.poseSnapshot == null ? 0.0F : this.poseSnapshot.swimAmount;
      }

      public int getGlidingTicks() {
         return this.poseSnapshot == null ? 0 : this.poseSnapshot.flyingTicks;
      }

      public boolean isUsingItem() {
         return this.poseSnapshot != null && this.poseSnapshot.usingItem;
      }

      public Hand getActiveHand() {
         return this.poseSnapshot == null ? Hand.MAIN_HAND : this.poseSnapshot.useHand;
      }

      public ItemStack getActiveItem() {
         return this.isUsingItem() ? this.getStackInHand(this.getActiveHand()) : ItemStack.EMPTY;
      }

      public int getItemUseTimeLeft() {
         return this.poseSnapshot == null ? 0 : this.poseSnapshot.useRemaining;
      }

      public int getItemUseTime() {
         return this.poseSnapshot == null ? 0 : this.poseSnapshot.ticksUsing;
      }
   }

   @Environment(EnvType.CLIENT)
   record PoseSnapshot(
      float pitch,
      float headYaw,
      EntityPose pose,
      boolean shift,
      boolean sprinting,
      boolean swimming,
      boolean flying,
      float swimAmount,
      int flyingTicks,
      int age,
      float attack,
      float walkPosition,
      float walkSpeed,
      Hand swingingArm,
      boolean usingItem,
      Hand useHand,
      int useRemaining,
      int ticksUsing
   ) {
      static PlayerDeathSnapshot.PoseSnapshot capture(AbstractClientPlayerEntity source) {
         return new PlayerDeathSnapshot.PoseSnapshot(
            source.getPitch(),
            source.headYaw,
            source.getPose(),
            source.isSneaking(),
            source.isSprinting(),
            source.isSwimming(),
            source.isGliding(),
            source.getLeaningPitch(1.0F),
            source.getGlidingTicks(),
            source.age,
            source.getHandSwingProgress(1.0F),
            source.limbAnimator.getAnimationProgress(1.0F),
            source.limbAnimator.getAmplitude(1.0F),
            source.preferredHand,
            source.isUsingItem(),
            source.getActiveHand(),
            source.getItemUseTimeLeft(),
            source.getItemUseTime()
         );
      }
   }
}
