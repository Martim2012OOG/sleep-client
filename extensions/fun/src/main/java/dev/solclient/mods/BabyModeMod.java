package dev.solclient.mods;

import dev.solclient.option.Option;

/** Players look tiny. */
public class BabyModeMod extends Mod {

	public static BabyModeMod instance;
	private final Option.Choice who = choice("Who", 0, "Everyone", "Only Me", "Others");
	private final Option.Num size = num("Size", 0.5F, 0.3F, 0.9F, 0.05F, "x");

	public BabyModeMod() {
		super("Baby Mode", "baby_mode", "Players look tiny.", Category.VISUAL);
		instance = this;
	}

	public static float scaleFor(net.minecraft.entity.Entity e) {
		BabyModeMod m = instance;
		if (m == null || !m.enabled || !(e instanceof net.minecraft.entity.player.PlayerEntity)) return 1F;
		boolean me = e == m.mc.player;
		if (m.who.idx() == 1 && !me || m.who.idx() == 2 && me) return 1F;
		return m.size.f();
	}
}
