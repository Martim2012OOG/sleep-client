package dev.solclient.mods;

/** Shows your own name (with the Plegumo logo) above your head in third person (F5), like Lunar. */
public class OwnNametagMod extends Mod {

	public static OwnNametagMod instance;

	public OwnNametagMod() {
		super("Own Nametag", "own_nametag", "See your own name and logo above your head in third person (F5).", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	private static long checkedAt;
	private static boolean cached;

	/**
	 * True when the server already handles the name above your head (a team that hides nametags, a text display or an armor stand
	 * riding you / floating over you, like MCPVP and other servers do). Then we must not draw our own nametag on top.
	 */
	public static boolean serverHandlesName(net.minecraft.entity.player.PlayerEntity p) {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 400L) {
			return cached;
		}
		checkedAt = now;
		boolean r = false;
		try {
			var team = p.getScoreboardTeam();
			if (team != null && team.getNameTagVisibilityRule() != net.minecraft.scoreboard.AbstractTeam.VisibilityRule.ALWAYS) {
				r = true;
			}
			if (!r && p.hasPassengers()) {
				r = true;
			}
			var world = net.minecraft.client.MinecraftClient.getInstance().world;
			if (!r && world != null) {
				net.minecraft.util.math.Box box = p.getBoundingBox().expand(0.6, 0.0, 0.6).stretch(0.0, 2.8, 0.0);
				r = !world.getOtherEntities(p, box, e -> e instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
						|| e instanceof net.minecraft.entity.decoration.ArmorStandEntity a && a.isCustomNameVisible()).isEmpty();
			}
		} catch (RuntimeException e) {
			r = false;
		}
		cached = r;
		return r;
	}
}
