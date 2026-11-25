package dev.solclient.mods;

import dev.solclient.option.Option;

/**
 * Transparent Players: other players are drawn see-through (like an invisible player seen by a spectator), so they
 * never block your view in fights. Their armour, items and nametag still show.
 */
public class TransparentPlayersMod extends Mod {

	public static TransparentPlayersMod instance;

	private final Option.Num range = num("Only Closer Than", 6, 1, 64, 1, " blocks");
	private final Option.Bool friends = bool("Also Caos Friends", true);

	public TransparentPlayersMod() {
		super("Transparent Players", "transparent_players", "Other players are see-through so they never block your view.", Category.VISUAL);
		instance = this;
	}

	/** True when this player should be drawn see-through. */
	public static boolean applies(net.minecraft.world.entity.Entity e) {
		if (instance == null || !instance.enabled || e == null || e == instance.mc.player || instance.mc.player == null) {
			return false;
		}
		double r = instance.range.f();
		if (instance.mc.player.distanceToSqr(e) > r * r) {
			return false;
		}
		if (!instance.friends.on() && e instanceof net.minecraft.world.entity.player.Player p) {
			for (dev.solclient.social.Social.User u : dev.solclient.social.Social.friends) {
				if (u.mcUsername != null && u.mcUsername.equalsIgnoreCase(p.getName().getString())) {
					return false;
				}
			}
		}
		return true;
	}
}
