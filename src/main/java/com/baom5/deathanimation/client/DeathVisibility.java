package com.baom5.deathanimation.client;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum DeathVisibility {
   MY_KILLS("Only players you kill"),
   EVERYONE_NEARBY("Everyone nearby");

   public final String label;

   DeathVisibility(String label) {
      this.label = label;
   }

   public static DeathVisibility orDefault(DeathVisibility visibility) {
      return visibility == null ? EVERYONE_NEARBY : visibility;
   }

   public boolean includes(UUID victim, UUID killer, UUID local) {
      return victim != null && (this == EVERYONE_NEARBY || local != null && !local.equals(victim) && local.equals(killer));
   }
}
