package com.baom5.deathanimation.client;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class Acrobatics {
   private Acrobatics() {
   }

   public static boolean supports(DeathAnimationConfig.Animation style) {
      return AnimationCatalog.contains(style) && AnimationCatalog.recipe(style).stunts() != null && !AnimationCatalog.recipe(style).stunts().isEmpty();
   }

   static DeathScene.Root adjust(DeathScene scene, DeathScene.Root raw) {
      if (!supports(scene.animation)) {
         return raw;
      }

      float p = scene.progress();
      float blend = 0.0F;
      float pivot = 0.88F;

      for (AnimationCatalog.Stunt stunt : AnimationCatalog.recipe(scene.animation).stunts()) {
         if (stunt.pivotY() != null) {
            pivot = stunt.pivotY();
         }

         if (!(p < stunt.start())) {
            float local = AnimationTimeline.phase(p, stunt.start(), stunt.end());
            if (stunt.land()) {
               blend = Math.max(blend, AnimationTimeline.smooth(AnimationTimeline.phase(local, 0.7F, 1.0F)));
            }
         }
      }

      DeathScene.Root pose = new DeathScene.Root(raw.x(), raw.y(), raw.z(), raw.pitch(), raw.yaw(), raw.roll(), raw.sx(), raw.sy(), raw.sz(), pivot);
      float bottom = minimumY(scene, pose);
      float contact = (float)(scene.floorY - scene.y) + 0.012F - bottom;
      float y = Math.max(contact, raw.y() + (contact - raw.y()) * blend);
      return new DeathScene.Root(raw.x(), y, raw.z(), raw.pitch(), raw.yaw(), raw.roll(), raw.sx(), raw.sy(), raw.sz(), pivot);
   }

   public static float minimumY(DeathScene scene, DeathScene.Root root) {
      float[] bottom = new float[]{1.0F / 0.0F};
      posedVertices(scene, root, point -> bottom[0] = Math.min(bottom[0], point.y));
      return Float.isFinite(bottom[0]) ? bottom[0] - root.y() : 0.0F;
   }

   public static void posedVertices(DeathScene scene, DeathScene.Root root, Consumer<Vector3f> output) {
      posedVertices(scene, root, -1, output);
   }

   public static Vector3f partCenter(DeathScene scene, int index) {
      Vector3f center = new Vector3f();
      int[] count = new int[]{0};
      posedVertices(scene, scene.root(), index, point -> {
         center.add(point);
         count[0]++;
      });
      return count[0] == 0 ? new Vector3f(0.0F, 1.5F, 0.0F) : center.div(count[0]);
   }

   private static void posedVertices(DeathScene scene, DeathScene.Root root, int onlyPart, Consumer<Vector3f> output) {
      if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         DeathBodyRig.Motion[] var27 = scene.rig(model);
         Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(180.0F - scene.avatar.bodyYaw));
         Quaternionf body = new Quaternionf()
            .rotationY((float)Math.toRadians(root.yaw()))
            .rotateX((float)Math.toRadians(root.pitch()))
            .rotateZ((float)Math.toRadians(root.roll()));

         for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
            if (onlyPart < 0 || part.ordinal() == onlyPart) {
               ModelPart bone = DeathBodyRig.part(model, part);
               ModelTransform initial = bone.getDefaultTransform();
               DeathBodyRig.Geometry geometry = DeathBodyRig.geometry(bone);
               Vector3f center = geometry.center();
               DeathBodyRig.Motion motion = var27[part.ordinal()];
               if (!(motion.alpha() < 0.01F) || onlyPart >= 0) {
                  Quaternionf rotation = new Quaternionf()
                     .rotationZYX(initial.roll() + motion.zRot(), initial.yaw() + motion.yRot(), initial.pitch() + motion.xRot());
                  float offsetY = motion.y();
                  if (motion.detached() && motion.groundBlend() >= 0.0F) {
                     float drop = 1.501F - initial.y() / 16.0F - center.y - geometry.bottom(rotation);
                     offsetY = Math.min(offsetY + drop * motion.groundBlend(), drop);
                  }

                  for (Vector3f vertex : geometry.vertices()) {
                     Vector3f local = new Vector3f(vertex);
                     if (scene.animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE) {
                        local.mul(motion.scaleX(), motion.scaleY(), motion.scaleZ());
                     }

                     Vector3f point = motion.detached() ? rotation.transform(local).add(center) : rotation.transform(local.add(center));
                     point.add(initial.x() / 16.0F + motion.x(), initial.y() / 16.0F + offsetY, initial.z() / 16.0F + motion.z());
                     point.set(-point.x * 0.9375F, (1.501F - point.y) * 0.9375F, point.z * 0.9375F);
                     facing.transform(point);
                     point.mul(root.sx(), root.sy(), root.sz());
                     point.y = point.y - root.pivotY();
                     body.transform(point);
                     point.y = point.y + root.pivotY();
                     point.add(root.x(), root.y(), root.z());
                     output.accept(point);
                  }
               }
            }
         }
      }
   }
}
