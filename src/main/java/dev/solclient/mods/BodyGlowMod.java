package dev.solclient.mods;

import dev.solclient.option.Option;

/** Players near you glow through walls (outline). */
public class BodyGlowMod extends Mod {

	public static BodyGlowMod instance;
	private final Option.Num range = num("Range", 32, 4, 128, 4, " blocks");
	private final Option.Bool friendsOnly = bool("Friends Only", false);

	public BodyGlowMod() {
		super("Body Glow", "body_glow", "Outline around players near you.", Category.VISUAL);
		instance = this;
	}

	public static boolean glows(net.minecraft.entity.Entity e) {
		BodyGlowMod m = instance;
		if (m == null || !m.enabled || !(e instanceof net.minecraft.entity.player.PlayerEntity) || e == m.mc.player || m.mc.player == null) return false;
		if (e.squaredDistanceTo(m.mc.player) > m.range.f() * m.range.f()) return false;
		if (m.friendsOnly.on()) {
			String n = e.getName().getString();
			for (var f : dev.solclient.social.Social.friends) {
				if (n.equalsIgnoreCase(f.mcUsername) || n.equalsIgnoreCase(f.name)) return true;
			}
			return false;
		}
		return true;
	}
}
