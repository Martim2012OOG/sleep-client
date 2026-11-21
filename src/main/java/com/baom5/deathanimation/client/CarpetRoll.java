package com.baom5.deathanimation.client;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class CarpetRoll {
   private static final int SEGMENTS = 32;
   private static final float LENGTH = 4.0F;
   private static final float WIDTH = 2.05F;
   private static final float STEP = 0.125F;
   private static final float INNER_RADIUS = 0.16F;
   private static final float RADIUS_GROWTH = 0.095F;

   private CarpetRoll() {
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      for (int i = 0; i < 32; i++) {
         BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         if (display != null) {
            display.setBlockState(i % 6 == 2 ? Blocks.YELLOW_CARPET.getDefaultState() : Blocks.RED_CARPET.getDefaultState());
            display.setNoGravity(true);
            display.setPosition(scene.x, scene.floorY, scene.z);
            add.accept(display);
         }
      }

      animate(scene);
   }

   static void animate(DeathScene scene) {
      float p = scene.progress();
      float wind = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.08F, 0.84F));
      float front = -2.0F + 4.0F * wind;
      float wound = Math.max(0.0F, front + 2.0F);
      float ground = (float)(scene.floorY - scene.y);
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));

      for (int i = 0; i < Math.min(32, scene.props.size()); i++) {
         Entity entity = scene.props.get(i);
         if (entity instanceof BlockDisplayEntity strip) {
            float material = -2.0F + (i + 0.5F) * 0.125F;
            float q = Math.clamp(front - material, 0.0F, 4.0F);
            float outerRadius = 0.16F + 0.095F * wound;
            float radius = Math.max(0.16F, outerRadius - 0.095F * q);
            float theta = (float)(-Math.log(radius / outerRadius) / 0.0949999988079071);
            float centerX = front;
            float x = centerX + radius * (float)Math.sin(theta);
            float y = ground + 0.03125F + outerRadius - radius * (float)Math.cos(theta);
            float dx = -0.095F * (float)Math.sin(theta) + (float)Math.cos(theta);
            float dy = 0.095F * (float)Math.cos(theta) + (float)Math.sin(theta);
            float tangent = q <= 0.001F ? 0.0F : (float)Math.toDegrees(Math.atan2(dy, dx));
            if (q <= 0.001F) {
               x = material;
               y = ground + 0.03125F;
               tangent = 0.0F;
            }

            float visible = p < 0.97F ? 1.0F : 1.0E-4F;
            float sx = 0.13F;
            float sy = 1.0F;
            float sz = 2.05F;
            Quaternionf rotation = new Quaternionf().rotationZ((float)Math.toRadians(tangent));
            rotation.premul(facing);
            Vector3f offset = rotation.transform(new Vector3f(-sx * 0.5F, -sy / 32.0F, -sz * 0.5F));
            strip.setTransformation(new AffineTransformation(offset, rotation, new Vector3f(sx * visible, sy * visible, sz * visible), new Quaternionf()));
            Vector3f position = facing.transform(new Vector3f(x, y, 0.0F));
            strip.setPosition(scene.x + position.x, scene.y + position.y, scene.z + position.z);
            strip.lastX = strip.lastRenderX = strip.getX();
            strip.lastY = strip.lastRenderY = strip.getY();
            strip.lastZ = strip.lastRenderZ = strip.getZ();
         }
      }
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float p = scene.progress();
      float wrap = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.24F, 0.82F));
      float wobble = (float)Math.sin(p * 38.0F) * (1.0F - wrap) * 0.12F;
      boolean arm = part == 2 || part == 3;
      boolean leg = part == 4 || part == 5;
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      float pitch = 0.0F;
      float yaw = 0.0F;
      float roll = 0.0F;
      if (part == 0) {
         pitch = (float)Math.toRadians(-8.0F + 5.0F * wobble);
         yaw = (float)Math.toRadians(3.0F * wobble);
      } else if (arm) {
         pitch = (float)Math.toRadians(-12.0F - 32.0F * wrap + side * 5.0F * wobble);
         roll = (float)Math.toRadians(side * (16.0F + 22.0F * wrap) + side * 8.0F * wobble);
      } else if (leg) {
         pitch = (float)Math.toRadians(8.0F + 28.0F * wrap + side * 4.0F * wobble);
         roll = (float)Math.toRadians(side * (5.0F + 8.0F * wrap));
      }

      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, pitch, yaw, roll);
   }
}
