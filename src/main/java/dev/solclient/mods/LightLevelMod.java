package dev.solclient.mods;

/** Block light where you stand (mobs spawn at 0). */
public class LightLevelMod extends TextHudMod {

	public LightLevelMod() {
		super("Light Level", "light_level", "Block light where you stand (mobs spawn at 0).", 0.01F, 0.55F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.world == null || mc.player == null) return edit ? "Light 15" : null;
		int l = mc.world.getLightLevel(net.minecraft.world.LightType.BLOCK, mc.player.getBlockPos());
		return "Light " + l;
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
