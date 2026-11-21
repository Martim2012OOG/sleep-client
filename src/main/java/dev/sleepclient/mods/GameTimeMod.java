package dev.sleepclient.mods;

/** The time inside the Minecraft world. */
public class GameTimeMod extends TextHudMod {

	public GameTimeMod() {
		super("Game Time", "game_time", "The time inside the Minecraft world.", 0.01F, 0.76F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.world == null) return edit ? "Day 12:00" : null;
		long t = Math.floorMod(mc.world.getTimeOfDay() + 6000L, 24000L);
		int h = (int) (t / 1000L), m = (int) ((t % 1000L) * 60L / 1000L);
		long day = mc.world.getTimeOfDay() % 24000L;
		return (day >= 13000L && day < 23000L ? "Night " : "Day ") + String.format("%02d:%02d", h, m);
	}

	static String fmt(float f) {
		return f == Math.floor(f) ? String.valueOf((int) f) : String.format("%.1f", f);
	}

	static String nice(String path) {
		StringBuilder b = new StringBuilder();
		for (String w : path.split("_")) {
			if (!w.isEmpty()) {
				b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
			}
		}
		return b.toString();
	}

	int count(net.minecraft.item.Item... items) {
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			var st = inv.getStack(i);
			for (var it : items) {
				if (st.isOf(it)) {
					n += st.getCount();
				}
			}
		}
		return n;
	}
}
