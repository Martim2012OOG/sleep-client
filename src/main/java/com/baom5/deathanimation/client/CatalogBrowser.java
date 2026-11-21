package com.baom5.deathanimation.client;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CatalogBrowser {
   public static final List<String> CATEGORIES = List.of(
      "Anime & Manga",
      "Games",
      "Shows",
      "Cartoons",
      "Movies",
      "Horror & Internet Horror",
      "YouTube & Internet Culture",
      "Mythology",
      "Comics",
      "Originals",
      "Sports",
      "Miscellaneous"
   );
   private static final Map<DeathAnimationConfig.Animation, CatalogBrowser.Entry> ENTRIES = load();

   private CatalogBrowser() {
   }

   private static Map<DeathAnimationConfig.Animation, CatalogBrowser.Entry> load() {
      EnumMap<DeathAnimationConfig.Animation, CatalogBrowser.Entry> entries = new EnumMap<>(DeathAnimationConfig.Animation.class);

      try (InputStream stream = CatalogBrowser.class.getResourceAsStream("/assets/deathanimation/animations/metadata.json")) {
         if (stream == null) {
            throw new IllegalStateException("Missing catalog metadata");
         }

         for (CatalogBrowser.Entry entry : (CatalogBrowser.Entry[])new Gson()
            .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), CatalogBrowser.Entry[].class)) {
            if (entry.categories != null) {
               entry = new CatalogBrowser.Entry(
                  entry.id,
                  entry.family,
                  entry.identity,
                  entry.source,
                  entry.categories.stream().map(CatalogBrowser::category).distinct().toList(),
                  entry.aliases,
                  entry.releaseOrder,
                  entry.reviewStatus
               );
            }

            DeathAnimationConfig.Animation animation = DeathAnimationConfig.Animation.valueOf(entry.id);
            if (!ReleaseProfile.allows(animation)) {
               throw new IllegalStateException("Excluded metadata packaged: " + entry.id);
            }

            if (animation == DeathAnimationConfig.Animation.NONE
               || entry.source == null
               || entry.categories == null
               || entry.aliases == null
               || !CATEGORIES.containsAll(entry.categories)
               || entries.put(animation, entry) != null) {
               throw new IllegalStateException("Invalid catalog metadata: " + entry.id);
            }
         }

         if (entries.size() != ReleaseProfile.animations().size() - 1) {
            throw new IllegalStateException("Incomplete catalog metadata");
         }
      } catch (Exception exception) {
         throw new IllegalStateException("Could not load browsing metadata", exception);
      }

      return Collections.unmodifiableMap(entries);
   }

   public static CatalogBrowser.Entry entry(DeathAnimationConfig.Animation animation) {
      return ENTRIES.get(animation);
   }

   private static String category(String legacy) {
      return switch (legacy) {
         case "Anime", "Manga" -> "Anime & Manga";
         case "Horror", "Internet Horror" -> "Horror & Internet Horror";
         case "YouTube" -> "YouTube & Internet Culture";
         default -> legacy;
      };
   }

   public static List<String> randomPool(List<DeathAnimationConfig.Animation> animations) {
      return animations.stream().filter(a -> a != DeathAnimationConfig.Animation.NONE && ReleaseProfile.allows(a)).map(Enum::name).distinct().toList();
   }

   public static List<String> sources() {
      return ENTRIES.values().stream().map(CatalogBrowser.Entry::source).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
   }

   public static List<DeathAnimationConfig.Animation> matching(
      String search,
      Set<String> categories,
      String source,
      boolean favouritesOnly,
      boolean enabledOnly,
      CatalogBrowser.Sort sort,
      Set<String> favourites,
      Set<String> enabled
   ) {
      String[] words = normalize(search).split("\\s+");
      String sourceNeedle = normalize(source);
      Set<String> chosenCategories = categories.stream().map(CatalogBrowser::category).collect(Collectors.toSet());
      ArrayList<DeathAnimationConfig.Animation> results = new ArrayList<>();

      for (DeathAnimationConfig.Animation animation : ReleaseProfile.animations()) {
         CatalogBrowser.Entry entry = ENTRIES.get(animation);
         if ((!favouritesOnly || favourites.contains(animation.name()))
            && (!enabledOnly || enabled.contains(animation.name()))
            && (chosenCategories.isEmpty() || entry != null && !Collections.disjoint(chosenCategories, entry.categories))
            && (sourceNeedle.isEmpty() || entry != null && normalize(entry.source).contains(sourceNeedle))) {
            String haystack = normalize(
               animation.label
                  + " "
                  + animation.name()
                  + " "
                  + (
                     entry == null
                        ? "none no animation"
                        : entry.identity
                           + " "
                           + entry.family
                           + " "
                           + entry.source
                           + " "
                           + String.join(" ", entry.categories)
                           + " "
                           + String.join(" ", entry.aliases)
                  )
            );
            if (Arrays.stream(words).allMatch(haystack::contains)) {
               results.add(animation);
            }
         }
      }

      if (sort == CatalogBrowser.Sort.NAME) {
         results.sort(Comparator.comparing(a -> a.label, String.CASE_INSENSITIVE_ORDER));
      }

      if (sort == CatalogBrowser.Sort.NEWEST) {
         results.sort(
            Comparator.<DeathAnimationConfig.Animation>comparingInt(a -> ENTRIES.containsKey(a) ? ENTRIES.get(a).releaseOrder : -1)
               .reversed()
               .thenComparingInt(Enum::ordinal)
         );
      }

      return List.copyOf(results);
   }

   private static String normalize(String text) {
      return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
   }

   @Environment(EnvType.CLIENT)
   public record Entry(
      String id, String family, String identity, String source, List<String> categories, List<String> aliases, int releaseOrder, String reviewStatus
   ) {
   }

   @Environment(EnvType.CLIENT)
   public enum Sort {
      CATALOG,
      NAME,
      NEWEST;
   }
}
