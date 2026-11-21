package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
final class FnafBiteFlicker {
   private FnafBiteFlicker() {
   }

   static void register() {
      HudElementRegistry.addLast(Identifier.of("deathanimation", "fnaf_bite_flicker"), FnafBiteFlicker::render);
   }

   private static void render(DrawContext graphics, RenderTickCounter deltaTracker) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && client.player != null && DeathAnimationConfig.INSTANCE.visualEffects) {
         Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

         for (DeathScene scene : DeathEffectController.scenes()) {
            if (scene.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN
               && !scene.preview
               && !scene.gui
               && scene.level == client.world
               && !(camera.squaredDistanceTo(scene.x, scene.y + 1.0, scene.z) > 100.0)) {
               float sinceBite = (scene.progress() - 0.565F) * 100.0F;
               if (!(sinceBite < 0.0F) && !(sinceBite >= 4.0F)) {
                  int pulse = (int)sinceBite;

                  int color = switch (pulse) {
                     case 0 -> 2030043135;
                     case 1 -> -805306368;
                     case 2 -> 1174405119;
                     case 3 -> -2113929216;
                     default -> 0;
                  };
                  if (color != 0) {
                     graphics.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), color);
                  }

                  return;
               }
            }
         }
      }
   }
}
