package dev.sleepclient.mods;

/** Your experience level. */
public class XpLevelMod extends TextHudMod {

	public XpLevelMod() {
		super("XP Level", "xp_level", "Your experience level.", 0.01F, 0.7F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Level 30" : null;
		return "Level " + mc.player.experienceLevel;
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

	int count(net.minecraft.world.item.Item... items) {
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var st = inv.getItem(i);
			for (var it : items) {
				if (st.is(it)) {
					n += st.getCount();
				}
			}
		}
		return n;
	}
}
