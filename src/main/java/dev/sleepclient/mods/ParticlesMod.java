package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

public class ParticlesMod extends Mod {

	private final Option.Num multiplier = num("Multiplier", 4, 1, 10, 1, "");
	private final Option.Bool sharpness = bool("Always Sharpness", true);
	private final Option.Bool snow = bool("Snow", false);
	private final Option.Bool slime = bool("Slime", false);
	private final Option.Bool flames = bool("Flames", false);

	public ParticlesMod() {
		super("Particles", "particles", "Change attack particles.", Category.VISUAL);
	}

	private void emit(Entity victim, ParticleEffect type, int times) {
		for (int i = 0; i < times; i++) {
			mc.particleManager.addEmitter(victim, type);
		}
	}

	public void onAttack(Entity victim) {
		PlayerEntity player = mc.player;
		if (player == null || !(victim instanceof LivingEntity)) {
			return;
		}
		int m = multiplier.i();
		boolean crit = player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing()
				&& !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && !player.hasVehicle();
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
