package dev.solclient.mods;

/** The biome you are standing in. */
public class BiomeMod extends TextHudMod {

	public BiomeMod() {
		super("Biome", "biome", "The biome you are standing in.", 0.01F, 0.52F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.level == null || mc.player == null) return edit ? "Plains" : null;
		return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> nice(k.identifier().getPath())).orElse("?");
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
