package com.baom5.deathanimation.client;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class AmongUsImpostorKill {
   private static final List<AmongUsImpostorKill.Part> IMPOSTOR = List.of(
      part("red_concrete", 0.0F, 0.55F, 0.0F, 0.54F, 0.56F, 0.42F),
      part("red_concrete", 0.0F, 0.96F, 0.0F, 0.7F, 0.54F, 0.48F),
      part("red_concrete", 0.0F, 1.28F, 0.0F, 0.56F, 0.2F, 0.44F),
      part("red_concrete", -0.17F, 0.17F, 0.02F, 0.22F, 0.34F, 0.32F),
      part("red_concrete", 0.17F, 0.17F, 0.02F, 0.22F, 0.34F, 0.32F),
      part("red_concrete", 0.0F, 0.86F, 0.29F, 0.25F, 0.48F, 0.27F),
      part("blue_concrete", 0.0F, 1.11F, -0.253F, 0.53F, 0.25F, 0.075F),
      part("light_blue_stained_glass", 0.0F, 1.12F, -0.298F, 0.43F, 0.15F, 0.035F),
      part("white_concrete", -0.105F, 1.17F, -0.32F, 0.1F, 0.035F, 0.018F),
      part("black_concrete", 0.0F, 0.91F, -0.27F, 0.21F, 0.055F, 0.035F)
   );
   private static final List<AmongUsImpostorKill.Part> CORPSE = List.of(
      part("white_concrete", 0.0F, 0.86F, 0.0F, 0.13F, 0.36F, 0.13F),
      part("white_concrete", 0.0F, 1.04F, 0.0F, 0.28F, 0.1F, 0.14F),
      part("white_concrete", -0.11F, 1.08F, 0.0F, 0.16F, 0.16F, 0.16F),
      part("white_concrete", 0.11F, 1.08F, 0.0F, 0.16F, 0.16F, 0.16F)
   );
   private static final int TONGUE_INDEX = IMPOSTOR.size();
   private static final int CORPSE_START = TONGUE_INDEX + 1;
   private static final int BLOOD_START = CORPSE_START + CORPSE.size();
   private static final int BLOOD_COUNT = 9;

   private AmongUsImpostorKill() {
   }

   private static AmongUsImpostorKill.Part part(String block, float x, float y, float z, float sx, float sy, float sz) {
      return new AmongUsImpostorKill.Part(block, x, y, z, sx, sy, sz);
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      for (AmongUsImpostorKill.Part part : IMPOSTOR) {
         spawnBlock(scene, part, add);
      }

      spawnBlock(scene, part("redstone_block", 0.0F, 1.03F, -0.3F, 0.08F, 0.08F, 0.001F), add);

      for (AmongUsImpostorKill.Part part : CORPSE) {
         spawnBlock(scene, part, add);
      }

      for (int i = 0; i < 9; i++) {
         spawnBlock(scene, part(i % 3 == 0 ? "red_terracotta" : "red_concrete", 0.0F, 0.0F, 0.0F, 0.12F, 0.04F, 0.04F), add);
      }

      animate(scene);
   }

   private static void spawnBlock(DeathScene scene, AmongUsImpostorKill.Part part, Consumer<Entity> add) {
      BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display != null) {
         display.setBlockState(((Block)Registries.BLOCK.get(Identifier.ofVanilla(part.block()))).getDefaultState());
         display.setPosition(scene.x, scene.y, scene.z);
         add.accept(display);
      }
   }

   static void animate(DeathScene scene) {
      float p = scene.progress();
      float reveal = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.015F, 0.08F));
      float approach = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.16F, 0.4F));
      float lunge = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.39F, 0.47F));
      float retreat = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.49F, 0.64F));
      float fade = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.68F, 0.78F));
      float imposterZ = 1.72F - 0.88F * approach - 0.28F * lunge + 0.55F * retreat;
      float imposterAlpha = reveal * (1.0F - fade);
      float lean = -0.3F
         * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.34F, 0.44F))
         * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.48F, 0.56F)));
      Quaternionf bodyRotation = new Quaternionf().rotationX(lean);

      for (int i = 0; i < IMPOSTOR.size() && i < scene.props.size(); i++) {
         poseDisplay(scene, scene.props.get(i), IMPOSTOR.get(i), imposterZ, imposterAlpha, bodyRotation);
      }

      float extend = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.39F, 0.47F))
         * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.49F, 0.54F)));
      float tongueLength = 0.78F * extend;
      AmongUsImpostorKill.Part tongue = part("redstone_block", 0.0F, 1.03F, -0.3F - tongueLength * 0.5F, 0.08F, 0.08F, Math.max(0.001F, tongueLength));
      if (TONGUE_INDEX < scene.props.size()) {
         poseDisplay(scene, scene.props.get(TONGUE_INDEX), tongue, imposterZ, imposterAlpha * extend, bodyRotation);
      }

      boolean killed = p >= 0.49F;
      DeathScene.Root corpseRoot = root(scene);
      Quaternionf corpseRotation = new Quaternionf().rotationZ((float)Math.toRadians(corpseRoot.roll()));
      float floorOffset = corpseRoot.y();

      for (int i = 0; i < CORPSE.size(); i++) {
         poseDisplay(scene, scene.props.get(CORPSE_START + i), CORPSE.get(i), 0.0F, killed ? 1.0F : 0.0F, corpseRotation, 0.35F, floorOffset);
      }

      float spray = AnimationTimeline.phase(p, 0.49F, 0.6F);
      boolean blood = killed && p < 0.6F && DeathAnimationConfig.INSTANCE.visualEffects;

      for (int i = 0; i < 9; i++) {
         float angle = (float)(0.37699111843077515 + i * 3.141592653589793 * 0.76 / 8.0);
         float direction = i % 2 == 0 ? 1.0F : -1.0F;
         float speed = 0.45F + i % 3 * 0.18F;
         AmongUsImpostorKill.Part streak = part(
            i % 3 == 0 ? "red_terracotta" : "red_concrete",
            direction * (0.05F + speed * spray),
            0.66F + (float)Math.sin(angle) * 0.55F * spray - 0.48F * spray * spray,
            -0.05F + (i % 3 - 1) * 0.16F * spray,
            0.12F,
            0.045F,
            0.045F
         );
         poseDisplay(
            scene, scene.props.get(BLOOD_START + i), streak, 0.0F, blood ? 1.0F - spray : 0.0F, new Quaternionf(), 0.35F, (float)(scene.floorY - scene.y)
         );
      }
   }

   private static float corpseBottom(Quaternionf rotation) {
      float low = 1.0F / 0.0F;

      for (int corner = 0; corner < 8; corner++) {
         Vector3f point = new Vector3f((corner & 1) == 0 ? -0.29F : 0.29F, ((corner & 2) == 0 ? 0.0F : 0.76F) - 0.35F, (corner & 4) == 0 ? -0.17F : 0.17F);
         low = Math.min(low, 0.35F + rotation.transform(point).y);
      }

      for (AmongUsImpostorKill.Part piece : CORPSE) {
         Vector3f center = rotation.transform(new Vector3f(piece.x(), piece.y() - 0.35F, piece.z()));
         float extent = Math.abs(rotation.transform(new Vector3f(piece.sx() * 0.5F, 0.0F, 0.0F)).y)
            + Math.abs(rotation.transform(new Vector3f(0.0F, piece.sy() * 0.5F, 0.0F)).y)
            + Math.abs(rotation.transform(new Vector3f(0.0F, 0.0F, piece.sz() * 0.5F)).y);
         low = Math.min(low, 0.35F + center.y - extent);
      }

      return low;
   }

   static DeathScene.Root root(DeathScene scene) {
      float fall = AnimationTimeline.phase(scene.progress(), 0.49F, 0.66F);
      float angle = -1.5707964F * Math.min(1.0F, 2.0F * fall * fall);
      Quaternionf rotation = new Quaternionf().rotationZ(angle);
      return new DeathScene.Root(
         0.0F, (float)(scene.floorY - scene.y) - corpseBottom(rotation), 0.0F, 0.0F, scene.sceneFacing, (float)Math.toDegrees(angle), 1.0F, 1.0F, 1.0F, 0.35F
      );
   }

   private static void poseDisplay(DeathScene scene, Entity entity, AmongUsImpostorKill.Part part, float zOffset, float alpha, Quaternionf bodyRotation) {
      poseDisplay(scene, entity, part, zOffset, alpha, bodyRotation, 0.72F, 0.0F);
   }

   private static void poseDisplay(
      DeathScene scene, Entity entity, AmongUsImpostorKill.Part part, float zOffset, float alpha, Quaternionf bodyRotation, float pivotY, float shiftY
   ) {
      if (!entity.isRemoved() && entity instanceof DisplayEntity display) {
         Vector3f local = new Vector3f(part.x(), part.y(), part.z() + zOffset);
         Vector3f pivot = new Vector3f(0.0F, pivotY, zOffset);
         local.sub(pivot);
         bodyRotation.transform(local);
         local.add(pivot);
         local.y += shiftY;
         Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
         facing.transform(local);
         Quaternionf rotation = new Quaternionf(bodyRotation).premul(facing);
         float visible = Math.max(1.0E-4F, alpha);
         Vector3f scale = new Vector3f(part.sx() * visible, part.sy() * visible, part.sz() * visible);
         Vector3f offset = rotation.transform(new Vector3f(scale).mul(-0.5F));
         display.setTransformation(new AffineTransformation(offset, rotation, scale, new Quaternionf()));
         entity.setPosition(scene.x + local.x, scene.y + local.y, scene.z + local.z);
         entity.lastX = entity.lastRenderX = entity.getX();
         entity.lastY = entity.lastRenderY = entity.getY();
         entity.lastZ = entity.lastRenderZ = entity.getZ();
      }
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float p = scene.progress();
      if (p >= 0.49F) {
         return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, part >= 4 ? 1.0F : 0.0F);
      }

      float guard = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.27F, 0.38F))
         * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.4F, 0.46F)));
      float recoil = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.41F, 0.48F));
      float fade = p < 0.49F ? 1.0F : 0.0F;
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      float x = 0.0F;
      float y = 0.0F;
      float z = 0.0F;
      float rx = 0.0F;
      float ry = 0.0F;
      float rz = 0.0F;
      if (part == 0) {
         y = 0.025F * recoil;
         rx = -0.22F * recoil;
      } else if (part == 1) {
         z = 0.035F * recoil;
         rx = 0.12F * recoil;
      } else if (part != 2 && part != 3) {
         rx = side * 0.25F * recoil;
      } else {
         rx = -0.32F * guard - 1.15F * recoil;
         rz = side * (0.16F * guard + 0.38F * recoil);
      }

      return new DeathBodyRig.Motion(false, x, y, z, rx, ry, rz, -1.0F, fade);
   }

   @Environment(EnvType.CLIENT)
   private record Part(String block, float x, float y, float z, float sx, float sy, float sz) {
   }
}
