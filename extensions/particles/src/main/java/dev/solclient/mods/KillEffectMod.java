package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

/** An effect when something you hit dies. */
public class KillEffectMod extends Mod {

	public static KillEffectMod instance;
	/** The 3D ones (made in Blockbench, played by dev.solclient.fx.killfx.KillFx) are added after the old ones, so saved choices stay the same. */
	private static final String[] FX3D = dev.solclient.fx.killfx.KillFxModel.IDS;
	private final Option.Choice effect = choice("Effect", 0, "Lightning", "Blood", "Hearts", "Souls", "Firework", "Totem", "Pac-Man (3D)", "Shark (3D)",
			"Mimic Chest (3D)", "Giant Frog (3D)", "Flytrap (3D)", "UFO (3D)", "Black Hole (3D)", "Firework Rocket (3D)", "Whale (3D)", "Dragon (3D)",
			"Giant Worm (3D)", "Snake (3D)", "Crocodile (3D)", "T-Rex (3D)", "Toilet Flush (3D)", "Giant Hand (3D)", "Kraken (3D)", "Hungry Pig (3D)",
			"Ghost (3D)", "Eagle (3D)", "Piano Drop (3D)", "Tornado (3D)", "Surprise Gift (3D)", "Cannon (3D)", "Random 3D");
	private final Option.Action test = action("Test Effect", "Test", this::test, () -> status.isEmpty() ? effect.label() : effect.label() + " - " + status);
	private final Option.Bool sound = bool("Sound", true);
	private final Option.Bool playersOnly = bool("Players Only", false);
	/** Everyone you hit in the last seconds, with where they were and their skin (so the effect plays where they died). */
	private static final class Target {
		Entity e;
		long hitAt;
		double x, y, z;
		float height;
		boolean hidden;
		String name;
		net.minecraft.util.Identifier skin;
	}

	private final Map<Integer, Target> hit = new HashMap<>();
	private static boolean chatHooked;
	private String status = "";
	private int kills;

	public KillEffectMod() {
		super("Kill Effect", "kill_effect", "Show an effect when something you hit dies.", Category.VISUAL);
		instance = this;
	}

