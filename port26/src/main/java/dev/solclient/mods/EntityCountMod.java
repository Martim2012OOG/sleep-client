package dev.solclient.mods;

/** How many entities are loaded around you. */
public class EntityCountMod extends TextHudMod {

	public EntityCountMod() {
		super("Entity Count", "entity_count", "How many entities are loaded around you.", 0.01F, 0.58F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.level == null) return edit ? "Entities: 42" : null;
		return "Entities: " + mc.level.getEntityCount();
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
