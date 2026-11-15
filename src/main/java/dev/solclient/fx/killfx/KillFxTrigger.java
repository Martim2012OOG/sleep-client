package dev.solclient.fx.killfx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Plays the kill effect equipped in the Locker when YOU kill someone. Works on its own (no extension needed):
 * 1. you hit a PLAYER (never mobs; remembered for 1.5 s, with its skin and place);
 * 2. any of the ways servers show a death happens right after: entity status 3, health 0, the player leaves the world
 *    but stays online (instant respawn), a huge teleport, turning into a spectator, or a kill / death message with the
 *    name. Many servers hide the first two, so one sign is enough only if you were the last to hit that player;
 * 3. the equipped effect plays where it died.
 */
public final class KillFxTrigger {

	/** Your hit must be the killing one: the death arrives right after it (a kill by someone else comes later). */
	private static final long MINE_MS = 1500L;
	/** Chat and titles can arrive a little later than the death itself. */
	private static final long TEXT_MS = 3000L;
	private static final java.util.regex.Pattern KILL_WORDS = java.util.regex.Pattern.compile(
			"kill|slain|slew|eliminat|shot|blown|pummel|finish|murder|destroy|defeat|morto|matou|mataste|abatid|\\bwon\\b|fell");

	private static final class Hit {
		Entity e;
		long at, removedAt;
		double x, y, z;
		String name;
		java.util.UUID uuid;
		Identifier skin;
		float lastHealth;
		/** Someone else's hit landed after yours. */
		boolean other;
	}

	private static final Map<Integer, Hit> HITS = new HashMap<>();

	private KillFxTrigger() {
	}

