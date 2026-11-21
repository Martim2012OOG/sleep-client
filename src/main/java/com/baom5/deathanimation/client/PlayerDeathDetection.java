package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.Unload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.HoverEvent.EntityContent;
import net.minecraft.text.HoverEvent.ShowEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlayerDeathDetection {
   private static final Logger LOG = LoggerFactory.getLogger("deathanimation/death-detection");
   private static final Map<UUID, PlayerDeathDetection.Observed> PLAYERS = new LinkedHashMap<>();
   private static final Map<Integer, PlayerDeathDetection.EntityIdentity> ENTITY_IDS = new HashMap<>();
   private static final DeathEvidenceTracker<PlayerDeathSnapshot> EVIDENCE = new DeathEvidenceTracker<>(PlayerDeathDetection::decision);
   private static ClientWorld level;
   private static Object connection;
   private static long tick;
   private static UUID attackTarget;
   private static Vec3d blastIntent;
   private static long intentAt = -10000L;
   private static final Pattern SUMMARY = Pattern.compile(
      "(?is)^\\W*Match Results:\\s*\\W*Winner:\\s*([A-Za-z0-9_]{1,16})\\s*\\W*Loser:\\s*([A-Za-z0-9_]{1,16})(?=\\W|$).*$"
   );

   private PlayerDeathDetection() {
   }

   static void register() {
      ClientEntityEvents.ENTITY_LOAD.register((Load)(entity, world) -> observe(entity));
      ClientEntityEvents.ENTITY_UNLOAD.register((Unload)(entity, world) -> removed(entity));
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> reset());
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, target, hit) -> {
         if (player == MinecraftClient.getInstance().player && context()) {
            if (target instanceof EndCrystalEntity) {
               localBlast(target.getEntityPos());
            } else if (target instanceof AbstractClientPlayerEntity) {
               attackTarget = target.getUuid();
               blastIntent = null;
               intentAt = tick;
            }
         }

         return ActionResult.PASS;
      });
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> {
         if (player == MinecraftClient.getInstance().player && context()) {
            BlockState block = world.getBlockState(hit.getBlockPos());
            if (block.isOf(Blocks.RESPAWN_ANCHOR) || block.getBlock() instanceof BedBlock) {
               localBlast(Vec3d.ofCenter(hit.getBlockPos()));
            }
         }

         return ActionResult.PASS;
      });
   }

   private static void reset() {
      EVIDENCE.clear();
      PLAYERS.clear();
      ENTITY_IDS.clear();
      level = null;
      connection = null;
      tick = 0L;
      attackTarget = null;
      blastIntent = null;
      intentAt = -10000L;
   }

   static void localBlast(Vec3d position) {
      if (context()) {
         attackTarget = null;
         blastIntent = position;
         intentAt = tick;
      }
   }

   private static boolean context() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.world != null) {
         if (level != client.world || connection != client.getNetworkHandler()) {
            reset();
            level = client.world;
            connection = client.getNetworkHandler();
         }

         return true;
      } else {
         return false;
      }
   }

   static void tick(ClientWorld world) {
      if (world == null) {
         reset();
      } else if (context()) {
         tick++;

         for (AbstractClientPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (!(player instanceof DeathAvatar)) {
               if (!player.isDead() && !player.isSpectator() && player instanceof AbstractClientPlayerEntity source) {
                  MotionHistory.record(source);
               }

               observe(player);
            }
         }

         for (Entry<UUID, PlayerDeathDetection.Observed> entry : PLAYERS.entrySet()) {
            PlayerDeathDetection.Observed p = entry.getValue();
            if (p.present && p.seen < tick) {
               p.present = false;
               EVIDENCE.removed(entry.getKey(), tick);
            }
         }

         EVIDENCE.advance(tick);
         ENTITY_IDS.entrySet().removeIf(e -> tick - e.getValue().seen > 100L);
         PLAYERS.entrySet().removeIf(e -> !e.getValue().present && tick - e.getValue().seen > 100L);
      }
   }

   public static void observe(Entity entity) {
      if (entity instanceof AbstractClientPlayerEntity player && !(player instanceof DeathAvatar) && context() && player.getEntityWorld() == level) {
         if (!PLAYERS.containsKey(player.getUuid()) && PLAYERS.size() >= 512) {
            ClientPlayerEntity local = MinecraftClient.getInstance().player;
            Entry<UUID, PlayerDeathDetection.Observed> oldest = PLAYERS.entrySet()
               .stream()
               .filter(e -> local == null || !e.getKey().equals(local.getUuid()))
               .min(
                  Comparator.<Entry<UUID, PlayerDeathDetection.Observed>>comparingInt(e -> e.getValue().present ? 1 : 0)
                     .thenComparingLong(e -> e.getValue().seen)
               )
               .orElseThrow();
            PLAYERS.remove(oldest.getKey());
            EVIDENCE.forget(oldest.getKey());
         }

         PlayerDeathDetection.Observed state = PLAYERS.computeIfAbsent(player.getUuid(), ignored -> new PlayerDeathDetection.Observed(player));
         float oldHealth = state.source == player ? state.lastHealth : 0.0F / 0.0F;
         PlayerDeathSnapshot previous = state.snapshot;
         if (state.source != player) {
            previous = null;
            state.beforeSpectator = null;
            state.mode = null;
            MotionHistory.forget(player.getUuid());
         }

         PlayerListEntry info = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(player.getUuid());
         GameMode actual = info == null ? null : info.getGameMode();
         if (player == MinecraftClient.getInstance().player && MinecraftClient.getInstance().interactionManager != null) {
            actual = MinecraftClient.getInstance().interactionManager.getCurrentGameMode();
         }

         if (state.mode == null) {
            state.mode = actual;
         }

         PlayerDeathSnapshot snapshot = state.mode == GameMode.SPECTATOR && state.beforeSpectator != null
            ? state.beforeSpectator
            : PlayerDeathSnapshot.capture(player, previous);
         if (PlayerDeathSnapshot.finite(snapshot.position())) {
            boolean teleported = previous != null && previous.position().squaredDistanceTo(snapshot.position()) > 256.0;
            state.source = player;
            state.snapshot = snapshot;
            state.seen = tick;
            state.present = true;
            EVIDENCE.observe(player.getUuid(), player, !player.isDead(), teleported, snapshot, tick);
            state.lastHealth = player.getHealth();
            if (Float.isFinite(oldHealth) && state.lastHealth < oldHealth) {
               EVIDENCE.damage(player.getUuid(), player, tick);
               correlateIntent(player.getUuid(), player);
            }

            if (previous == null && state.mode == GameMode.SPECTATOR) {
               state.beforeSpectator = snapshot;
               EVIDENCE.spectator(player.getUuid(), player, true, false, tick);
            }

            PlayerDeathDetection.EntityIdentity oldId = ENTITY_IDS.get(player.getId());
            long ambiguousUntil = oldId == null ? 0L : oldId.ambiguousUntil;
            if (oldId != null && !oldId.uuid.equals(player.getUuid())) {
               ambiguousUntil = Math.max(ambiguousUntil, oldId.seen + 100L);
            }

            ENTITY_IDS.put(player.getId(), new PlayerDeathDetection.EntityIdentity(player.getUuid(), player, tick, ambiguousUntil));
            if (ENTITY_IDS.size() > 2048) {
               Entry<Integer, PlayerDeathDetection.EntityIdentity> oldest = ENTITY_IDS.entrySet()
                  .stream()
                  .min(Comparator.comparingLong(e -> e.getValue().seen))
                  .orElseThrow();
               ENTITY_IDS.remove(oldest.getKey());
            }
         }
      }
   }

   public static void gameMode(UUID uuid, GameMode mode) {
      if (mode != null && context()) {
         if (level.getPlayerByUuid(uuid) instanceof AbstractClientPlayerEntity player) {
            observe(player);
         }

         PlayerDeathDetection.Observed state = PLAYERS.get(uuid);
         if (state != null && state.mode != mode) {
            GameMode previous = state.mode;
            state.previousMode = previous;
            if (mode == GameMode.SPECTATOR) {
               state.beforeSpectator = state.snapshot;
            }

            state.mode = mode;
            EVIDENCE.spectator(uuid, state.source, mode == GameMode.SPECTATOR, previous == GameMode.SURVIVAL || previous == GameMode.ADVENTURE, tick);
            if (mode != GameMode.SPECTATOR) {
               state.beforeSpectator = null;
            }

            if (debug()) {
               LOG.info(
                  "gamemode uuid={} name={} previous={} new={} tick={} lastObservedHealth={} snapshotPreserved={} evidence={}",
                  new Object[]{
                     uuid,
                     state.snapshot == null ? "unknown" : state.snapshot.name(),
                     previous,
                     mode,
                     tick,
                     state.snapshot == null ? "unknown" : state.snapshot.observedHealth(),
                     state.beforeSpectator != null,
                     EVIDENCE.context(uuid, tick)
                  }
               );
            }
         }
      }
   }

   public static void localGameMode(GameMode mode) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         gameMode(player.getUuid(), mode);
      }
   }

   private static boolean debug() {
      return DeathAnimationConfig.INSTANCE.deathDetectionDebug || Boolean.getBoolean("deathanimation.deathDebug");
   }

   public static void renderReached(DeathScene scene) {
      if (!scene.gui && !scene.preview && !scene.playbackRenderObserved) {
         scene.playbackRenderObserved = true;
         if (debug()) {
            LOG.info("worldRenderReached=true uuid={} avatar={} animation={}", new Object[]{scene.sourceUuid, scene.avatar.getId(), scene.animation});
         }
      }
   }

   private static PlayerDeathDetection.EntityIdentity resolve(int entityId) {
      if (!context()) {
         return null;
      }

      Entity entity = level.getEntityById(entityId);
      if (entity != null) {
         if (entity instanceof AbstractClientPlayerEntity player && !(player instanceof DeathAvatar)) {
            observe(player);
            return ENTITY_IDS.get(entityId);
         } else {
            return null;
         }
      } else {
         PlayerDeathDetection.EntityIdentity identity = ENTITY_IDS.get(entityId);
         return identity != null && tick - identity.seen <= 100L && tick >= identity.ambiguousUntil ? identity : null;
      }
   }

   public static void damaged(int entityId) {
      PlayerDeathDetection.EntityIdentity id = resolve(entityId);
      if (id != null) {
         EVIDENCE.damage(id.uuid, id.incarnation, tick);
         correlateIntent(id.uuid, id.incarnation);
         packet(id.uuid, "hurt_animation");
      }
   }

   public static void damaged(EntityDamageS2CPacket packet) {
      PlayerDeathDetection.EntityIdentity id = resolve(packet.entityId());
      if (id != null) {
         EVIDENCE.damage(id.uuid, id.incarnation, tick);
         Entity source = packet.createDamageSource(level).getAttacker();
         if (!(source instanceof AbstractClientPlayerEntity)) {
            source = level.getEntityById(packet.sourceCauseId());
         }

         if (source instanceof AbstractClientPlayerEntity attacker) {
            EVIDENCE.damageSource(id.uuid, id.incarnation, attacker.getUuid(), tick);
            PlayerDeathDetection.Observed target = PLAYERS.get(attacker.getUuid());
            if (target != null) {
               EVIDENCE.combat(attacker.getUuid(), target.source, id.uuid, tick);
            }
         } else {
            EVIDENCE.damageSource(id.uuid, id.incarnation, null, tick);
            correlateIntent(id.uuid, id.incarnation);
         }

         packet(id.uuid, "damage_event_" + packet.sourceType());
      }
   }

   private static void correlateIntent(UUID victim, Object token) {
      ClientPlayerEntity local = MinecraftClient.getInstance().player;
      PlayerDeathDetection.Observed state = PLAYERS.get(victim);
      if (local != null && !local.getUuid().equals(victim) && state != null && tick - intentAt <= 20L) {
         if (victim.equals(attackTarget) || blastIntent != null && blastIntent.squaredDistanceTo(state.snapshot.position()) <= 144.0) {
            EVIDENCE.combat(victim, token, local.getUuid(), tick);
         }
      }
   }

   public static void status(int entityId, byte status) {
      if (status == 3 || status == 35) {
         PlayerDeathDetection.EntityIdentity id = resolve(entityId);
         if (id != null) {
            packet(id.uuid, "entity_status_" + status);
            if (status == 35) {
               EVIDENCE.totem(id.uuid, id.incarnation, tick);
            } else {
               EVIDENCE.signal(id.uuid, id.incarnation, DeathEvidenceTracker.Signal.DEATH_STATUS, tick);
            }
         }
      }
   }

   public static void health(float health) {
      if (context()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         observe(player);
         if (player != null) {
            packet(player.getUuid(), "health_" + health);
         }

         if (player != null && Float.isFinite(health) && health < player.getHealth()) {
            EVIDENCE.damage(player.getUuid(), player, tick);
         }

         if (player != null && Float.isFinite(health) && health <= 0.0F) {
            EVIDENCE.signal(player.getUuid(), DeathEvidenceTracker.Signal.HEALTH_ZERO, tick);
         }
      }
   }

   public static void combatKill(int playerId) {
      if (context()) {
         PlayerDeathDetection.EntityIdentity id = resolve(playerId);
         if (id != null) {
            packet(id.uuid, "combat_kill");
            EVIDENCE.signal(id.uuid, id.incarnation, DeathEvidenceTracker.Signal.COMBAT_KILL, tick);
         }
      }
   }

   private static void packet(UUID id, String kind) {
      if (debug()) {
         LOG.info("packet={} uuid={} tick={} evidence={}", new Object[]{kind, id, tick, EVIDENCE.context(id, tick)});
      }
   }

   public static void removed(Entity entity) {
      if (entity instanceof AbstractClientPlayerEntity player && !(player instanceof DeathAvatar) && context() && player.getEntityWorld() == level) {
         PlayerDeathDetection.Observed state = PLAYERS.get(player.getUuid());
         if (state == null || state.source == player) {
            observe(player);
         } else if (player.isDead()) {
            EVIDENCE.signal(player.getUuid(), player, DeathEvidenceTracker.Signal.ENTITY_DEAD, tick);
         }

         EVIDENCE.removed(player.getUuid(), player, tick);
         state = PLAYERS.get(player.getUuid());
         if (state != null && state.source == player) {
            state.present = false;
         }
      }
   }

   public static void respawn(boolean sameDimension) {
      if (sameDimension && context()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         observe(player);
         if (player != null) {
            EVIDENCE.respawn(player.getUuid(), tick);
         }
      }
   }

   public static void systemMessage(Text message) {
      if (context()) {
         matchText(message, "system_chat");
         if (message.getContent() instanceof TranslatableTextContent translated && translated.getKey().startsWith("death.") && translated.getArgs().length > 0) {
            Object victim = translated.getArgs()[0];
            if (victim instanceof Text component) {
               HashSet<EntityContent> identities = new HashSet<>();
               collectIdentity(component, identities, 0);
               if (!identities.isEmpty()) {
                  EntityContent identity = identities.iterator().next();
                  if (identities.size() == 1 && identity.entityType == EntityType.PLAYER) {
                     EVIDENCE.message(identity.uuid, true, translatedKiller(translated), tick);
                  }

                  return;
               }
            }

            String name = victim instanceof Text component ? component.getString() : (victim instanceof String s ? s : null);
            if (name != null) {
               namedMessage(name, true, translatedKiller(translated));
            }
         } else {
            String original = message.getString();
            ServerDeathMessages.Parsed parsed = ServerDeathMessages.parse(original, ServerDeathMessages.Channel.SERVER_SYSTEM);
            ServerDeathMessages.Match match = parsed.match();
            UUID victim = match == null ? null : knownName(match.victim());
            UUID killer = match != null && match.killer() != null ? knownName(match.killer()) : null;
            String result = match == null
               ? parsed.reason()
               : (
                  victim == null
                     ? "unresolved_or_ambiguous_victim"
                     : (match.killer() != null && killer == null ? "unresolved_or_ambiguous_killer" : "correlating")
               );
            if (debug()) {
               LOG.info(
                  "deathNoticeInput channel=system_chat original={} normalized={} matched={} format={} victim={} killer={} victimUuid={} killerUuid={} result={} tick={}",
                  new Object[]{
                     logText(original),
                     logText(parsed.normalized()),
                     match != null,
                     match == null ? null : match.format(),
                     match == null ? null : match.victim(),
                     match == null ? null : match.killer(),
                     victim,
                     killer,
                     result,
                     tick
                  }
               );
            }

            if (result.equals("correlating")) {
               if (match.killer() != null) {
                  EVIDENCE.serverDeathNotice(victim, killer, tick);
               } else {
                  EVIDENCE.message(victim, false, tick);
               }

               if (debug()) {
                  LOG.info(
                     "deathNoticeCorrelating uuid={} killer={} evidence={} requirements=recent_damage,matching_killer_if_named,death_transition",
                     new Object[]{victim, killer, EVIDENCE.context(victim, tick)}
                  );
               }
            }
         }
      }
   }

   private static String logText(String text) {
      String clean = text.replace("\r", "\\r").replace("\n", "\\n");
      return clean.substring(0, Math.min(1024, clean.length()));
   }

   public static void matchText(Text message, String channel) {
      if (context()) {
         ClientPlayerEntity local = MinecraftClient.getInstance().player;
         if (local != null) {
            String text = message.getString().replaceAll("§[0-9a-fk-orA-FK-OR]", "").replace("\\n", "\n").trim();
            DeathEvidenceTracker.Notice notice = DeathEvidenceTracker.notice(text);
            Matcher summary = SUMMARY.matcher(text);
            if (debug() && (!channel.equals("system_chat") || notice != DeathEvidenceTracker.Notice.NONE || summary.matches())) {
               LOG.info(
                  "resultInput channel={} notice={} text={} tick={}",
                  new Object[]{channel, notice, text.replaceAll("\\s+", " ").substring(0, Math.min(240, text.replaceAll("\\s+", " ").length())), tick}
               );
            }

            if (summary.matches()) {
               UUID won = knownName(summary.group(1));
               UUID lost = knownName(summary.group(2));
               if (won != null && lost != null && !won.equals(lost)) {
                  EVIDENCE.summary(local.getUuid(), won, lost, tick);
                  if (debug()) {
                     LOG.info("victimIdentified channel={} winner={} victim={} evidence={}", new Object[]{channel, won, lost, EVIDENCE.context(lost, tick)});
                  }
               } else if (debug()) {
                  LOG.info("resultRejected channel={} reason=unresolved_or_ambiguous_summary_victim", channel);
               }
            }

            if (notice != DeathEvidenceTracker.Notice.NONE) {
               if (notice == DeathEvidenceTracker.Notice.START || notice == DeathEvidenceTracker.Notice.ABORT) {
                  attackTarget = null;
                  blastIntent = null;
                  intentAt = -10000L;
               }

               EVIDENCE.notice(local.getUuid(), notice, tick);
               if (debug()) {
                  LOG.info(
                     "resultCorrelating channel={} local={} notice={} requirements=recent_damage,victim_identity,transition windowTicks={} evidence={}",
                     new Object[]{channel, local.getUuid(), notice, 30L, EVIDENCE.context(local.getUuid(), tick)}
                  );
               }
            }
         }
      }
   }

   public static void playerInfoRemoved(List<UUID> players) {
      if (context()) {
         for (UUID id : players) {
            EVIDENCE.disconnected(id, tick);
            packet(id, "player_info_removed");
         }
      }
   }

   private static UUID knownName(String name) {
      ArrayList<ServerDeathMessages.Identity> identities = new ArrayList<>();

      for (Entry<UUID, PlayerDeathDetection.Observed> entry : PLAYERS.entrySet()) {
         PlayerDeathSnapshot snapshot = entry.getValue().snapshot;
         if (snapshot != null && tick - entry.getValue().seen <= 100L) {
            identities.add(new ServerDeathMessages.Identity(entry.getKey(), snapshot.profile().name()));
         }
      }

      ClientPlayNetworkHandler clientConnection = MinecraftClient.getInstance().getNetworkHandler();
      if (clientConnection != null) {
         for (PlayerListEntry info : clientConnection.getPlayerList()) {
            identities.add(new ServerDeathMessages.Identity(info.getProfile().id(), info.getProfile().name()));
         }
      }

      return ServerDeathMessages.resolve(name, identities);
   }

   private static void collectIdentity(Text component, Set<EntityContent> identities, int depth) {
      if (depth <= 16 && identities.size() <= 1) {
         if (component.getStyle().getHoverEvent() instanceof ShowEntity entity) {
            identities.add(entity.entity());
         }

         for (Text sibling : component.getSiblings()) {
            collectIdentity(sibling, identities, depth + 1);
         }
      }
   }

   private static UUID translatedKiller(TranslatableTextContent message) {
      if (message.getArgs().length < 2) {
         return null;
      }

      Object killer = message.getArgs()[1];
      if (killer instanceof Text component) {
         HashSet<EntityContent> identities = new HashSet<>();
         collectIdentity(component, identities, 0);
         if (identities.isEmpty()) {
            return knownName(component.getString());
         }

         EntityContent identity = identities.iterator().next();
         return identities.size() == 1 && identity.entityType == EntityType.PLAYER ? identity.uuid : null;
      } else {
         return killer instanceof String name ? knownName(name) : null;
      }
   }

   private static void namedMessage(String name, boolean structured, UUID killer) {
      UUID found = null;

      for (Entry<UUID, PlayerDeathDetection.Observed> entry : PLAYERS.entrySet()) {
         PlayerDeathSnapshot snapshot = entry.getValue().snapshot;
         if (snapshot != null && tick - entry.getValue().seen <= 100L) {
            boolean matches = snapshot.profile().name().equalsIgnoreCase(name) || snapshot.name().equalsIgnoreCase(name);
            if (!matches && structured) {
               matches = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(snapshot.profile().name()) + "(?![A-Za-z0-9_])", 2).matcher(name).find();
            }

            if (matches) {
               if (found != null) {
                  return;
               }

               found = entry.getKey();
            }
         }
      }

      if (found != null) {
         EVIDENCE.message(found, structured, killer, tick);
      }
   }

   private static void decision(DeathEvidenceTracker.Decision<PlayerDeathSnapshot> result) {
      DeathAnimationConfig config = DeathAnimationConfig.INSTANCE;
      PlayerDeathSnapshot snapshot = result.snapshot();
      PlayerDeathDetection.Observed state = PLAYERS.get(result.player());
      if (debug()) {
         LOG.info(
            "decision={} signal={} uuid={} name={} position={} lastObservedHealth={} snapshotHealth={} previousMode={} newMode={} evidence={} reason={}",
            new Object[]{
               result.accepted() && config.enabled ? "accepted" : "rejected",
               result.signal(),
               result.player(),
               snapshot == null ? "unknown" : snapshot.name(),
               snapshot == null ? "unknown" : snapshot.position(),
               state == null ? (snapshot == null ? "unknown" : snapshot.observedHealth()) : state.source.getHealth(),
               snapshot == null ? "unknown" : snapshot.observedHealth(),
               state == null ? null : state.previousMode,
               state == null ? null : state.mode,
               EVIDENCE.context(result.player(), tick),
               result.accepted() && !config.enabled ? "mod_disabled" : result.reason()
            }
         );
      }

      if (result.accepted()) {
         if (config.enabled && snapshot != null && level == MinecraftClient.getInstance().world) {
            ClientPlayerEntity local = MinecraftClient.getInstance().player;
            DeathVisibility visibility = DeathVisibility.orDefault(config.deathVisibility);
            if (!visibility.includes(result.player(), result.killer(), local == null ? null : local.getUuid())) {
               if (debug()) {
                  LOG.info(
                     "animationStarted=false uuid={} killer={} visibility={} reason=death_visibility_filter",
                     new Object[]{result.player(), result.killer(), visibility}
                  );
               }
            } else {
               try {
                  if (debug()) {
                     LOG.info(
                        "snapshotSelected uuid={} killer={} visibility={} position={} skin={} layers={} equipment={} animationRequested=true",
                        new Object[]{
                           result.player(),
                           result.killer(),
                           visibility,
                           snapshot.position(),
                           snapshot.skin().profile().id(),
                           snapshot.layers(),
                           snapshot.equipment().keySet()
                        }
                     );
                  }

                  boolean started = DeathEffectController.playDetectedDeath(level, snapshot.materialize(level), snapshot.position());
                  if (debug()) {
                     LOG.info(
                        "animationStarted={} uuid={} position={} victimSnapshot=true reason={}",
                        new Object[]{started, result.player(), snapshot.position(), started ? "scene_created" : "no_enabled_animation"}
                     );
                  }
               } catch (RuntimeException error) {
                  LOG.error("animationStarted=false uuid={} reason=scene_creation_failed", result.player(), error);
               }
            }
         } else {
            if (debug()) {
               LOG.info(
                  "animationStarted=false uuid={} reason={}",
                  result.player(),
                  !config.enabled ? "mod_disabled" : (snapshot == null ? "missing_snapshot" : "world_changed")
               );
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record EntityIdentity(UUID uuid, Object incarnation, long seen, long ambiguousUntil) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Observed {
      AbstractClientPlayerEntity source;
      PlayerDeathSnapshot snapshot;
      GameMode mode;
      GameMode previousMode;
      PlayerDeathSnapshot beforeSpectator;
      long seen;
      float lastHealth = 0.0F / 0.0F;
      boolean present = true;

      Observed(AbstractClientPlayerEntity source) {
         this.source = source;
      }
   }
}
