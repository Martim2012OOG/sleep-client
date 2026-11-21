package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.AnimationCatalog;
import com.baom5.deathanimation.client.CalendarAgeOut;
import com.baom5.deathanimation.client.DeathAnimationConfig;
import com.baom5.deathanimation.client.DeathAvatar;
import com.baom5.deathanimation.client.DeathEffectController;
import com.baom5.deathanimation.client.DeathScene;
import com.baom5.deathanimation.client.DeathSceneRenderer;
import com.baom5.deathanimation.client.GoldMaterial;
import com.baom5.deathanimation.client.HangingRagdoll;
import com.baom5.deathanimation.client.Kamehameha;
import com.baom5.deathanimation.client.PlayerActors;
import com.baom5.deathanimation.client.PlayerDeathDetection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
   @Unique
   private float deathanimations$timer;
   @Unique
   private LivingEntityRenderState deathanimations$modified;

   @Shadow
   public abstract Identifier method_3885(LivingEntityRenderState var1);

   @ModifyVariable(method = "method_4054", at = @At("HEAD"), argsOnly = true)
   private OrderedRenderCommandQueue deathanimations$goldMaterial(
      OrderedRenderCommandQueue original, LivingEntityRenderState state, MatrixStack poses, OrderedRenderCommandQueue collector, CameraRenderState camera
   ) {
      return GoldMaterial.collector(DeathEffectController.sceneFor(state), original);
   }

   @Inject(method = "method_62355", at = @At("HEAD"))
   private void deathanimations$goldEquipment(LivingEntity source, LivingEntityRenderState state, float partial, CallbackInfo ci) {
      if (source instanceof DeathAvatar avatar) {
         DeathScene scene = DeathEffectController.sceneForAvatar(avatar);
         if (scene != null && scene.gold() != null) {
            scene.gold().prepareEquipment();
         }
      }
   }

   @Inject(method = "method_62463", at = @At("RETURN"), cancellable = true)
   private void deathanimations$movingBounds(LivingEntity entity, CallbackInfoReturnable<Box> cir) {
      if (entity instanceof DeathAvatar avatar) {
         PlayerActors.Binding actor = PlayerActors.forEntity(avatar);
         if (actor != null) {
            cir.setReturnValue(PlayerActors.bounds(actor));
            return;
         }

         DeathScene scene = DeathEffectController.sceneForAvatar(avatar);
         if (scene != null) {
            cir.setReturnValue(scene.renderBounds());
         }
      }
   }

   @Inject(method = "method_62355", at = @At("TAIL"))
   private void deathanimations$cleanSnapshot(LivingEntity source, LivingEntityRenderState state, float partial, CallbackInfo ci) {
      if (source instanceof DeathAvatar) {
         state.displayName = null;
         state.hurt = false;
         DeathScene scene = DeathEffectController.sceneFor(state);
         if (scene != null && scene.gold() != null && scene.age >= 54.0F) {
            state.age = 54.0F;
         }

         if (scene != null && scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION && scene.progress() >= 0.25F) {
            state.light = 15728880;
         }

         if (scene != null && Kamehameha.supports(scene) && scene.progress() >= 0.657F) {
            state.light = 15728880;
         }
      }
   }

   @Inject(method = "method_4054", at = @At("HEAD"), cancellable = true)
   private void deathanimations$visibility(
      LivingEntityRenderState state, MatrixStack poses, OrderedRenderCommandQueue collector, CameraRenderState camera, CallbackInfo ci
   ) {
      PlayerActors.Binding actor = PlayerActors.forState(state);
      if (actor != null) {
         state.shadowRadius = 0.0F;
         state.shadowPieces.clear();
         if (!PlayerActors.visible(actor)) {
            ci.cancel();
         }
      } else {
         DeathScene scene = DeathEffectController.sceneFor(state);
         if (DeathEffectController.hidesOriginal(state)) {
            state.shadowRadius = 0.0F;
            ci.cancel();
         } else if (scene != null) {
            PlayerDeathDetection.renderReached(scene);
            if (HangingRagdoll.supports(scene.animation)) {
               AnimationCatalog.animate(scene);
            }

            if (scene.gui || HangingRagdoll.supports(scene.animation)) {
               DeathSceneRenderer.submitPreviewProps(scene, poses, collector, camera);
            }

            if (DeathAnimationConfig.INSTANCE.hideCorpseShadows) {
               state.shadowRadius = 0.0F;
            }

            if (!scene.bodyVisible()) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(method = "method_4058", at = @At("HEAD"))
   private void deathanimations$pose(LivingEntityRenderState state, MatrixStack poses, float bodyYaw, float scale, CallbackInfo ci) {
      PlayerActors.Binding actor = PlayerActors.forState(state);
      if (actor != null) {
         PlayerActors.transform(actor, poses);
      } else {
         DeathScene scene = DeathEffectController.sceneFor(state);
         boolean noMotion = state instanceof PlayerEntityRenderState
            && DeathAnimationConfig.INSTANCE.enabled
            && DeathAnimationConfig.INSTANCE.isEnabled(DeathAnimationConfig.Animation.NONE)
            && state.deathTime > 0.0F;
         if (scene != null || noMotion) {
            this.deathanimations$modified = state;
            this.deathanimations$timer = state.deathTime;
            state.deathTime = 0.0F;
            if (scene != null) {
               DeathScene.Root root = scene.root();
               poses.translate(root.x(), root.y(), root.z());
               poses.translate(0.0F, root.pivotY(), 0.0F);
               poses.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(root.yaw()));
               poses.multiply(RotationAxis.POSITIVE_X.rotationDegrees(root.pitch()));
               poses.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(root.roll()));
               poses.translate(0.0F, -root.pivotY(), 0.0F);
               poses.scale(root.sx(), root.sy(), root.sz());
            }
         }
      }
   }

   @Inject(method = "method_4058", at = @At("RETURN"))
   private void deathanimations$restore(LivingEntityRenderState state, MatrixStack poses, float bodyYaw, float scale, CallbackInfo ci) {
      if (this.deathanimations$modified == state) {
         state.deathTime = this.deathanimations$timer;
         this.deathanimations$modified = null;
      }
   }

   @Inject(method = "method_62484", at = @At("RETURN"), cancellable = true)
   private void deathanimations$color(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
      DeathScene calendar = CalendarAgeOut.skeletonScene(state);
      if (calendar != null) {
         cir.setReturnValue(CalendarAgeOut.skeletonTint(calendar));
      } else {
         PlayerActors.Binding actor = PlayerActors.forState(state);
         if (actor != null) {
            cir.setReturnValue(PlayerActors.tint(actor));
         } else {
            DeathScene scene = DeathEffectController.sceneFor(state);
            if (scene != null) {
               cir.setReturnValue(scene.tint());
            }
         }
      }
   }

   @Inject(method = "method_24302", at = @At("RETURN"), cancellable = true)
   private void deathanimations$transparency(
      LivingEntityRenderState state, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderLayer> cir
   ) {
      if (CalendarAgeOut.skeletonScene(state) != null) {
         cir.setReturnValue(RenderLayers.entityTranslucent(this.method_3885(state)));
      } else {
         PlayerActors.Binding actor = PlayerActors.forState(state);
         if (actor != null) {
            cir.setReturnValue(RenderLayers.entityTranslucent(this.method_3885(state)));
         } else {
            DeathScene scene = DeathEffectController.sceneFor(state);
            if (scene != null
               && (
                  scene.tint() >>> 24 < 255
                     || scene.animation == DeathAnimationConfig.Animation.ENDER
                     || scene.animation == DeathAnimationConfig.Animation.DUST
                     || scene.animation == DeathAnimationConfig.Animation.TOTEM
                     || scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR
                     || AnimationCatalog.contains(scene.animation)
                        && AnimationCatalog.recipe(scene.animation).limbs() != null
                        && AnimationCatalog.recipe(scene.animation).limbs().stream().anyMatch(track -> track.visibleUntil() <= 1.0F)
               )) {
               cir.setReturnValue(RenderLayers.entityTranslucent(this.method_3885(state)));
            }
         }
      }
   }

   @Inject(method = "method_23622", at = @At("RETURN"), cancellable = true)
   private static void deathanimations$overlay(LivingEntityRenderState state, float white, CallbackInfoReturnable<Integer> cir) {
      if (state instanceof PlayerEntityRenderState) {
         DeathScene scene = DeathEffectController.sceneFor(state);
         if (scene != null && Kamehameha.supports(scene) && scene.progress() >= 0.657F) {
            cir.setReturnValue(OverlayTexture.packUv(OverlayTexture.getU(0.85F), OverlayTexture.getV(false)));
         } else {
            if (DeathEffectController.sceneFor(state) != null || state.deathTime > 0.0F && !DeathAnimationConfig.INSTANCE.redDeathTint) {
               cir.setReturnValue(OverlayTexture.DEFAULT_UV);
            }
         }
      }
   }
}
