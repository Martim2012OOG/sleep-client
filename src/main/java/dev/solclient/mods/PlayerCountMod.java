package dev.solclient.mods;

/** How many players are online on the server. */
public class PlayerCountMod extends TextHudMod {

	public PlayerCountMod() {
		super("Player Count", "player_count", "How many players are online on the server.", 0.01F, 0.79F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.getNetworkHandler() == null) return edit ? "Players: 12" : null;
		return "Players: " + mc.getNetworkHandler().getPlayerList().size();
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
