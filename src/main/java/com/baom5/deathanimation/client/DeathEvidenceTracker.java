package com.baom5.deathanimation.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class DeathEvidenceTracker<S> {
   static final long RETENTION = 100L;
   static final long CORROBORATION = 60L;
   static final long MATCH_WINDOW = 30L;
   static final int MAX_PLAYERS = 512;
   private final Map<UUID, DeathEvidenceTracker.Track<S>> tracks = new LinkedHashMap<>();
   private final Consumer<DeathEvidenceTracker.Decision<S>> decisions;
   private UUID winner;
   private long wonAt = -10000L;
   private long roundAt = -10000L;
   private DeathEvidenceTracker.Life<S> claimedWin;

   static DeathEvidenceTracker.Notice notice(String text) {
      String clean = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
      if (clean.equals("you died")) {
         return DeathEvidenceTracker.Notice.DIED;
      } else if (clean.matches("round won(?: [0-9]+ [0-9]+)?")) {
         return DeathEvidenceTracker.Notice.WON;
      } else if (clean.equals("fight") || clean.equals("round started")) {
         return DeathEvidenceTracker.Notice.START;
      } else {
         return clean.matches(
               "(?:match|round) (?:draw|cancelled|canceled|timeout|forfeited)(?: .*)?|opponent (?:disconnected|forfeited)(?: .*)?|round won by forfeit|time ran out"
            )
            ? DeathEvidenceTracker.Notice.ABORT
            : DeathEvidenceTracker.Notice.NONE;
      }
   }

   DeathEvidenceTracker(Consumer<DeathEvidenceTracker.Decision<S>> decisions) {
      this.decisions = decisions;
   }

   void clear() {
      this.tracks.clear();
      this.winner = null;
      this.wonAt = -10000L;
      this.roundAt = -10000L;
      this.claimedWin = null;
   }

   void forget(UUID id) {
      this.tracks.remove(id);
   }

   int size() {
      return this.tracks.size();
   }

   private DeathEvidenceTracker.Life<S> current(UUID id) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      return track == null ? null : track.lives.peekLast();
   }

   private DeathEvidenceTracker.Life<S> incarnation(UUID id, Object token) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      if (track == null) {
         return null;
      }

      Iterator<DeathEvidenceTracker.Life<S>> lives = track.lives.descendingIterator();

      while (lives.hasNext()) {
         DeathEvidenceTracker.Life<S> life = lives.next();
         if (life.incarnation == token) {
            return life;
         }
      }

      return null;
   }

   void observe(UUID id, Object incarnation, boolean alive, boolean teleported, S sample, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.computeIfAbsent(id, ignored -> new DeathEvidenceTracker.Track<>());
      DeathEvidenceTracker.Life<S> life = track.lives.peekLast();
      if (life == null || life.incarnation != incarnation || alive && life.spectatorAt < 0L && (life.deadObserved || life.emitted && teleported)) {
         if (life != null && life.incarnation == incarnation && life.spectating) {
            life.seen = tick;
            life.present = true;
            return;
         }

         if (life != null) {
            life.present = false;
            life.removed = tick;
         }

         life = new DeathEvidenceTracker.Life<>(incarnation, sample, tick);
         track.lives.addLast(life);

         while (track.lives.size() > 4) {
            track.lives.removeFirst();
         }
      }

      if (life.spectating) {
         life.seen = tick;
         life.present = true;
         if (!alive && !life.deadObserved && life.sawAlive) {
            this.queue(id, life, DeathEvidenceTracker.Signal.ENTITY_DEAD, tick);
         }

         life.deadObserved = !alive;
      } else {
         if (teleported) {
            life.beforeTeleport = life.sample;
            life.teleported = tick;
         }

         life.sample = sample;
         life.seen = tick;
         life.present = true;
         if (alive && tick > life.born) {
            life.sawAlive = true;
         } else if (!alive && !life.deadObserved && life.sawAlive) {
            this.queue(id, life, DeathEvidenceTracker.Signal.ENTITY_DEAD, tick);
         }

         life.deadObserved = !alive;
         if (teleported) {
            this.fallback(id, life, tick);
         }

         if (this.tracks.size() > 512) {
            Iterator<Entry<UUID, DeathEvidenceTracker.Track<S>>> iterator = this.tracks.entrySet().iterator();
            iterator.next();
            iterator.remove();
         }
      }
   }

   void spectator(UUID id, Object token, boolean entering, boolean fromPlayingMode, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(id, token);
      if (life != null && life.spectating != entering) {
         life.spectating = entering;
         if (entering) {
            life.beforeSpectator = null;
            life.beforeSpectator = this.deathSample(life, DeathEvidenceTracker.Signal.CORROBORATED_MESSAGE, tick);
            if (fromPlayingMode && life.sawAlive) {
               life.spectatorAt = tick;
            } else {
               life.sawAlive = false;
            }

            if (!this.fallback(id, life, tick) && life.pending == null && !life.emitted) {
               this.reject(id, DeathEvidenceTracker.Signal.SPECTATOR_TRANSITION, "spectator_requires_independent_death_evidence", life.beforeSpectator);
            }
         }

         if (!entering && life.emitted) {
            life.deadObserved = false;
         }
      }
   }

   String context(UUID id, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      return life == null
         ? "no_tracked_life"
         : "damageAge="
            + (tick - life.damage)
            + ",spectatorAge="
            + (tick - life.spectatorAt)
            + ",removedAge="
            + (tick - life.removed)
            + ",playerInfoRemovedAge="
            + (tick - life.playerInfoRemoved)
            + ",respawnAge="
            + (tick - life.respawn)
            + ",teleportAge="
            + (tick - life.teleported)
            + ",announcementAge="
            + (tick - life.announcementAt)
            + ",announcedKiller="
            + life.announcedKiller
            + ",lastDamager="
            + life.lastDamager
            + ",damagerAge="
            + (tick - life.damagerAt)
            + ",killerReported="
            + life.killerReported
            + ",reportedKiller="
            + life.reportedKiller
            + ",totemAge="
            + (tick - life.totem)
            + ",messageAge="
            + (tick - life.message)
            + ",combatant="
            + life.combatant
            + ",combatAge="
            + (tick - life.combatAt)
            + ",localTitleAge="
            + (tick - life.localDeathAt)
            + ",namedResultAge="
            + (tick - life.namedResultAt)
            + ",winAge="
            + (tick - this.wonAt)
            + ",spectating="
            + life.spectating
            + ",snapshotPreserved="
            + (life.beforeSpectator != null)
            + ",pending="
            + life.pending
            + ",emitted="
            + life.emitted;
   }

   void combat(UUID victim, Object token, UUID other, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(victim, token);
      if (life != null && other != null && !victim.equals(other)) {
         life.combatant = other;
         life.combatAt = tick;
      }
   }

   void damageSource(UUID victim, Object token, UUID attacker, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(victim, token);
      if (life != null) {
         life.lastDamager = attacker;
         life.damagerAt = tick;
         if (attacker != null) {
            this.combat(victim, token, attacker, tick);
         }
      }
   }

   void notice(UUID local, DeathEvidenceTracker.Notice notice, long tick) {
      if (notice != DeathEvidenceTracker.Notice.START && notice != DeathEvidenceTracker.Notice.ABORT) {
         if (notice == DeathEvidenceTracker.Notice.DIED) {
            this.winner = null;
            this.wonAt = -10000L;
            this.claimedWin = null;
            DeathEvidenceTracker.Track<S> track = this.tracks.get(local);
            if (track == null) {
               return;
            }

            Iterator<DeathEvidenceTracker.Life<S>> lives = track.lives.descendingIterator();

            while (lives.hasNext()) {
               DeathEvidenceTracker.Life<S> life = lives.next();
               if (life.sawAlive && tick - life.seen <= 100L && tick - life.damage <= 30L) {
                  life.localDeathAt = tick;
                  return;
               }
            }

            DeathEvidenceTracker.Life<S> life = track.lives.peekLast();
            if (life != null) {
               life.localDeathAt = tick;
            }
         } else if (notice == DeathEvidenceTracker.Notice.WON
            && (this.winner == null || !this.winner.equals(local) || tick - this.wonAt > 60L || this.newWinCandidate(local, tick))) {
            this.winner = local;
            this.wonAt = tick;
            this.claimedWin = null;
         }
      } else {
         this.roundAt = tick;
         this.winner = null;
         this.wonAt = -10000L;
         this.claimedWin = null;

         for (DeathEvidenceTracker.Track<S> track : this.tracks.values()) {
            for (DeathEvidenceTracker.Life<S> life : track.lives) {
               if (life.reportedByMatch) {
                  life.killerReported = false;
                  life.reportedKiller = null;
                  life.reportedKillerAt = -10000L;
                  life.reportedByMatch = false;
               }

               life.localDeathAt = -10000L;
               life.namedResultAt = -10000L;
               if (notice == DeathEvidenceTracker.Notice.ABORT && isMatch(life.pending)) {
                  this.cancel(life);
               }
            }
         }
      }
   }

   private boolean newWinCandidate(UUID local, long tick) {
      if (this.claimedWin == null) {
         return false;
      }

      for (DeathEvidenceTracker.Track<S> track : this.tracks.values()) {
         for (DeathEvidenceTracker.Life<S> life : track.lives) {
            if (life != this.claimedWin
               && !life.emitted
               && life.damage > this.wonAt
               && local.equals(life.combatant)
               && this.matchDamage(life, tick)
               && this.matchTransition(life, tick, false)) {
               return true;
            }
         }
      }

      return false;
   }

   void summary(UUID local, UUID winner, UUID loser, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(loser);
      if (track != null && !winner.equals(loser)) {
         Iterator<DeathEvidenceTracker.Life<S>> lives = track.lives.descendingIterator();
         DeathEvidenceTracker.Life<S> chosen = null;

         while (lives.hasNext()) {
            DeathEvidenceTracker.Life<S> life = lives.next();
            if (life.sawAlive && tick - life.seen <= 100L && tick - life.damage <= 30L) {
               chosen = life;
               break;
            }
         }

         if (chosen != null) {
            chosen.namedResultAt = tick;
            chosen.namedLocal = loser.equals(local);
            this.reportKiller(chosen, winner, tick);
            chosen.reportedByMatch = true;
            if (winner.equals(this.winner)) {
               this.claimedWin = chosen;
            }
         }
      }
   }

   void disconnected(UUID id, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      if (track != null) {
         for (DeathEvidenceTracker.Life<S> life : track.lives) {
            life.playerInfoRemoved = tick;
         }
      }
   }

   void serverDeathNotice(UUID victim, UUID killer, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(victim);
      if (track == null) {
         this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "no_recent_player_snapshot", null);
      } else if (killer != null && !victim.equals(killer)) {
         DeathEvidenceTracker.Life<S> chosen = null;
         Iterator<DeathEvidenceTracker.Life<S>> lives = track.lives.descendingIterator();

         while (lives.hasNext()) {
            DeathEvidenceTracker.Life<S> life = lives.next();
            if (life.sawAlive && tick - life.seen <= 100L) {
               if (chosen == null) {
                  chosen = life;
               }

               if (tick - life.damage <= 30L) {
                  chosen = life;
                  break;
               }
            }
         }

         if (chosen == null) {
            this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "no_recent_playing_life", this.current(victim).sample);
         } else if (!chosen.emitted && chosen.pending == null) {
            if (chosen.announcementAt >= 0L && tick - chosen.announcementAt <= 30L) {
               if (!killer.equals(chosen.announcedKiller)) {
                  chosen.announcementAt = -10000L;
                  chosen.announcedKiller = null;
                  this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "conflicting_named_killers", chosen.sample);
               }
            } else {
               chosen.announcementAt = tick;
               chosen.announcedKiller = killer;
               this.correlateNotice(victim, chosen, tick);
            }
         } else {
            this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "duplicate_death_notice", chosen.sample);
         }
      } else {
         this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "unresolved_or_self_killer", this.current(victim).sample);
      }
   }

   private String noticeMissing(DeathEvidenceTracker.Life<S> life, long tick) {
      if (life.totem < 0L || life.totem < life.damage && life.totem < life.announcementAt) {
         if (!life.sawAlive || tick - life.seen > 100L) {
            return "missing_recent_playing_snapshot";
         } else if (Math.abs(life.announcementAt - life.damage) > 30L || tick - life.damage > 30L) {
            return "missing_recent_damage";
         } else if (Objects.equals(life.announcedKiller, life.combatant) && Math.abs(life.combatAt - life.damage) <= 30L) {
            long transition = Math.max(life.playerInfoRemoved, Math.max(life.removed, Math.max(life.respawn, Math.max(life.teleported, life.spectatorAt))));
            return Math.abs(transition - life.announcementAt) <= 30L && tick - transition <= 30L ? null : "missing_death_transition";
         } else {
            return "missing_or_mismatched_killer_attribution";
         }
      } else {
         return "totem_survival";
      }
   }

   private void correlateNotice(UUID victim, DeathEvidenceTracker.Life<S> life, long tick) {
      if (life.announcementAt >= 0L && !life.emitted && life.pending == null) {
         String missing = this.noticeMissing(life, tick);
         if (tick - life.announcementAt > 30L) {
            life.announcementAt = -10000L;
            life.announcedKiller = null;
            this.reject(victim, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, "notice_expired_" + missing, life.sample);
         } else if (missing == null) {
            this.queue(victim, life, DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE, tick);
         }
      }
   }

   private static boolean isMatch(DeathEvidenceTracker.Signal signal) {
      return signal == DeathEvidenceTracker.Signal.MATCH_LOCAL_DEATH || signal == DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH;
   }

   private void cancel(DeathEvidenceTracker.Life<S> life) {
      life.pending = null;
      life.pendingSample = null;
      life.pendingKiller = null;
   }

   private boolean matchDamage(DeathEvidenceTracker.Life<S> life, long tick) {
      return life.sawAlive && tick - life.seen <= 100L && life.damage >= this.roundAt && tick - life.damage <= 30L && life.totem < life.damage;
   }

   private boolean matchTransition(DeathEvidenceTracker.Life<S> life, long tick, boolean local) {
      long transition = local ? life.spectatorAt : Math.max(life.spectatorAt, Math.max(life.playerInfoRemoved, Math.max(life.removed, life.teleported)));
      return tick - transition <= 30L && Math.abs(transition - life.damage) <= 30L;
   }

   private void matchResults(long tick) {
      for (Entry<UUID, DeathEvidenceTracker.Track<S>> entry : this.tracks.entrySet()) {
         for (DeathEvidenceTracker.Life<S> life : entry.getValue().lives) {
            if (!life.emitted && life.pending == null && this.matchDamage(life, tick)) {
               if (tick - life.localDeathAt <= 30L && life.localDeathAt > life.totem && this.matchTransition(life, tick, true)) {
                  this.queue(entry.getKey(), life, DeathEvidenceTracker.Signal.MATCH_LOCAL_DEATH, tick);
               } else if (tick - life.namedResultAt <= 30L && life.namedResultAt > life.totem && this.matchTransition(life, tick, life.namedLocal)) {
                  this.queue(
                     entry.getKey(),
                     life,
                     life.namedLocal ? DeathEvidenceTracker.Signal.MATCH_LOCAL_DEATH : DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH,
                     tick
                  );
               }
            }
         }
      }

      if (this.winner != null && tick - this.wonAt <= 30L && this.newWinCandidate(this.winner, tick)) {
         this.wonAt = tick;
         this.claimedWin = null;
      }

      if (this.winner != null
         && tick - this.wonAt <= 30L
         && (
            this.claimedWin == null
               || this.claimedWin.pending == DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH && tick - this.claimedWin.namedResultAt > 30L
         )) {
         DeathEvidenceTracker.Life<S> found = null;
         UUID victim = null;

         for (Entry<UUID, DeathEvidenceTracker.Track<S>> entry : this.tracks.entrySet()) {
            if (!entry.getKey().equals(this.winner)) {
               DeathEvidenceTracker.Life<S> candidate = null;

               for (DeathEvidenceTracker.Life<S> life : entry.getValue().lives) {
                  if (this.winner.equals(life.combatant)
                     && tick - life.combatAt <= 30L
                     && this.matchDamage(life, tick)
                     && this.wonAt > life.totem
                     && this.matchTransition(life, tick, false)) {
                     candidate = life;
                  }
               }

               if (candidate != null) {
                  if (found != null) {
                     if (this.claimedWin != null && this.claimedWin.pending == DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH) {
                        this.cancel(this.claimedWin);
                     }

                     this.claimedWin = found;
                     this.reject(entry.getKey(), DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH, "ambiguous_round_opponent", candidate.sample);
                     return;
                  }

                  found = candidate;
                  victim = entry.getKey();
               }
            }
         }

         if (found != null) {
            this.claimedWin = found;
            if (found.pending == null && !found.emitted) {
               this.queue(victim, found, DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH, tick);
            }
         }
      }
   }

   void damage(UUID id, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      if (life != null) {
         this.damage(id, life, tick);
      }
   }

   void damage(UUID id, Object token, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(id, token);
      if (life != null) {
         this.damage(id, life, tick);
      }
   }

   private void damage(UUID id, DeathEvidenceTracker.Life<S> life, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      if (life == this.current(id) && life.present && life.emitted && !life.deadObserved && !life.spectating && tick - track.confirmed >= 10L) {
         life.present = false;
         life.removed = track.confirmed;
         DeathEvidenceTracker.Life<S> next = new DeathEvidenceTracker.Life<>(life.incarnation, life.sample, tick);
         next.sawAlive = life.sawAlive;
         track.lives.addLast(next);

         while (track.lives.size() > 4) {
            track.lives.removeFirst();
         }

         life = next;
      }

      life.damage = tick;
      life.damageSample = life.sample;
      this.fallback(id, life, tick);
   }

   void signal(UUID id, DeathEvidenceTracker.Signal signal, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      if (life == null) {
         this.reject(id, signal, "no_recent_player_snapshot", null);
      } else {
         this.queue(id, life, signal, tick);
      }
   }

   void signal(UUID id, Object token, DeathEvidenceTracker.Signal signal, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(id, token);
      if (life == null) {
         this.reject(id, signal, "no_matching_recent_life", null);
      } else if (signal != DeathEvidenceTracker.Signal.ENTITY_DEAD || life.sawAlive) {
         this.queue(id, life, signal, tick);
      }
   }

   void removed(UUID id, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      if (life != null) {
         this.removed(id, life, tick);
      }
   }

   void removed(UUID id, Object token, long tick) {
      DeathEvidenceTracker.Life<S> life = this.incarnation(id, token);
      if (life != null) {
         this.removed(id, life, tick);
      }
   }

   private void removed(UUID id, DeathEvidenceTracker.Life<S> life, long tick) {
      life.present = false;
      life.removed = tick;
      if (!this.fallback(id, life, tick) && life.pending == null && !life.emitted) {
         this.reject(id, DeathEvidenceTracker.Signal.ENTITY_REMOVED, "removal_is_not_death", life.sample);
      }
   }

   void respawn(UUID id, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      if (life != null) {
         life.respawn = tick;
         life.present = false;
         if (!this.fallback(id, life, tick) && life.pending == null && !life.emitted) {
            this.reject(id, DeathEvidenceTracker.Signal.RESPAWN_TRANSITION, "respawn_or_dimension_transition_is_not_death", life.sample);
         }
      }
   }

   void totem(UUID id, long tick) {
      DeathEvidenceTracker.Life<S> life = this.current(id);
      if (life != null) {
         this.totem(id, life.incarnation, tick);
      }
   }

   void totem(UUID id, Object token, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      if (track != null) {
         for (DeathEvidenceTracker.Life<S> life : track.lives) {
            if (life.incarnation == token && tick - life.seen <= 100L) {
               life.totem = tick;
               life.localDeathAt = -10000L;
               life.namedResultAt = -10000L;
               life.announcementAt = -10000L;
               life.announcedKiller = null;
               life.lastDamager = null;
               life.damagerAt = -10000L;
               life.reportedKiller = null;
               life.reportedKillerAt = -10000L;
               life.killerReported = false;
               life.reportedByMatch = false;
               life.pendingKiller = null;
               if (life.pending != null && life.pending != DeathEvidenceTracker.Signal.COMBAT_KILL) {
                  this.reject(id, life.pending, "totem_survival", life.pendingSample);
                  life.pending = null;
                  life.pendingSample = null;
               }
            }
         }

         DeathEvidenceTracker.Life<S> life = track.lives.peekLast();
         if (life.incarnation == token) {
            life.deadObserved = true;
         }
      }
   }

   void message(UUID id, boolean structured, long tick) {
      this.message(id, structured, null, false, tick);
   }

   void message(UUID id, boolean structured, UUID killer, long tick) {
      this.message(id, structured, killer, true, tick);
   }

   private void reportKiller(DeathEvidenceTracker.Life<S> life, UUID killer, long tick) {
      life.killerReported = true;
      life.reportedKiller = killer;
      life.reportedKillerAt = tick;
      life.reportedByMatch = false;
   }

   private void message(UUID id, boolean structured, UUID killer, boolean reported, long tick) {
      DeathEvidenceTracker.Track<S> track = this.tracks.get(id);
      DeathEvidenceTracker.Signal signal = structured ? DeathEvidenceTracker.Signal.DEATH_MESSAGE : DeathEvidenceTracker.Signal.CORROBORATED_MESSAGE;
      if (track == null) {
         this.reject(id, signal, "no_recent_player_snapshot", null);
      } else {
         for (DeathEvidenceTracker.Life<S> old : track.lives) {
            if (old.pending != null && this.current(id).damage <= old.pendingAt) {
               if (reported) {
                  this.reportKiller(old, killer, tick);
               }

               this.reject(id, signal, "duplicate_pending_death_message", old.pendingSample);
               return;
            }
         }

         Iterator<DeathEvidenceTracker.Life<S>> candidates = track.lives.descendingIterator();

         while (candidates.hasNext()) {
            DeathEvidenceTracker.Life<S> life = candidates.next();
            if (tick - life.seen <= 100L && life.sawAlive) {
               if ((life.emitted || life.pending != null) && tick - track.confirmed <= 60L) {
                  this.reject(id, signal, "duplicate_death_message", life.sample);
                  return;
               }

               if (tick - track.confirmed > 60L || life.damage > track.confirmed) {
                  if (structured) {
                     DeathEvidenceTracker.Life<S> previous = null;

                     for (DeathEvidenceTracker.Life<S> old : track.lives) {
                        if (old != life && old.sawAlive && tick - old.seen <= 60L && !old.present && life.damage <= old.removed) {
                           previous = old;
                        }
                     }

                     if (previous != null) {
                        if (previous.emitted || previous.pending != null) {
                           this.reject(id, signal, "duplicate_message_for_replaced_life", previous.sample);
                           return;
                        }

                        life = previous;
                     }

                     if (reported) {
                        this.reportKiller(life, killer, tick);
                     }

                     this.queue(id, life, signal, tick);
                     return;
                  }

                  life.message = tick;
                  if (this.fallback(id, life, tick)) {
                     return;
                  }
               }
            }
         }

         this.reject(id, signal, "unconfirmed_message_or_old_life", this.current(id).sample);
      }
   }

   private boolean fallback(UUID id, DeathEvidenceTracker.Life<S> life, long tick) {
      if (tick - life.message <= 60L
         && tick - life.damage <= 60L
         && life.sawAlive
         && tick - Math.max(Math.max(Math.max(life.removed, life.respawn), life.teleported), life.spectatorAt) <= 60L
         && life.totem < life.damage) {
         this.queue(id, life, DeathEvidenceTracker.Signal.CORROBORATED_MESSAGE, tick);
         return true;
      } else {
         return false;
      }
   }

   private S deathSample(DeathEvidenceTracker.Life<S> life, DeathEvidenceTracker.Signal signal, long tick) {
      if (isMatch(signal)) {
         return life.beforeSpectator != null ? life.beforeSpectator : (life.damageSample != null ? life.damageSample : life.sample);
      }

      if (life.beforeSpectator != null && life.spectating) {
         return life.beforeSpectator;
      }

      if (tick - life.teleported <= 60L) {
         if (life.damageSample != null
            && life.damage <= life.teleported
            && tick - life.damage <= 60L
            && (
               signal == DeathEvidenceTracker.Signal.CORROBORATED_MESSAGE
                  || signal == DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE
                  || tick - life.teleported <= 2L
            )) {
            return life.damageSample;
         }

         if (life.beforeTeleport != null && tick - life.teleported <= 1L && life.damage < life.teleported) {
            return life.beforeTeleport;
         }
      }

      return life.sample;
   }

   private void queue(UUID id, DeathEvidenceTracker.Life<S> life, DeathEvidenceTracker.Signal signal, long tick) {
      if (!life.emitted && life.pending == null) {
         if (tick - life.seen > 100L) {
            this.reject(id, signal, "expired_snapshot", life.sample);
         } else {
            life.pending = signal;
            life.pendingAt = tick;
            life.pendingSample = this.deathSample(life, signal, tick);
            life.pendingKiller = null;
            if (isMatch(signal)) {
               life.pendingKiller = life.killerReported
                  ? life.reportedKiller
                  : (signal == DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH ? this.winner : null);
            }
         }
      } else {
         if (life.pending != null && (signal == DeathEvidenceTracker.Signal.COMBAT_KILL || isMatch(life.pending) && !isMatch(signal))) {
            life.pending = signal;
         }

         this.reject(id, signal, "duplicate_signal", life.sample);
      }
   }

   private UUID killer(DeathEvidenceTracker.Life<S> life, long tick) {
      if (life.killerReported && !life.reportedByMatch && tick - life.reportedKillerAt <= 60L) {
         return life.reportedKiller;
      } else if (life.announcedKiller != null && tick - life.announcementAt <= 30L) {
         return life.announcedKiller;
      } else if (life.pendingKiller != null) {
         return life.pendingKiller;
      } else {
         return tick - life.damagerAt <= 100L && Math.abs(life.damagerAt - life.damage) <= 2L ? life.lastDamager : null;
      }
   }

   void advance(long tick) {
      for (Entry<UUID, DeathEvidenceTracker.Track<S>> entry : this.tracks.entrySet()) {
         for (DeathEvidenceTracker.Life<S> life : entry.getValue().lives) {
            this.correlateNotice(entry.getKey(), life, tick);
         }
      }

      this.matchResults(tick);
      Iterator<Entry<UUID, DeathEvidenceTracker.Track<S>>> iterator = this.tracks.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, DeathEvidenceTracker.Track<S>> entry = iterator.next();
         DeathEvidenceTracker.Track<S> track = entry.getValue();

         for (DeathEvidenceTracker.Life<S> life : track.lives) {
            if (life.pending != null
               && life.pendingAt < tick
               && (!isMatch(life.pending) && life.pending != DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE || tick - life.pendingAt >= 2L)) {
               DeathEvidenceTracker.Signal signal = life.pending;
               S sample = life.pendingSample;
               UUID killer = this.killer(life, tick);
               life.pending = null;
               life.pendingSample = null;
               life.pendingKiller = null;
               if (!life.emitted) {
                  life.emitted = true;
                  track.confirmed = tick;
                  String reason = signal == DeathEvidenceTracker.Signal.MATCH_LOCAL_DEATH
                     ? "local_result_damage_spectator_confirmed_once"
                     : (
                        signal == DeathEvidenceTracker.Signal.MATCH_OPPONENT_DEATH
                           ? "opponent_result_damage_transition_confirmed_once"
                           : (
                              signal == DeathEvidenceTracker.Signal.SERVER_DEATH_NOTICE
                                 ? "server_named_kill_damage_attribution_transition_confirmed_once"
                                 : "confirmed_once_for_life"
                           )
                     );
                  this.decisions.accept(new DeathEvidenceTracker.Decision<>(entry.getKey(), signal, true, reason, sample, killer));
               }
            }
         }

         track.lives.removeIf(lifex -> !lifex.present && tick - lifex.seen > 100L && lifex.pending == null);
         if (track.lives.isEmpty()) {
            iterator.remove();
         }
      }
   }

   private void reject(UUID id, DeathEvidenceTracker.Signal signal, String reason, S snapshot) {
      this.decisions.accept(new DeathEvidenceTracker.Decision<>(id, signal, false, reason, snapshot, null));
   }

   @Environment(EnvType.CLIENT)
   record Decision<S>(UUID player, DeathEvidenceTracker.Signal signal, boolean accepted, String reason, S snapshot, UUID killer) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Life<S> {
      Object incarnation;
      S sample;
      S damageSample;
      S beforeTeleport;
      S pendingSample;
      S beforeSpectator;
      final long born;
      long seen;
      long damage = -10000L;
      long removed = -10000L;
      long playerInfoRemoved = -10000L;
      long respawn = -10000L;
      long teleported = -10000L;
      long message = -10000L;
      long totem = -10000L;
      long pendingAt;
      long spectatorAt = -10000L;
      boolean present = true;
      boolean sawAlive;
      boolean deadObserved;
      boolean emitted;
      boolean spectating;
      DeathEvidenceTracker.Signal pending;
      UUID combatant;
      UUID announcedKiller;
      UUID lastDamager;
      UUID reportedKiller;
      UUID pendingKiller;
      long damagerAt = -10000L;
      long reportedKillerAt = -10000L;
      boolean killerReported;
      boolean reportedByMatch;
      long announcementAt = -10000L;
      long combatAt = -10000L;
      long localDeathAt = -10000L;
      long namedResultAt = -10000L;
      boolean namedLocal;

      Life(Object incarnation, S sample, long tick) {
         this.incarnation = incarnation;
         this.sample = sample;
         this.seen = this.born = tick;
      }
   }

   @Environment(EnvType.CLIENT)
   enum Notice {
      NONE,
      DIED,
      WON,
      START,
      ABORT;
   }

   @Environment(EnvType.CLIENT)
   enum Signal {
      ENTITY_DEAD,
      HEALTH_ZERO,
      DEATH_STATUS,
      COMBAT_KILL,
      DEATH_MESSAGE,
      CORROBORATED_MESSAGE,
      SERVER_DEATH_NOTICE,
      ENTITY_REMOVED,
      RESPAWN_TRANSITION,
      SPECTATOR_TRANSITION,
      MATCH_LOCAL_DEATH,
      MATCH_OPPONENT_DEATH;
   }

   @Environment(EnvType.CLIENT)
   private static final class Track<S> {
      final Deque<DeathEvidenceTracker.Life<S>> lives = new ArrayDeque<>();
      long confirmed = -4611686018427387904L;
   }
}
