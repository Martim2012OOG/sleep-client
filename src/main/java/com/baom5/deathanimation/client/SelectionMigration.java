package com.baom5.deathanimation.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SelectionMigration {
   private static final Set<String> VALID = new HashSet<>(ReleaseProfile.animations().stream().map(Enum::name).toList());

   private SelectionMigration() {
   }

   public static List<String> enabled(List<String> saved) {
      if (saved == null) {
         return List.of();
      }

      List<String> result = saved.stream().map(SelectionMigration::canonicalId).filter(VALID::contains).distinct().toList();
      return result.contains("NONE") ? List.of("NONE") : result;
   }

   public static List<String> favourites(List<String> saved) {
      return saved == null ? List.of() : saved.stream().map(SelectionMigration::canonicalId).filter(VALID::contains).distinct().toList();
   }

   private static String canonicalId(String id) {
      if ("TNT_V1".equals(id) || "TNT_V2".equals(id)) {
         return "RAGDOLL";
      } else {
         return "FULL_1338_INFLATABLE_CASTLE_DEFLATE".equals(id) ? "FULL_1338_PLAYER_DEFLATE" : id;
      }
   }
}