	/** The effect to play: the one equipped in the Locker / on the site (null = none, the Kill Effect mod decides). */
	public static String equipped() {
		try {
			String id = dev.solclient.social.Social.equippedKillEffect();
			return id != null && KillFxModel.get(id) != null ? id : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** You hit something. */
	public static void onAttack(Entity e) {
		// hitting an end crystal: whoever stands next to it can die from your explosion
		if (e instanceof net.minecraft.entity.decoration.EndCrystalEntity && MinecraftClient.getInstance().world != null) {
			for (var p : MinecraftClient.getInstance().world.getPlayers()) {
				if (p != MinecraftClient.getInstance().player && p.squaredDistanceTo(e) < 64) {
					onAttack(p);
				}
			}
			return;
		}
		if (!(e instanceof net.minecraft.entity.player.PlayerEntity l) || e == MinecraftClient.getInstance().player || equipped() == null && !dev.solclient.mods.KillSoundsMod.on()
				|| l.isDead() || l.getHealth() <= 0F || recentlyKilled(e.getId())) {
			return; // hitting the body while it falls must not count again
		}
		Hit h = HITS.computeIfAbsent(e.getId(), k -> new Hit());
		if (h.e == null) {
			System.out.println("[Sleep] kill effect: watching " + e.getName().getString());
		}
		h.e = e;
		h.at = System.currentTimeMillis();
		h.x = e.getX();
		h.y = e.getY();
		h.z = e.getZ();
		h.name = e.getName().getString();
		h.uuid = e.getUuid();
		h.lastHealth = l.getHealth();
		h.other = false;
		h.removedAt = 0;
		if (e instanceof net.minecraft.client.network.AbstractClientPlayerEntity p) {
			try {
				h.skin = p.getSkin().body().texturePath();
			} catch (RuntimeException ex) {
				h.skin = null;
			}
		}
	}

	/** The server's death signal (status 3) for this entity. */
	public static void onStatus(LivingEntity e, byte status) {
		if (status != 3) {
			return;
		}
		Hit h = HITS.get(e.getId());
		if (h == null || System.currentTimeMillis() - h.at > MINE_MS || h.other || byOther(e)) {
			return;
		}
		HITS.remove(e.getId());
		h.x = e.getX();
		h.y = e.getY();
		h.z = e.getZ();
		fire(h, "death signal");
	}

	/** Every client tick: health 0 as a backup, and old hits forgotten. */
	public static void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) {
			HITS.clear();
			return;
		}
		if (HITS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Hit> it = HITS.values().iterator();
		while (it.hasNext()) {
			Hit h = it.next();
			Entity e = h.e;
			if (e == null || now - h.at > TEXT_MS) {
				it.remove();
				continue;
			}
			if (e.isRemoved() && h.removedAt == 0 && now - h.at <= MINE_MS && mc.player.squaredDistanceTo(h.x, h.y, h.z) < 48 * 48) {
				h.removedAt = now; // gone from the world next to you: a kill only if they're still online (not if they logged off)
			}
			if (h.removedAt > 0) {
				if (now - h.removedAt >= 200L) {
					it.remove();
					if (!h.other && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(h.uuid) != null) {
						fire(h, "left the world");
					}
				}
				continue;
			}
			if (e.isRemoved() || now - h.at > MINE_MS) {
				continue; // only a chat / title message can still confirm it
			}
			if (e instanceof LivingEntity l && (l.isDead() || l.getHealth() <= 0F)) {
				it.remove();
				if (!h.other && !byOther(l)) {
					fire(h, "health 0");
				}
				continue;
			}
			// instant respawn: thrown far away, usually with the health back up
			if (e instanceof LivingEntity l) {
				double far = e.squaredDistanceTo(h.x, h.y, h.z);
				boolean healed = h.lastHealth <= 10F && l.getHealth() >= h.lastHealth + 8F;
				if (!h.other && (far > 64 * 64 || far > 24 * 24 && healed)) {
					it.remove();
					fire(h, "respawned");
					continue;
				}
				h.lastHealth = l.getHealth();
			}
			h.x = e.getX();
			h.y = e.getY();
			h.z = e.getZ();
		}
	}

	/** A hit the server confirmed: yours (also arrows and other things you shot) or someone else's. */
	public static void onDamage(int victimId, int causeId) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null || victimId == mc.player.getId()) {
			return;
		}
		if (causeId == mc.player.getId()) {
			Entity v = mc.world.getEntityById(victimId);
			if (v != null) {
				onAttack(v);
			}
			return;
		}
		Hit h = HITS.get(victimId);
		if (h != null && causeId >= 0 && mc.world.getEntityById(causeId) instanceof net.minecraft.entity.player.PlayerEntity) {
			h.other = true;
		}
	}

	/** A player turned into a spectator (many minigames do this instead of a real death). */
	public static void onSpectator(java.util.UUID id) {
		long now = System.currentTimeMillis();
		Iterator<Hit> it = HITS.values().iterator();
		while (it.hasNext()) {
			Hit h = it.next();
			if (id.equals(h.uuid) && now - h.at <= MINE_MS && !h.other) {
				it.remove();
				fire(h, "spectator");
				return;
			}
		}
	}

	private static boolean hasWord(String text, String word) {
		int i = text.indexOf(word);
		while (i >= 0) {
			boolean left = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) && text.charAt(i - 1) != '_';
			int end = i + word.length();
			boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end)) && text.charAt(end) != '_';
			if (left && right) {
				return true;
			}
			i = text.indexOf(word, i + 1);
		}
		return false;
	}

	/** Chat, title or action bar from the server: "X was slain by you", "You killed X", "X eliminated by Marzox_x"... */
	public static void onServerText(String raw) {
		if (HITS.isEmpty() || raw == null || raw.length() > 256) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		String t = raw.replaceAll("\u00A7.", "").toLowerCase(java.util.Locale.ROOT);
		if (!KILL_WORDS.matcher(t).find()) {
			return;
		}
		String me = mc.getSession().getUsername().toLowerCase(java.util.Locale.ROOT);
		if (t.contains(me + ":") || t.contains(me + " >") || t.contains(me + " »")) {
			return; // something you typed
		}
		boolean mine = hasWord(t, me) || hasWord(t, "you") || hasWord(t, "tu") || hasWord(t, "mataste");
		if (!mine) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Hit> it = HITS.values().iterator();
		while (it.hasNext()) {
			Hit h = it.next();
			if (h.name != null && now - h.at <= TEXT_MS && hasWord(t, h.name.toLowerCase(java.util.Locale.ROOT))) {
				it.remove();
				fire(h, "server message");
				return;
			}
		}
	}

	/** The client only knows who hit last when it was you: somebody else = not your kill. */
	private static boolean byOther(LivingEntity l) {
		LivingEntity by = l.getAttacker();
		MinecraftClient mc = MinecraftClient.getInstance();
		return by != null && by != mc.player;
	}

	/** Last time the Locker effect played (the Kill Effect mod then does not play a second one). */
	public static volatile long lastPlayed;

	/** entity id -> when its kill effect played: one effect per death, however many signals or clicks follow. */
	private static final Map<Integer, Long> KILLED = new HashMap<>();

	private static boolean recentlyKilled(int id) {
		Long t = KILLED.get(id);
		return t != null && System.currentTimeMillis() - t < 5000L;
	}

	private static void fire(Hit h, String why) {
		if (h.e != null) {
			if (recentlyKilled(h.e.getId())) {
				return;
			}
			long now = System.currentTimeMillis();
			KILLED.values().removeIf(t -> now - t > 10_000L);
			KILLED.put(h.e.getId(), now);
		}
		dev.solclient.mods.KillSoundsMod.onKill();
		String id = equipped();
		System.out.println("[Sleep] kill effect: " + h.name + " -> " + why + " -> " + id);
		if (id == null) {
			return;
		}
		Entity e = h.e;
		boolean stillDead = e instanceof LivingEntity l && (l.isDead() || l.getHealth() <= 0F) && !e.isRemoved();
		if (KillFx.play(id, h.x, h.y, h.z, h.skin, stillDead && h.skin != null ? e.getId() : -1)) {
			lastPlayed = System.currentTimeMillis();
		}
	}
}
