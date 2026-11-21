package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class AmongUsEjected {
   private AmongUsEjected() {
   }

   static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      Vector3f offset = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(-4.0F + 14.0F * p, 0.0F, 0.0F));
      return new DeathScene.Root(
         offset.x, (scene.gui ? 0.0F : (float)(scene.floorY - scene.y)) + 2.1F, offset.z, 0.0F, scene.sceneFacing, 360.0F * p, 1.0F, 1.0F, 1.0F, 0.9F
      );
   }

   static String verdict(DeathScene scene) {
      return scene.ejectedImpostor ? "imposter" : "crewmate";
   }

   static void register() {
      HudElementRegistry.addLast(
         Identifier.of("deathanimation", "ejection_verdict"),
         (graphics, delta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && client.player != null) {
               Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

               for (DeathScene scene : DeathEffectController.scenes()) {
                  if (scene.animation == DeathAnimationConfig.Animation.BATCH01_AIRLOCK_VERDICT
                     && !scene.gui
                     && scene.level == client.world
                     && !(camera.squaredDistanceTo(scene.x, scene.floorY + 3.0, scene.z) > 1024.0)) {
                     draw(graphics, scene, 0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                     if (scene.progress() >= 0.3F && scene.progress() < 0.96F) {
                        break;
                     }
                  }
               }
            }
         }
      );
   }

   static void draw(DrawContext graphics, DeathScene scene, int x, int y, int width, int height) {
      float p = scene.progress();
      float alpha = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.86F, 0.96F));
      if (!(p < 0.3F) && !(alpha <= 0.01F)) {
         if (scene.ejectedImpostor && DeathAnimationConfig.INSTANCE.visualEffects && p < 0.36F) {
            int flash = Math.round(130.0F * (1.0F - AnimationTimeline.phase(p, 0.3F, 0.36F)));
            graphics.fill(x, y, x + width, y + height, flash << 24 | 13172736);
         }

         TextRenderer font = MinecraftClient.getInstance().textRenderer;
         String text = verdict(scene);
         int verdictColor = scene.ejectedImpostor ? 16728128 : 65535;
         float scale = Math.max(0.1F, Math.min(2.0F, (width - 12.0F) / Math.max(1, font.getWidth(text))));
         float revealedWidth = 0.0F;

         for (int i = 0; i < text.length(); i++) {
            revealedWidth += font.getWidth(text.substring(i, i + 1)) * letterAlpha(p, i);
         }

         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(x + width * 0.5F, y + height * 0.16F);
         pose.scale(scale, scale);
         float cursor = -revealedWidth * 0.5F;

         for (int i = 0; i < text.length(); i++) {
            String letter = text.substring(i, i + 1);
            float reveal = letterAlpha(p, i);
            int opacity = Math.round(255.0F * alpha * reveal);
            if (opacity > 3) {
               graphics.drawTextWithShadow(font, letter, Math.round(cursor), 0, opacity << 24 | verdictColor);
            }

            cursor += font.getWidth(letter) * reveal;
         }

         pose.popMatrix();
      }
   }

   private static float letterAlpha(float p, int index) {
      float start = 0.3F + index * 0.012F;
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, start, start + 0.025F));
   }
}
