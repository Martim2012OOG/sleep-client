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
	private final Option.Action test = action("Test Effect", "Test", this::test, () -> effect.label());
	private final Option.Bool sound = bool("Sound", true);
	private final Option.Bool playersOnly = bool("Players Only", false);
	/** entity id -> when you last hit it */
	private final Map<Integer, Long> hit = new HashMap<>();
	private final Map<Integer, Entity> ents = new HashMap<>();

	public KillEffectMod() {
		super("Kill Effect", "kill_effect", "Show an effect when something you hit dies.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || playersOnly.on() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) {
			return;
		}
		hit.put(e.getId(), System.currentTimeMillis());
		ents.put(e.getId(), e);
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, Long>> it = hit.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Long> en = it.next();
			Entity e = ents.get(en.getKey());
			if (e == null || now - en.getValue() > 4000) {
				it.remove();
				ents.remove(en.getKey());
				continue;
			}
			boolean dead = e instanceof LivingEntity l ? l.isDead() || l.getHealth() <= 0 : !e.isAlive();
			if (dead) {
				play(e);
				it.remove();
				ents.remove(en.getKey());
			}
		}
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
			if (dev.solclient.fx.killfx.KillFx.play(fx3d(), x, mc.player.getY(), z, skin, -1)) {
				return;
			}
		}
		play(x, mc.player.getY(), z, 1.8, null);
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

	private void play(Entity e) {
		play(e.getX(), e.getY(), e.getZ(), e.getHeight(), e);
	}

	private void play(double x, double y, double z, double h, Entity victim) {
		int idx = effect.idx();
		String fx = victim == null ? null : ownedFx3d();
		if (fx != null && dev.solclient.fx.killfx.KillFx.play(fx, victim)) {
			return; // the 3D effect brings its own sounds
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
