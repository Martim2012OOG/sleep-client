package dev.sleepclient.mods;

/** The chunk you are in (and your position inside it). */
public class ChunkPosMod extends TextHudMod {

	public ChunkPosMod() {
		super("Chunk Position", "chunk_pos", "The chunk you are in (and your position inside it).", 0.01F, 0.61F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Chunk 0, 0" : null;
		var c = mc.player.chunkPosition();
		var p = mc.player.blockPosition();
		return "Chunk " + c.x() + ", " + c.z() + "  (" + (p.getX() & 15) + ", " + (p.getZ() & 15) + ")";
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
