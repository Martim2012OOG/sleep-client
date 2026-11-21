package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

@Environment(EnvType.CLIENT)
public final class PlayerSkinLayers {
   private PlayerSkinLayers() {
   }

   static PlayerSkinLayers.Snapshot prepare(PlayerEntityModel model, PlayerEntityRenderState state) {
      ModelPart[] layers = new ModelPart[]{model.hat, model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants};
      boolean[] shown = new boolean[]{
         state.hatVisible, state.jacketVisible, state.leftSleeveVisible, state.rightSleeveVisible, state.leftPantsLegVisible, state.rightPantsLegVisible
      };
      PlayerSkinLayers.Snapshot snapshot = new PlayerSkinLayers.Snapshot(layers);

      for (int i = 0; i < layers.length; i++) {
         layers[i].setTransform(layers[i].getDefaultTransform());
         layers[i].visible = shown[i];
      }

      return snapshot;
   }

   static boolean visibleChildPath(ModelPart root, String path) {
      ModelPart current = root;
      if (!path.isEmpty()) {
         for (String name : path.split("/")) {
            if (!name.isEmpty()) {
               if (!current.hasChild(name)) {
                  return false;
               }

               current = current.getChild(name);
               if (!current.visible) {
                  return false;
               }
            }
         }
      }

      return !current.hidden;
   }

   static long visibilityKey(ModelPart root) {
      long mask = root.hidden ? -9223372036854775808L : 0L;
      int shift = 0;

      for (ModelPart part : root.traverse()) {
         if (part != root) {
            if (part.visible) {
               mask |= 1L << shift;
            }

            if (part.hidden) {
               mask |= 1L << shift + 1;
            }

            shift += 2;
         }
      }

      return mask;
   }

   @Environment(EnvType.CLIENT)
   static final class Snapshot {
      final ModelPart[] layers;
      final ModelTransform[] poses = new ModelTransform[6];
      final boolean[] visible = new boolean[6];

      Snapshot(ModelPart[] layers) {
         this.layers = layers;

         for (int i = 0; i < layers.length; i++) {
            this.poses[i] = layers[i].getTransform();
            this.visible[i] = layers[i].visible;
         }
      }

      void restore() {
         for (int i = 0; i < this.layers.length; i++) {
            this.layers[i].setTransform(this.poses[i]);
            this.layers[i].visible = this.visible[i];
         }
      }
   }
}
