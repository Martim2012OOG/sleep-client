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
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class FredbearBite {
   private static final List<FredbearBite.Part> HEAD = List.of(
      part("gold_block", 0.0F, 0.03F, 0.02F, 0.88F, 0.82F, 0.62F),
      part("yellow_concrete", 0.0F, 0.36F, 0.01F, 0.72F, 0.42F, 0.64F),
      part("gold_block", 0.0F, -0.2F, 0.03F, 0.72F, 0.32F, 0.58F),
      part("yellow_terracotta", -0.39F, 0.04F, 0.02F, 0.18F, 0.63F, 0.58F),
      part("yellow_terracotta", 0.39F, 0.04F, 0.02F, 0.18F, 0.63F, 0.58F),
      part("gold_block", -0.51F, 0.43F, 0.01F, 0.25F, 0.3F, 0.3F),
      part("gold_block", 0.51F, 0.43F, 0.01F, 0.25F, 0.3F, 0.3F),
      part("yellow_terracotta", -0.51F, 0.44F, -0.16F, 0.12F, 0.16F, 0.025F),
      part("yellow_terracotta", 0.51F, 0.44F, -0.16F, 0.12F, 0.16F, 0.025F),
      part("black_concrete", -0.21F, 0.16F, -0.315F, 0.24F, 0.24F, 0.075F),
      part("black_concrete", 0.21F, 0.16F, -0.315F, 0.24F, 0.24F, 0.075F),
      part("white_concrete", -0.21F, 0.16F, -0.365F, 0.15F, 0.16F, 0.035F),
      part("white_concrete", 0.21F, 0.16F, -0.365F, 0.15F, 0.16F, 0.035F),
      part("black_concrete", -0.195F, 0.15F, -0.39F, 0.055F, 0.085F, 0.025F),
      part("black_concrete", 0.225F, 0.15F, -0.39F, 0.055F, 0.085F, 0.025F),
      part("gold_block", -0.22F, 0.34F, -0.34F, 0.31F, 0.095F, 0.095F),
      part("gold_block", 0.22F, 0.34F, -0.34F, 0.31F, 0.095F, 0.095F),
      part("gold_block", -0.17F, -0.045F, -0.345F, 0.32F, 0.24F, 0.2F),
      part("gold_block", 0.17F, -0.045F, -0.345F, 0.32F, 0.24F, 0.2F),
      part("black_concrete", 0.0F, -0.245F, -0.355F, 0.49F, 0.3F, 0.08F),
      part("blackstone", 0.0F, -0.245F, -0.405F, 0.4F, 0.22F, 0.035F),
      part("black_concrete", 0.0F, 0.025F, -0.475F, 0.21F, 0.14F, 0.1F),
      part("light_gray_concrete", -0.055F, 0.055F, -0.522F, 0.045F, 0.035F, 0.018F),
      part("light_gray_concrete", 0.055F, 0.055F, -0.522F, 0.045F, 0.035F, 0.018F),
      part("bone_block", -0.2F, -0.132F, -0.443F, 0.075F, 0.13F, 0.07F),
      part("bone_block", -0.1F, -0.145F, -0.449F, 0.075F, 0.15F, 0.07F),
      part("bone_block", 0.0F, -0.15F, -0.45F, 0.075F, 0.16F, 0.07F),
      part("bone_block", 0.1F, -0.145F, -0.449F, 0.075F, 0.15F, 0.07F),
      part("bone_block", 0.2F, -0.132F, -0.443F, 0.075F, 0.13F, 0.07F),
      jaw("yellow_terracotta", 0.0F, -0.45F, -0.36F, 0.6F, 0.24F, 0.27F),
      jaw("gold_block", -0.29F, -0.36F, -0.24F, 0.16F, 0.26F, 0.24F),
      jaw("gold_block", 0.29F, -0.36F, -0.24F, 0.16F, 0.26F, 0.24F),
      jaw("bone_block", -0.2F, -0.34F, -0.475F, 0.075F, 0.1F, 0.06F),
      jaw("bone_block", -0.1F, -0.33F, -0.49F, 0.075F, 0.11F, 0.06F),
      jaw("bone_block", 0.0F, -0.325F, -0.495F, 0.075F, 0.12F, 0.06F),
      jaw("bone_block", 0.1F, -0.33F, -0.49F, 0.075F, 0.11F, 0.06F),
      jaw("bone_block", 0.2F, -0.34F, -0.475F, 0.075F, 0.1F, 0.06F),
      part("iron_block", -0.36F, -0.27F, -0.275F, 0.12F, 0.12F, 0.12F),
      part("iron_block", 0.36F, -0.27F, -0.275F, 0.12F, 0.12F, 0.12F),
      part("purple_concrete", 0.0F, 0.57F, -0.015F, 0.57F, 0.12F, 0.46F),
      part("purple_concrete", 0.0F, 0.78F, 0.015F, 0.35F, 0.34F, 0.32F),
      part("magenta_concrete", 0.0F, 0.65F, -0.005F, 0.37F, 0.065F, 0.34F),
      part("yellow_concrete", 0.0F, 0.48F, -0.01F, 0.4F, 0.055F, 0.37F)
   );
   private static final float HINGE_Y = -0.25F;
   private static final float HINGE_Z = -0.28F;
   static final float BITE_FRAME = 0.565F;
   static final float HEAD_HOLD_END = 0.72F;

   private FredbearBite() {
   }

   private static FredbearBite.Part part(String block, float x, float y, float z, float sx, float sy, float sz) {
      return new FredbearBite.Part(block, x, y, z, sx, sy, sz, false);
   }

   private static FredbearBite.Part jaw(String block, float x, float y, float z, float sx, float sy, float sz) {
      return new FredbearBite.Part(block, x, y, z, sx, sy, sz, true);
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      for (FredbearBite.Part part : HEAD) {
         BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         if (display != null) {
            display.setBlockState(((Block)Registries.BLOCK.get(Identifier.ofVanilla(part.block()))).getDefaultState());
            display.setPosition(scene.x, scene.y, scene.z);
            add.accept(display);
         }
      }

      animate(scene);
   }

   public static void animate(DeathScene scene) {
      float p = scene.progress();
      float reveal = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.19F, 0.27F));
      float fade = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.86F, 0.96F));
      float approach = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.4F, 0.53F));
      float biteDrop = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.42F, 0.53F));
      float headY = 2.48F - 0.12F * approach - 0.31F * biteDrop + headLift(p);
      float headZ = 0.92F - 0.52F * approach;
      float open = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.22F, 0.31F))
         * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.525F, 0.565F)));
      float jawOpen = 62.0F * open;
      float jawAngle = (float)Math.toRadians(-jawOpen);
      Quaternionf face = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));

      for (int i = 0; i < HEAD.size() && i < scene.props.size(); i++) {
         Entity entity = scene.props.get(i);
         if (!entity.isRemoved() && entity instanceof DisplayEntity display) {
            FredbearBite.Part part = HEAD.get(i);
            float openDrop = part.lowerJaw() ? -0.45F * open : 0.0F;
            Vector3f local = new Vector3f(part.x(), part.y(), part.z());
            Quaternionf rotation = new Quaternionf();
            if (part.lowerJaw()) {
               local.sub(0.0F, -0.25F, -0.28F);
               rotation.rotationX(jawAngle).transform(local);
               local.add(0.0F, -0.25F + openDrop, -0.28F - 0.06F * open);
               rotation.rotationX(jawAngle);
            }

            local.add(0.0F, headY, headZ);
            face.transform(local);
            rotation.premul(face);
            float visible = reveal * fade;
            Vector3f scale = new Vector3f(part.sx() * visible, part.sy() * visible, part.sz() * visible);
            Vector3f offset = rotation.transform(new Vector3f(scale).mul(-0.5F));
            display.setTransformation(new AffineTransformation(offset, rotation, scale, new Quaternionf()));
            entity.setPosition(scene.x + local.x, scene.y + local.y, scene.z + local.z);
            entity.lastX = entity.lastRenderX = entity.getX();
            entity.lastY = entity.lastRenderY = entity.getY();
            entity.lastZ = entity.lastRenderZ = entity.getZ();
         }
      }
   }

   static Vec3d heldHeadPosition(DeathScene scene) {
      return new Vec3d(scene.x, scene.y + 1.6415625 + headLift(scene.progress()), scene.z);
   }

   private static float headLift(float progress) {
      return 0.32F * AnimationTimeline.smooth(AnimationTimeline.phase(progress, 0.565F, 0.65F));
   }

   @Environment(EnvType.CLIENT)
   private record Part(String block, float x, float y, float z, float sx, float sy, float sz, boolean lowerJaw) {
   }
}
