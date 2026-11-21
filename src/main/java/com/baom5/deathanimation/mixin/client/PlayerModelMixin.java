package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathBodyRig;
import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import com.baom5.deathanimation.client.PlayerActors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
   @Inject(method = "method_72859", at = @At("HEAD"), cancellable = true)
   private void deathanimations$followHand(PlayerEntityRenderState state, Arm arm, MatrixStack poses, CallbackInfo ci) {
      DeathScene scene = DeathEffectController.sceneFor(state);
      PlayerActors.Binding actor = PlayerActors.forState(state);
      if (scene != null || actor != null) {
         PlayerEntityModel model = (PlayerEntityModel)(Object)this;
         DeathBodyRig.Part part = arm == Arm.LEFT ? DeathBodyRig.Part.LEFT_ARM : DeathBodyRig.Part.RIGHT_ARM;
         DeathBodyRig.Motion motion = (actor == null ? scene.rig(model) : PlayerActors.rig(actor))[part.ordinal()];
         ModelPart bone = DeathBodyRig.part(model, part);
         ModelTransform initial = bone.getDefaultTransform();
         model.getRootPart().applyTransform(poses);
         if (motion.detached()) {
            DeathBodyRig.Geometry geometry = DeathBodyRig.geometry(bone);
            Vector3f center = geometry.center();
            poses.translate(
               initial.x() / 16.0F + center.x + motion.x(), initial.y() / 16.0F + center.y + motion.y(), initial.z() / 16.0F + center.z + motion.z()
            );
            poses.multiply(new Quaternionf().rotationZYX(motion.zRot(), motion.yRot(), motion.xRot()));
            poses.translate(-center.x, -center.y, -center.z);
         } else {
            poses.translate(initial.x() / 16.0F + motion.x(), initial.y() / 16.0F + motion.y(), initial.z() / 16.0F + motion.z());
            poses.multiply(new Quaternionf().rotationZYX(initial.roll() + motion.zRot(), initial.yaw() + motion.yRot(), initial.pitch() + motion.xRot()));
         }

         ci.cancel();
      }
   }
}
