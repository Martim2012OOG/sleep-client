package dev.sleepclient.mods;

/** Your yaw and pitch. */
public class RotationMod extends TextHudMod {

	public RotationMod() {
		super("Rotation", "rotation", "Your yaw and pitch.", 0.01F, 0.85F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Yaw 90.0  Pitch 0.0" : null;
		return "Yaw " + String.format("%.1f", net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot())) + "  Pitch " + String.format("%.1f", mc.player.getXRot());
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
