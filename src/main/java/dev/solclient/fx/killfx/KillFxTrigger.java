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
 * 2. the server says that entity died (entity status 3, sent to every client at the moment of the death), or its health
 *    reaches 0 while you were the last to hit it;
 * 3. the equipped effect plays where it died.
 */
public final class KillFxTrigger {

	/** Your hit must be the killing one: the death arrives right after it (a kill by someone else comes later). */
	private static final long MINE_MS = 1500L;

	private static final class Hit {
		Entity e;
		long at;
		double x, y, z;
		String name;
		Identifier skin;
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
		if (!(e instanceof net.minecraft.entity.player.PlayerEntity l) || e == MinecraftClient.getInstance().player || equipped() == null
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
		if (h == null || System.currentTimeMillis() - h.at > MINE_MS || byOther(e)) {
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
			if (e == null || now - h.at > MINE_MS || e.isRemoved()) {
				it.remove();
				continue;
			}
			if (e instanceof LivingEntity l && (l.isDead() || l.getHealth() <= 0F)) {
				it.remove();
				if (!byOther(l)) {
					fire(h, "health 0");
				}
				continue;
			}
			h.x = e.getX();
			h.y = e.getY();
			h.z = e.getZ();
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
