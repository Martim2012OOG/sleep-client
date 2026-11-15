package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class ParticlesMod extends Mod {

	private final Option.Num multiplier = num("Multiplier", 4, 1, 10, 1, "");
	private final Option.Bool sharpness = bool("Always Sharpness", true);
	private final Option.Bool snow = bool("Snow", false);
	private final Option.Bool slime = bool("Slime", false);
	private final Option.Bool flames = bool("Flames", false);

	public ParticlesMod() {
		super("Particles", "particles", "Change attack particles.", Category.VISUAL);
	}

	private void emit(Entity victim, ParticleOptions type, int times) {
		for (int i = 0; i < times; i++) {
			mc.particleEngine.createTrackingEmitter(victim, type);
		}
	}

	public void onAttack(Entity victim) {
		Player player = mc.player;
		if (player == null || !(victim instanceof LivingEntity)) {
			return;
		}
		int m = multiplier.i();
		boolean crit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable()
				&& !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger();
		if (crit) {
			emit(victim, ParticleTypes.CRIT, m - 1);
		}
		if (sharpness.on()) {
			emit(victim, ParticleTypes.ENCHANTED_HIT, m);
		}
		if (snow.on()) {
			emit(victim, ParticleTypes.ITEM_SNOWBALL, m);
		}
		if (slime.on()) {
			emit(victim, ParticleTypes.ITEM_SLIME, m);
		}
		if (flames.on()) {
			emit(victim, ParticleTypes.FLAME, m);
		}
	}
}
