package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;

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
		if (!(e instanceof LivingEntity) || playersOnly.on() && !(e instanceof net.minecraft.entity.player.PlayerEntity)) {
			return;
		}
		Fx.burst(ParticleTypes.BUBBLE_POP, e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), amount.i(), 0.5);
	}
}
