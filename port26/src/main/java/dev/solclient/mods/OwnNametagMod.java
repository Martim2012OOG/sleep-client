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

	/** True when the server floats its OWN plate over you (text display, armor stand with a name, or something riding you). Team-hidden names do not count. */
	public static boolean customPlate(net.minecraft.world.entity.player.Player p) {
		long now = System.currentTimeMillis();
		if (now - plateAt < 400L) {
			return plateCached; // the entity search runs a few times a second, not every frame
		}
		plateAt = now;
		plateCached = customPlateNow(p);
		return plateCached;
	}

	private static long plateAt;
	private static boolean plateCached;

	private static boolean customPlateNow(net.minecraft.world.entity.player.Player p) {
		try {
			if (p.isVehicle()) {
				return true;
			}
			var world = net.minecraft.client.Minecraft.getInstance().level;
			if (world != null) {
				net.minecraft.world.phys.AABB box = p.getBoundingBox().inflate(0.6, 0.0, 0.6).expandTowards(0.0, 2.8, 0.0);
				return !world.getEntities(p, box, e -> e instanceof net.minecraft.world.entity.Display.TextDisplay
						|| e instanceof net.minecraft.world.entity.decoration.ArmorStand a && a.isCustomNameVisible()).isEmpty();
			}
		} catch (RuntimeException ignored) {
		}
		return false;
	}

	private static long checkedAt;
	private static boolean cached;

	/**
	 * True when the server already handles the name above your head (a team that hides nametags, a text display or an armor stand
	 * riding you / floating over you, like MCPVP and other servers do). Then we must not draw our own nametag on top.
	 */
	public static boolean serverHandlesName(net.minecraft.world.entity.player.Player p) {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 400L) {
			return cached;
		}
		checkedAt = now;
		boolean r = false;
		try {
			var team = p.getTeam();
			if (team != null && team.getNameTagVisibility() != net.minecraft.world.scores.Team.Visibility.ALWAYS) {
				r = true;
			}
			if (!r && p.isVehicle()) {
				r = true;
			}
			var world = net.minecraft.client.Minecraft.getInstance().level;
			if (!r && world != null) {
				net.minecraft.world.phys.AABB box = p.getBoundingBox().inflate(0.6, 0.0, 0.6).expandTowards(0.0, 2.8, 0.0);
				r = !world.getEntities(p, box, e -> e instanceof net.minecraft.world.entity.Display.TextDisplay
						|| e instanceof net.minecraft.world.entity.decoration.ArmorStand a && a.isCustomNameVisible()).isEmpty();
			}
		} catch (RuntimeException e) {
			r = false;
		}
		cached = r;
		return r;
	}
}
