package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Bubbles pop around whatever you hit. */
public class HitBubblesMod extends Mod {

	public static HitBubblesMod instance;
	private final Option.Num amount = num("Bubbles", 14, 4, 40, 1, "");
	private final Option.Bool playersOnly = bool("Players Only", false);

	public HitBubblesMod() {
		super("Hit Bubbles", "hit_bubbles", "Bubbles pop around whatever you hit.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || playersOnly.on() && !(e instanceof net.minecraft.world.entity.player.Player)) {
			return;
		}
		Fx.burst(ParticleTypes.BUBBLE_POP, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), amount.i(), 0.5);
	}
}
