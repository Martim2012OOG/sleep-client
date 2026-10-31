package dev.solclient.mods;

/** Remembers where you died and shows the way back. */
public class DeathPointMod extends TextHudMod {

	private double dx, dy, dz;
	private String dim;
	private boolean has, wasDead;

	public DeathPointMod() {
		super("Death Point", "death_point", "Shows where you last died and how far it is.", 0.01F, 0.30F);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null || mc.world == null) return;
		boolean dead = p.isDead() || p.getHealth() <= 0;
		if (dead && !wasDead) {
			dx = p.getX(); dy = p.getY(); dz = p.getZ();
			dim = mc.world.getRegistryKey().getValue().getPath();
			has = true;
			if (mc.inGameHud != null) {
				mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Death point: " + (int) dx + " " + (int) dy + " " + (int) dz).formatted(net.minecraft.util.Formatting.RED));
			}
		}
		wasDead = dead;
	}

	@Override
	protected String text(boolean edit) {
		if (!has || mc.player == null || mc.world == null) return edit ? "Death: 120 64 -30 (52m)" : null;
		String here = mc.world.getRegistryKey().getValue().getPath();
		String where = (int) dx + " " + (int) dy + " " + (int) dz;
		if (!here.equals(dim)) return "Death: " + where + " (" + dim + ")";
		double d = Math.sqrt(mc.player.squaredDistanceTo(dx, dy, dz));
		return "Death: " + where + " (" + Math.round(d) + "m)";
	}
}
