package com.baom5.deathanimation.client;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ReleaseProfile {
   private static final Set<String> PUBLIC_IDS = load();
   private static final List<DeathAnimationConfig.Animation> AVAILABLE = Arrays.stream(DeathAnimationConfig.Animation.values())
      .filter(ReleaseProfile::allows)
      .toList();

   private ReleaseProfile() {
   }

   private static Set<String> load() {
      try (InputStream stream = ReleaseProfile.class.getResourceAsStream("/assets/deathanimation/animations/public-release.json")) {
         if (stream == null) {
            return null;
         }

         String[] ids = (String[])new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), String[].class);
         Set<String> result = Set.of(ids);
         if (result.size() != 48 || result.contains("NONE") || result.contains("SLIME_BOUNCE")) {
            throw new IllegalStateException("Invalid public release allowlist");
         }

         for (String id : result) {
            DeathAnimationConfig.Animation.valueOf(id);
         }

         return result;
      } catch (Exception error) {
         throw new IllegalStateException("Could not load release profile", error);
      }
   }

   public static boolean isPublic() {
      return PUBLIC_IDS != null;
   }

   public static boolean allows(DeathAnimationConfig.Animation animation) {
      return animation != null && (PUBLIC_IDS == null || animation == DeathAnimationConfig.Animation.NONE || PUBLIC_IDS.contains(animation.name()));
   }

   public static List<DeathAnimationConfig.Animation> animations() {
      return AVAILABLE;
   }
}
