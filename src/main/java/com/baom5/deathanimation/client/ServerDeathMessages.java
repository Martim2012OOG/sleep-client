package com.baom5.deathanimation.client;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Collection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class ServerDeathMessages {
   private static final String NAME = "([A-Za-z0-9_]{1,16})";
   private static final String PREFIX = "(?:(?:\\[(?:death|kill|combat|pvp|server)\\])\\s*)?";
   private static final String SUFFIX = "(?:\\s*\\([0-9., /+-]{1,32}\\))?(?:\\s+using \\[[^\\]\\r\\n]{1,128}\\])?[.!]?";
   private static final Pattern BY = Pattern.compile(
      "^(?:(?:\\[(?:death|kill|combat|pvp|server)\\])\\s*)?([A-Za-z0-9_]{1,16}) (?:was )?(?:defeated|eliminated|slain|shot|killed|blown up|impaled|pummeled|squashed|burnt|burned|poked|stung|skewered|finished off) by ([A-Za-z0-9_]{1,16})(?:\\s*\\([0-9., /+-]{1,32}\\))?(?:\\s+using \\[[^\\]\\r\\n]{1,128}\\])?[.!]?$",
      2
   );
   private static final Pattern ACTIVE = Pattern.compile(
      "^(?:(?:\\[(?:death|kill|combat|pvp|server)\\])\\s*)?([A-Za-z0-9_]{1,16}) (?:killed|defeated|eliminated) ([A-Za-z0-9_]{1,16})(?:\\s*\\([0-9., /+-]{1,32}\\))?(?:\\s+using \\[[^\\]\\r\\n]{1,128}\\])?[.!]?$",
      2
   );
   private static final Pattern ENVIRONMENT = Pattern.compile(
      "^(?:(?:\\[(?:death|kill|combat|pvp|server)\\])\\s*)?([A-Za-z0-9_]{1,16}) (?:died|fell|hit the ground|blew up|burned to death|drowned|tried to swim in lava|went up in flames|starved to death|withered away|walked into|experienced kinetic energy)(?:$|[ .!].*)",
      2
   );

   static ServerDeathMessages.Parsed parse(String original, ServerDeathMessages.Channel channel) {
      if (channel != ServerDeathMessages.Channel.SERVER_SYSTEM) {
         return new ServerDeathMessages.Parsed("", null, "player_chat_is_not_server_evidence");
      }

      if (original != null && original.length() <= 4096) {
         String clean = Normalizer.normalize(original, Form.NFKC)
            .replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
            .replaceAll("[\\p{Cf}\\uFE0E\\uFE0F]", "")
            .replaceAll("[\\p{So}\\p{Sk}]", " ")
            .trim();
         if (clean.indexOf(10) < 0 && clean.indexOf(13) < 0) {
            clean = clean.replaceAll("\\s+", " ").trim();
            Matcher by = BY.matcher(clean);
            if (by.matches()) {
               return new ServerDeathMessages.Parsed(clean, new ServerDeathMessages.Match("victim_by_killer", by.group(1), by.group(2)), "matched");
            }

            Matcher active = ACTIVE.matcher(clean);
            if (active.matches()) {
               return new ServerDeathMessages.Parsed(clean, new ServerDeathMessages.Match("killer_verb_victim", active.group(2), active.group(1)), "matched");
            }

            Matcher environment = ENVIRONMENT.matcher(clean);
            return environment.matches()
               ? new ServerDeathMessages.Parsed(clean, new ServerDeathMessages.Match("victim_environment", environment.group(1), null), "matched")
               : new ServerDeathMessages.Parsed(clean, null, "no_supported_anchored_format");
         } else {
            return new ServerDeathMessages.Parsed(clean, null, "multiline_not_a_death_notice");
         }
      } else {
         return new ServerDeathMessages.Parsed("", null, "invalid_message_length");
      }
   }

   static UUID resolve(String name, Collection<ServerDeathMessages.Identity> identities) {
      if (name == null) {
         return null;
      }

      UUID found = null;

      for (ServerDeathMessages.Identity identity : identities) {
         if (name.equalsIgnoreCase(identity.name())) {
            if (found != null && !found.equals(identity.uuid())) {
               return null;
            }

            found = identity.uuid();
         }
      }

      return found;
   }

   @Environment(EnvType.CLIENT)
   enum Channel {
      SERVER_SYSTEM,
      PLAYER_CHAT;
   }

   @Environment(EnvType.CLIENT)
   record Identity(UUID uuid, String name) {
   }

   @Environment(EnvType.CLIENT)
   record Match(String format, String victim, String killer) {
   }

   @Environment(EnvType.CLIENT)
   record Parsed(String normalized, ServerDeathMessages.Match match, String reason) {
   }
}
