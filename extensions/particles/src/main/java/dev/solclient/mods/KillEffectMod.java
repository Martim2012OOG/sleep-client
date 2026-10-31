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
	private final Option.Choice effect = choice("Effect", 0, "Lightning", "Blood", "Hearts", "Souls", "Firework", "Totem");
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

	private void play(Entity e) {
		double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getHeight();
		switch (effect.idx()) {
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
		if (sound.on() && effect.idx() != 0 && mc.player != null) {
			mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 0.8F);
		}
	}
}
