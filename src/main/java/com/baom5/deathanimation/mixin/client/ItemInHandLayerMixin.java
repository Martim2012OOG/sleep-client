package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import com.baom5.deathanimation.client.GoldMaterial;
import com.baom5.deathanimation.client.Kamehameha;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemFeatureRenderer.class)
public abstract class ItemInHandLayerMixin {
   @ModifyVariable(method = "method_4192", at = @At("HEAD"), argsOnly = true)
   private OrderedRenderCommandQueue deathanimations$goldHand(
      OrderedRenderCommandQueue original,
      ArmedEntityRenderState state,
      ItemRenderState item,
      ItemStack stack,
      Arm arm,
      MatrixStack poses,
      OrderedRenderCommandQueue collector,
      int light
   ) {
      DeathScene scene = DeathEffectController.sceneFor(state);
      return GoldMaterial.collector(scene, original, arm == state.mainArm);
   }

   @Inject(method = "method_4192", at = @At("HEAD"), cancellable = true)
   private void deathanimations$hideMissingHand(
      ArmedEntityRenderState state,
      ItemRenderState item,
      ItemStack stack,
      Arm arm,
      MatrixStack poses,
      OrderedRenderCommandQueue collector,
      int light,
      CallbackInfo ci
   ) {
      DeathScene scene = DeathEffectController.sceneFor(state);
      if (scene != null) {
         if (scene.gold() != null
            && state instanceof PlayerEntityRenderState avatar
            && ((FeatureRenderer)(Object)this).getContextModel() instanceof PlayerEntityModel model) {
            ItemRenderState held = scene.gold().handModel(arm);
            if (!held.isEmpty()) {
               poses.push();
               model.setArmAngle(avatar, arm, poses);
               poses.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
               poses.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
               poses.translate((arm == Arm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);
               held.render(poses, GoldMaterial.collector(scene, collector, arm == state.mainArm), light, OverlayTexture.DEFAULT_UV, 0);
               poses.pop();
            }

            ci.cancel();
         } else if (Kamehameha.supports(scene) && scene.progress() >= 0.657F) {
            ci.cancel();
         } else if (scene.bodyVisible() && scene.tint() >>> 24 >= 48) {
            if (((FeatureRenderer)(Object)this).getContextModel() instanceof BipedEntityModel<?> model) {
               int index = arm == Arm.LEFT ? 2 : 3;
               if (scene.rig(model)[index].alpha() < 0.15F) {
                  ci.cancel();
               }
            }
         } else {
            ci.cancel();
         }
      }
   }
}