	@Override
	public void init() {
		super.init();
		if (chatHooked) {
			return;
		}
		chatHooked = true;
		// minigame servers often never let a player "die": the kill message in the chat is the surest sign
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			if (!overlay && instance != null && instance.enabled) {
				instance.onChat(msg.getString());
			}
		});
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, time) -> {
			if (instance != null && instance.enabled) {
				instance.onChat(msg.getString());
			}
		});
	}

	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || playersOnly.on() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) {
			return;
		}
		Target t = hit.computeIfAbsent(e.getId(), k -> new Target());
		t.e = e;
		t.hitAt = System.currentTimeMillis();
		t.x = e.getX();
		t.y = e.getY();
		t.z = e.getZ();
		t.height = e.getHeight();
		t.hidden = e.isInvisible() || e.isSpectator();
		t.name = e instanceof net.minecraft.entity.player.PlayerEntity p ? p.getName().getString() : null;
		if (e instanceof net.minecraft.client.network.AbstractClientPlayerEntity p) {
			try {
				t.skin = p.getSkin().body().texturePath();
			} catch (RuntimeException ex) {
				t.skin = null;
			}
		}
	}

	@Override
	public void tick() {
		if (mc.world == null || mc.player == null) {
			hit.clear();
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, Target>> it = hit.entrySet().iterator();
		while (it.hasNext()) {
			Target t = it.next().getValue();
			long since = now - t.hitAt;
			Entity e = t.e;
			Entity live = mc.world.getEntityById(e.getId());
			String why = null;
			if (live != e || e.isRemoved()) {
				// gone from the world right after your hit (servers often remove / hide a player who was killed)
				if (since < MINE_MS && mc.player.squaredDistanceTo(t.x, t.y, t.z) < 24 * 24) {
					why = "removed";
				} else {
					it.remove();
					continue;
				}
			} else if (e instanceof LivingEntity l && (l.isDead() || l.getHealth() <= 0F)) {
				if (since < MINE_MS && !hitByOther(l)) {
					why = "died";
				} else {
					it.remove(); // somebody else finished them
					continue;
				}
			} else if (since < MINE_MS && !t.hidden && (e.isSpectator() || e.isInvisible())) {
				why = "spectator";
			} else if (since < MINE_MS && e.squaredDistanceTo(t.x, t.y, t.z) > 10 * 10) {
				why = "respawned"; // teleported far away in one go: the minigame respawned them
			}
			if (why != null) {
				it.remove();
				kill(t);
				continue;
			}
			if (since > 5000) {
				it.remove();
				continue;
			}
			t.x = e.getX();
			t.y = e.getY();
			t.z = e.getZ();
		}
	}

	/** Your hit must be this recent for the kill to be yours (vanilla gives the kill to the last one who hit). */
	private static final long MINE_MS = 1500L;

	/**
	 * True when the last hit before they died came from someone else: the dying player's attacker is known to the client only
	 * when it is you, so a "recent damage" mark newer than your own hit means another player or something else killed them.
	 */
	private boolean hitByOther(LivingEntity l) {
		Target t = hit.get(l.getId());
		if (t == null) {
			return true;
		}
		net.minecraft.entity.LivingEntity by = l.getAttacker();
		return by != null && by != mc.player;
	}

	private static final String[] KILL_WORDS = {"kill", "slain", "elimin", "died", "death", "dead", "fell", "shot", "murder", "destroy", "final",
			"morto", "matou", "morreu", "eliminad", "abatid", "\u2620", "\u2694", "\u00bb", ">>", "by "};

	private void onChat(String text) {
		if (text == null || hit.isEmpty() || mc.player == null) {
			return;
		}
		String low = text.toLowerCase(java.util.Locale.ROOT);
		String me = mc.player.getName().getString().toLowerCase(java.util.Locale.ROOT);
		boolean word = false;
		for (String w : KILL_WORDS) {
			if (low.contains(w)) {
				word = true;
				break;
			}
		}
		long now = System.currentTimeMillis();
		final boolean killWord = word;
		mc.execute(() -> {
			Iterator<Map.Entry<Integer, Target>> it = hit.entrySet().iterator();
			while (it.hasNext()) {
				Target t = it.next().getValue();
				if (t.name == null || now - t.hitAt > 6000) {
					continue;
				}
				String n = t.name.toLowerCase(java.util.Locale.ROOT);
				int at = low.indexOf(n);
				if (at < 0 || low.startsWith(me + ":") || low.startsWith("<" + me + ">")) {
					continue; // not about them, or something you typed yourself
				}
				// only YOUR kills: the message must name them and you, with a kill word
				int mine = low.indexOf(me);
				if (mine >= 0 && mine < at) {
					String between = low.substring(mine + me.length(), at);
					if (between.contains(" by ") || between.contains(" por ") || between.contains("was ") || between.contains("foi ")) {
						continue; // "you were killed BY them": that is your death, not your kill
					}
				}
				if (mc.player.isDead() || mc.player.getHealth() <= 0F) {
					continue;
				}
				if (killWord && mine >= 0 && !low.startsWith(n + ":")) {
					it.remove();
					kill(t);
				}
			}
		});
	}

	private void kill(Target t) {
		kills++;
		Entity e = t.e;
		boolean stillDead = e instanceof LivingEntity l && (l.isDead() || l.getHealth() <= 0F) && !e.isRemoved();
		play(t.x, t.y, t.z, t.height, e, t.skin, stillDead ? e.getId() : -1);
	}

	/** Plays the chosen effect a few blocks in front of you. */
	private void test() {
		if (mc.player == null) {
			return;
		}
		net.minecraft.util.math.Vec3d look = mc.player.getRotationVec(1F);
		double l = Math.max(0.01, Math.hypot(look.x, look.z));
		double x = mc.player.getX() + look.x / l * 4, z = mc.player.getZ() + look.z / l * 4;
		if (effect.idx() >= 6) {
			// the stand-in wears your own skin, so you can see how it looks on a player
			net.minecraft.util.Identifier skin = null;
			try {
				skin = mc.player.getSkin().body().texturePath();
			} catch (RuntimeException ignored) {
			}
			String id = fx3d();
			if (dev.solclient.fx.killfx.KillFx.play(id, x, mc.player.getY(), z, skin, -1)) {
				status = "playing " + id;
				return;
			}
			status = "could not load '" + id + "': " + dev.solclient.fx.killfx.KillFxModel.lastError;
			mc.player.sendMessage(net.minecraft.text.Text.literal("Kill effect " + status), false);
			return;
		}
		play(x, mc.player.getY(), z, 1.8, null, null, -1);
		status = "played";
	}

	private String fx3d() {
		int i = effect.idx() - 6;
		return i < FX3D.length ? FX3D[i] : FX3D[java.util.concurrent.ThreadLocalRandom.current().nextInt(FX3D.length)];
	}

	/**
	 * The 3D effect to play on a kill: the one equipped in the shop's Locker wins; otherwise the one picked here, if you
	 * own it (when the site sells kill effects). null = not owned (a classic effect plays instead).
	 */
	private String ownedFx3d() {
		String eq = dev.solclient.social.Social.equippedKillEffect();
		if (eq != null && dev.solclient.fx.killfx.KillFxModel.get(eq) != null) {
			return eq;
		}
		if (effect.idx() < 6) {
			return null;
		}
		java.util.Set<String> own = dev.solclient.social.Social.ownedKillEffects();
		if (own == null) {
			return fx3d(); // the site does not sell them (yet): all free
		}
		int i = effect.idx() - 6;
		if (i >= FX3D.length) { // random among the ones you own
			java.util.List<String> mine = new java.util.ArrayList<>();
			for (String f : FX3D) {
				if (own.contains(f)) {
					mine.add(f);
				}
			}
			return mine.isEmpty() ? null : mine.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(mine.size()));
		}
		if (own.contains(FX3D[i])) {
			return FX3D[i];
		}
		long now = System.currentTimeMillis();
		if (mc.player != null && now - toldAt > 20_000L) {
			toldAt = now;
			mc.player.sendMessage(net.minecraft.text.Text.literal("Buy " + effect.label() + " in the Shop to use it on kills (Test always works)"), true);
		}
		return null;
	}

	private long toldAt;

	private void play(double x, double y, double z, double h, Entity victim, net.minecraft.util.Identifier skin, int hideId) {
		int idx = effect.idx();
		String fx = victim == null ? null : ownedFx3d();
		if (fx != null && dev.solclient.fx.killfx.KillFx.play(fx, x, y, z, skin, hideId)) {
			status = "kill " + kills + ": " + fx;
			return; // the 3D effect brings its own sounds
		}
		if (fx != null) {
			status = "could not load '" + fx + "': " + dev.solclient.fx.killfx.KillFxModel.lastError;
		}
		if (idx >= 6) {
			idx = 0; // not owned / could not load: lightning instead
		}
		switch (idx) {
			case 0 -> {
				for (double dy = 0; dy < 12; dy += 0.25) {
					Fx.spawn(ParticleTypes.ELECTRIC_SPARK, x, y + dy, z, 0, 0, 0);
				}
				Fx.burst(ParticleTypes.END_ROD, x, y + h / 2, z, 20, 0.3);
				if (sound.on() && mc.player != null) {
					mc.player.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, 1.4F);
				}
			}
			case 1 -> Fx.burst(ParticleTypes.DAMAGE_INDICATOR, x, y + h / 2, z, 30, 0.4);
			case 2 -> Fx.burst(ParticleTypes.HEART, x, y + h / 2, z, 14, 0.3);
			case 3 -> Fx.burst(ParticleTypes.SOUL, x, y + h / 2, z, 20, 0.15);
			case 4 -> Fx.burst(ParticleTypes.FIREWORK, x, y + h / 2, z, 40, 0.5);
			default -> Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, x, y + h / 2, z, 40, 0.6);
		}
		if (sound.on() && idx != 0 && mc.player != null) {
			mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 0.8F);
		}
	}
}
