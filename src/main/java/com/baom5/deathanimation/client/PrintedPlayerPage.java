package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.PlayerSkinType;

@Environment(EnvType.CLIENT)
final class PrintedPlayerPage {
   static final float WIDTH = 1.35F;
   static final float HEIGHT = 1.9F;
   static final int PAPER = 0;
   static final int SKIN = 1;
   static final int OVERLAY = 2;
   List<PrintedPlayerPage.Patch> patches = List.of();
   private PlayerSkinType model;
   private int layerMask = -1;

   PrintedPlayerPage(DeathScene scene) {
      this.refresh(scene);
   }

   boolean refresh(DeathScene scene) {
      PlayerSkinType currentModel = scene.avatar.getSkin().model();
      int currentMask = 0;

      for (PlayerModelPart part : PlayerModelPart.values()) {
         if (scene.avatar.showsSkinPart(part)) {
            currentMask |= part.getBitFlag();
         }
      }

      if (this.model == currentModel && this.layerMask == currentMask) {
         return false;
      }

      this.model = currentModel;
      this.layerMask = currentMask;
      ArrayList<PrintedPlayerPage.Patch> result = new ArrayList<>();
      result.add(new PrintedPlayerPage.Patch(-0.675F, 0.0F, 0.675F, 1.9F, 0.0F, 0.0F, 1.0F, 1.0F, 0));
      boolean slim = this.model == PlayerSkinType.SLIM;
      float arm = slim ? 0.15F : 0.2F;
      add(result, -0.2F, 1.35F, 0.2F, 1.75F, 8.0F, 8.0F, 16.0F, 16.0F, 1);
      add(result, -0.2F, 0.75F, 0.2F, 1.35F, 20.0F, 20.0F, 28.0F, 32.0F, 1);
      add(result, -0.2F - arm, 0.75F, -0.2F, 1.35F, 44.0F, 20.0F, slim ? 47.0F : 48.0F, 32.0F, 1);
      add(result, 0.2F, 0.75F, 0.2F + arm, 1.35F, 36.0F, 52.0F, slim ? 39.0F : 40.0F, 64.0F, 1);
      add(result, -0.2F, 0.15F, 0.0F, 0.75F, 4.0F, 20.0F, 8.0F, 32.0F, 1);
      add(result, 0.0F, 0.15F, 0.2F, 0.75F, 20.0F, 52.0F, 24.0F, 64.0F, 1);
      if (scene.avatar.showsSkinPart(PlayerModelPart.HAT)) {
         add(result, -0.2F, 1.35F, 0.2F, 1.75F, 40.0F, 8.0F, 48.0F, 16.0F, 2);
      }

      if (scene.avatar.showsSkinPart(PlayerModelPart.JACKET)) {
         add(result, -0.2F, 0.75F, 0.2F, 1.35F, 20.0F, 36.0F, 28.0F, 48.0F, 2);
      }

      if (scene.avatar.showsSkinPart(PlayerModelPart.RIGHT_SLEEVE)) {
         add(result, -0.2F - arm, 0.75F, -0.2F, 1.35F, 44.0F, 36.0F, slim ? 47.0F : 48.0F, 48.0F, 2);
      }

      if (scene.avatar.showsSkinPart(PlayerModelPart.LEFT_SLEEVE)) {
         add(result, 0.2F, 0.75F, 0.2F + arm, 1.35F, 52.0F, 52.0F, slim ? 55.0F : 56.0F, 64.0F, 2);
      }

      if (scene.avatar.showsSkinPart(PlayerModelPart.RIGHT_PANTS_LEG)) {
         add(result, -0.2F, 0.15F, 0.0F, 0.75F, 4.0F, 36.0F, 8.0F, 48.0F, 2);
      }

      if (scene.avatar.showsSkinPart(PlayerModelPart.LEFT_PANTS_LEG)) {
         add(result, 0.0F, 0.15F, 0.2F, 0.75F, 4.0F, 52.0F, 8.0F, 64.0F, 2);
      }

      this.patches = List.copyOf(result);
      return true;
   }

   private static void add(List<PrintedPlayerPage.Patch> output, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int material) {
      output.add(new PrintedPlayerPage.Patch(x0, y0, x1, y1, u0 / 64.0F, v0 / 64.0F, u1 / 64.0F, v1 / 64.0F, material));
   }

   @Environment(EnvType.CLIENT)
   record Patch(float left, float bottom, float right, float top, float u0, float v0, float u1, float v1, int material) {
   }
}
