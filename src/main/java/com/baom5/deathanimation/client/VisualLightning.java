package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;

@Environment(EnvType.CLIENT)
final class VisualLightning extends LightningEntity {
   VisualLightning(ClientWorld level) {
      super(EntityType.LIGHTNING_BOLT, level);
      this.setCosmetic(true);
   }

   public void tick() {
      this.age++;
      if (this.age > 5) {
         this.discard();
      }
   }
}
