package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.CarpetPlayerWrap;
import com.baom5.deathanimation.client.DeathAnimationConfig;
import com.baom5.deathanimation.client.DeathBodyRig;
import com.baom5.deathanimation.client.Kamehameha;
import com.baom5.deathanimation.client.NestedPlayerShell;
import com.baom5.deathanimation.client.PlayerDeflate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Model.class)
public abstract class ModelMixin {
   @Inject(method = "method_62100", at = @At("HEAD"))
   private void deathanimations$hideRigParts(MatrixStack poseStack, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
      if ((Object)this instanceof BipedEntityModel<?> humanoid) {
         DeathBodyRig.Pose pose = DeathBodyRig.activeFor(this);
         if (pose != null) {
            pose.prepareLayers(humanoid);

            for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
               ModelPart modelPart = DeathBodyRig.part(humanoid, part);
               DeathBodyRig.Motion motion = pose.motion(part);
               pose.saveOriginal(part, modelPart);
               if (NestedPlayerShell.active(pose)) {
                  modelPart.visible = false;
               } else if (CarpetPlayerWrap.active(pose)) {
                  modelPart.visible = false;
               } else if (pose.scene() != null && pose.scene().laserGrid() != null) {
                  modelPart.visible = false;
               } else if (Kamehameha.replaces(this)) {
                  modelPart.visible = false;
               } else if (motion.detached()) {
                  modelPart.visible = false;
               } else {
                  ModelTransform initial = modelPart.getDefaultTransform();
                  modelPart.originX = initial.x() + motion.x() * 16.0F;
                  modelPart.originY = initial.y() + motion.y() * 16.0F;
                  modelPart.originZ = initial.z() + motion.z() * 16.0F;
                  modelPart.pitch = initial.pitch() + motion.xRot();
                  modelPart.yaw = initial.yaw() + motion.yRot();
                  modelPart.roll = initial.roll() + motion.zRot();
                  if (motion.alpha() < 0.999F) {
                     modelPart.visible = false;
                  }
               }
            }
         }
      }
   }

   @Inject(method = "method_62100", at = @At("TAIL"))
   private void deathanimations$renderRigParts(MatrixStack poseStack, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
      if ((Object)this instanceof BipedEntityModel<?> humanoid) {
         DeathBodyRig.Pose pose = DeathBodyRig.activeFor(this);
         if (pose != null) {
            if (NestedPlayerShell.active(pose)) {
               NestedPlayerShell.render(humanoid, pose, poseStack, consumer, light, overlay, color);

               for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
                  ModelPart bone = DeathBodyRig.part(humanoid, part);
                  DeathBodyRig.PartState saved = pose.original(part);
                  bone.setOrigin(saved.x(), saved.y(), saved.z());
                  bone.setAngles(saved.xRot(), saved.yRot(), saved.zRot());
                  bone.visible = saved.visible();
               }

               pose.restoreLayers();
            } else if (CarpetPlayerWrap.active(pose)) {
               CarpetPlayerWrap.render(humanoid, pose, poseStack, consumer, light, overlay, color);

               for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
                  ModelPart bone = DeathBodyRig.part(humanoid, part);
                  DeathBodyRig.PartState saved = pose.original(part);
                  bone.setOrigin(saved.x(), saved.y(), saved.z());
                  bone.setAngles(saved.xRot(), saved.yRot(), saved.zRot());
                  bone.visible = saved.visible();
               }

               pose.restoreLayers();
            } else if (pose.scene() != null && pose.scene().laserGrid() != null) {
               pose.scene().laserGrid().render(humanoid, poseStack, consumer, light, overlay, color, pose.renderKey());

               for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
                  ModelPart bone = DeathBodyRig.part(humanoid, part);
                  DeathBodyRig.PartState saved = pose.original(part);
                  bone.setOrigin(saved.x(), saved.y(), saved.z());
                  bone.setAngles(saved.xRot(), saved.yRot(), saved.zRot());
                  bone.visible = saved.visible();
               }

               pose.restoreLayers();
            } else if (Kamehameha.replaces(this)) {
               Kamehameha.render(humanoid, pose, poseStack, consumer, light, overlay, color);

               for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
                  ModelPart bone = DeathBodyRig.part(humanoid, part);
                  DeathBodyRig.PartState saved = pose.original(part);
                  bone.setOrigin(saved.x(), saved.y(), saved.z());
                  bone.setAngles(saved.xRot(), saved.yRot(), saved.zRot());
                  bone.visible = saved.visible();
               }

               pose.restoreLayers();
            } else {
               for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
                  ModelPart modelPart = DeathBodyRig.part(humanoid, part);
                  DeathBodyRig.Motion motion = pose.motion(part);
                  DeathBodyRig.PartState original = pose.original(part);
                  if (motion.detached() && original.visible() && motion.alpha() > 0.003F) {
                     poseStack.push();

                     try {
                        DeathBodyRig.Geometry geometry = DeathBodyRig.geometry(modelPart);
                        ModelTransform initial = modelPart.getDefaultTransform();
                        boolean deflate = pose.scene() != null && pose.scene().animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE;
                        Vector3f center = deflate ? PlayerDeflate.referenceCenter(pose.scene(), part) : geometry.center();
                        Quaternionf rotation = new Quaternionf().rotationZYX(motion.zRot(), motion.yRot(), motion.xRot());
                        float centerY = initial.y() / 16.0F + center.y;
                        float y = motion.y();
                        if (motion.groundBlend() >= 0.0F) {
                           float groundDrop = 1.501F - centerY - geometry.bottom(rotation, motion.scaleX(), motion.scaleY(), motion.scaleZ());
                           y = Math.min(y + groundDrop * motion.groundBlend(), groundDrop);
                        }

                        poseStack.translate(initial.x() / 16.0F + center.x + motion.x(), centerY + y, initial.z() / 16.0F + center.z + motion.z());
                        poseStack.multiply(rotation);
                        if (deflate) {
                           poseStack.scale(motion.scaleX(), motion.scaleY(), motion.scaleZ());
                           poseStack.translate(-center.x, -center.y, -center.z);
                        } else {
                           poseStack.translate(-center.x, -center.y, -center.z);
                           poseStack.scale(motion.scaleX(), motion.scaleY(), motion.scaleZ());
                        }

                        modelPart.visible = true;
                        modelPart.setOrigin(0.0F, 0.0F, 0.0F);
                        modelPart.setAngles(0.0F, 0.0F, 0.0F);
                        int alpha = Math.round((color >>> 24) * motion.alpha());
                        if (deflate) {
                           PlayerDeflate.renderPart(pose.scene(), part, modelPart, poseStack, consumer, light, overlay, alpha << 24 | color & 16777215);
                        } else {
                           modelPart.render(poseStack, consumer, light, overlay, alpha << 24 | color & 16777215);
                        }
                     } finally {
                        poseStack.pop();
                     }
                  } else if (!motion.detached() && original.visible() && motion.alpha() > 0.003F && motion.alpha() < 0.999F) {
                     modelPart.visible = true;
                     int alpha = Math.round((color >>> 24) * motion.alpha());
                     modelPart.render(poseStack, consumer, light, overlay, alpha << 24 | color & 16777215);
                  }

                  modelPart.originX = original.x();
                  modelPart.originY = original.y();
                  modelPart.originZ = original.z();
                  modelPart.pitch = original.xRot();
                  modelPart.yaw = original.yRot();
                  modelPart.roll = original.zRot();
                  modelPart.visible = original.visible();
               }

               pose.restoreLayers();
            }
         }
      }
   }
}
