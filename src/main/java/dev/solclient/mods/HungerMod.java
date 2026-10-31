package dev.solclient.mods;

/** Food level and saturation. */
public class HungerMod extends TextHudMod {

	public HungerMod() {
		super("Hunger", "hunger", "Food level and saturation.", 0.01F, 0.67F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Food 20  Sat 5" : null;
		var h = mc.player.getHungerManager();
		return "Food " + h.getFoodLevel() + "  Sat " + fmt(h.getSaturationLevel());
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
